using InfraLab.Domain;

namespace InfraLab.Application;

public sealed class ScenarioCatalog
{
    private readonly IReadOnlyDictionary<string, Scenario> byId;

    private ScenarioCatalog(IReadOnlyDictionary<string, Scenario> byId) => this.byId = byId;

    public IReadOnlyList<Scenario> Scenarios => byId.Values.ToArray();

    public bool TryGet(string id, out Scenario scenario) => byId.TryGetValue(id, out scenario!);

    public static ScenarioCatalog Create(IEnumerable<Scenario>? source, ScenarioDefinitionValidator? validator = null)
    {
        var scenarios = source?.ToArray() ?? [];
        var issues = new List<(int Index, ScenarioValidationIssue Issue)>();
        if (scenarios.Length == 0)
        {
            issues.Add((0, new("SCENARIO_CORPUS_EMPTY", "Scenario corpus is empty.")));
        }

        for (var index = 0; index < scenarios.Length; index++)
        {
            var scenario = scenarios[index];
            if (scenario is null)
            {
                issues.Add((index, new("SCENARIO_ID_REQUIRED", "Scenario is missing.")));
                continue;
            }

            issues.AddRange((validator ?? new ScenarioDefinitionValidator()).Validate(scenario)
                .Where(x => x.IsError)
                .Select(x => (index, x)));
        }

        foreach (var duplicate in scenarios.Where(x => x is not null)
                     .GroupBy(x => x.Id, StringComparer.Ordinal)
                     .Where(x => x.Count() > 1))
        {
            issues.Add((0, new("DUPLICATE_SCENARIO_ID", "Scenario identifier is duplicated.")));
        }

        if (issues.Count > 0)
        {
            throw new ScenarioCatalogValidationException(issues.Take(8).Select(x => $"[{x.Index}] {x.Issue.Code}: {x.Issue.Message}"));
        }

        return new ScenarioCatalog(scenarios.ToDictionary(x => x.Id, StringComparer.Ordinal));
    }
}

public sealed class ScenarioCatalogValidationException : InvalidOperationException
{
    public ScenarioCatalogValidationException(IEnumerable<string> safeIssues)
        : base($"Scenario catalog validation failed: {string.Join("; ", safeIssues)}")
    {
    }
}
