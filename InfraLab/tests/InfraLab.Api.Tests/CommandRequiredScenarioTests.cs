using System.Text.Json;
using InfraLab.Application;
using InfraLab.Domain;

namespace InfraLab.Api.Tests;

public sealed class CommandRequiredScenarioTests
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    [Fact]
    public void Published_beginner_actions_all_reveal_evidence()
    {
        foreach (var scenario in BeginnerScenarios())
        {
            Assert.Equal(8, scenario.Version.Actions.Count);
            Assert.Equal(4, scenario.Version.Private.RequiredEvidenceIds.Count);
            Assert.Equal(4, scenario.Version.Actions.Count(action => action.InvestigationGroup == InvestigationGroup.Core));
            Assert.Equal(4, scenario.Version.Actions.Count(action => action.InvestigationGroup == InvestigationGroup.Supplemental));
            Assert.All(scenario.Version.Actions, action =>
            {
                Assert.NotEmpty(action.RevealsEvidenceIds);
                Assert.NotNull(action.ExecutionExample);
                Assert.Equal("Command", action.ExecutionExample!.Kind);
                Assert.False(string.IsNullOrWhiteSpace(action.ExecutionExample.Text));
            });
        }
    }

    [Fact]
    public void Published_beginner_required_evidence_is_revealed_by_actions()
    {
        foreach (var scenario in BeginnerScenarios())
        {
            var revealed = scenario.Version.Actions.SelectMany(action => action.RevealsEvidenceIds).ToHashSet();
            Assert.All(scenario.Version.Private.RequiredEvidenceIds, evidenceId => Assert.Contains(evidenceId, revealed));
        }
    }

    [Fact]
    public void Required_actions_alone_make_diagnosis_available_without_supplemental_actions()
    {
        foreach (var scenario in BeginnerScenarios())
        {
            var state = new AttemptState(ScenarioPhase.Observe, 0, [], []);
            foreach (var action in scenario.Version.Actions.Where(action => action.InvestigationGroup == InvestigationGroup.Core))
                state = new ScenarioEngine().ExecuteAction(scenario, state, action.Id, state.StateVersion);
            var engine = new ScenarioEngine();
            Assert.Equal(ScenarioPhase.Investigate, state.Phase);
            Assert.True(engine.CanAdvanceToDiagnosis(scenario, state));
            Assert.Equal(ScenarioPhase.Diagnose, engine.AdvanceToDiagnosis(scenario, state, state.StateVersion).Phase);
        }
    }

    [Fact]
    public void Supplemental_actions_alone_do_not_reach_diagnose()
    {
        foreach (var scenario in BeginnerScenarios())
        {
            var state = new AttemptState(ScenarioPhase.Observe, 0, [], []);
            foreach (var action in scenario.Version.Actions.Where(action => action.InvestigationGroup == InvestigationGroup.Supplemental))
                state = new ScenarioEngine().ExecuteAction(scenario, state, action.Id, state.StateVersion);

            Assert.Equal(ScenarioPhase.Investigate, state.Phase);
        }
    }

    [Fact]
    public void Command_engine_remains_available_for_future_command_enabled_scenarios()
    {
        var action = new ScenarioAction("inspect", "inspect", ScenarioPhase.Investigate, [new CommandPattern("ip route show")], "", ["evidence"], 1, false, true);
        Assert.Equal("inspect", new CommandEngine().Match("sudo ip route show", [action]).ActionId);
    }

    private static IReadOnlyList<Scenario> BeginnerScenarios()
    {
        var root = new DirectoryInfo(Directory.GetCurrentDirectory());
        while (root is not null && !Directory.Exists(Path.Combine(root.FullName, "content"))) root = root.Parent;
        if (root is null) throw new DirectoryNotFoundException("Scenario content directory was not found.");
        return Directory.EnumerateFiles(Path.Combine(root.FullName, "content", "lpic1"), "*.json")
            .Select(file => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(file), JsonOptions) ?? throw new InvalidOperationException("Scenario JSON could not be read."))
            .Where(scenario => scenario.Difficulty == "beginner")
            .ToArray();
    }
}
