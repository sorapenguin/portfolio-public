using System.Net;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class HealthEndpointTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Live_returns_healthy_without_using_the_readiness_probe()
    {
        await using var factory = CreateFactory(ReadinessStatus.PostgreSqlUnavailable);
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/health/live");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        await AssertHealthyResponseAsync(response);
    }

    [Fact]
    public async Task Ready_returns_healthy_for_the_database_and_valid_published_catalog()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/health/ready");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        await AssertHealthyResponseAsync(response);
    }

    [Fact]
    public async Task Successful_live_probe_is_suppressed_to_debug_logging()
    {
        var logs = new InMemoryLoggerProvider();
        await using var factory = new TestServerFactory(logs);
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/health/live");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.DoesNotContain(logs.Entries, entry => entry.EventId == ObservabilityEvents.RequestCompleted.Id && entry.Level >= LogLevel.Information);
    }

    [Theory]
    [InlineData(ReadinessStatus.PostgreSqlUnavailable)]
    [InlineData(ReadinessStatus.RequiredScenarioMissing)]
    [InlineData(ReadinessStatus.ScenarioValidationFailed)]
    public async Task Ready_returns_a_safe_503_for_each_readiness_failure(ReadinessStatus status)
    {
        await using var factory = CreateFactory(status);
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/health/ready");
        var body = await response.Content.ReadAsStringAsync();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.DoesNotContain("Postgre", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Exception", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("correctDiagnosisId", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("requiredEvidenceIds", body, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Legacy_api_health_keeps_the_readiness_contract()
    {
        await using var factory = CreateFactory(ReadinessStatus.RequiredScenarioMissing);
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/api/health");

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
    }

    [Fact]
    public async Task Production_unhandled_exception_returns_a_safe_problem_response()
    {
        var logs = new InMemoryLoggerProvider();
        await using var factory = new TestServerFactory(logs).WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Production");
            builder.UseSetting(ForwardedHeadersConfiguration.TrustedProxyNetworksConfigurationKey, "127.0.0.1/32");
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<IInfraLabReadinessProbe>();
                services.AddScoped<IInfraLabReadinessProbe, ThrowingReadinessProbe>();
            });
        });
        using var client = factory.CreateClient();

        var response = await client.GetAsync("/health/ready");
        var body = await response.Content.ReadAsStringAsync();

        Assert.Equal(HttpStatusCode.InternalServerError, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        Assert.DoesNotContain("readiness probe test failure", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Exception", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("System.", body, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("ConnectionStrings", body, StringComparison.OrdinalIgnoreCase);
        using var problem = JsonDocument.Parse(body);
        var correlationId = problem.RootElement.GetProperty("correlationId").GetString();
        Assert.Matches("^[0-9a-f]{32}$", correlationId);
        Assert.Equal(correlationId, response.Headers.GetValues(Observability.CorrelationHeaderName).Single());
        Assert.Contains(logs.Entries, entry => entry.EventId == ObservabilityEvents.UnhandledException.Id && entry.Message.Contains(correlationId!, StringComparison.Ordinal));
        Assert.DoesNotContain(logs.Entries, entry => entry.Message.Contains("readiness probe test failure", StringComparison.OrdinalIgnoreCase));
    }

    [Fact]
    public async Task Production_ignores_an_invalid_external_correlation_id_and_does_not_log_request_secrets()
    {
        var logs = new InMemoryLoggerProvider();
        await using var factory = new TestServerFactory(logs).WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Production");
            builder.UseSetting(ForwardedHeadersConfiguration.TrustedProxyNetworksConfigurationKey, "127.0.0.1/32");
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<IInfraLabReadinessProbe>();
                services.AddScoped<IInfraLabReadinessProbe, SensitiveThrowingReadinessProbe>();
            });
        });
        using var client = factory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, "/health/ready?password=do-not-log")
        {
            Headers = { { Observability.CorrelationHeaderName, new string('x', 500) } }
        };

        var response = await client.SendAsync(request);

        var correlationId = response.Headers.GetValues(Observability.CorrelationHeaderName).Single();
        Assert.Matches("^[0-9a-f]{32}$", correlationId);
        Assert.DoesNotContain(logs.Entries, entry => entry.Message.Contains("do-not-log", StringComparison.OrdinalIgnoreCase));
        Assert.DoesNotContain(logs.Entries, entry => entry.Message.Contains("password", StringComparison.OrdinalIgnoreCase));
        Assert.Contains(logs.Entries, entry => entry.EventId == ObservabilityEvents.RequestFailed.Id && entry.Level >= LogLevel.Warning);
    }

    private static WebApplicationFactory<Program> CreateFactory(ReadinessStatus status) =>
        new TestServerFactory().WithWebHostBuilder(builder =>
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<IInfraLabReadinessProbe>();
                services.AddScoped<IInfraLabReadinessProbe>(_ => new StubReadinessProbe(status));
            }));

    private static async Task AssertHealthyResponseAsync(HttpResponseMessage response)
    {
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(new[] { "status" }, json.RootElement.EnumerateObject().Select(property => property.Name));
        Assert.Equal("Healthy", json.RootElement.GetProperty("status").GetString());
    }

    private sealed class StubReadinessProbe(ReadinessStatus status) : IInfraLabReadinessProbe
    {
        public Task<ReadinessStatus> CheckAsync(CancellationToken cancellationToken) => Task.FromResult(status);
    }

    private sealed class ThrowingReadinessProbe : IInfraLabReadinessProbe
    {
        public Task<ReadinessStatus> CheckAsync(CancellationToken cancellationToken) =>
            throw new InvalidOperationException("readiness probe test failure");
    }

    private sealed class SensitiveThrowingReadinessProbe : IInfraLabReadinessProbe
    {
        public Task<ReadinessStatus> CheckAsync(CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Password=do-not-log; StateJson=do-not-log; ScoreJson=do-not-log");
    }
}

public sealed class InMemoryLoggerProvider : ILoggerProvider
{
    private readonly List<InMemoryLogEntry> entries = [];
    public IReadOnlyList<InMemoryLogEntry> Entries => entries;
    public ILogger CreateLogger(string categoryName) => new InMemoryLogger(entries);
    public void Dispose() { }

    private sealed class InMemoryLogger(List<InMemoryLogEntry> entries) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => NullScope.Instance;
        public bool IsEnabled(LogLevel logLevel) => true;
        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter) =>
            entries.Add(new InMemoryLogEntry(logLevel, eventId.Id, formatter(state, exception)));
    }

    private sealed class NullScope : IDisposable
    {
        public static readonly NullScope Instance = new();
        public void Dispose() { }
    }
}

public sealed record InMemoryLogEntry(LogLevel Level, int EventId, string Message);
