using System.Text.RegularExpressions;
using InfraLab.Domain;

namespace InfraLab.Application;

public enum MatchOutcome { Matched, Empty, Unrecognized, Ambiguous }
public sealed record CommandMatchResult(string? ActionId, MatchOutcome Outcome, string Message, string Normalized);
public sealed class LearningFeedbackException(string message) : InvalidOperationException(message);

public sealed class CommandEngine
{
    public string Normalize(string raw, bool allowSudo = true)
    {
        var normalized = Regex.Replace(raw.Trim(), "\\s+", " ");
        return allowSudo && normalized.StartsWith("sudo ", StringComparison.Ordinal) ? normalized[5..] : normalized;
    }

    public CommandMatchResult Match(string raw, IEnumerable<ScenarioAction> actions)
    {
        var rawNormalized = Normalize(raw, allowSudo: false);
        if (string.IsNullOrEmpty(rawNormalized)) return new(null, MatchOutcome.Empty, "Enter a command.", rawNormalized);
        var matches = actions.Where(a => a.Patterns.Any(p => string.Equals(p.Value, Normalize(raw, p.AllowSudo), p.IgnoreCase ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal))).Select(a => a.Id).Distinct().ToArray();
        return matches.Length switch { 1 => new(matches[0], MatchOutcome.Matched, "Matched.", Normalize(raw)), > 1 => new(null, MatchOutcome.Ambiguous, "Ambiguous command.", Normalize(raw)), _ => new(null, MatchOutcome.Unrecognized, "This command is not recognized for this scenario.", rawNormalized) };
    }
}

public sealed class ScenarioEngine
{
    public AttemptState ExecuteAction(Scenario scenario, AttemptState state, string actionId, int expectedVersion)
    {
        Ensure(state, expectedVersion);
        var action = scenario.Version.Actions.SingleOrDefault(x => x.Id == actionId) ?? throw new InvalidOperationException("Unknown action.");
        if (state.Phase is not (ScenarioPhase.Observe or ScenarioPhase.Investigate) || action.Phase != ScenarioPhase.Investigate) throw new InvalidOperationException("Invalid phase.");
        if (state.ExecutedActionIds.Contains(actionId)) throw new InvalidOperationException("Action has already been executed.");
        return state with { Phase = ScenarioPhase.Investigate, StateVersion = state.StateVersion + 1, ExecutedActionIds = state.ExecutedActionIds.Append(actionId).ToHashSet(), RevealedEvidenceIds = state.RevealedEvidenceIds.Concat(action.RevealsEvidenceIds).ToHashSet() };
    }

    public bool CanAdvanceToDiagnosis(Scenario scenario, AttemptState state) => state.Phase == ScenarioPhase.Investigate && scenario.Version.Private.RequiredEvidenceIds.IsSubsetOf(state.RevealedEvidenceIds);

    public AttemptState AdvanceToDiagnosis(Scenario scenario, AttemptState state, int expectedVersion)
    {
        Ensure(state, expectedVersion); Require(state.Phase, ScenarioPhase.Investigate);
        if (!CanAdvanceToDiagnosis(scenario, state)) throw new InvalidOperationException("Required evidence has not been revealed.");
        return state with { Phase = ScenarioPhase.Diagnose, StateVersion = state.StateVersion + 1 };
    }

    public AttemptState AnswerDiagnosis(Scenario s, AttemptState state, string id, int expected)
    {
        Ensure(state, expected); Require(state.Phase, ScenarioPhase.Diagnose); ValidateOption(id, s.Version.Diagnoses.Select(x => x.Id), "diagnosis");
        if (!s.Version.Private.RequiredEvidenceIds.IsSubsetOf(state.RevealedEvidenceIds)) throw new InvalidOperationException("Required evidence has not been revealed.");
        if (state.IncorrectDiagnosisIds.Contains(id)) throw new InvalidOperationException("This option has already been answered.");
        return id == s.Version.Private.CorrectDiagnosisId
            ? state with { DiagnosisId = id, Phase = ScenarioPhase.Remediate, StateVersion = state.StateVersion + 1 }
            : state with { IncorrectDiagnosisIds = state.IncorrectDiagnosisIds.Append(id).ToHashSet(), StateVersion = state.StateVersion + 1 };
    }

