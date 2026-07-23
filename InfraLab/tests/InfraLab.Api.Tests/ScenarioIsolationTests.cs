using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class ScenarioIsolationTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Scenario_catalog_contains_multiple_isolated_scenarios()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await ScenariosAsync(client);

        Assert.True(scenarios.Length >= 2);
        Assert.Equal(scenarios.Length, scenarios.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count());
        foreach (var scenario in scenarios)
        {
            using var detail = JsonDocument.Parse(await client.GetStringAsync($"/api/scenarios/{scenario.Id}"));
            Assert.Equal(["category", "difficulty", "id", "summary", "title"], detail.RootElement.EnumerateObject().Select(x => x.Name).Order());
            Assert.Equal(scenario.Id, detail.RootElement.GetProperty("id").GetString());
        }
    }

    [Fact]
    public async Task Starting_each_scenario_uses_its_own_definition()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await ScenariosAsync(client);
        var attempts = new List<PublicAttemptView>();

        foreach (var scenario in scenarios)
        {
            var attempt = await StartAsync(client, scenario.Id);
            Assert.Equal(scenario.Id, attempt.ScenarioId);
            Assert.Equal(scenario.Title, attempt.ScenarioTitle);
            Assert.Equal((int)ScenarioPhase.Observe, attempt.Phase);
            Assert.Equal((int)AttemptStatus.InProgress, attempt.Status);
            Assert.Equal(0, attempt.StateVersion);
            Assert.Empty(attempt.RevealedEvidence);
            Assert.NotEmpty(attempt.AvailableActions);
            Assert.Equal(attempt.AvailableActions.Length, attempt.AvailableActions.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count());
            attempts.Add(attempt);
        }

        Assert.Equal(attempts.Count, attempts.Select(x => x.Id).Distinct().Count());
    }

    [Fact]
    public async Task Completing_one_scenario_does_not_modify_another_attempt()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await ScenariosAsync(client);
        var completed = await StartAsync(client, scenarios[0].Id);
        var untouched = await StartAsync(client, scenarios[1].Id);
        var before = await SnapshotAsync(untouched.Id);

        completed = await ReachDiagnoseAsync(client, completed);
        completed = await SubmitAsync(client, completed, "diagnosis", new SubmitDiagnosisRequest(completed.AvailableDiagnoses[0].Id, completed.StateVersion, Guid.NewGuid().ToString("N")));
        completed = await SubmitAsync(client, completed, "remediation", new SubmitRemediationRequest(completed.AvailableRemediations[0].Id, completed.StateVersion, Guid.NewGuid().ToString("N")));
        completed = await SubmitAsync(client, completed, "verification", new SubmitVerificationRequest(completed.AvailableVerifications.Take(1).Select(x => x.Id).ToArray(), completed.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal((int)AttemptStatus.Completed, completed.Status);

        var after = await SnapshotAsync(untouched.Id);
        Assert.Equal(before, after);
        Assert.Equal(HttpStatusCode.UnprocessableEntity, (await client.GetAsync($"/api/attempts/{untouched.Id}/result")).StatusCode);
        Assert.Equal(HttpStatusCode.UnprocessableEntity, (await client.GetAsync($"/api/attempts/{untouched.Id}/review")).StatusCode);
    }

    private static async Task<PublicAttemptView> ReachDiagnoseAsync(HttpClient client, PublicAttemptView attempt)
    {
        return await ScenarioTestProgression.ReachDiagnoseAsync(client, attempt);
    }

    private static async Task<PublicAttemptView> StartAsync(HttpClient client, string scenarioId)
    {
        var response = await client.PostAsync($"/api/scenarios/{scenarioId}/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var attempt = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);
        return attempt!;
    }

    private static async Task<PublicAttemptView> SubmitAsync(HttpClient client, PublicAttemptView attempt, string operation, object request)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/{operation}", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<PublicAnswerResult>();
        Assert.NotNull(result);
        Assert.NotNull(result!.Attempt);
        Assert.Contains(result.Outcome, new[] { "Correct", "Incorrect" });
        return result.Attempt;
    }

    private async Task<AttemptSnapshot> SnapshotAsync(Guid attemptId)
    {
        await using var db = fixture.CreateContext();
        var attempt = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == attemptId);
        var events = await db.AttemptEvents.CountAsync(x => x.AttemptId == attemptId);
        return new(attempt.CurrentPhase, attempt.Status, attempt.StateVersion, events, attempt.ScoreJson is not null);
    }

    private static async Task<ScenarioListItem[]> ScenariosAsync(HttpClient client)
    {
        var scenarios = await client.GetFromJsonAsync<ScenarioListItem[]>("/api/scenarios");
        Assert.NotNull(scenarios);
        Assert.True(scenarios!.Length >= 2);
        return scenarios;
    }

    private sealed record AttemptSnapshot(ScenarioPhase Phase, AttemptStatus Status, int StateVersion, int EventCount, bool HasScore);
}
