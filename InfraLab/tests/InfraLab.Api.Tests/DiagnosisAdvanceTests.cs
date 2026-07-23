using System.Net;
using System.Net.Http.Json;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class DiagnosisAdvanceTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Required_evidence_only_sets_public_readiness_and_keeps_investigation_actions_available()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await StartAsync(client);

        attempt = await ExecuteAsync(client, attempt, "default-route");
        Assert.Equal((int)ScenarioPhase.Investigate, attempt.Phase);
        Assert.False(attempt.CanAdvanceToDiagnosis);
        Assert.Empty(attempt.AvailableDiagnoses);

        foreach (var actionId in new[] { "ip-connectivity", "hostname-resolution", "dns-server-query" })
            attempt = await ExecuteAsync(client, attempt, actionId);

        Assert.Equal((int)ScenarioPhase.Investigate, attempt.Phase);
        Assert.True(attempt.CanAdvanceToDiagnosis);
        Assert.Empty(attempt.AvailableDiagnoses);
        Assert.Contains(attempt.AvailableActions, action => action.Id == "interface-status");

        attempt = await ExecuteAsync(client, attempt, "interface-status");
        Assert.Equal((int)ScenarioPhase.Investigate, attempt.Phase);
        Assert.True(attempt.CanAdvanceToDiagnosis);
        Assert.Contains(attempt.AvailableActions, action => action.Id == "ip-address");
    }

    [Fact]
    public async Task Investigation_actions_remain_public_with_executed_status_and_cannot_be_replayed()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await StartAsync(client);
        Assert.Equal(8, attempt.AvailableActions.Length);
        Assert.All(attempt.AvailableActions, action =>
        {
            Assert.False(action.Executed);
            Assert.NotNull(action.ExecutionExample);
            Assert.False(string.IsNullOrWhiteSpace(action.ExecutionExample!.Text));
        });

        attempt = await ExecuteAsync(client, attempt, "interface-status");
        Assert.Equal(8, attempt.AvailableActions.Length);
        Assert.True(attempt.AvailableActions.Single(action => action.Id == "interface-status").Executed);
        var replay = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/actions", new ActionRequest("interface-status", null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, replay.StatusCode);
        var reloaded = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{attempt.Id}");
        Assert.NotNull(reloaded);
        Assert.True(reloaded!.AvailableActions.Single(action => action.Id == "interface-status").Executed);
    }

    [Fact]
    public async Task Explicit_advance_changes_phase_once_exposes_diagnoses_and_replays_idempotently()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var ready = await ReachReadyAsync(client);
        var key = Guid.NewGuid().ToString("N");

        var first = await client.PostAsJsonAsync($"/api/attempts/{ready.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(ready.StateVersion, key));
        var diagnosed = await first.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.NotNull(diagnosed);
        Assert.Equal((int)ScenarioPhase.Diagnose, diagnosed!.Phase);
        Assert.Equal(ready.StateVersion + 1, diagnosed.StateVersion);
        Assert.False(diagnosed.CanAdvanceToDiagnosis);
        Assert.NotEmpty(diagnosed.AvailableDiagnoses);
        Assert.Empty(diagnosed.AvailableActions);

        await using var beforeReplay = fixture.CreateContext();
        var eventCount = await beforeReplay.AttemptEvents.CountAsync(attemptEvent => attemptEvent.AttemptId == ready.Id);
        var replay = await client.PostAsJsonAsync($"/api/attempts/{ready.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(ready.StateVersion, key));
        var replayed = await replay.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
        Assert.NotNull(replayed);
        Assert.Equal(diagnosed.StateVersion, replayed!.StateVersion);
        await using var afterReplay = fixture.CreateContext();
        Assert.Equal(eventCount, await afterReplay.AttemptEvents.CountAsync(attemptEvent => attemptEvent.AttemptId == ready.Id));
    }

    [Fact]
    public async Task Advance_rejects_unready_and_stale_requests_without_leaking_private_progression_data()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var unready = await StartAsync(client);
        unready = await ExecuteAsync(client, unready, "default-route");

        var rejected = await client.PostAsJsonAsync($"/api/attempts/{unready.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(unready.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, rejected.StatusCode);
        var reloaded = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{unready.Id}");
        Assert.NotNull(reloaded);
        Assert.Equal(unready.StateVersion, reloaded!.StateVersion);
        Assert.Equal((int)ScenarioPhase.Investigate, reloaded.Phase);

        var ready = await ReachReadyAsync(client);
        var stale = await client.PostAsJsonAsync($"/api/attempts/{ready.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(ready.StateVersion - 1, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.Conflict, stale.StatusCode);
        var json = await client.GetStringAsync($"/api/attempts/{ready.Id}");
        foreach (var privateName in new[] { "requiredEvidenceIds", "investigationGroup", "core", "supplemental" })
            Assert.DoesNotContain(privateName, json, StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<PublicAttemptView> StartAsync(HttpClient client)
    {
        var response = await client.PostAsync("/api/scenarios/linux-dns-resolution-001/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        return await response.Content.ReadFromJsonAsync<PublicAttemptView>() ?? throw new InvalidOperationException();
    }

    private static async Task<PublicAttemptView> ReachReadyAsync(HttpClient client)
    {
        var attempt = await StartAsync(client);
        foreach (var actionId in new[] { "default-route", "ip-connectivity", "hostname-resolution", "dns-server-query" })
            attempt = await ExecuteAsync(client, attempt, actionId);
        Assert.True(attempt.CanAdvanceToDiagnosis);
        return attempt;
    }

    private static async Task<PublicAttemptView> ExecuteAsync(HttpClient client, PublicAttemptView attempt, string actionId)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/actions", new ActionRequest(actionId, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.Content.ReadFromJsonAsync<PublicAttemptView>() ?? throw new InvalidOperationException();
    }
}
