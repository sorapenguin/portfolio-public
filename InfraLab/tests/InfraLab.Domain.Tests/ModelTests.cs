using InfraLab.Domain;

namespace InfraLab.Domain.Tests;

public class ModelTests
{
    [Fact]
    public void InitialAttemptState_IsObserveAndVersionZero()
    {
        var state = new AttemptState(ScenarioPhase.Observe, 0, new HashSet<string>(), new HashSet<string>());
        Assert.Equal(ScenarioPhase.Observe, state.Phase);
        Assert.Equal(0, state.StateVersion);
    }
}
