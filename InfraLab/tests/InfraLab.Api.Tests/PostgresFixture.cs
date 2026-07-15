using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace InfraLab.Api.Tests;

[CollectionDefinition("PostgreSQL", DisableParallelization = true)]
public sealed class PostgreSqlCollection : ICollectionFixture<PostgresFixture>;

public sealed class PostgresFixture : IAsyncLifetime
{
    private readonly string connectionString = TestDatabase.ConnectionString;

    public InfraLabDbContext CreateContext() => new(new DbContextOptionsBuilder<InfraLabDbContext>().UseNpgsql(connectionString).Options);

    public async Task InitializeAsync()
    {
        await using var db = CreateContext();
        await db.Database.MigrateAsync();
    }

    public async Task ResetAsync()
    {
        await using var db = CreateContext();
        await db.AttemptEvents.ExecuteDeleteAsync();
        await db.Attempts.ExecuteDeleteAsync();
    }

    public Task DisposeAsync() => Task.CompletedTask;
}

internal static class TestDatabase
{
    public static string ConnectionString
    {
        get
        {
            var value = Environment.GetEnvironmentVariable("ConnectionStrings__InfraLabTest");
            if (string.IsNullOrWhiteSpace(value))
                throw new InvalidOperationException("ConnectionStrings__InfraLabTest is required for PostgreSQL integration tests; no fallback is available.");
            var builder = new NpgsqlConnectionStringBuilder(value);
            if (!string.Equals(builder.Database, "infralab_test", StringComparison.Ordinal))
                throw new InvalidOperationException("PostgreSQL integration tests require the dedicated infralab_test database.");
            return value;
        }
    }
}
