using System.Text.Json;
using System.Security.Cryptography;
using System.Text;
using InfraLab.Domain;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Npgsql;

namespace InfraLab.Infrastructure;

public sealed class AttemptEntity
{
    public Guid Id { get; set; }
    public Guid AnonymousSessionId { get; set; }
    public string ScenarioId { get; set; } = string.Empty;
    public string ScenarioVersionId { get; set; } = string.Empty;
    public AttemptStatus Status { get; set; }
    public ScenarioPhase CurrentPhase { get; set; }
    public int StateVersion { get; set; }
    public string StateJson { get; set; } = string.Empty;
    public string? ScoreJson { get; set; }
    public DateTimeOffset StartedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
    public List<AttemptEventEntity> Events { get; } = [];
}

public sealed class AttemptEventEntity
{
    public Guid Id { get; set; }
    public Guid AttemptId { get; set; }
    public AttemptEntity Attempt { get; set; } = null!;
    public int Sequence { get; set; }
    public string EventType { get; set; } = string.Empty;
    public ScenarioPhase Phase { get; set; }
    public string? ActionOrAnswerId { get; set; }
    public int PreviousStateVersion { get; set; }
    public int ResultStateVersion { get; set; }
    public string IdempotencyKey { get; set; } = string.Empty;
    public string Outcome { get; set; } = string.Empty;
    public string? PayloadJson { get; set; }
    public string ResultAttemptJson { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; }
}

public sealed class InfraLabDbContext(DbContextOptions<InfraLabDbContext> options) : DbContext(options)
{
    public DbSet<AttemptEntity> Attempts => Set<AttemptEntity>();
    public DbSet<AttemptEventEntity> AttemptEvents => Set<AttemptEventEntity>();

    protected override void OnModelCreating(ModelBuilder model)
    {
        model.Entity<AttemptEntity>(e =>
        {
            e.ToTable("attempts");
            e.HasKey(x => x.Id);
            e.Property(x => x.ScenarioId).HasMaxLength(200);
            e.Property(x => x.ScenarioVersionId).HasMaxLength(200);
            e.Property(x => x.StateVersion).IsConcurrencyToken();
            e.Property(x => x.StateJson).HasColumnType("jsonb");
            e.Property(x => x.ScoreJson).HasColumnType("jsonb");
            e.HasMany(x => x.Events).WithOne(x => x.Attempt).HasForeignKey(x => x.AttemptId).OnDelete(DeleteBehavior.Restrict);
            e.HasIndex(x => new { x.AnonymousSessionId, x.UpdatedAt });
        });
        model.Entity<AttemptEventEntity>(e =>
        {
            e.ToTable("attempt_events");
            e.HasKey(x => x.Id);
            e.Property(x => x.EventType).HasMaxLength(80);
            e.Property(x => x.ActionOrAnswerId).HasMaxLength(200);
            e.Property(x => x.IdempotencyKey).HasMaxLength(100);
            e.Property(x => x.Outcome).HasMaxLength(80);
            e.Property(x => x.PayloadJson).HasColumnType("jsonb");
            e.Property(x => x.ResultAttemptJson).HasColumnType("jsonb");
            e.HasIndex(x => new { x.AttemptId, x.IdempotencyKey }).IsUnique();
            e.HasIndex(x => new { x.AttemptId, x.Sequence }).IsUnique();
        });
    }
}

public interface IAttemptStore
{
    Task<Attempt> StartAsync(Guid anonymousSessionId, string scenarioId, string scenarioVersionId, CancellationToken cancellationToken = default);
    Task<Attempt?> FindAsync(Guid id, CancellationToken cancellationToken = default);
    Task<AttemptReviewData?> FindReviewDataAsync(Guid id, CancellationToken cancellationToken = default);
    Task<AttemptMutationResult> MutateAsync(Guid attemptId, int expectedStateVersion, string idempotencyKey, string eventType, string? actionOrAnswerId, string requestValue, Func<AttemptState, AttemptState> mutate, Func<AttemptState, ScoreBreakdown?>? score = null, CancellationToken cancellationToken = default);
}

public sealed record AttemptMutationResult(Attempt Attempt, bool WasReplay);
public sealed record AttemptReviewData(Attempt Attempt, string? VerificationId);

public sealed record IdempotencyPayload(string EventType, string Value, int StateVersion);

public sealed class EfAttemptStore : IAttemptStore
{
    private readonly InfraLabDbContext db;
    private readonly IAttemptStoreFaultInjector faults;
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public EfAttemptStore(InfraLabDbContext db) : this(db, NoopAttemptStoreFaultInjector.Instance) { }
    internal EfAttemptStore(InfraLabDbContext db, IAttemptStoreFaultInjector faults) { this.db = db; this.faults = faults; }