    public AttemptState AnswerRemediation(Scenario s, AttemptState state, string id, int expected)
    {
        Ensure(state, expected); Require(state.Phase, ScenarioPhase.Remediate); ValidateOption(id, s.Version.Remediations.Select(x => x.Id), "remediation");
        if (state.IncorrectRemediationIds.Contains(id)) throw new InvalidOperationException("This option has already been answered.");
        return id == s.Version.Private.CorrectRemediationId
            ? state with { RemediationId = id, Phase = ScenarioPhase.Verify, StateVersion = state.StateVersion + 1 }
            : state with { IncorrectRemediationIds = state.IncorrectRemediationIds.Append(id).ToHashSet(), StateVersion = state.StateVersion + 1 };
    }

    public AttemptState AnswerVerification(Scenario s, AttemptState state, IEnumerable<string> ids, int expected)
    {
        Ensure(state, expected); Require(state.Phase, ScenarioPhase.Verify);
        var values = ids?.ToArray() ?? throw new InvalidOperationException("The verification options are invalid.");
        if (values.Length == 0 || values.Any(string.IsNullOrWhiteSpace) || values.Distinct(StringComparer.Ordinal).Count() != values.Length || values.Any(id => !s.Version.Verifications.Any(x => x.Id == id))) throw new InvalidOperationException("The verification options are invalid.");
        var set = values.ToHashSet(StringComparer.Ordinal);
        if (s.Version.VerificationSelectionMode == VerificationSelectionMode.Single && values.Length != 1) throw new InvalidOperationException("Select one verification option.");
        if (s.Version.VerificationSelectionMode == VerificationSelectionMode.Single && state.IncorrectVerificationIds.Contains(values[0])) throw new InvalidOperationException("This option has already been answered.");
        var correct = s.Version.Private.RequiredVerificationIds.IsSubsetOf(set) && (s.Version.VerificationSelectionMode != VerificationSelectionMode.Single || set.SetEquals(s.Version.Private.RequiredVerificationIds));
        return correct
            ? state with { VerificationIds = set, Phase = ScenarioPhase.Review, StateVersion = state.StateVersion + 1 }
            : state with { IncorrectVerificationIds = state.IncorrectVerificationIds.Append(values[0]).ToHashSet(), StateVersion = state.StateVersion + 1 };
    }

    public AttemptState ExecuteCommand(Scenario scenario, AttemptState state, string actionId, int expectedVersion)
    {
        Ensure(state, expectedVersion);
        var action = scenario.Version.Actions.SingleOrDefault(x => x.Id == actionId) ?? throw new InvalidOperationException("Unknown action.");
        if (state.Phase is not (ScenarioPhase.Observe or ScenarioPhase.Investigate) || action.Phase != ScenarioPhase.Investigate) throw new InvalidOperationException("Invalid phase.");
        if (!action.Repeatable && state.ExecutedActionIds.Contains(actionId)) throw new InvalidOperationException("Action has already been executed.");
        var reveals = action.CommandRevealsEvidenceIds.Count > 0 ? action.CommandRevealsEvidenceIds : action.RevealsEvidenceIds;
        return state with { Phase = ScenarioPhase.Investigate, StateVersion = state.StateVersion + 1, ExecutedActionIds = state.ExecutedActionIds.Append(actionId).ToHashSet(), RevealedEvidenceIds = state.RevealedEvidenceIds.Concat(reveals).ToHashSet() };
    }

    private static void ValidateOption(string id, IEnumerable<string> known, string type)
    {
        if (string.IsNullOrWhiteSpace(id) || !known.Contains(id)) throw new InvalidOperationException($"The {type} option is invalid.");
    }
    private static void Ensure(AttemptState state, int expected) { if (state.Phase == ScenarioPhase.Review) throw new InvalidOperationException("Attempt is completed."); if (state.StateVersion != expected) throw new InvalidOperationException("StateVersion conflict."); }
    private static void Require(ScenarioPhase actual, ScenarioPhase expected) { if (actual != expected) throw new InvalidOperationException("Invalid phase."); }
}

public sealed class ScoringEngine
{
    public ScoreBreakdown Calculate(Scenario scenario, AttemptState state)
    {
        var diagnosis = Math.Max(0, 30 - state.IncorrectDiagnosisIds.Count * 10);
        var remediation = Math.Max(0, 30 - state.IncorrectRemediationIds.Count * 10);
        var verification = Math.Max(0, 30 - state.IncorrectVerificationIds.Count * 10);
        const int investigation = 10;
        return new(diagnosis, remediation, verification, investigation, 0, diagnosis + remediation + verification + investigation);
    }
}
