using InfraLab.Domain;

namespace InfraLab.Application;

public sealed record ScenarioValidationIssue(string Code, string Message, bool IsError = true);

public sealed class ScenarioDefinitionValidator
{
    private readonly CommandEngine commands = new();

    public IReadOnlyList<ScenarioValidationIssue> Validate(Scenario scenario)
    {
        var issues = new List<ScenarioValidationIssue>();
        Required(scenario.Id, "SCENARIO_ID_REQUIRED", issues);
        Required(scenario.Title, "USER_TEXT_REQUIRED", issues);
        Required(scenario.Category, "USER_TEXT_REQUIRED", issues);
        Required(scenario.Difficulty, "USER_TEXT_REQUIRED", issues);
        Required(scenario.Summary, "USER_TEXT_REQUIRED", issues);
        Required(scenario.Version.Id, "SCENARIO_VERSION_ID_REQUIRED", issues);
        if (scenario.Version.Number < 1) issues.Add(new("SCENARIO_VERSION_NUMBER_INVALID", "Scenario version number must be at least one."));
        if (scenario.Symptoms.Count == 0 || scenario.Symptoms.Any(string.IsNullOrWhiteSpace)) issues.Add(new("SYMPTOMS_REQUIRED", "At least one usable symptom is required."));
        if (scenario.Category is not "lpic1") issues.Add(new("SCENARIO_CATEGORY_INVALID", $"Unsupported category: {scenario.Category}."));
        if (scenario.Difficulty is not "beginner") issues.Add(new("SCENARIO_DIFFICULTY_INVALID", $"Unsupported difficulty: {scenario.Difficulty}."));

        if (scenario.Version.Actions.Count == 0) issues.Add(new("ACTIONS_REQUIRED", "At least one action is required."));
        if (scenario.Version.Evidence.Count == 0) issues.Add(new("EVIDENCE_REQUIRED", "At least one evidence item is required."));
        Unique(scenario.Version.Actions.Select(x => x.Id), "DUPLICATE_ACTION_ID", issues);
        Unique(scenario.Version.Evidence.Select(x => x.Id), "DUPLICATE_EVIDENCE_ID", issues);
        foreach (var item in scenario.Version.Evidence)
        {
            Id(item.Id, "EVIDENCE_ID_INVALID", issues);
            Required(item.Title, "USER_TEXT_REQUIRED", issues);
            Required(item.Text, "USER_TEXT_REQUIRED", issues);
        }

        Candidates(scenario.Version.Diagnoses.Select(x => (x.Id, x.Text)), "DIAGNOSIS", issues);
        Candidates(scenario.Version.Remediations.Select(x => (x.Id, x.Text)), "REMEDIATION", issues);
        Candidates(scenario.Version.Verifications.Select(x => (x.Id, x.Text)), "VERIFICATION", issues);

        var evidence = scenario.Version.Evidence.Select(x => x.Id).ToHashSet();
        foreach (var action in scenario.Version.Actions)
        {
            Id(action.Id, "ACTION_ID_INVALID", issues);
            Required(action.Label, "USER_TEXT_REQUIRED", issues);
            if (action.Phase != ScenarioPhase.Investigate) issues.Add(new("ACTION_PHASE_INVALID", $"Action {action.Id} must use the Investigate phase."));
            if (action.RevealsEvidenceIds.Distinct().Count() != action.RevealsEvidenceIds.Count ||
                action.RevealsEvidenceIds.Any(id => !evidence.Contains(id)))
            {
                issues.Add(new("REQUIRED_REFERENCE_NOT_FOUND", "Action evidence reference is invalid."));
            }
            if (action.CommandRevealsEvidenceIds.Distinct().Count() != action.CommandRevealsEvidenceIds.Count ||
                action.CommandRevealsEvidenceIds.Any(id => !evidence.Contains(id)))
            {
                issues.Add(new("REQUIRED_REFERENCE_NOT_FOUND", "Command evidence reference is invalid."));
            }

            Unique(action.Patterns.Select(x => x.Value), "COMMAND_PATTERN_DUPLICATE", issues);
            if (action.Patterns.Any(x => string.IsNullOrWhiteSpace(x.Value)))
            {
                issues.Add(new("COMMAND_PATTERN_REQUIRED", "Command pattern is required."));
            }
            if (action.Patterns.Count == 0) issues.Add(new("COMMAND_PATTERN_REQUIRED", $"Action {action.Id} requires at least one command pattern."));
        }

        var validationInputs = new List<string>();
        foreach (var action in scenario.Version.Actions)
        {
            foreach (var pattern in action.Patterns)
            {
                if (string.IsNullOrWhiteSpace(pattern.ValidationInput))
                {
                    issues.Add(new("COMMAND_VALIDATION_INPUT_REQUIRED", "Command validation input is required."));
                    continue;
                }

                if (!IsValidValidationInput(pattern.ValidationInput))
                {
                    issues.Add(new("COMMAND_VALIDATION_INPUT_INVALID", "Command validation input is invalid."));
                    continue;
                }

                var match = commands.Match(pattern.ValidationInput, scenario.Version.Actions);
                if (match.Outcome == MatchOutcome.Ambiguous)
                {
                    issues.Add(new("COMMAND_VALIDATION_INPUT_AMBIGUOUS", "Command validation input is ambiguous."));
                }
                else if (match.Outcome != MatchOutcome.Matched || match.ActionId != action.Id)
                {
                    issues.Add(new("COMMAND_VALIDATION_INPUT_NOT_MATCHED", "Command validation input does not match its command."));
                }

                validationInputs.Add(match.Normalized);
            }
        }

        Unique(validationInputs, "COMMAND_VALIDATION_INPUT_DUPLICATE", issues);

        var diagnoses = scenario.Version.Diagnoses.Select(x => x.Id).ToHashSet();
        var remediations = scenario.Version.Remediations.Select(x => x.Id).ToHashSet();
        var verifications = scenario.Version.Verifications.Select(x => x.Id).ToHashSet();
        var definition = scenario.Version.Private;
        if (!diagnoses.Contains(definition.CorrectDiagnosisId) || !remediations.Contains(definition.CorrectRemediationId))
        {
            issues.Add(new("REQUIRED_REFERENCE_NOT_FOUND", "Required candidate reference is invalid."));
        }

        if (definition.RequiredVerificationIds.Count == 0)
        {
            issues.Add(new("VERIFICATION_REQUIRED_EMPTY", "Required verification is missing."));
        }

        if (definition.RequiredVerificationIds.Count != definition.RequiredVerificationIds.Distinct().Count())
        {
            issues.Add(new("VERIFICATION_REQUIRED_DUPLICATE", "Required verification contains duplicates."));
        }

        if (definition.RequiredVerificationIds.Any(id => !verifications.Contains(id)))
        {
            issues.Add(new("VERIFICATION_REQUIRED_NOT_FOUND", "Required verification reference is invalid."));
        }

        if (definition.RequiredEvidenceIds.Count != definition.RequiredEvidenceIds.Distinct().Count() ||
            definition.RequiredEvidenceIds.Any(id => !evidence.Contains(id)))
        {
            issues.Add(new("REQUIRED_REFERENCE_NOT_FOUND", "Required evidence reference is invalid."));
        }
        References(definition.DiagnosisHints.Keys, diagnoses, "DIAGNOSIS_HINT_REFERENCE_INVALID", issues);
        References(definition.RemediationHints.Keys, remediations, "REMEDIATION_HINT_REFERENCE_INVALID", issues);
        References(definition.VerificationHints.Keys, verifications, "VERIFICATION_HINT_REFERENCE_INVALID", issues);
        References(definition.DiagnosisSuccessFeedback.Keys, diagnoses, "DIAGNOSIS_SUCCESS_REFERENCE_INVALID", issues);
        References(definition.RemediationSuccessFeedback.Keys, remediations, "REMEDIATION_SUCCESS_REFERENCE_INVALID", issues);
        References(definition.VerificationSuccessFeedback.Keys, verifications, "VERIFICATION_SUCCESS_REFERENCE_INVALID", issues);
        if (scenario.Category == "lpic1" && scenario.Difficulty == "beginner")
        {
            var coreEvidence = scenario.Version.Actions.Where(x => x.InvestigationGroup == InvestigationGroup.Core).SelectMany(x => x.RevealsEvidenceIds).ToHashSet();
            if (!definition.RequiredEvidenceIds.IsSubsetOf(coreEvidence)) issues.Add(new("CORE_EVIDENCE_UNREACHABLE", "Core actions must reveal all required evidence."));
            if (scenario.Version.Actions.Where(x => x.InvestigationGroup == InvestigationGroup.Supplemental).SelectMany(x => x.RevealsEvidenceIds).Any(definition.RequiredEvidenceIds.Contains)) issues.Add(new("SUPPLEMENTAL_REVEALS_REQUIRED_EVIDENCE", "Supplemental actions must not reveal required evidence."));
            foreach (var id in diagnoses.Where(id => id != definition.CorrectDiagnosisId)) if (!definition.DiagnosisHints.ContainsKey(id)) issues.Add(new("DIAGNOSIS_HINT_REQUIRED", $"Incorrect diagnosis hint is missing: {id}."));
            foreach (var id in remediations.Where(id => id != definition.CorrectRemediationId)) if (!definition.RemediationHints.ContainsKey(id)) issues.Add(new("REMEDIATION_HINT_REQUIRED", $"Incorrect remediation hint is missing: {id}."));
            foreach (var id in verifications.Where(id => !definition.RequiredVerificationIds.Contains(id))) if (!definition.VerificationHints.ContainsKey(id)) issues.Add(new("VERIFICATION_HINT_REQUIRED", $"Incorrect verification hint is missing: {id}."));
        }

        return issues;
    }

