using InfraLab.Application;
using InfraLab.Domain;

namespace InfraLab.Application.Tests;

public class EngineTests
{
    [Fact]
    public void Normalize_RemovesSudoAndCollapsesWhitespace()
    {
        var engine = new CommandEngine();
        Assert.Equal("systemctl status app.service", engine.Normalize(" sudo   systemctl status app.service "));
    }

    [Fact]
    public void Match_MapsTypedCommandToSameAction()
    {
        var action = new ScenarioAction("status", "status", ScenarioPhase.Investigate, [new CommandPattern("systemctl status app.service")], "", [], 1, false, true);
        Assert.Equal("status", new CommandEngine().Match("sudo systemctl status app.service", [action]).ActionId);
    }

    [Fact]
    public void Scoring_DangerousRemediationLosesSafetyPoints()
    {
        var privateData = new ScenarioPrivate("d", "safe", new HashSet<string> { "v" }, new HashSet<string>(), [], "");
        var scenario = new Scenario("s", "t", "c", "d", "", [], new ScenarioVersion("v", 1, [], [], [new DiagnosisOption("d", "")], [new RemediationOption("safe", ""), new RemediationOption("bad", "", true)], [new VerificationOption("v", "")], privateData));
        var state = new AttemptState(ScenarioPhase.Review, 3, new HashSet<string>(), new HashSet<string>(), "d", "bad", new HashSet<string> { "v" });
        Assert.Equal(0, new ScoringEngine().Calculate(scenario, state).Safety);
    }

    [Fact]
    public void ExecuteAction_StaysInInvestigationUntilRequiredEvidenceIsRevealed()
    {
        var action = new ScenarioAction("inspect", "inspect", ScenarioPhase.Investigate, [], "", ["required"], 1, false, false);
        var scenario = new Scenario("s", "t", "c", "d", "", [], new ScenarioVersion("v", 1, [action], [], [], [], [], new ScenarioPrivate("", "", [], new HashSet<string> { "required" }, [], "")));
        var next = new ScenarioEngine().ExecuteAction(scenario, new AttemptState(ScenarioPhase.Observe, 0, [], []), "inspect", 0);
        Assert.Equal(ScenarioPhase.Diagnose, next.Phase);
    }
}
