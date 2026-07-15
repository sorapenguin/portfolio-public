using System.Text.RegularExpressions;
using InfraLab.Domain;

namespace InfraLab.Application;

public enum MatchOutcome { Matched, Empty, Unrecognized, Ambiguous }
public sealed record CommandMatchResult(string? ActionId, MatchOutcome Outcome, string Message, string Normalized);
public sealed class CommandEngine
{
    public string Normalize(string raw)
    {
        var normalized = Regex.Replace(raw.Trim(), "\\s+", " ");
        return normalized.StartsWith("sudo ", StringComparison.Ordinal) ? normalized[5..] : normalized;
    }
    public CommandMatchResult Match(string raw, IEnumerable<ScenarioAction> actions)
    {
        var normalized = Normalize(raw);
        if (string.IsNullOrEmpty(normalized)) return new(null, MatchOutcome.Empty, "コマンドを入力してください。", normalized);
        var matches = actions.Where(a => a.Patterns.Any(p => string.Equals(p.Value, normalized, p.IgnoreCase ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal))).Select(a => a.Id).Distinct().ToArray();
        return matches.Length switch { 1 => new(matches[0], MatchOutcome.Matched, "登録済みの調査操作です。", normalized), > 1 => new(null, MatchOutcome.Ambiguous, "曖昧なコマンドです。", normalized), _ => new(null, MatchOutcome.Unrecognized, "この演習では未登録のコマンドです。表示されている調査候補を確認してください。", normalized) };
    }
}
public sealed class ScenarioEngine
{
    public AttemptState ExecuteAction(Scenario scenario, AttemptState state, string actionId, int expectedVersion)
    {
        Ensure(state, expectedVersion); var action = scenario.Version.Actions.SingleOrDefault(x => x.Id == actionId) ?? throw new InvalidOperationException("Unknown action.");
        if (state.Phase is not (ScenarioPhase.Observe or ScenarioPhase.Investigate) || action.Phase != ScenarioPhase.Investigate) throw new InvalidOperationException("このフェーズではその操作を実行できません。");
        if (!action.Repeatable && state.ExecutedActionIds.Contains(actionId)) throw new InvalidOperationException("この操作は既に実行済みです。");
        var executed = state.ExecutedActionIds.Append(actionId).ToHashSet(); var evidence = state.RevealedEvidenceIds.Concat(action.RevealsEvidenceIds).ToHashSet();
        var phase = scenario.Version.Private.RequiredEvidenceIds.IsSubsetOf(evidence)
            ? ScenarioPhase.Diagnose
            : ScenarioPhase.Investigate;
        return state with { Phase = phase, StateVersion = state.StateVersion + 1, ExecutedActionIds = executed, RevealedEvidenceIds = evidence };
    }
    public AttemptState AnswerDiagnosis(Scenario s, AttemptState state, string id, int expected) { Ensure(state, expected); Require(state.Phase, ScenarioPhase.Diagnose); if (string.IsNullOrWhiteSpace(id) || !s.Version.Diagnoses.Any(x => x.Id == id)) throw new InvalidOperationException("The diagnosis option is invalid."); if (!s.Version.Private.RequiredEvidenceIds.IsSubsetOf(state.RevealedEvidenceIds)) throw new InvalidOperationException("必要な証拠がまだ開示されていません。"); return state with { DiagnosisId = id, Phase = ScenarioPhase.Remediate, StateVersion = state.StateVersion + 1 }; }
    public AttemptState AnswerRemediation(Scenario s, AttemptState state, string id, int expected) { Ensure(state, expected); Require(state.Phase, ScenarioPhase.Remediate); if (string.IsNullOrWhiteSpace(id) || !s.Version.Remediations.Any(x => x.Id == id)) throw new InvalidOperationException("The remediation option is invalid."); return state with { RemediationId = id, Phase = ScenarioPhase.Verify, StateVersion = state.StateVersion + 1 }; }
    public AttemptState AnswerVerification(Scenario s, AttemptState state, IEnumerable<string> ids, int expected) { Ensure(state, expected); Require(state.Phase, ScenarioPhase.Verify); var values = ids?.ToArray() ?? throw new InvalidOperationException("The verification options are invalid."); if (values.Length == 0 || values.Any(string.IsNullOrWhiteSpace) || values.Distinct(StringComparer.Ordinal).Count() != values.Length || values.Any(id => !s.Version.Verifications.Any(x => x.Id == id))) throw new InvalidOperationException("The verification options are invalid."); var set = values.ToHashSet(StringComparer.Ordinal); if (!s.Version.Private.RequiredVerificationIds.IsSubsetOf(set)) throw new InvalidOperationException("復旧確認が不足しています。"); return state with { VerificationIds = set, Phase = ScenarioPhase.Review, StateVersion = state.StateVersion + 1 }; }
    private static void Ensure(AttemptState state, int expected) { if (state.Phase == ScenarioPhase.Review) throw new InvalidOperationException("完了済みの試行には操作できません。"); if (state.StateVersion != expected) throw new InvalidOperationException("StateVersion conflict."); }
    public AttemptState ExecuteCommand(Scenario scenario, AttemptState state, string actionId, int expectedVersion)
    {
        Ensure(state, expectedVersion);
        var action = scenario.Version.Actions.SingleOrDefault(x => x.Id == actionId) ?? throw new InvalidOperationException("Unknown action.");
        if (state.Phase is not (ScenarioPhase.Observe or ScenarioPhase.Investigate) || action.Phase != ScenarioPhase.Investigate) throw new InvalidOperationException("Invalid phase.");
        if (!action.Repeatable && state.ExecutedActionIds.Contains(actionId)) throw new InvalidOperationException("Action has already been executed.");
        var reveals = action.CommandRevealsEvidenceIds.Count > 0 ? action.CommandRevealsEvidenceIds : action.RevealsEvidenceIds;
        var evidence = state.RevealedEvidenceIds.Concat(reveals).ToHashSet();
        var phase = scenario.Version.Private.RequiredEvidenceIds.IsSubsetOf(evidence) ? ScenarioPhase.Diagnose : ScenarioPhase.Investigate;
        return state with { Phase = phase, StateVersion = state.StateVersion + 1, ExecutedActionIds = state.ExecutedActionIds.Append(actionId).ToHashSet(), RevealedEvidenceIds = evidence };
    }
    private static void Require(ScenarioPhase actual, ScenarioPhase expected) { if (actual != expected) throw new InvalidOperationException("Invalid phase."); }
}
public sealed class ScoringEngine
{
    public ScoreBreakdown Calculate(Scenario scenario, AttemptState state)
    {
        var p = scenario.Version.Private; var diagnosis = state.DiagnosisId == p.CorrectDiagnosisId ? 40 : 0; var remediation = state.RemediationId == p.CorrectRemediationId ? 25 : 0; var verification = p.RequiredVerificationIds.IsSubsetOf(state.VerificationIds) ? 20 : 0;
        var unnecessary = Math.Max(0, state.ExecutedActionIds.Count - p.ExemplaryPath.Count); var investigation = Math.Max(0, 10 - unnecessary * 2); var dangerous = scenario.Version.Actions.Where(a => state.ExecutedActionIds.Contains(a.Id) && a.IsDangerous).Any() || scenario.Version.Remediations.Any(r => r.Id == state.RemediationId && r.IsDangerous); var safety = dangerous ? 0 : 5;
        return new(diagnosis, remediation, verification, investigation, safety, diagnosis + remediation + verification + investigation + safety);
    }
}
