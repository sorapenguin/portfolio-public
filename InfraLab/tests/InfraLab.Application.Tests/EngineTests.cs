using InfraLab.Application;
using InfraLab.Domain;

namespace InfraLab.Application.Tests;

public class EngineTests
{
    [Fact]
    public void Normalize_RemovesSudoAndCollapsesWhitespace() => Assert.Equal("systemctl status app.service", new CommandEngine().Normalize(" sudo   systemctl status app.service "));

    [Fact]
    public void Match_respects_the_pattern_allow_sudo_setting()
    {
        var command = new CommandEngine();
        var action = new ScenarioAction("status", "status", ScenarioPhase.Investigate,
            [new("systemctl status app.service", "systemctl status app.service", AllowSudo: false)], "", [], 0, false, false);

        Assert.Equal(MatchOutcome.Unrecognized, command.Match("sudo systemctl status app.service", [action]).Outcome);
        Assert.Equal(MatchOutcome.Matched, command.Match("systemctl status app.service", [action]).Outcome);
    }

    [Fact]
    public void Wrong_answers_are_state_changes_and_cannot_be_repeated()
    {
        var scenario = LearningScenario();
        var next = new ScenarioEngine().AnswerDiagnosis(scenario, new AttemptState(ScenarioPhase.Diagnose, 4, [], ["evidence"]), "wrong-diagnosis", 4);
        Assert.Equal(ScenarioPhase.Diagnose, next.Phase);
        Assert.Equal(5, next.StateVersion);
        Assert.Contains("wrong-diagnosis", next.IncorrectDiagnosisIds);
        Assert.Throws<InvalidOperationException>(() => new ScenarioEngine().AnswerDiagnosis(scenario, next, "wrong-diagnosis", 5));
    }

    [Fact]
    public void Correct_answer_advances_once()
    {
        var next = new ScenarioEngine().AnswerDiagnosis(LearningScenario(), new AttemptState(ScenarioPhase.Diagnose, 4, [], ["evidence"]), "correct-diagnosis", 4);
        Assert.Equal(ScenarioPhase.Remediate, next.Phase);
        Assert.Equal(5, next.StateVersion);
    }

    [Theory]
    [InlineData(0, 0, 0, 30, 30, 30, 100)]
    [InlineData(1, 0, 0, 20, 30, 30, 90)]
    [InlineData(1, 1, 1, 20, 20, 20, 70)]
    [InlineData(2, 0, 0, 10, 30, 30, 80)]
    [InlineData(3, 0, 0, 0, 30, 30, 70)]
    [InlineData(4, 0, 0, 0, 30, 30, 70)]
    public void Scoring_uses_100_point_fixed_formula(int diagnosisMistakes, int remediationMistakes, int verificationMistakes, int expectedDiagnosis, int expectedRemediation, int expectedVerification, int expectedTotal)
    {
        var scenario = LearningScenario();
        var diagnosisWrong = Enumerable.Range(0, diagnosisMistakes).Select(x => $"wrong-diagnosis-{x}").ToHashSet();
        var remediationWrong = Enumerable.Range(0, remediationMistakes).Select(x => $"wrong-remediation-{x}").ToHashSet();
        var verificationWrong = Enumerable.Range(0, verificationMistakes).Select(x => $"wrong-verification-{x}").ToHashSet();
        var state = new AttemptState(ScenarioPhase.Review, 9, [], [], "correct-diagnosis", "correct-remediation", ["verification"], diagnosisWrong, remediationWrong, verificationWrong);
        var score = new ScoringEngine().Calculate(scenario, state);
        Assert.Equal(expectedDiagnosis, score.Diagnosis);
        Assert.Equal(expectedRemediation, score.Remediation);
        Assert.Equal(expectedVerification, score.Verification);
        Assert.Equal(10, score.Investigation);
        Assert.Equal(0, score.Safety);
        Assert.Equal(expectedTotal, score.Total);
        Assert.Equal(score.Investigation + score.Diagnosis + score.Remediation + score.Verification, score.Total);
    }

    [Fact]
    public void Scoring_does_not_reduce_investigation_for_extra_actions_or_add_safety()
    {
        var scenario = LearningScenario();
        var minimumInvestigation = new AttemptState(ScenarioPhase.Review, 9, ["action-1", "action-2", "action-3", "action-4"], [], "correct-diagnosis", "correct-remediation", ["verification"]);
        var allActions = minimumInvestigation with { ExecutedActionIds = Enumerable.Range(1, 8).Select(x => $"action-{x}").ToHashSet() };

        var minimumScore = new ScoringEngine().Calculate(scenario, minimumInvestigation);
        var allActionsScore = new ScoringEngine().Calculate(scenario, allActions);

        Assert.Equal(10, minimumScore.Investigation);
        Assert.Equal(10, allActionsScore.Investigation);
        Assert.Equal(100, minimumScore.Total);
        Assert.Equal(minimumScore.Total, allActionsScore.Total);
        Assert.Equal(0, minimumScore.Safety);
        Assert.Equal(0, allActionsScore.Safety);
        Assert.Equal(allActionsScore.Investigation + allActionsScore.Diagnosis + allActionsScore.Remediation + allActionsScore.Verification, allActionsScore.Total);
    }

    [Fact]
    public void Observation_and_investigation_remain_internal_phases()
    {
        var action = new ScenarioAction("inspect", "inspect", ScenarioPhase.Investigate, [], "", ["required"], 1, false, false);
        var scenario = new Scenario("s", "t", "c", "d", "", [], new ScenarioVersion("v", 1, [action], [], [], [], [], new ScenarioPrivate("", "", [], ["required"], [], "")));
        var next = new ScenarioEngine().ExecuteAction(scenario, new AttemptState(ScenarioPhase.Observe, 0, [], []), "inspect", 0);
        Assert.Equal(ScenarioPhase.Investigate, next.Phase);
        Assert.True(new ScenarioEngine().CanAdvanceToDiagnosis(scenario, next));
    }

    private static Scenario LearningScenario()
    {
        var data = new ScenarioPrivate("correct-diagnosis", "correct-remediation", ["verification"], ["evidence"], [], "");
        return new Scenario("s", "t", "c", "d", "", [], new ScenarioVersion("v", 1, [], [], [new DiagnosisOption("correct-diagnosis", ""), new DiagnosisOption("wrong-diagnosis", "")], [new RemediationOption("correct-remediation", ""), new RemediationOption("wrong-remediation", "")], [new VerificationOption("verification", "")], data));
    }
}
