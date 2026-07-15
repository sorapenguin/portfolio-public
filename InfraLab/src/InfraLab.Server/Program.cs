using System.Text.Json;
using InfraLab.Application;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddOpenApi();
builder.Services.AddSingleton<CommandEngine>();
builder.Services.AddSingleton<ScenarioEngine>();
builder.Services.AddSingleton<ScoringEngine>();

var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
if (string.IsNullOrWhiteSpace(connectionString))
    throw new InvalidOperationException("PostgreSQL configuration is required. Set ConnectionStrings__DefaultConnection.");

builder.Services.AddDbContext<InfraLabDbContext>(options => options.UseNpgsql(connectionString));
builder.Services.AddScoped<IAttemptStore, EfAttemptStore>();

var app = builder.Build();
app.UseBlazorFrameworkFiles();
app.UseStaticFiles();
if (app.Environment.IsDevelopment()) app.MapOpenApi();

var contentRoot = Path.GetFullPath(Path.Combine(app.Environment.ContentRootPath, "..", "..", "content"));
var scenarios = Directory.EnumerateFiles(contentRoot, "*.json", SearchOption.AllDirectories)
    .Select(path => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(path), new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
        ?? throw new InvalidOperationException("Scenario JSON could not be read."));
var catalog = ScenarioCatalog.Create(scenarios);

