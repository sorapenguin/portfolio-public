using System.Net;
using System.Net.Http.Json;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class MutationContractTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    private static readonly string[] Mutations = ["action", "command", "diagnosis", "remediation", "verification"];

    [Fact]
    public async Task Null_empty_or_whitespace_required_values_return_422_without_persistence()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in Mutations)
        foreach (var value in new string?[] { null, string.Empty, "   " })
        {
            var attempt = await PrepareAsync(client, mutation);
            var key = Guid.NewGuid().ToString("N");
            var before = await SnapshotAsync(attempt.Id);
            var response = await SendAsync(client, attempt.Id, mutation, attempt, value, key, attempt.StateVersion);
            Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
            await AssertUnchangedAsync(attempt.Id, key, before);
        }
    }

    [Fact]
    public async Task Stale_state_version_returns_409_without_persistence()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in Mutations)
        {
            var attempt = await PrepareAsync(client, mutation);
            var value = ValidValue(mutation, attempt);
            var success = await SendAsync(client, attempt.Id, mutation, attempt, value, Guid.NewGuid().ToString("N"), attempt.StateVersion);
            Assert.Equal(HttpStatusCode.OK, success.StatusCode);
            var before = await SnapshotAsync(attempt.Id);
            var rejectedKey = Guid.NewGuid().ToString("N");
            var response = await SendAsync(client, attempt.Id, mutation, attempt, value, rejectedKey, attempt.StateVersion);
            Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
            await AssertUnchangedAsync(attempt.Id, rejectedKey, before);
            await AssertNoPrivateResponseAsync(response);
        }
    }

    [Fact]
    public async Task Same_key_and_same_request_replays_original_response_without_new_event()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in Mutations)
        {
            var attempt = await PrepareAsync(client, mutation);
            var value = ValidValue(mutation, attempt);
            var key = Guid.NewGuid().ToString("N");
            var first = await SendAsync(client, attempt.Id, mutation, attempt, value, key, attempt.StateVersion);
            var firstResult = await first.Content.ReadFromJsonAsync<PublicAttemptView>();
            var beforeReplay = await SnapshotAsync(attempt.Id);
            var replay = await SendAsync(client, attempt.Id, mutation, attempt, value, key, attempt.StateVersion);
            var replayResult = await replay.Content.ReadFromJsonAsync<PublicAttemptView>();
            Assert.Equal(HttpStatusCode.OK, first.StatusCode);
            Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
            Assert.NotNull(firstResult);
            Assert.NotNull(replayResult);
            Assert.Equal(firstResult.Id, replayResult.Id);
            Assert.Equal(firstResult.Phase, replayResult.Phase);
            Assert.Equal(firstResult.Status, replayResult.Status);
            Assert.Equal(firstResult.StateVersion, replayResult.StateVersion);
            Assert.Equal(firstResult.ExecutedActions.Order(), replayResult.ExecutedActions.Order());
            await AssertUnchangedAsync(attempt.Id, key, beforeReplay, requireKey: true);
        }
    }

    [Fact]
    public async Task Same_key_with_different_payload_or_state_version_returns_409_without_new_event()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in Mutations)
        {
            var attempt = await PrepareAsync(client, mutation);
            var value = ValidValue(mutation, attempt);
            var key = Guid.NewGuid().ToString("N");
            Assert.Equal(HttpStatusCode.OK, (await SendAsync(client, attempt.Id, mutation, attempt, value, key, attempt.StateVersion)).StatusCode);
            var before = await SnapshotAsync(attempt.Id);
            var differentPayload = await SendAsync(client, attempt.Id, mutation, attempt, DifferentValue(mutation, value), key, attempt.StateVersion);
            var differentVersion = await SendAsync(client, attempt.Id, mutation, attempt, value, key, attempt.StateVersion + 1);
            Assert.Equal(HttpStatusCode.Conflict, differentPayload.StatusCode);
            Assert.Equal(HttpStatusCode.Conflict, differentVersion.StatusCode);
            await AssertUnchangedAsync(attempt.Id, key, before, requireKey: true);
            await AssertNoPrivateResponseAsync(differentPayload);
            await AssertNoPrivateResponseAsync(differentVersion);
        }
    }

    [Fact]
    public async Task Same_key_cannot_be_reused_across_mutation_types()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await PrepareAsync(client, "action");
        var key = Guid.NewGuid().ToString("N");
        Assert.Equal(HttpStatusCode.OK, (await SendAsync(client, attempt.Id, "action", attempt, ValidValue("action", attempt), key, attempt.StateVersion)).StatusCode);
        var before = await SnapshotAsync(attempt.Id);

        var response = await SendAsync(client, attempt.Id, "command", attempt, ValidValue("command", attempt), key, attempt.StateVersion);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        await AssertUnchangedAsync(attempt.Id, key, before, requireKey: true);
    }

    [Fact]
    public async Task Unknown_action_or_command_returns_422_without_persistence()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in new[] { "action", "command" })
        {
            var attempt = await PrepareAsync(client, mutation);
            var key = Guid.NewGuid().ToString("N");
            var before = await SnapshotAsync(attempt.Id);
            var response = await SendAsync(client, attempt.Id, mutation, attempt, "unknown-value", key, attempt.StateVersion);
            Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
            await AssertUnchangedAsync(attempt.Id, key, before);
        }
    }

    [Fact]
    public async Task Missing_attempt_returns_404_without_creating_state()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        foreach (var mutation in Mutations)
        {
            var missingId = Guid.NewGuid();
            await using var beforeDb = fixture.CreateContext();
            var attemptCount = await beforeDb.Attempts.CountAsync();
            var eventCount = await beforeDb.AttemptEvents.CountAsync();
            var response = await SendMissingAttemptAsync(client, missingId, mutation);
            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
            await AssertNoPrivateResponseAsync(response);
            await using var afterDb = fixture.CreateContext();
            Assert.Equal(attemptCount, await afterDb.Attempts.CountAsync());
            Assert.Equal(eventCount, await afterDb.AttemptEvents.CountAsync());
        }
    }

    private async Task<PublicAttemptView> PrepareAsync(HttpClient client, string mutation)
    {
        var started = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var attempt = await started.Content.ReadFromJsonAsync<PublicAttemptView>() ?? throw new InvalidOperationException();
        var requiredPhase = Array.IndexOf(Mutations, mutation);
        if (requiredPhase >= 2)
        {
            foreach (var action in new[] { "app-status", "app-journal", "unit-cat", "ls-permissions" })
                attempt = await ReadAttemptAsync(await SendAsync(client, attempt.Id, "action", attempt, action, Guid.NewGuid().ToString("N"), attempt.StateVersion));
        }
        if (requiredPhase >= 3)
            attempt = await ReadAttemptAsync(await SendAsync(client, attempt.Id, "diagnosis", attempt, ValidValue("diagnosis", attempt), Guid.NewGuid().ToString("N"), attempt.StateVersion));
        if (requiredPhase >= 4)
            attempt = await ReadAttemptAsync(await SendAsync(client, attempt.Id, "remediation", attempt, ValidValue("remediation", attempt), Guid.NewGuid().ToString("N"), attempt.StateVersion));
        return attempt;
    }

    private static string ValidValue(string mutation, PublicAttemptView attempt) => mutation switch
    {
        "action" => attempt.AvailableActions[0].Id,
        "command" => "systemctl status app.service",
        "diagnosis" => attempt.AvailableDiagnoses[0].Id,
        "remediation" => attempt.AvailableRemediations[0].Id,
        "verification" => "__all__",
        _ => throw new ArgumentOutOfRangeException(nameof(mutation))
    };

    private static string DifferentValue(string mutation, string value) => mutation switch { "command" => value + " ", "verification" => "__first__", _ => "different-value" };

    private static Task<HttpResponseMessage> SendAsync(HttpClient client, Guid id, string mutation, PublicAttemptView attempt, string? value, string key, int stateVersion) => mutation switch
    {
        "action" => client.PostAsJsonAsync($"/api/attempts/{id}/actions", new ActionRequest(value, null, stateVersion, key)),
        "command" => client.PostAsJsonAsync($"/api/attempts/{id}/commands", new ActionRequest(null, value, stateVersion, key)),
        "diagnosis" => client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(value!, stateVersion, key)),
        "remediation" => client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(value!, stateVersion, key)),
        "verification" => client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(value == "__all__" ? attempt.AvailableVerifications.Select(x => x.Id).ToArray() : value == "__first__" ? [attempt.AvailableVerifications[0].Id] : value is null ? null! : [value], stateVersion, key)),
        _ => throw new ArgumentOutOfRangeException(nameof(mutation))
    };

    private static Task<HttpResponseMessage> SendMissingAttemptAsync(HttpClient client, Guid id, string mutation)
    {
        const string key = "missing-attempt-key";
        return mutation switch
        {
            "action" => client.PostAsJsonAsync($"/api/attempts/{id}/actions", new ActionRequest("app-status", null, 0, key)),
            "command" => client.PostAsJsonAsync($"/api/attempts/{id}/commands", new ActionRequest(null, "systemctl status app.service", 0, key)),
            "diagnosis" => client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest("value", 0, key)),
            "remediation" => client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest("value", 0, key)),
            "verification" => client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(["value"], 0, key)),
            _ => throw new ArgumentOutOfRangeException(nameof(mutation))
        };
    }

    private static async Task<PublicAttemptView> ReadAttemptAsync(HttpResponseMessage response)
    {
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.Content.ReadFromJsonAsync<PublicAttemptView>() ?? throw new InvalidOperationException();
    }

    private async Task<Snapshot> SnapshotAsync(Guid id)
    {
        await using var db = fixture.CreateContext();
        var attempt = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        return new(attempt.StateJson, attempt.StateVersion, attempt.Status, attempt.ScoreJson, await db.AttemptEvents.CountAsync(x => x.AttemptId == id));
    }

    private async Task AssertUnchangedAsync(Guid id, string key, Snapshot before, bool requireKey = false)
    {
        await using var db = fixture.CreateContext();
        var attempt = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        Assert.Equal(before.StateJson, attempt.StateJson);
        Assert.Equal(before.StateVersion, attempt.StateVersion);
        Assert.Equal(before.Status, attempt.Status);
        Assert.Equal(before.ScoreJson, attempt.ScoreJson);
        Assert.Equal(before.EventCount, await db.AttemptEvents.CountAsync(x => x.AttemptId == id));
        Assert.Equal(requireKey, await db.AttemptEvents.AnyAsync(x => x.AttemptId == id && x.IdempotencyKey == key));
    }

    private static async Task AssertNoPrivateResponseAsync(HttpResponseMessage response)
    {
        var body = await response.Content.ReadAsStringAsync();
        foreach (var forbidden in new[] { "scoreJson", "stateJson", "resultAttemptJson", "correct", "scoring", "idempotencyKey" })
            Assert.DoesNotContain(forbidden, body, StringComparison.OrdinalIgnoreCase);
    }

    private sealed record Snapshot(string StateJson, int StateVersion, AttemptStatus Status, string? ScoreJson, int EventCount);
}
