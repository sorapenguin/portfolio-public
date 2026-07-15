namespace InfraLab.Domain;

public enum ScenarioType { Linux, Aws, Security, Architecture }
public enum ScenarioPhase { Observe, Investigate, Diagnose, Remediate, Verify, Review }
public enum AttemptStatus { InProgress, Completed, Abandoned }
public enum EvidenceType { Log, CommandOutput, Metric, Config }

public sealed record Scenario(string Id, string Title, string Category, string Difficulty, string Summary, IReadOnlyList<string> Symptoms, ScenarioVersion Version);
public sealed record ScenarioVersion(string Id, int Number, IReadOnlyList<ScenarioAction> Actions, IReadOnlyList<Evidence> Evidence, IReadOnlyList<DiagnosisOption> Diagnoses, IReadOnlyList<RemediationOption> Remediations, IReadOnlyList<VerificationOption> Verifications, ScenarioPrivate Private);
public sealed record ScenarioAction(string Id, string Label, ScenarioPhase Phase, IReadOnlyList<CommandPattern> Patterns, string Output, IReadOnlyList<string> RevealsEvidenceIds, int Cost, bool IsDangerous, bool Repeatable, IReadOnlyList<string>? CommandRevealsEvidenceIds = null)
{
    public IReadOnlyList<string> CommandRevealsEvidenceIds { get; init; } = CommandRevealsEvidenceIds ?? [];
}
public sealed record CommandPattern(string Value, string ValidationInput = "", bool IgnoreCase = false, bool AllowSudo = true);
public sealed record Evidence(string Id, string Title, EvidenceType Type, string Text);
public sealed record DiagnosisOption(string Id, string Text);
public sealed record RemediationOption(string Id, string Text, bool IsDangerous = false);
public sealed record VerificationOption(string Id, string Text);
public sealed record ScenarioPrivate(string CorrectDiagnosisId, string CorrectRemediationId, HashSet<string> RequiredVerificationIds, HashSet<string> RequiredEvidenceIds, IReadOnlyList<string> ExemplaryPath, string Explanation);
public sealed record Attempt(Guid Id, string ScenarioId, string ScenarioVersionId, AttemptStatus Status, AttemptState State, DateTimeOffset CreatedAt, ScoreBreakdown? Score = null);
public sealed record AttemptState(ScenarioPhase Phase, int StateVersion, HashSet<string> ExecutedActionIds, HashSet<string> RevealedEvidenceIds, string? DiagnosisId = null, string? RemediationId = null, HashSet<string>? VerificationIds = null)
{
    public HashSet<string> VerificationIds { get; init; } = VerificationIds ?? new HashSet<string>();
}
public sealed record AttemptEvent(Guid Id, Guid AttemptId, int Sequence, string EventType, ScenarioPhase Phase, string? ActionId, string? RawInput, string? NormalizedInput, string Outcome, IReadOnlyList<string> RevealedEvidenceIds, int StateVersion, string IdempotencyKey, DateTimeOffset CreatedAt);
public sealed record ScoreBreakdown(int Diagnosis, int Remediation, int Verification, int Investigation, int Safety, int Total);
public sealed record UserProgress(string AnonymousId, string ScenarioId, int? BestScore, bool RetryLater);
