using System.Text.Json;
using System.Threading.RateLimiting;
using InfraLab.Application;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
if (builder.Environment.IsProduction())
{
    builder.Logging.ClearProviders();
    builder.Logging.AddJsonConsole(options =>
    {
        options.IncludeScopes = true;
        options.TimestampFormat = "O";
        options.UseUtcTimestamp = true;
    });
}
builder.Services.AddOpenApi();
builder.Services.AddSingleton<CommandEngine>();
builder.Services.AddSingleton<ScenarioEngine>();
builder.Services.AddSingleton<ScoringEngine>();
var scenarioValidator = new ScenarioDefinitionValidator();
builder.Services.AddSingleton(scenarioValidator);

var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
if (string.IsNullOrWhiteSpace(connectionString))
    throw new InvalidOperationException("PostgreSQL configuration is required. Set ConnectionStrings__DefaultConnection.");

builder.Services.AddDbContext<InfraLabDbContext>(options => options.UseNpgsql(connectionString));
builder.Services.AddScoped<IAttemptStore, EfAttemptStore>();

// Content path: override via InfraLab:ContentPath for Docker; defaults to dev relative path.
// The catalog is constructed once at startup and reused by request handlers and readiness checks.
var contentRoot = builder.Configuration["InfraLab:ContentPath"]
    ?? Path.GetFullPath(Path.Combine(builder.Environment.ContentRootPath, "..", "..", "content"));
var scenarios = Directory.EnumerateFiles(contentRoot, "*.json", SearchOption.AllDirectories)
    .Select(path => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(path), new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
        ?? throw new InvalidOperationException("Scenario JSON could not be read."));
var catalog = ScenarioCatalog.Create(scenarios, scenarioValidator);
builder.Services.AddSingleton(catalog);
builder.Services.AddScoped<IInfraLabReadinessProbe, InfraLabReadinessProbe>();

// Rate limiting — named policy applied to attempt creation in production
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddFixedWindowLimiter("create-attempt", policy =>
    {
        policy.Window = TimeSpan.FromMinutes(1);
        policy.PermitLimit = 20;
        policy.QueueLimit = 0;
        policy.AutoReplenishment = true;
    });
});

var app = builder.Build();
var applicationLogger = app.Services.GetRequiredService<ILoggerFactory>().CreateLogger("InfraLab.Observability");
var applicationVersion = Observability.GetApplicationVersion(builder.Configuration);

applicationLogger.LogInformation(ObservabilityEvents.CatalogLoaded,
    "Scenario catalog loaded successfully. ScenarioCount: {ScenarioCount}.", catalog.Scenarios.Count());
applicationLogger.LogInformation(ObservabilityEvents.ApplicationStarted,
    "Application started. Environment: {Environment}. ApplicationVersion: {ApplicationVersion}.", app.Environment.EnvironmentName, applicationVersion);
app.Lifetime.ApplicationStopping.Register(() => applicationLogger.LogInformation(ObservabilityEvents.ApplicationStopping,
    "Application stopping. Environment: {Environment}. ApplicationVersion: {ApplicationVersion}.", app.Environment.EnvironmentName, applicationVersion));

if (!app.Environment.IsDevelopment())
{
    // Production only accepts forwarded headers from the explicitly configured proxy network.
    // Do not enable this middleware without a trust boundary.
    var forwardedOptions = ForwardedHeadersConfiguration.Create(
        builder.Configuration[ForwardedHeadersConfiguration.TrustedProxyNetworksConfigurationKey]);
    app.UseForwardedHeaders(forwardedOptions);
}

app.Use((context, next) => Observability.LogRequestAsync(context, next, applicationLogger, app.Environment.EnvironmentName, applicationVersion));
if (!app.Environment.IsDevelopment())
{
    // Keep request logging outside the handler so the final 500 status is recorded with its correlation scope.
    app.UseExceptionHandler(errorApp => errorApp.Run(context =>
        Observability.WriteSafeExceptionAsync(context, applicationLogger)));
}
app.UseRateLimiter();
app.UseBlazorFrameworkFiles();
app.UseStaticFiles();
if (app.Environment.IsDevelopment()) app.MapOpenApi();

// Liveness is intentionally dependency-free. Readiness checks PostgreSQL and the cached, validated scenario catalog.
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy" })).DisableRateLimiting();
app.MapGet("/health/ready", ReadyAsync).DisableRateLimiting();
// Backward compatibility for existing probes; it has the same readiness contract as /health/ready.
app.MapGet("/api/health", ReadyAsync).DisableRateLimiting();

