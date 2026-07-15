namespace InfraLab.Contracts;

public sealed record ScenarioListItem(string Id, string Title, string Category, string Difficulty, string Summary);
public sealed record PublicEvidence(string Id, string Title, int Type, string Text);
public sealed record PublicActionView(string Id, string Label);
public sealed record PublicDiagnosisView(string Id, string Label);
public sealed record PublicRemediationView(string Id, string Label);
public sealed record PublicVerificationView(string Id, string Label);
public sealed record PublicAttemptView(Guid Id, string ScenarioId, string ScenarioTitle, string[] Symptoms, int Status, int Phase, int StateVersion, PublicEvidence[] RevealedEvidence, PublicActionView[] AvailableActions, string[] ExecutedActions, PublicDiagnosisView[] AvailableDiagnoses, PublicRemediationView[] AvailableRemediations, PublicVerificationView[] AvailableVerifications);
public sealed record ActionRequest(string? ActionId, string? Input, int StateVersion, string IdempotencyKey);
public sealed record SubmitDiagnosisRequest(string OptionId, int StateVersion, string IdempotencyKey);
public sealed record SubmitRemediationRequest(string OptionId, int StateVersion, string IdempotencyKey);
public sealed record SubmitVerificationRequest(IReadOnlyList<string> OptionIds, int StateVersion, string IdempotencyKey);
public sealed record PublicScoreBreakdown(int Diagnosis, int Remediation, int Verification, int Investigation, int Safety, int Total);
public sealed record PublicAttemptResult(Guid AttemptId, string ScenarioId, string ScenarioTitle, int Status, PublicScoreBreakdown Score);
public sealed record PublicReviewEntry(string SelectedLabel, string ExpectedLabel, bool IsCorrect, string? Explanation, int? EarnedScore, int? MaxScore);
public sealed record PublicVerificationReviewEntry(IReadOnlyList<string> SelectedLabels, IReadOnlyList<string> ExpectedLabels, bool IsCorrect, string? Explanation, int? EarnedScore, int? MaxScore);
public sealed record PublicAttemptReview(Guid AttemptId, string ScenarioId, string ScenarioTitle, PublicReviewEntry? Diagnosis, PublicReviewEntry? Remediation, PublicVerificationReviewEntry? Verification);
