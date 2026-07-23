using InfraLab.Contracts;

namespace InfraLab.Client.Models;

// These values are deliberately presentation-only. Server ScenarioPhase values remain persisted as-is.
public enum AttemptDisplayPhase { Investigation, Observation = Investigation, Diagnosis, Remediation, Verification, Review }
public sealed record AttemptDisplayState(AttemptDisplayPhase DisplayPhase, AttemptDisplayPhase? PendingPhase);

public static class AttemptPresentation
{
    public static IReadOnlyList<AttemptDisplayPhase> PhaseOrder { get; } = [AttemptDisplayPhase.Investigation, AttemptDisplayPhase.Diagnosis, AttemptDisplayPhase.Remediation, AttemptDisplayPhase.Verification, AttemptDisplayPhase.Review];

    public static AttemptDisplayPhase FromServerPhase(int phase) => phase switch
    {
        0 or 1 => AttemptDisplayPhase.Investigation,
        2 => AttemptDisplayPhase.Diagnosis,
        3 => AttemptDisplayPhase.Remediation,
        4 => AttemptDisplayPhase.Verification,
        5 => AttemptDisplayPhase.Review,
        _ => throw new ArgumentOutOfRangeException(nameof(phase))
    };

    public static AttemptDisplayState ForServerPhase(int serverPhase) => new(FromServerPhase(serverPhase), null);
    public static AttemptDisplayState DeferTransition(AttemptDisplayPhase currentDisplayPhase, int serverPhase) { var destination = FromServerPhase(serverPhase); return currentDisplayPhase == destination ? new(destination, null) : new(currentDisplayPhase, destination); }
    public static AttemptDisplayState AdvancePendingPhase(AttemptDisplayPhase displayPhase, AttemptDisplayPhase? pendingPhase) => pendingPhase is null ? new(displayPhase, null) : new(pendingPhase.Value, null);
    public static bool ShowsPhase(AttemptDisplayPhase displayPhase, AttemptDisplayPhase? pendingPhase, AttemptDisplayPhase phase) => displayPhase == phase && pendingPhase is null;
    public static bool ShowsInvestigation(AttemptDisplayPhase displayPhase) => displayPhase == AttemptDisplayPhase.Investigation;
    public static bool CanAdvanceToDiagnosis(PublicAttemptView attempt) => attempt.Phase == 1 && attempt.CanAdvanceToDiagnosis;
    public static bool IsSingleVerification(PublicAttemptView attempt) => attempt.VerificationSelectionMode == 1;
    public static bool ShowsManualCommandToggle(PublicAttemptView attempt) => attempt.SupportsCommandInput && attempt.Phase is 0 or 1;
    public static bool HasExecutionExamples(IEnumerable<PublicActionView> actions) => actions.Any(action => !string.IsNullOrWhiteSpace(action.ExecutionExample?.Text) || !string.IsNullOrWhiteSpace(action.RepresentativeCommand));
    public static string InvestigationGuidance(AttemptDisplayPhase phase) => phase == AttemptDisplayPhase.Investigation ? "調査操作を選び、Evidenceから原因を絞り込みます。" : "必要な情報が揃いました。診断へ進めます。";
    public static string AdvanceLabel(AttemptDisplayPhase phase) => phase switch { AttemptDisplayPhase.Diagnosis => "診断へ進む", AttemptDisplayPhase.Remediation => "対処へ進む", AttemptDisplayPhase.Verification => "復旧確認へ進む", AttemptDisplayPhase.Review => "結果・解説を見る", _ => "調査へ進む" };
}
