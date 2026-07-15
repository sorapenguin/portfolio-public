using System.Net;
using System.Text;
using InfraLab.Client.Services;

namespace InfraLab.Api.Tests;

public sealed class InfraLabApiClientTests
{
    [Fact]
    public async Task Conflict_response_preserves_status_code()
    {
        var exception = await Assert.ThrowsAsync<ApiRequestException>(() => CreateClient(HttpStatusCode.Conflict).GetAttemptAsync(Guid.NewGuid(), CancellationToken.None));

        Assert.Equal(HttpStatusCode.Conflict, exception.StatusCode);
    }

    [Fact]
    public async Task Unprocessable_entity_response_preserves_status_code()
    {
        var exception = await Assert.ThrowsAsync<ApiRequestException>(() => CreateClient(HttpStatusCode.UnprocessableEntity).GetAttemptAsync(Guid.NewGuid(), CancellationToken.None));

        Assert.Equal(HttpStatusCode.UnprocessableEntity, exception.StatusCode);
    }

    [Fact]
    public async Task Not_found_response_becomes_api_not_found_exception()
    {
        await Assert.ThrowsAsync<ApiNotFoundException>(() => CreateClient(HttpStatusCode.NotFound).GetAttemptAsync(Guid.NewGuid(), CancellationToken.None));
    }

    [Fact]
    public async Task Get_attempt_response_deserializes_to_public_attempt_view()
    {
        var attemptId = Guid.NewGuid();
        var client = CreateClient(HttpStatusCode.OK, $$"""
            {
              "id":"{{attemptId}}",
              "scenarioId":"scenario-1",
              "scenarioTitle":"公開シナリオ",
              "symptoms":["症状"],
              "status":0,
              "phase":1,
              "stateVersion":3,
              "revealedEvidence":[],
              "availableActions":[],
              "executedActions":[],
              "availableDiagnoses":[],
              "availableRemediations":[],
              "availableVerifications":[]
            }
            """);

        var attempt = await client.GetAttemptAsync(attemptId, CancellationToken.None);

        Assert.Equal(attemptId, attempt.Id);
        Assert.Equal("scenario-1", attempt.ScenarioId);
        Assert.Equal(3, attempt.StateVersion);
    }

    [Fact]
    public async Task Start_attempt_posts_to_existing_route_and_deserializes_public_attempt_view()
    {
        var attemptId = Guid.NewGuid();
        var handler = new StubHandler(() => new HttpResponseMessage(HttpStatusCode.Created)
        {
            Content = new StringContent($$"""{"id":"{{attemptId}}","scenarioId":"scenario-1","scenarioTitle":"公開シナリオ","symptoms":[],"status":0,"phase":0,"stateVersion":0,"revealedEvidence":[],"availableActions":[],"executedActions":[],"availableDiagnoses":[],"availableRemediations":[],"availableVerifications":[]}""", Encoding.UTF8, "application/json")
        });
        var client = new InfraLabApiClient(new HttpClient(handler) { BaseAddress = new Uri("http://localhost/") });

        var attempt = await client.StartAttemptAsync("scenario-1", CancellationToken.None);

        Assert.Equal(HttpMethod.Post, handler.LastRequest!.Method);
        Assert.Equal("/api/scenarios/scenario-1/attempts", handler.LastRequest.RequestUri!.AbsolutePath);
        Assert.Equal(attemptId, attempt.Id);
        Assert.Equal(0, attempt.StateVersion);
    }

    [Fact]
    public async Task Get_attempt_review_uses_review_route_and_deserializes_public_dto()
    {
        var attemptId = Guid.NewGuid();
        var handler = new StubHandler(() => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent($$"""{"attemptId":"{{attemptId}}","scenarioId":"scenario-1","scenarioTitle":"公開シナリオ","diagnosis":{"selectedLabel":"選択","expectedLabel":"推奨","isCorrect":true,"explanation":null,"earnedScore":1,"maxScore":null},"remediation":null,"verification":null}""", Encoding.UTF8, "application/json")
        });
        var client = new InfraLabApiClient(new HttpClient(handler) { BaseAddress = new Uri("http://localhost/") });

        var review = await client.GetAttemptReviewAsync(attemptId, CancellationToken.None);

        Assert.Equal($"/api/attempts/{attemptId}/review", handler.LastRequest!.RequestUri!.AbsolutePath);
        Assert.Equal(attemptId, review.AttemptId);
        Assert.NotNull(review.Diagnosis);
        Assert.True(review.Diagnosis.IsCorrect);
    }

    [Fact]
    public async Task Get_attempt_review_not_found_becomes_api_not_found_exception()
    {
        await Assert.ThrowsAsync<ApiNotFoundException>(() => CreateClient(HttpStatusCode.NotFound).GetAttemptReviewAsync(Guid.NewGuid(), CancellationToken.None));
    }

    [Fact]
    public async Task Get_attempt_review_422_preserves_status_code()
    {
        var exception = await Assert.ThrowsAsync<ApiRequestException>(() => CreateClient(HttpStatusCode.UnprocessableEntity).GetAttemptReviewAsync(Guid.NewGuid(), CancellationToken.None));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, exception.StatusCode);
    }

    private static InfraLabApiClient CreateClient(HttpStatusCode statusCode, string content = "{}") =>
        new(new HttpClient(new StubHandler(() => new HttpResponseMessage(statusCode)
        {
            Content = new StringContent(content, Encoding.UTF8, "application/json")
        }))
        {
            BaseAddress = new Uri("http://localhost/")
        });

    private sealed class StubHandler(Func<HttpResponseMessage> createResponse) : HttpMessageHandler
    {
        public HttpRequestMessage? LastRequest { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(CreateResponse(request));

        private HttpResponseMessage CreateResponse(HttpRequestMessage request)
        {
            LastRequest = request;
            return createResponse();
        }
    }
}