app.MapGet("/api/scenarios", () => catalog.Scenarios.Select(PublicScenario));
app.MapGet("/api/scenarios/{id}", (string id) => catalog.TryGet(id, out var scenario) ? Results.Ok(PublicScenario(scenario)) : Results.NotFound());
var createAttempt = app.MapPost("/api/scenarios/{id}/attempts", async (string id, IAttemptStore store, CancellationToken ct) =>
{
    if (!catalog.TryGet(id, out var scenario)) return Results.NotFound();
    var attempt = await store.StartAsync(Guid.NewGuid(), scenario.Id, scenario.Version.Id, ct);
    return Results.Created($"/api/attempts/{attempt.Id}", PublicAttempt(attempt, scenario));
});
if (app.Environment.IsProduction()) createAttempt.RequireRateLimiting("create-attempt");

app.MapGet("/api/attempts/{id:guid}", async (Guid id, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? Results.Ok(PublicAttempt(attempt, scenario))
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/commands", async (Guid id, ActionRequest request, CommandEngine commands, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    if (attempt is null || !catalog.TryGet(attempt.ScenarioId, out var scenario)) return Results.NotFound();
    var match = commands.Match(request.Input ?? string.Empty, scenario.Version.Actions);
    if (match.ActionId is null) return Results.UnprocessableEntity(new { detail = match.Message });
    return await ExecuteAsync(id, request, match.ActionId, request.Input ?? string.Empty, "command", scenario, engine, scoring, store, ct, true);
});
app.MapPost("/api/attempts/{id:guid}/actions", async (Guid id, ActionRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? await ExecuteAsync(id, request, request.ActionId, request.ActionId ?? string.Empty, "action", scenario, engine, scoring, store, ct, false)
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/advance-to-diagnosis", async (Guid id, AdvanceToDiagnosisRequest request, ScenarioEngine engine, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    if (attempt is null || !catalog.TryGet(attempt.ScenarioId, out var scenario)) return Results.NotFound();
    try
    {
        var result = await store.MutateAsync(id, request.StateVersion, request.IdempotencyKey, "advance-to-diagnosis", "advance-to-diagnosis", string.Empty,
            state => engine.AdvanceToDiagnosis(scenario, state, request.StateVersion), _ => null, ct);
        return Results.Ok(PublicAttempt(result.Attempt, scenario));
    }
    catch (AttemptConcurrencyException) { return Results.Conflict(new { detail = "StateVersion conflict." }); }
    catch (AttemptCompletedException) { return Results.UnprocessableEntity(new { detail = "Attempt is completed." }); }
    catch (InvalidOperationException ex) { return Results.UnprocessableEntity(new { detail = ex.Message }); }
});
app.MapPost("/api/attempts/{id:guid}/diagnosis", async (Guid id, SubmitDiagnosisRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "diagnosis", request.OptionId, request.OptionId, state => engine.AnswerDiagnosis(scenario, state, request.OptionId, request.StateVersion), scenario, scoring, store, ct, request.OptionId == scenario.Version.Private.CorrectDiagnosisId, request.OptionId is null ? null : (request.OptionId == scenario.Version.Private.CorrectDiagnosisId ? "Correct. Moving to remediation…" : scenario.Version.Private.DiagnosisHints.GetValueOrDefault(request.OptionId)))
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/remediation", async (Guid id, SubmitRemediationRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "remediation", request.OptionId, request.OptionId, state => engine.AnswerRemediation(scenario, state, request.OptionId, request.StateVersion), scenario, scoring, store, ct, request.OptionId == scenario.Version.Private.CorrectRemediationId, request.OptionId is null ? null : (request.OptionId == scenario.Version.Private.CorrectRemediationId ? "Correct. Moving to verification…" : scenario.Version.Private.RemediationHints.GetValueOrDefault(request.OptionId)))
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/verification", async (Guid id, SubmitVerificationRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    if (attempt is null || !catalog.TryGet(attempt.ScenarioId, out var scenario)) return Results.NotFound();
    var optionIds = request.OptionIds?.ToArray();
    if (optionIds is null || optionIds.Length == 0 || optionIds.Any(string.IsNullOrWhiteSpace) || optionIds.Distinct(StringComparer.Ordinal).Count() != optionIds.Length || optionIds.Any(optionId => !scenario.Version.Verifications.Any(x => x.Id == optionId))) return Results.UnprocessableEntity();
    if (scenario.Version.VerificationSelectionMode == VerificationSelectionMode.Single && optionIds.Length != 1) return Results.UnprocessableEntity(new { detail = "復旧確認は1つ選んでください。" });
    var canonical = scenario.Version.Verifications.Where(x => optionIds.Contains(x.Id, StringComparer.Ordinal)).Select(x => x.Id).ToArray();
    var verificationCorrect = scenario.Version.Private.RequiredVerificationIds.SetEquals(canonical);
    return await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "verification", string.Join("", canonical), string.Join("", canonical), state => engine.AnswerVerification(scenario, state, canonical, request.StateVersion), scenario, scoring, store, ct, verificationCorrect, verificationCorrect ? "Correct. Moving to results and explanation…" : scenario.Version.Private.VerificationHints.GetValueOrDefault(canonical[0]));
});
app.MapGet("/api/attempts/{id:guid}/result", async (Guid id, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    if (attempt is null) return Results.NotFound();
    if (attempt.Status != AttemptStatus.Completed || attempt.Score is null) return Results.UnprocessableEntity();
    if (!catalog.TryGet(attempt.ScenarioId, out var scenario)) return Results.NotFound();
    var score = attempt.Score;
    return Results.Ok(new PublicAttemptResult(attempt.Id, attempt.ScenarioId, scenario.Title, (int)attempt.Status, new PublicScoreBreakdown(score.Diagnosis, score.Remediation, score.Verification, score.Investigation, score.Safety, score.Total)));
});
app.MapGet("/api/attempts/{id:guid}/review", async (Guid id, IAttemptStore store, CancellationToken ct) =>
{
    var data = await store.FindReviewDataAsync(id, ct);
    if (data is null) return Results.NotFound();
    if (data.Attempt.Status != AttemptStatus.Completed || data.Attempt.Score is null) return Results.UnprocessableEntity();
    if (!catalog.TryGet(data.Attempt.ScenarioId, out var scenario)) return Results.NotFound();
    return TryPublicReview(data.Attempt, data.VerificationId, scenario, out var review)
        ? Results.Ok(review)
        : Results.UnprocessableEntity();
});
app.MapPost("/api/attempts/{id:guid}/retry", async (Guid id, IAttemptStore store, CancellationToken ct) =>
{
    var original = await store.FindAsync(id, ct);
    if (original is null) return Results.NotFound();
    if (!catalog.TryGet(original.ScenarioId, out var scenario)) return Results.NotFound();
    var retry = await store.StartAsync(Guid.NewGuid(), original.ScenarioId, original.ScenarioVersionId, ct);
    return Results.Created($"/api/attempts/{retry.Id}", PublicAttempt(retry, scenario));
});
app.MapFallbackToFile("index.html");
app.Run();

