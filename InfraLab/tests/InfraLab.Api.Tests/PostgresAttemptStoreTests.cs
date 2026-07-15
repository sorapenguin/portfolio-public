using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class PostgresAttemptStoreTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Migration_creates_persistence_schema_and_attempt_reloads_in_new_context()
    {
        await using var db = fixture.CreateContext();
        Assert.True((await db.Database.GetAppliedMigrationsAsync()).Any());
        var started = await new EfAttemptStore(db).StartAsync(Guid.NewGuid(), "scenario", "v1");

        await using var reloaded = fixture.CreateContext();
        var attempt = await new EfAttemptStore(reloaded).FindAsync(started.Id);
        Assert.NotNull(attempt);
        AssertStateEqual(started.State, attempt.State);
        Assert.Equal(0, await reloaded.AttemptEvents.CountAsync());
    }

    [Fact]
    public async Task Sequential_and_parallel_same_idempotency_key_mutate_once_and_replay_first_result()
    {
        var id = await StartAsync();
        var key = Guid.NewGuid().ToString("N");
        var first = await MutateAsync(id, key, 0);
        var replay = await MutateAsync(id, key, 0);
        Assert.False(first.WasReplay);
        Assert.True(replay.WasReplay);
        AssertStateEqual(first.Attempt.State, replay.Attempt.State);

        var parallelKey = Guid.NewGuid().ToString("N");
        var results = await Task.WhenAll(MutateAsync(id, parallelKey, 1), MutateAsync(id, parallelKey, 1));
        Assert.Single(results, x => !x.WasReplay);
        Assert.All(results, x => Assert.Equal(2, x.Attempt.State.StateVersion));

        await using var verify = fixture.CreateContext();
        var events = await verify.AttemptEvents.Where(x => x.AttemptId == id).OrderBy(x => x.Sequence).ToListAsync();
        Assert.Equal(new[] { 1, 2 }, events.Select(x => x.Sequence));
        Assert.All(events, x => Assert.Equal(x.ResultStateVersion, x.Sequence));
    }

    [Fact]
    public async Task Different_keys_with_same_version_allow_only_one_and_leave_no_losing_event()
    {
        var id = await StartAsync();
        var outcomes = await Task.WhenAll(Observe(MutateAsync(id, Guid.NewGuid().ToString("N"), 0)), Observe(MutateAsync(id, Guid.NewGuid().ToString("N"), 0)));
        Assert.Equal(new[] { "conflict", "success" }, outcomes.Order());

        await using var db = fixture.CreateContext();
        Assert.Single(await db.AttemptEvents.Where(x => x.AttemptId == id).ToListAsync());
        Assert.Equal(1, (await new EfAttemptStore(db).FindAsync(id))!.State.StateVersion);
    }

    [Fact]
    public async Task Old_key_replays_after_later_mutation_and_keys_are_scoped_to_attempt()
    {
        var firstAttempt = await StartAsync();
        var secondAttempt = await StartAsync();
        var key = Guid.NewGuid().ToString("N");
        var first = await MutateAsync(firstAttempt, key, 0);
        await MutateAsync(firstAttempt, Guid.NewGuid().ToString("N"), 1);
        var oldReplay = await MutateAsync(firstAttempt, key, 0);
        var other = await MutateAsync(secondAttempt, key, 0);
        Assert.True(oldReplay.WasReplay);
        AssertStateEqual(first.Attempt.State, oldReplay.Attempt.State);
        Assert.False(other.WasReplay);
    }

    [Theory]
    [InlineData(FaultPoint.AfterStateSaved)]
    [InlineData(FaultPoint.AfterEventSavedBeforeCommit)]
    public async Task Faults_roll_back_attempt_event_and_idempotency_key(FaultPoint point)
    {
        var id = await StartAsync();
        var key = Guid.NewGuid().ToString("N");
        await using (var failing = fixture.CreateContext())
        {
            var store = new EfAttemptStore(failing, new ThrowingFaultInjector(point));
            await Assert.ThrowsAsync<InjectedPersistenceFailure>(() => store.MutateAsync(id, 0, key, "action", "action-1", "action-1", Increment));
        }

        await using (var check = fixture.CreateContext())
        {
            var attempt = (await new EfAttemptStore(check).FindAsync(id))!;
            Assert.Equal(0, attempt.State.StateVersion);
            Assert.Empty(await check.AttemptEvents.Where(x => x.AttemptId == id).ToListAsync());
        }
        var retry = await MutateAsync(id, key, 0);
        Assert.False(retry.WasReplay);
        Assert.Equal(1, retry.Attempt.State.StateVersion);
    }

    [Fact]
    public async Task Completed_score_is_snapshotted_reloaded_and_not_changed_by_replay()
    {
        var id = await StartAsync();
        var key = Guid.NewGuid().ToString("N");
        var score = new ScoreBreakdown(40, 25, 20, 10, 0, 95);
        await using (var db = fixture.CreateContext())
        {
            var result = await new EfAttemptStore(db).MutateAsync(id, 0, key, "verification", "verify", "verify", state => state with { Phase = ScenarioPhase.Review, StateVersion = 1 }, _ => score);
            Assert.Equal(score, result.Attempt.Score);
        }
        await using (var reloaded = fixture.CreateContext())
        {
            var attempt = (await new EfAttemptStore(reloaded).FindAsync(id))!;
            Assert.Equal(AttemptStatus.Completed, attempt.Status);
            Assert.Equal(score, attempt.Score);
            var replay = await new EfAttemptStore(reloaded).MutateAsync(id, 0, key, "verification", "verify", "verify", Increment);
            Assert.True(replay.WasReplay);
            Assert.Equal(score, replay.Attempt.Score);
        }
    }

    [Fact]
    public async Task Sequences_are_scoped_to_each_attempt()
    {
        var first = await StartAsync();
        var second = await StartAsync();
        await MutateAsync(first, Guid.NewGuid().ToString("N"), 0);
        await MutateAsync(first, Guid.NewGuid().ToString("N"), 1);
        await MutateAsync(second, Guid.NewGuid().ToString("N"), 0);

        await using var db = fixture.CreateContext();
        var firstEvents = await db.AttemptEvents.Where(x => x.AttemptId == first).OrderBy(x => x.Sequence).ToListAsync();
        var secondEvents = await db.AttemptEvents.Where(x => x.AttemptId == second).OrderBy(x => x.Sequence).ToListAsync();
        Assert.Equal(new[] { 1, 2 }, firstEvents.Select(x => x.Sequence));
        Assert.Equal(new[] { 1 }, secondEvents.Select(x => x.Sequence));
        Assert.All(firstEvents, x => { Assert.Equal(first, x.AttemptId); Assert.Equal(x.Sequence, x.ResultStateVersion); });
        Assert.All(secondEvents, x => { Assert.Equal(second, x.AttemptId); Assert.Equal(x.Sequence, x.ResultStateVersion); });
    }

    private async Task<Guid> StartAsync()
    {
        await using var db = fixture.CreateContext();
        return (await new EfAttemptStore(db).StartAsync(Guid.NewGuid(), "scenario", "v1")).Id;
    }

    private async Task<AttemptMutationResult> MutateAsync(Guid id, string key, int version)
    {
        await using var db = fixture.CreateContext();
        return await new EfAttemptStore(db).MutateAsync(id, version, key, "action", "action-1", "action-1", Increment);
    }

    private static AttemptState Increment(AttemptState state) => state with { StateVersion = state.StateVersion + 1, ExecutedActionIds = state.ExecutedActionIds.Append("action-1").ToHashSet() };

    private static async Task<string> Observe(Task<AttemptMutationResult> task)
    {
        try { await task; return "success"; }
        catch (AttemptConcurrencyException) { return "conflict"; }
    }

    private static void AssertStateEqual(AttemptState expected, AttemptState actual)
    {
        Assert.Equal(expected.Phase, actual.Phase);
        Assert.Equal(expected.StateVersion, actual.StateVersion);
        Assert.Equal(expected.DiagnosisId, actual.DiagnosisId);
        Assert.Equal(expected.RemediationId, actual.RemediationId);
        Assert.True(expected.ExecutedActionIds.SetEquals(actual.ExecutedActionIds));
        Assert.True(expected.RevealedEvidenceIds.SetEquals(actual.RevealedEvidenceIds));
        Assert.True(expected.VerificationIds.SetEquals(actual.VerificationIds));
    }
}

public enum FaultPoint { AfterStateSaved, AfterEventSavedBeforeCommit }
public sealed class InjectedPersistenceFailure : Exception;
public sealed class ThrowingFaultInjector(FaultPoint point) : IAttemptStoreFaultInjector
{
    public void ThrowAfterStateSaved() { if (point == FaultPoint.AfterStateSaved) throw new InjectedPersistenceFailure(); }
    public void ThrowAfterEventSavedBeforeCommit() { if (point == FaultPoint.AfterEventSavedBeforeCommit) throw new InjectedPersistenceFailure(); }
}