app.MapGet("/api/scenarios", () => catalog.Scenarios.Select(PublicScenario));
app.MapGet("/api/scenarios/{id}", (string id) => catalog.TryGet(id, out var scenario) ? Results.Ok(PublicScenario(scenario)) : Results.NotFound());
app.MapPost("/api/scenarios/{id}/attempts", async (string id, IAttemptStore store, CancellationToken ct) =>
{
    if (!catalog.TryGet(id, out var scenario)) return Results.NotFound();
    var attempt = await store.StartAsync(Guid.NewGuid(), scenario.Id, scenario.Version.Id, ct);
    return Results.Created($"/api/attempts/{attempt.Id}", PublicAttempt(attempt, scenario));
});
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
app.MapPost("/api/attempts/{id:guid}/diagnosis", async (Guid id, SubmitDiagnosisRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "diagnosis", request.OptionId, request.OptionId, state => engine.AnswerDiagnosis(scenario, state, request.OptionId, request.StateVersion), scenario, scoring, store, ct)
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/remediation", async (Guid id, SubmitRemediationRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    return attempt is not null && catalog.TryGet(attempt.ScenarioId, out var scenario)
        ? await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "remediation", request.OptionId, request.OptionId, state => engine.AnswerRemediation(scenario, state, request.OptionId, request.StateVersion), scenario, scoring, store, ct)
        : Results.NotFound();
});
app.MapPost("/api/attempts/{id:guid}/verification", async (Guid id, SubmitVerificationRequest request, ScenarioEngine engine, ScoringEngine scoring, IAttemptStore store, CancellationToken ct) =>
{
    var attempt = await store.FindAsync(id, ct);
    if (attempt is null || !catalog.TryGet(attempt.ScenarioId, out var scenario)) return Results.NotFound();
    var optionIds = request.OptionIds?.ToArray();
    if (optionIds is null || optionIds.Length == 0 || optionIds.Any(string.IsNullOrWhiteSpace) || optionIds.Distinct(StringComparer.Ordinal).Count() != optionIds.Length || optionIds.Any(optionId => !scenario.Version.Verifications.Any(x => x.Id == optionId))) return Results.UnprocessableEntity();
    var canonical = scenario.Version.Verifications.Where(x => optionIds.Contains(x.Id, StringComparer.Ordinal)).Select(x => x.Id).ToArray();
    return await AnswerAsync(id, request.StateVersion, request.IdempotencyKey, "verification", string.Join("\u001F", canonical), string.Join("\u001F", canonical), state => engine.AnswerVerification(scenario, state, canonical, request.StateVersion), scenario, scoring, store, ct);
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

async Task<IResult> AnswerAsync(Guid id, int stateVersion, string idempotencyKey, string eventType, string answerId, string? requestValue, Func<AttemptState, AttemptState> mutate, Scenario scenario, ScoringEngine scoring, IAttemptStore store, CancellationToken ct)
{
    try
    {
        var result = await store.MutateAsync(id, stateVersion, idempotencyKey, eventType, answerId, requestValue ?? answerId, mutate, state => state.Phase == ScenarioPhase.Review ? scoring.Calculate(scenario, state) : null, ct);
        return Results.Ok(PublicAttempt(result.Attempt, scenario));
    }
    catch (KeyNotFoundException) { return Results.NotFound(); }
    catch (AttemptConcurrencyException) { return Results.Conflict(new { detail = "StateVersion conflict." }); }
    catch (AttemptCompletedException) { return Results.UnprocessableEntity(new { detail = "Attempt is completed." }); }
    catch (InvalidOperationException ex) { return Results.UnprocessableEntity(new { detail = ex.Message }); }
}

ScenarioListItem PublicScenario(Scenario s) => new(s.Id, s.Title, s.Category, s.Difficulty, s.Summary);
PublicAttemptView PublicAttempt(Attempt a, Scenario s) => new(a.Id, a.ScenarioId, s.Title, s.Symptoms.ToArray(), (int)a.Status, (int)a.State.Phase, a.State.StateVersion, s.Version.Evidence.Where(x => a.State.RevealedEvidenceIds.Contains(x.Id)).Select(x => new PublicEvidence(x.Id, x.Title, (int)x.Type, x.Text)).ToArray(), a.State.Phase is ScenarioPhase.Observe or ScenarioPhase.Investigate ? s.Version.Actions.Where(x => x.Phase == ScenarioPhase.Investigate && (x.Repeatable || !a.State.ExecutedActionIds.Contains(x.Id))).Select(x => new PublicActionView(x.Id, x.Label)).ToArray() : [], a.State.ExecutedActionIds.ToArray(), a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Diagnose ? s.Version.Diagnoses.Select(x => new PublicDiagnosisView(x.Id, x.Text)).ToArray() : [], a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Remediate ? s.Version.Remediations.Select(x => new PublicRemediationView(x.Id, x.Text)).ToArray() : [], a.Status == AttemptStatus.InProgress && a.State.Phase == ScenarioPhase.Verify ? s.Version.Verifications.Select(x => new PublicVerificationView(x.Id, x.Text)).ToArray() : []);

bool TryPublicReview(Attempt attempt, string? verificationId, Scenario currentScenario, out PublicAttemptReview? review)
{
    review = null;
    var score = attempt.Score;
    if (score is null || string.IsNullOrWhiteSpace(attempt.State.DiagnosisId) || string.IsNullOrWhiteSpace(attempt.State.RemediationId))
        return false;
    var diagnosis = currentScenario.Version.Diagnoses.SingleOrDefault(x => x.Id == attempt.State.DiagnosisId);
    var expectedDiagnosis = currentScenario.Version.Diagnoses.SingleOrDefault(x => x.Id == currentScenario.Version.Private.CorrectDiagnosisId);
    var remediation = currentScenario.Version.Remediations.SingleOrDefault(x => x.Id == attempt.State.RemediationId);
    var expectedRemediation = currentScenario.Version.Remediations.SingleOrDefault(x => x.Id == currentScenario.Version.Private.CorrectRemediationId);
    var selectedVerifications = currentScenario.Version.Verifications.Where(x => attempt.State.VerificationIds.Contains(x.Id)).ToArray();
    var expectedVerifications = currentScenario.Version.Verifications.Where(x => currentScenario.Version.Private.RequiredVerificationIds.Contains(x.Id)).ToArray();
    if (diagnosis is null || expectedDiagnosis is null || remediation is null || expectedRemediation is null || selectedVerifications.Length != attempt.State.VerificationIds.Count || expectedVerifications.Length != currentScenario.Version.Private.RequiredVerificationIds.Count)
        return false;
    review = new(attempt.Id, attempt.ScenarioId, currentScenario.Title,
        new(diagnosis.Text, expectedDiagnosis.Text, score.Diagnosis > 0, null, score.Diagnosis, null),
        new(remediation.Text, expectedRemediation.Text, score.Remediation > 0, null, score.Remediation, null),
        new(selectedVerifications.Select(x => x.Text).ToArray(), expectedVerifications.Select(x => x.Text).ToArray(), score.Verification > 0, null, score.Verification, null));
    return true;
}

public partial class Program;