async Task<IResult> ReadyAsync(IInfraLabReadinessProbe readiness, CancellationToken ct) =>
    await readiness.CheckAsync(ct) is ReadinessStatus.Healthy
        ? Results.Ok(new { status = "Healthy" })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable);

async Task<IResult> ExecuteAsync(Guid id, ActionRequest request, string? actionId, string requestValue, string eventType, Scenario scenario, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct, bool useCommandEvidence)
{
    try
    {
        var result = await store.MutateAsync(id, request.StateVersion, request.IdempotencyKey, eventType, actionId ?? string.Empty, requestValue,
            state => useCommandEvidence ? engine.ExecuteCommand(scenario, state, actionId!, request.StateVersion) : engine.ExecuteAction(scenario, state, actionId!, request.StateVersion), state => state.Phase == ScenarioPhase.Review ? scoring.Calculate(scenario, state) : null, ct);
        return Results.Ok(PublicAttempt(result.Attempt, scenario));
    }
    catch (KeyNotFoundException) { return Results.NotFound(); }
    catch (AttemptConcurrencyException) { return Results.Conflict(new { detail = "StateVersion conflict." }); }
    catch (AttemptCompletedException) { return Results.UnprocessableEntity(new { detail = "Attempt is completed." }); }
    catch (InvalidOperationException ex) { return Results.UnprocessableEntity(new { detail = ex.Message }); }
}

async Task<IResult> AnswerAsync(Guid id, int stateVersion, string idempotencyKey, string eventType, string answerId, string? requestValue, Func<AttemptState, AttemptState> mutate, Scenario scenario, ScoringEngine scoring, IAttemptStore store, CancellationToken ct, bool isCorrect, string? feedback = null)
{
    try
    {
        var metadata = new MutationResultMetadata(isCorrect ? "Correct" : "Incorrect", feedback, answerId);
        var result = await store.MutateAsync(id, stateVersion, idempotencyKey, eventType, answerId, requestValue ?? answerId, mutate, state => state.Phase == ScenarioPhase.Review ? scoring.Calculate(scenario, state) : null, ct, metadata);
        var replayed = result.Metadata ?? metadata;
        return Results.Ok(new PublicAnswerResult(replayed.Outcome, PublicAttempt(result.Attempt, scenario), replayed.Feedback, replayed.SelectedOptionId));
    }
    catch (KeyNotFoundException) { return Results.NotFound(); }
    catch (AttemptConcurrencyException) { return Results.Conflict(new { detail = "StateVersion conflict." }); }
    catch (AttemptCompletedException) { return Results.UnprocessableEntity(new { detail = "Attempt is completed." }); }
    catch (InvalidOperationException ex) { return Results.UnprocessableEntity(new { detail = ex.Message }); }
}