    private static void Candidates(IEnumerable<(string Id, string Text)> values, string group, List<ScenarioValidationIssue> issues)
    {
        var candidates = values.ToArray();
        if (candidates.Length == 0)
        {
            issues.Add(new("USER_TEXT_REQUIRED", $"{group} candidates are required."));
        }

        Unique(candidates.Select(x => x.Id), "DUPLICATE_CANDIDATE_ID", issues);
        Unique(candidates.Select(x => x.Text), "DUPLICATE_CANDIDATE_LABEL", issues);
        foreach (var candidate in candidates)
        {
            Id(candidate.Id, "CANDIDATE_ID_INVALID", issues);
            Required(candidate.Text, "USER_TEXT_REQUIRED", issues);
        }
    }

    private static void Unique(IEnumerable<string> values, string code, List<ScenarioValidationIssue> issues)
    {
        var items = values.ToArray();
        if (items.Distinct(StringComparer.Ordinal).Count() != items.Length)
        {
            issues.Add(new(code, "Duplicate value."));
        }
    }

    private static void References(IEnumerable<string> values, HashSet<string> candidates, string code, List<ScenarioValidationIssue> issues)
    {
        foreach (var value in values.Where(value => !candidates.Contains(value)))
        {
            issues.Add(new(code, $"Referenced candidate does not exist: {value}."));
        }
    }

    private static void Id(string value, string code, List<ScenarioValidationIssue> issues)
    {
        if (string.IsNullOrWhiteSpace(value) || value != value.Trim() || value.Any(char.IsControl))
        {
            issues.Add(new(code, "Identifier is invalid."));
        }
    }

    private static void Required(string value, string code, List<ScenarioValidationIssue> issues)
    {
        if (string.IsNullOrWhiteSpace(value) || value.Any(char.IsControl) ||
            value.Contains("TODO", StringComparison.OrdinalIgnoreCase) ||
            value.Contains("TBD", StringComparison.OrdinalIgnoreCase) ||
            value.Contains("FIXME", StringComparison.OrdinalIgnoreCase))
        {
            issues.Add(new(code, "Required text is invalid."));
        }
    }

    private static bool IsValidValidationInput(string value) =>
        value == value.Trim() &&
        !value.Any(char.IsControl) &&
        !value.Contains("TODO", StringComparison.OrdinalIgnoreCase) &&
        !value.Contains("TBD", StringComparison.OrdinalIgnoreCase) &&
        !value.Contains("FIXME", StringComparison.OrdinalIgnoreCase);
}
