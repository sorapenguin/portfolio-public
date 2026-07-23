using System.Text.Json;
using InfraLab.Application;
using InfraLab.Domain;

namespace InfraLab.Content.Tests;

public sealed class ScenarioContentTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    [Fact]
    public void Published_scenarios_deserialize_validate_and_have_unique_ids_and_versions()
    {
        var scenarios = LoadPublishedScenarios();

        Assert.Equal(3, scenarios.Count);
        Assert.Equal(scenarios.Count, scenarios.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count());
        Assert.Equal(scenarios.Count, scenarios.Select(x => x.Version.Id).Distinct(StringComparer.Ordinal).Count());
        Assert.All(scenarios, scenario => Assert.False(new ScenarioDefinitionValidator().Validate(scenario).Any(x => x.IsError), $"Scenario validation failed: {scenario.Id}"));
        _ = ScenarioCatalog.Create(scenarios);
    }

    [Fact]
    public void Published_beginner_lpic1_scenarios_preserve_the_content_invariants()
    {
        var scenarios = LoadPublishedScenarios();

        Assert.All(scenarios, scenario =>
        {
            var version = scenario.Version;
            Assert.Equal("lpic1", scenario.Category);
            Assert.Equal("beginner", scenario.Difficulty);
            Assert.Equal(8, version.Actions.Count);
            Assert.Equal(4, version.Private.RequiredEvidenceIds.Count);
            Assert.Equal(4, version.Diagnoses.Count);
            Assert.Equal(4, version.Remediations.Count);
            Assert.Equal(4, version.Verifications.Count);
            Assert.All(version.Actions, action => Assert.False(string.IsNullOrWhiteSpace(action.ExecutionExample?.Text)));
            Assert.All(version.Diagnoses.Where(x => x.Id != version.Private.CorrectDiagnosisId), option => Assert.True(version.Private.DiagnosisHints.ContainsKey(option.Id)));
            Assert.All(version.Remediations.Where(x => x.Id != version.Private.CorrectRemediationId), option => Assert.True(version.Private.RemediationHints.ContainsKey(option.Id)));
            Assert.All(version.Verifications.Where(x => !version.Private.RequiredVerificationIds.Contains(x.Id)), option => Assert.True(version.Private.VerificationHints.ContainsKey(option.Id)));
        });
    }

    private static IReadOnlyList<Scenario> LoadPublishedScenarios()
    {
        var contentRoot = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "content"));
        Assert.True(Directory.Exists(contentRoot), $"Content root not found: {contentRoot}");
        return Directory.EnumerateFiles(contentRoot, "*.json", SearchOption.AllDirectories)
            .Select(path => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(path), Json))
            .Select((scenario, index) => scenario ?? throw new Xunit.Sdk.XunitException($"Scenario at index {index} did not deserialize."))
            .ToArray();
    }
}