ScenarioListItem PublicScenario(Scenario s) => new(s.Id, s.Title, s.Category, s.Difficulty, s.Summary);
PublicAttemptView PublicAttempt(Attempt a, Scenario s, string? feedback = null, string? answerOutcome = null, string? selectedOptionId = null) => new(a.Id, a.ScenarioId, s.Title, s.Symptoms.ToArray(), (int)a.Status, (int)a.State.Phase, a.State.StateVersion, s.Version.Evidence.Where(x => a.State.RevealedEvidenceIds.Contains(x.Id)).Select(x => new PublicEvidence(x.Id, x.Title, (int)x.Type, x.Text, x.Method, x.SampleOutput, x.Interpretation)).ToArray(), a.State.Phase is ScenarioPhase.Observe or ScenarioPhase.Investigate ? s.Version.Actions.Where(x => x.Phase == ScenarioPhase.Investigate).Select(x => new PublicActionView(x.Id, x.Label, x.RepresentativeCommand, a.State.ExecutedActionIds.Contains(x.Id), x.ExecutionExample is null ? null : new PublicExecutionExample(x.ExecutionExample.Kind, x.ExecutionExample.Text))).ToArray() : [], a.State.ExecutedActionIds.ToArray(), a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Diagnose ? s.Version.Diagnoses.Select(x => new PublicDiagnosisView(x.Id, x.Text)).ToArray() : [], a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Remediate ? s.Version.Remediations.Select(x => new PublicRemediationView(x.Id, x.Text)).ToArray() : [], a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Verify ? s.Version.Verifications.Select(x => new PublicVerificationView(x.Id, x.Text)).ToArray() : [], s.Version.SupportsCommandInput, feedback, (int)s.Version.VerificationSelectionMode, a.Status == AttemptStatus.InProgress && new ScenarioEngine().CanAdvanceToDiagnosis(s, a.State), IncorrectIds(a.State), answerOutcome, selectedOptionId);

string[] IncorrectIds(AttemptState state) => state.Phase switch { ScenarioPhase.Diagnose => state.IncorrectDiagnosisIds.ToArray(), ScenarioPhase.Remediate => state.IncorrectRemediationIds.ToArray(), ScenarioPhase.Verify => state.IncorrectVerificationIds.ToArray(), _ => [] };

bool TryPublicReview(Attempt attempt, string? verificationId, Scenario currentScenario, out PublicAttemptReview? review)
{
    review = null;
    var score = attempt.Score;
    if (score is null || string.IsNullOrWhiteSpace(attempt.State.DiagnosisId) || string.IsNullOrWhiteSpace(attempt.State.RemediationId))
        return false;
    var diagnosis = currentScenario.Version.Diagnoses.SingleOrDefault(x => x.Id == attempt.State.DiagnosisId);
    var remediation = currentScenario.Version.Remediations.SingleOrDefault(x => x.Id == attempt.State.RemediationId);
    var selectedVerifications = currentScenario.Version.Verifications.Where(x => attempt.State.VerificationIds.Contains(x.Id)).ToArray();
    if (diagnosis is null || remediation is null || selectedVerifications.Length != attempt.State.VerificationIds.Count)
        return false;
    review = new(attempt.Id, attempt.ScenarioId, currentScenario.Title,
        new(diagnosis.Text, diagnosis.Text, true, currentScenario.Version.Private.DiagnosisSuccessFeedback.GetValueOrDefault(attempt.State.DiagnosisId), score.Diagnosis, 30, attempt.State.IncorrectDiagnosisIds.Count),
        new(remediation.Text, remediation.Text, true, currentScenario.Version.Private.RemediationSuccessFeedback.GetValueOrDefault(attempt.State.RemediationId), score.Remediation, 30, attempt.State.IncorrectRemediationIds.Count),
        new(selectedVerifications.Select(x => x.Text).ToArray(), selectedVerifications.Select(x => x.Text).ToArray(), true, verificationId is null ? null : currentScenario.Version.Private.VerificationSuccessFeedback.GetValueOrDefault(verificationId), score.Verification, 30, attempt.State.IncorrectVerificationIds.Count));
    return true;
}

public partial class Program;