    public async Task<Attempt> StartAsync(Guid sessionId, string scenarioId, string versionId, CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        var state = new AttemptState(ScenarioPhase.Observe, 0, [], []);
        var entity = new AttemptEntity
        {
            Id = Guid.NewGuid(),
            AnonymousSessionId = sessionId,
            ScenarioId = scenarioId,
            ScenarioVersionId = versionId,
            Status = AttemptStatus.InProgress,
            CurrentPhase = state.Phase,
            StateVersion = state.StateVersion,
            StateJson = JsonSerializer.Serialize(state, JsonOptions),
            StartedAt = now,
            CreatedAt = now,
            UpdatedAt = now
        };
        db.Attempts.Add(entity);
        await db.SaveChangesAsync(ct);
        return ToDomain(entity);
    }

    public async Task<Attempt?> FindAsync(Guid id, CancellationToken ct = default) =>
        (await db.Attempts.AsNoTracking().SingleOrDefaultAsync(x => x.Id == id, ct)) is { } entity ? ToDomain(entity) : null;

    public async Task<AttemptReviewData?> FindReviewDataAsync(Guid id, CancellationToken ct = default)
    {
        var entity = await db.Attempts.AsNoTracking().SingleOrDefaultAsync(x => x.Id == id, ct);
        if (entity is null) return null;
        var verificationId = await db.AttemptEvents.AsNoTracking()
            .Where(x => x.AttemptId == id && x.EventType == "verification")
            .OrderByDescending(x => x.Sequence)
            .Select(x => x.ActionOrAnswerId)
            .FirstOrDefaultAsync(ct);
        return new(ToDomain(entity), verificationId);
    }

    public async Task<AttemptMutationResult> MutateAsync(Guid id, int expectedVersion, string key, string eventType, string? answerId, string requestValue, Func<AttemptState, AttemptState> mutate, Func<AttemptState, ScoreBreakdown?>? score = null, CancellationToken ct = default)
    {
        IDbContextTransaction? transaction = null;
        try
        {
            transaction = await db.Database.BeginTransactionAsync(ct);
            var replay = await db.AttemptEvents.AsNoTracking().SingleOrDefaultAsync(x => x.AttemptId == id && x.IdempotencyKey == key, ct);
            if (replay is not null)
            {
                EnsureSameRequest(replay, eventType, requestValue, expectedVersion);
                await transaction.CommitAsync(ct);
                return new(ReplayAttempt(replay), true);
            }

            // Serialize mutations for this Attempt across all server processes. A second check after
            // the row lock observes a concurrent same-key transaction after it commits.
            var entity = await db.Attempts
                .FromSqlInterpolated($"SELECT * FROM attempts WHERE \"Id\" = {id} FOR UPDATE")
                .SingleOrDefaultAsync(ct) ?? throw new KeyNotFoundException("Attempt not found.");
            replay = await db.AttemptEvents.AsNoTracking().SingleOrDefaultAsync(x => x.AttemptId == id && x.IdempotencyKey == key, ct);
            if (replay is not null)
            {
                EnsureSameRequest(replay, eventType, requestValue, expectedVersion);
                await transaction.CommitAsync(ct);
                return new(ReplayAttempt(replay), true);
            }
            if (entity.StateVersion != expectedVersion) throw new AttemptConcurrencyException();
            if (entity.Status != AttemptStatus.InProgress) throw new AttemptCompletedException();

            var current = DeserializeState(entity.StateJson);
            var next = mutate(current);
            if (next.StateVersion != current.StateVersion + 1) throw new InvalidOperationException("StateVersion must increment once.");

            var now = DateTimeOffset.UtcNow;
            entity.StateJson = JsonSerializer.Serialize(next, JsonOptions);
            entity.CurrentPhase = next.Phase;
            entity.StateVersion = next.StateVersion;
            entity.UpdatedAt = now;
            if (next.Phase == ScenarioPhase.Review)
            {
                entity.Status = AttemptStatus.Completed;
                entity.CompletedAt = now;
                entity.ScoreJson = JsonSerializer.Serialize(score?.Invoke(next) ?? throw new InvalidOperationException("Completed attempts require a score."), JsonOptions);
            }

            var result = ToDomain(entity);
            await db.SaveChangesAsync(ct);
            faults.ThrowAfterStateSaved();
            db.AttemptEvents.Add(new AttemptEventEntity
            {
                Id = Guid.NewGuid(),
                AttemptId = id,
                // StateVersion is incremented exactly once per mutation, so it is a per-attempt, race-free sequence.
                Sequence = next.StateVersion,
                EventType = eventType,
                Phase = current.Phase,
                ActionOrAnswerId = answerId,
                PreviousStateVersion = current.StateVersion,
                ResultStateVersion = next.StateVersion,
                IdempotencyKey = key,
                Outcome = "accepted",
                PayloadJson = JsonSerializer.Serialize(new IdempotencyPayload(eventType, FingerprintValue(eventType, requestValue), expectedVersion), JsonOptions),
                ResultAttemptJson = JsonSerializer.Serialize(result, JsonOptions),
                CreatedAt = now
            });

            // StateVersion is a concurrency token: EF emits it in the UPDATE predicate and raises if no row matches.
            await db.SaveChangesAsync(ct);
            faults.ThrowAfterEventSavedBeforeCommit();
            await transaction.CommitAsync(ct);
            return new(result, false);
        }
        catch (DbUpdateConcurrencyException)
        {
            if (transaction is not null) await transaction.RollbackAsync(ct);
            db.ChangeTracker.Clear();
            var replay = await db.AttemptEvents.AsNoTracking().SingleOrDefaultAsync(x => x.AttemptId == id && x.IdempotencyKey == key, ct);
            if (replay is not null)
            {
                EnsureSameRequest(replay, eventType, requestValue, expectedVersion);
                return new(ReplayAttempt(replay), true);
            }
            throw new AttemptConcurrencyException();
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException
        {
            SqlState: PostgresErrorCodes.UniqueViolation,
            ConstraintName: "IX_attempt_events_AttemptId_IdempotencyKey"
        })
        {
            if (transaction is not null) await transaction.RollbackAsync(ct);
            db.ChangeTracker.Clear();
            var replay = await db.AttemptEvents.AsNoTracking().SingleOrDefaultAsync(x => x.AttemptId == id && x.IdempotencyKey == key, ct);
            if (replay is not null)
            {
                EnsureSameRequest(replay, eventType, requestValue, expectedVersion);
                return new(ReplayAttempt(replay), true);
            }
            throw;
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException
        {
            SqlState: PostgresErrorCodes.UniqueViolation,
            ConstraintName: "IX_attempt_events_AttemptId_Sequence"
        })
        {
            // EF may insert the event before issuing the optimistic state UPDATE. A duplicate sequence
            // is therefore the same stale-StateVersion conflict, never an idempotent replay.
            if (transaction is not null) await transaction.RollbackAsync(ct);
            db.ChangeTracker.Clear();
            throw new AttemptConcurrencyException();
        }
        catch
        {
            if (transaction is not null) await transaction.RollbackAsync(ct);
            db.ChangeTracker.Clear();
            throw;
        }
        finally
        {
            if (transaction is not null) await transaction.DisposeAsync();
        }
    }

