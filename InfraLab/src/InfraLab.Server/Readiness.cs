using InfraLab.Application;
using InfraLab.Infrastructure;

public enum ReadinessStatus
{
    Healthy,
    PostgreSqlUnavailable,
    RequiredScenarioMissing,
    ScenarioValidationFailed
}

public interface IInfraLabReadinessProbe
{
    Task<ReadinessStatus> CheckAsync(CancellationToken cancellationToken);
}

public sealed class InfraLabReadinessProbe(
    InfraLabDbContext db,
    ScenarioCatalog catalog,
    ScenarioDefinitionValidator validator,
    ILogger<InfraLabReadinessProbe> logger) : IInfraLabReadinessProbe
{
    private static readonly string[] RequiredScenarioIds =
    [
        "linux-systemd-203-001",
        "linux-dns-resolution-001",
        "linux-inode-exhaustion-001"
    ];

    public async Task<ReadinessStatus> CheckAsync(CancellationToken cancellationToken)
    {
        try
        {
            if (!await db.Database.CanConnectAsync(cancellationToken))
            {
                logger.LogWarning("Readiness check failed: PostgreSQL connection is unavailable.");
                return ReadinessStatus.PostgreSqlUnavailable;
            }
        }
        catch (Exception exception)
        {
            logger.LogError("Readiness check failed: PostgreSQL connection raised {ErrorType}.", exception.GetType().Name);
            return ReadinessStatus.PostgreSqlUnavailable;
        }

        foreach (var scenarioId in RequiredScenarioIds)
        {
            if (!catalog.TryGet(scenarioId, out _))
            {
                logger.LogError("Readiness check failed: a required scenario is missing.");
                return ReadinessStatus.RequiredScenarioMissing;
            }
        }

        try
        {
            if (catalog.Scenarios.SelectMany(validator.Validate).Any(issue => issue.IsError))
            {
                logger.LogError("Readiness check failed: scenario validation reported an error.");
                return ReadinessStatus.ScenarioValidationFailed;
            }
        }
        catch (Exception exception)
        {
            logger.LogError("Readiness check failed: scenario validation raised {ErrorType}.", exception.GetType().Name);
            return ReadinessStatus.ScenarioValidationFailed;
        }

        return ReadinessStatus.Healthy;
    }
}
