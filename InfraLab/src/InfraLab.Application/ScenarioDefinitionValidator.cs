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