    private static Attempt ReplayAttempt(AttemptEventEntity replay) => JsonSerializer.Deserialize<Attempt>(replay.ResultAttemptJson, JsonOptions) ?? throw new InvalidOperationException("Invalid stored replay result.");
    private static void EnsureSameRequest(AttemptEventEntity replay, string eventType, string requestValue, int expectedVersion)
    {
        if (replay.EventType != eventType || replay.PreviousStateVersion != expectedVersion)
            throw new AttemptConcurrencyException();

        var payload = replay.PayloadJson is null ? null : JsonSerializer.Deserialize<IdempotencyPayload>(replay.PayloadJson, JsonOptions);
        if (payload is not null && payload.EventType == eventType && payload.Value == FingerprintValue(eventType, requestValue) && payload.StateVersion == expectedVersion)
            return;

        // Events persisted before request fingerprints were introduced contain only the resolved
        // action/answer ID. They can be safely replayed for non-command mutations only.
        if (eventType != "command" && replay.PayloadJson is not null)
        {
            using var document = JsonDocument.Parse(replay.PayloadJson);
            if (document.RootElement.TryGetProperty("id", out var id) && id.GetString() == requestValue)
                return;
        }

        throw new AttemptConcurrencyException();
    }

    private static string FingerprintValue(string eventType, string requestValue) =>
        eventType == "command"
            ? Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(requestValue)))
            : requestValue;
    private static AttemptState DeserializeState(string json) => JsonSerializer.Deserialize<AttemptState>(json, JsonOptions) ?? throw new InvalidOperationException("Invalid state.");
    private static Attempt ToDomain(AttemptEntity entity) => new(entity.Id, entity.ScenarioId, entity.ScenarioVersionId, entity.Status, DeserializeState(entity.StateJson), entity.StartedAt, entity.ScoreJson is null ? null : JsonSerializer.Deserialize<ScoreBreakdown>(entity.ScoreJson, JsonOptions));
}

internal interface IAttemptStoreFaultInjector
{
    void ThrowAfterStateSaved();
    void ThrowAfterEventSavedBeforeCommit();
}

internal sealed class NoopAttemptStoreFaultInjector : IAttemptStoreFaultInjector
{
    internal static readonly NoopAttemptStoreFaultInjector Instance = new();
    public void ThrowAfterStateSaved() { }
    public void ThrowAfterEventSavedBeforeCommit() { }
}

public sealed class AttemptConcurrencyException : Exception;
public sealed class AttemptCompletedException : Exception;
