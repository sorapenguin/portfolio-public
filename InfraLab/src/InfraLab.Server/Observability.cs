using System.Diagnostics;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Routing;

public static partial class Observability
{
    public const string CorrelationHeaderName = "X-Correlation-ID";
    private const int MaximumCorrelationIdLength = 32;

    public static string GetCorrelationId(HttpRequest request)
    {
        var supplied = request.Headers[CorrelationHeaderName].ToString();
        if (supplied.Length == MaximumCorrelationIdLength && CorrelationIdPattern().IsMatch(supplied))
            return supplied.ToLowerInvariant();

        var traceId = Activity.Current?.TraceId.ToString();
        return !string.IsNullOrWhiteSpace(traceId) ? traceId : Guid.NewGuid().ToString("N");
    }

    public static string GetSafeRoute(HttpContext context) =>
        context.GetEndpoint() is RouteEndpoint endpoint
            ? endpoint.RoutePattern.RawText ?? context.Request.Path.Value ?? "/"
            : context.Request.Path.Value ?? "/";

    public static string GetApplicationVersion(IConfiguration configuration) =>
        configuration["InfraLab:ApplicationVersion"] is { Length: > 0 and <= 80 } version
            ? version
            : "unknown";

    public static async Task LogRequestAsync(HttpContext context, RequestDelegate next, ILogger logger, string environment, string version)
    {
        var correlationId = GetCorrelationId(context.Request);
        context.TraceIdentifier = correlationId;
        context.Response.Headers[CorrelationHeaderName] = correlationId;
        var stopwatch = Stopwatch.StartNew();

        using (logger.BeginScope(new Dictionary<string, object?>
        {
            ["CorrelationId"] = correlationId,
            ["TraceId"] = Activity.Current?.TraceId.ToString(),
            ["SpanId"] = Activity.Current?.SpanId.ToString(),
            ["RequestId"] = context.TraceIdentifier,
            ["Environment"] = environment,
            ["ApplicationVersion"] = version
        }))
        {
            try
            {
                await next(context);
            }
            finally
            {
                stopwatch.Stop();
                var route = GetSafeRoute(context);
                var statusCode = context.Response.StatusCode;
                var isHealthyProbe = statusCode == StatusCodes.Status200OK &&
                    (route is "/health/live" or "/health/ready" or "/api/health");

                if (isHealthyProbe)
                    logger.LogDebug(ObservabilityEvents.RequestCompleted, "HTTP request completed: {Method} {Route} {StatusCode} in {ElapsedMilliseconds} ms.", context.Request.Method, route, statusCode, stopwatch.Elapsed.TotalMilliseconds);
                else if (statusCode >= StatusCodes.Status500InternalServerError)
                    logger.LogWarning(ObservabilityEvents.RequestFailed, "HTTP request failed: {Method} {Route} {StatusCode} in {ElapsedMilliseconds} ms.", context.Request.Method, route, statusCode, stopwatch.Elapsed.TotalMilliseconds);
                else
                    logger.LogInformation(ObservabilityEvents.RequestCompleted, "HTTP request completed: {Method} {Route} {StatusCode} in {ElapsedMilliseconds} ms.", context.Request.Method, route, statusCode, stopwatch.Elapsed.TotalMilliseconds);
            }
        }
    }

    public static Task WriteSafeExceptionAsync(HttpContext context, ILogger logger)
    {
        var error = context.Features.Get<IExceptionHandlerFeature>()?.Error;
        // ExceptionHandler clears the response before invoking this branch, so restore the support header here.
        context.Response.Headers[CorrelationHeaderName] = context.TraceIdentifier;
        logger.LogError(ObservabilityEvents.UnhandledException,
            "Unhandled request exception. ExceptionType: {ExceptionType}. CorrelationId: {CorrelationId}.",
            error?.GetType().Name ?? "Unknown", context.TraceIdentifier);

        return Results.Problem(
            statusCode: StatusCodes.Status500InternalServerError,
            title: "An unexpected error occurred.",
            extensions: new Dictionary<string, object?> { ["correlationId"] = context.TraceIdentifier })
            .ExecuteAsync(context);
    }

    [GeneratedRegex("^[0-9a-fA-F]{32}$", RegexOptions.CultureInvariant)]
    private static partial Regex CorrelationIdPattern();
}

public static class ObservabilityEvents
{
    public static readonly EventId ApplicationStarted = new(1000, nameof(ApplicationStarted));
    public static readonly EventId CatalogLoaded = new(1001, nameof(CatalogLoaded));
    public static readonly EventId ApplicationStopping = new(1002, nameof(ApplicationStopping));
    public static readonly EventId UnhandledException = new(1100, nameof(UnhandledException));
    public static readonly EventId RequestCompleted = new(1200, nameof(RequestCompleted));
    public static readonly EventId RequestFailed = new(1201, nameof(RequestFailed));
}
