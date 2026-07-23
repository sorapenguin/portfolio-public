using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class ApiEndToEndTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Full_flow_replays_idempotently_reloads_and_retries()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await client.GetFromJsonAsync<JsonDocument>("/api/scenarios");
        Assert.Contains(scenarios!.RootElement.EnumerateArray(), item => item.GetProperty("id").GetString() == "linux-systemd-203-001");

        var start = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        Assert.Equal(HttpStatusCode.Created, start.StatusCode);
        var attempt = await start.Content.ReadFromJsonAsync<JsonDocument>();
        var id = attempt!.RootElement.GetProperty("id").GetGuid();
        var version = attempt.RootElement.GetProperty("stateVersion").GetInt32();

        foreach (var action in new[] { "app-status", "app-journal", "unit-cat", "ls-permissions" })
        {
            var key = Guid.NewGuid().ToString("N");
            var response = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", new { actionId = action, stateVersion = version, idempotencyKey = key });
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            var body = await response.Content.ReadFromJsonAsync<JsonDocument>();
            version = body!.RootElement.GetProperty("stateVersion").GetInt32();
            if (action == "app-status")
            {
                var replay = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", new { actionId = action, stateVersion = version - 1, idempotencyKey = key });
                Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
            }
        }

        version = await AssertMutation(client, id, "/advance-to-diagnosis", new { stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });

        var diagnosisKey = Guid.NewGuid().ToString("N");
        var diagnosisResponse = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new { optionId = "not-executable", stateVersion = version, idempotencyKey = diagnosisKey });
        var diagnosisResult = await ReadAnswerResultAsync(diagnosisResponse);
        Assert.Equal("Correct", diagnosisResult.Outcome); Assert.Equal(3, diagnosisResult.Attempt.Phase); version = diagnosisResult.Attempt.StateVersion;
        var diagnosisReplay = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new { optionId = "not-executable", stateVersion = version - 1, idempotencyKey = diagnosisKey });
        var diagnosisReplayResult = await ReadAnswerResultAsync(diagnosisReplay); Assert.Equal(version, diagnosisReplayResult.Attempt.StateVersion); Assert.Equal(3, diagnosisReplayResult.Attempt.Phase);
        var remediationKey = Guid.NewGuid().ToString("N");
        var remediationResponse = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new { optionId = "chmod-and-restart", stateVersion = version, idempotencyKey = remediationKey });
        var remediationResult = await ReadAnswerResultAsync(remediationResponse);
        Assert.Equal("Correct", remediationResult.Outcome); Assert.Equal(4, remediationResult.Attempt.Phase); version = remediationResult.Attempt.StateVersion;
        var verificationId = remediationResult.Attempt.AvailableVerifications[0].Id; var verificationKey = Guid.NewGuid().ToString("N");
        var verificationResponse = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new { optionIds = new[] { verificationId }, stateVersion = version, idempotencyKey = verificationKey });
        var verificationResult = await ReadAnswerResultAsync(verificationResponse);
        Assert.Equal("Correct", verificationResult.Outcome); Assert.Equal(5, verificationResult.Attempt.Phase); version = verificationResult.Attempt.StateVersion;

        var reloaded = await client.GetAsync($"/api/attempts/{id}");
        Assert.Equal(HttpStatusCode.OK, reloaded.StatusCode);
        var result = await client.GetAsync($"/api/attempts/{id}/result");
        Assert.Equal(HttpStatusCode.OK, result.StatusCode);
        var resultBody = await result.Content.ReadFromJsonAsync<JsonDocument>();
        Assert.Equal(100, resultBody!.RootElement.GetProperty("score").GetProperty("total").GetInt32());
        var completedMutation = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", new { actionId = "app-status", stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        Assert.Equal(HttpStatusCode.UnprocessableEntity, completedMutation.StatusCode);
        var retry = await client.PostAsync($"/api/attempts/{id}/retry", null);
        Assert.Equal(HttpStatusCode.Created, retry.StatusCode);
        var retryId = (await retry.Content.ReadFromJsonAsync<JsonDocument>())!.RootElement.GetProperty("id").GetGuid();
        Assert.NotEqual(id, retryId);
        var retryResult = await client.GetAsync($"/api/attempts/{retryId}/result");
        Assert.Equal(HttpStatusCode.UnprocessableEntity, retryResult.StatusCode);
    }

    [Fact]
    public async Task Openapi_is_available_and_does_not_expose_persistence_entities()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var response = await client.GetAsync("/openapi/v1.json");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var json = await response.Content.ReadAsStringAsync();
        using var document = JsonDocument.Parse(json);
        Assert.True(document.RootElement.GetProperty("paths").TryGetProperty("/api/attempts/{id}", out _));
        Assert.DoesNotContain("AttemptEntity", json, StringComparison.Ordinal);
        Assert.DoesNotContain("ScenarioPrivate", json, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Scenario_list_returns_only_public_list_fields()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        using var json = JsonDocument.Parse(await client.GetStringAsync("/api/scenarios"));
        var item = json.RootElement.EnumerateArray().Single(x => x.GetProperty("id").GetString() == "linux-systemd-203-001");
        Assert.Equal(new[] { "category", "difficulty", "id", "summary", "title" }, item.EnumerateObject().Select(x => x.Name).Order());
        Assert.Equal("linux-systemd-203-001", item.GetProperty("id").GetString());
        Assert.Equal("beginner", item.GetProperty("difficulty").GetString());
    }

    [Fact]
    public async Task Scenario_detail_returns_only_public_fields()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        using var json = JsonDocument.Parse(await client.GetStringAsync("/api/scenarios/linux-systemd-203-001"));
        var item = json.RootElement;
        Assert.Equal(new[] { "category", "difficulty", "id", "summary", "title" }, item.EnumerateObject().Select(x => x.Name).Order());
        foreach (var forbidden in new[] { "symptoms", "actions", "evidence", "diagnoses", "remediations", "verifications", "correctAnswer", "correctDiagnosisId", "requiredActions", "scoring", "weight", "modelPath", "scoreJson", "resultAttemptJson" }) Assert.False(item.TryGetProperty(forbidden, out _));
        var deserialized = JsonSerializer.Deserialize<ScenarioListItem>(item.GetRawText(), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(deserialized); Assert.Equal("linux-systemd-203-001", deserialized.Id); Assert.Equal("beginner", deserialized.Difficulty);
    }

    [Fact]
    public void Scenario_list_json_deserializes_with_client_options()
    {
        const string json = "[{\"id\":\"linux-systemd-203-001\",\"title\":\"デプロイ後にアプリケーションサービスが起動しない\",\"category\":\"lpic1\",\"difficulty\":\"beginner\",\"summary\":\"systemd 203/EXEC の障害を調査します。\"}]";
        var scenarios = JsonSerializer.Deserialize<List<ScenarioListItem>>(json, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        var item = Assert.Single(scenarios!);
        Assert.Equal("linux-systemd-203-001", item.Id);
        Assert.Equal("beginner", item.Difficulty);
        Assert.Equal("lpic1", item.Category);
        Assert.Equal("デプロイ後にアプリケーションサービスが起動しない", item.Title);
        Assert.Equal("systemd 203/EXEC の障害を調査します。", item.Summary);
    }

    [Fact]
    public async Task Start_attempt_response_deserializes_with_client_options()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var response = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var json = await response.Content.ReadAsStringAsync();
        var attempt = JsonSerializer.Deserialize<PublicAttemptView>(json, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(attempt); Assert.NotEqual(Guid.Empty, attempt.Id); Assert.Equal(0, attempt.StateVersion); Assert.Equal(0, attempt.Status);
    }

    [Fact]
    public async Task Attempt_view_json_deserializes_with_client_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var started = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        using var startedJson = JsonDocument.Parse(await started.Content.ReadAsStringAsync()); var id = startedJson.RootElement.GetProperty("id").GetGuid();
        var json = await client.GetStringAsync($"/api/attempts/{id}");
        var attempt = JsonSerializer.Deserialize<PublicAttemptView>(json, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(attempt); Assert.Equal(id, attempt.Id); Assert.Equal(0, attempt.StateVersion);
    }

    [Fact]
    public async Task Attempt_view_exposes_public_context_and_current_actions_only()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        using var json = JsonDocument.Parse(await (await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null)).Content.ReadAsStringAsync());
        var root = json.RootElement;
        Assert.True(root.TryGetProperty("scenarioTitle", out _)); Assert.True(root.TryGetProperty("symptoms", out _)); Assert.True(root.TryGetProperty("availableActions", out var actions));
        Assert.True(actions.GetArrayLength() > 0);
        Assert.Equal(new[] { "executed", "executionExample", "id", "label", "representativeCommand" }, actions[0].EnumerateObject().Select(x => x.Name).Order());
        Assert.True(actions[0].GetProperty("executionExample").GetProperty("text").GetString() is { Length: > 0 });
        Assert.False(actions[0].GetProperty("executed").GetBoolean());
        foreach (var forbidden in new[] { "private", "scoreJson", "resultAttemptJson", "correctAnswer", "scoring", "weights", "requiredActions", "requiredEvidenceIds", "investigationGroup", "core", "supplemental" }) Assert.False(root.TryGetProperty(forbidden, out _));
        Assert.Empty(root.GetProperty("revealedEvidence").EnumerateArray());
    }

    [Fact]
    public async Task Beginner_evidence_and_command_capability_are_public_without_private_scoring_data()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var dnsStarted = await client.PostAsync("/api/scenarios/linux-dns-resolution-001/attempts", null);
        var dns = await dnsStarted.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(dns); Assert.False(dns!.SupportsCommandInput);

        var action = await client.PostAsJsonAsync($"/api/attempts/{dns.Id}/actions", new ActionRequest("interface-status", null, dns.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, action.StatusCode);
        using var evidenceJson = JsonDocument.Parse(await action.Content.ReadAsStringAsync());
        var evidence = evidenceJson.RootElement.GetProperty("revealedEvidence")[0];
        Assert.True(evidence.TryGetProperty("method", out _));
        Assert.True(evidence.TryGetProperty("sampleOutput", out _));
        Assert.True(evidence.TryGetProperty("interpretation", out _));
        foreach (var forbidden in new[] { "correctDiagnosisId", "correctRemediationId", "requiredVerificationIds", "requiredEvidenceIds", "private" }) Assert.False(evidenceJson.RootElement.TryGetProperty(forbidden, out _));

        var inodeStarted = await client.PostAsync("/api/scenarios/linux-inode-exhaustion-001/attempts", null);
        var inode = await inodeStarted.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(inode); Assert.False(inode!.SupportsCommandInput);
    }

    [Fact]
    public async Task Dns_investigation_readiness_keeps_actions_available_until_explicit_diagnosis_start()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var started = await client.PostAsync("/api/scenarios/linux-dns-resolution-001/attempts", null);
        var attempt = await started.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);

        foreach (var actionId in new[] { "default-route", "ip-connectivity", "hostname-resolution", "dns-server-query" })
        {
            var response = await client.PostAsJsonAsync($"/api/attempts/{attempt!.Id}/actions", new ActionRequest(actionId, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            attempt = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
            Assert.NotNull(attempt);
        }

        Assert.Equal(1, attempt!.Phase);
        Assert.True(attempt.CanAdvanceToDiagnosis);
        Assert.NotEmpty(attempt.AvailableActions);
        Assert.Empty(attempt.AvailableDiagnoses);
        Assert.Empty(attempt.AvailableRemediations);
        Assert.Empty(attempt.AvailableVerifications);
        var advance = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(attempt.StateVersion, Guid.NewGuid().ToString("N")));
        attempt = await advance.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.Equal(HttpStatusCode.OK, advance.StatusCode);
        Assert.NotNull(attempt); Assert.Equal(2, attempt!.Phase); Assert.NotEmpty(attempt.AvailableDiagnoses);
        var responseJson = await client.GetStringAsync($"/api/attempts/{attempt.Id}");
        foreach (var forbidden in new[] { "correctDiagnosisId", "requiredVerificationIds", "diagnosisHints", "private" }) Assert.DoesNotContain(forbidden, responseJson, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Dns_correct_answers_return_only_the_selected_success_feedback()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var started = await client.PostAsync("/api/scenarios/linux-dns-resolution-001/attempts", null);
        var attempt = await started.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);
        foreach (var actionId in new[] { "default-route", "ip-connectivity", "hostname-resolution", "dns-server-query" })
        {
            var action = await client.PostAsJsonAsync($"/api/attempts/{attempt!.Id}/actions", new ActionRequest(actionId, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
            attempt = await action.Content.ReadFromJsonAsync<PublicAttemptView>();
            Assert.NotNull(attempt);
        }

        var advance = await client.PostAsJsonAsync($"/api/attempts/{attempt!.Id}/advance-to-diagnosis", new AdvanceToDiagnosisRequest(attempt.StateVersion, Guid.NewGuid().ToString("N")));
        attempt = await advance.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.Equal(HttpStatusCode.OK, advance.StatusCode);
        Assert.NotNull(attempt);

        var diagnosis = await client.PostAsJsonAsync($"/api/attempts/{attempt!.Id}/diagnosis", new SubmitDiagnosisRequest("resolver-configuration", attempt.StateVersion, Guid.NewGuid().ToString("N")));
        var diagnosedResult = await ReadAnswerResultAsync(diagnosis);
        var diagnosed = diagnosedResult.Attempt;
        Assert.Equal(HttpStatusCode.OK, diagnosis.StatusCode);
        Assert.Equal("Correct", diagnosedResult.Outcome); Assert.Equal("resolver-configuration", diagnosedResult.SelectedOptionId); Assert.Contains("Correct", diagnosedResult.Feedback);

        var remediation = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/remediation", new SubmitRemediationRequest("set-correct-resolver", diagnosed.StateVersion, Guid.NewGuid().ToString("N")));
        var remediatedResult = await ReadAnswerResultAsync(remediation);
        var remediated = remediatedResult.Attempt;
        Assert.Equal(HttpStatusCode.OK, remediation.StatusCode);
        Assert.Equal("Correct", remediatedResult.Outcome); Assert.Equal("set-correct-resolver", remediatedResult.SelectedOptionId); Assert.Contains("Correct", remediatedResult.Feedback);

        Assert.Equal(1, remediated.VerificationSelectionMode);
        var verification = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/verification", new SubmitVerificationRequest([remediated.AvailableVerifications[0].Id], remediated.StateVersion, Guid.NewGuid().ToString("N")));
        var verifiedResult = await ReadAnswerResultAsync(verification);
        var verified = verifiedResult.Attempt;
        Assert.Equal(HttpStatusCode.OK, verification.StatusCode);
        Assert.Equal("Correct", verifiedResult.Outcome); Assert.Equal(remediated.AvailableVerifications[0].Id, verifiedResult.SelectedOptionId); Assert.Contains("Correct", verifiedResult.Feedback);

        var restoredJson = await client.GetStringAsync($"/api/attempts/{attempt.Id}");
        foreach (var privateName in new[] { "diagnosisSuccessFeedback", "remediationSuccessFeedback", "verificationSuccessFeedback", "resolver-configuration", "set-correct-resolver", "complete-verification-plan" }) Assert.DoesNotContain(privateName, restoredJson, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Attempt_view_exposes_diagnosis_options_only_during_diagnose()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var started = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var initial = await started.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(initial); Assert.Empty(initial.AvailableDiagnoses);

        var investigate = await client.PostAsJsonAsync($"/api/attempts/{initial.Id}/actions", new ActionRequest("app-status", null, initial.StateVersion, Guid.NewGuid().ToString("N")));
        var investigating = await investigate.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(investigating); Assert.Empty(investigating.AvailableDiagnoses);

        var id = await PrepareDiagnoseAttempt(client);
        using var diagnoseJson = JsonDocument.Parse(await client.GetStringAsync($"/api/attempts/{id}"));
        var diagnoses = diagnoseJson.RootElement.GetProperty("availableDiagnoses");
        Assert.NotEmpty(diagnoses.EnumerateArray());
        foreach (var diagnosis in diagnoses.EnumerateArray())
        {
            Assert.Equal(new[] { "id", "label" }, diagnosis.EnumerateObject().Select(x => x.Name).Order());
            foreach (var forbidden in new[] { "correct", "isCorrect", "correctAnswer", "correctDiagnosisId", "score", "scoring", "weight", "scoreJson", "resultAttemptJson", "requiredActions", "modelPath" }) Assert.False(diagnosis.TryGetProperty(forbidden, out _));
        }

        var before = JsonSerializer.Deserialize<PublicAttemptView>(diagnoseJson.RootElement.GetRawText(), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(before!.AvailableDiagnoses[0].Id, before.StateVersion, Guid.NewGuid().ToString("N")));
        var remediatingResult = await ReadAnswerResultAsync(response);
        var remediating = remediatingResult.Attempt;
        Assert.Equal("Correct", remediatingResult.Outcome); Assert.Equal(3, remediating.Phase); Assert.Empty(remediating.AvailableDiagnoses);
    }

    [Fact]
    public async Task Diagnosis_response_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareDiagnoseAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before);
        var diagnosis = Assert.IsType<PublicDiagnosisView>(Assert.Single(before!.AvailableDiagnoses.Take(1)));
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(diagnosis.Id, before.StateVersion, Guid.NewGuid().ToString("N")));
        var result = await ReadAnswerResultAsync(response);
        Assert.Equal("Correct", result.Outcome); Assert.Equal(diagnosis.Id, result.SelectedOptionId); Assert.NotNull(result.Attempt); Assert.Equal(3, result.Attempt.Phase); Assert.Equal(before.StateVersion + 1, result.Attempt.StateVersion); Assert.False(string.IsNullOrWhiteSpace(result.Feedback));
    }

    [Fact]
    public async Task Registered_diagnosis_option_advances_to_remediation()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareDiagnoseAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before); var diagnosis = Assert.IsType<PublicDiagnosisView>(Assert.Single(before.AvailableDiagnoses.Take(1)));

        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(diagnosis.Id, before.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync(); using var json = JsonDocument.Parse(body);
        var result = JsonSerializer.Deserialize<PublicAnswerResult>(body, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(result); Assert.Equal("Correct", result.Outcome); Assert.Equal(diagnosis.Id, result.SelectedOptionId);
        var after = result.Attempt;
        Assert.Equal(3, after.Phase); Assert.Equal(before.StateVersion + 1, after.StateVersion); Assert.Empty(after.AvailableDiagnoses); Assert.NotEmpty(after.AvailableRemediations);
        Assert.Equal("application/json", response.Content.Headers.ContentType!.MediaType);
        foreach (var forbidden in new[] { "correctDiagnosisId", "requiredEvidenceIds", "diagnosisHints", "private", "successFeedback" }) Assert.False(json.RootElement.TryGetProperty(forbidden, out _));
    }

    [Fact]
    public async Task Attempt_view_exposes_remediation_options_only_during_remediate()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareDiagnoseAttempt(client);
        var diagnosing = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(diagnosing); Assert.Empty(diagnosing.AvailableRemediations);

        var diagnosis = diagnosing.AvailableDiagnoses[0];
        var diagnosisResponse = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(diagnosis.Id, diagnosing.StateVersion, Guid.NewGuid().ToString("N")));
        var remediatingResult = await ReadAnswerResultAsync(diagnosisResponse);
        var remediating = remediatingResult.Attempt;
        Assert.Equal("Correct", remediatingResult.Outcome); Assert.NotEmpty(remediating.AvailableRemediations);

        using var json = JsonDocument.Parse(JsonSerializer.Serialize(remediating, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        var remediations = json.RootElement.GetProperty("availableRemediations");
        foreach (var remediation in remediations.EnumerateArray())
        {
            Assert.Equal(new[] { "id", "label" }, remediation.EnumerateObject().Select(x => x.Name).Order());
            foreach (var forbidden in new[] { "correct", "isCorrect", "isDangerous", "dangerous", "risk", "severity", "score", "scoring", "weight", "penalty", "correctRemediationId", "requiredActions", "modelPath" }) Assert.False(remediation.TryGetProperty(forbidden, out _));
        }

        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(remediating.AvailableRemediations[0].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
        var verifyingResult = await ReadAnswerResultAsync(response);
        var verifying = verifyingResult.Attempt;
        Assert.Equal("Correct", verifyingResult.Outcome); Assert.Equal(4, verifying.Phase); Assert.Empty(verifying.AvailableRemediations);
    }

    [Fact]
    public async Task Remediation_response_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareRemediateAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before);
        var remediation = Assert.IsType<PublicRemediationView>(Assert.Single(before!.AvailableRemediations.Take(1)));
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(remediation.Id, before.StateVersion, Guid.NewGuid().ToString("N")));
        var result = await ReadAnswerResultAsync(response);
        Assert.Equal("Correct", result.Outcome); Assert.Equal(remediation.Id, result.SelectedOptionId); Assert.Equal(4, result.Attempt.Phase); Assert.Equal(before.StateVersion + 1, result.Attempt.StateVersion); Assert.False(string.IsNullOrWhiteSpace(result.Feedback));
    }

    [Fact]
    public async Task Registered_remediation_option_advances_to_verification()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareRemediateAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before); var remediation = before.AvailableRemediations[0];

        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(remediation.Id, before.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync(); using var json = JsonDocument.Parse(body);
        var result = JsonSerializer.Deserialize<PublicAnswerResult>(body, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(result); Assert.Equal("Correct", result.Outcome); Assert.Equal(remediation.Id, result.SelectedOptionId);
        var after = result.Attempt;
        Assert.Equal(4, after.Phase); Assert.Equal(before.StateVersion + 1, after.StateVersion); Assert.Empty(after.AvailableRemediations); Assert.NotEmpty(after.AvailableVerifications);
        Assert.Equal("application/json", response.Content.Headers.ContentType!.MediaType);
        foreach (var forbidden in new[] { "correctRemediationId", "requiredEvidenceIds", "remediationHints", "private", "successFeedback" }) Assert.False(json.RootElement.TryGetProperty(forbidden, out _));
    }

    [Fact]
    public async Task Unknown_remediation_option_returns_422_and_leaves_attempt_unchanged()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareRemediateAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        await using var beforeDb = fixture.CreateContext(); var persistedBefore = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var eventCount = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == id);
        var key = Guid.NewGuid().ToString("N"); var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest("unknown-option", before!.StateVersion, key));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        foreach (var forbidden in new[] { "correct", "isCorrect", "isDangerous", "dangerous", "risk", "score", "weight", "correctRemediationId" }) Assert.DoesNotContain(forbidden, body, StringComparison.OrdinalIgnoreCase);
        await using var afterDb = fixture.CreateContext(); var persistedAfter = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        Assert.Equal(persistedBefore.StateJson, persistedAfter.StateJson); Assert.Equal(persistedBefore.StateVersion, persistedAfter.StateVersion); Assert.Equal(persistedBefore.Status, persistedAfter.Status); Assert.Equal(eventCount, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id)); Assert.DoesNotContain(await afterDb.AttemptEvents.Where(x => x.AttemptId == id).ToListAsync(), x => x.IdempotencyKey == key);
    }

    [Fact]
    public void Remediation_request_contract_preserves_existing_json_shape()
    {
        var request = new SubmitRemediationRequest("option", 9, "key");
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(request, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        Assert.Equal(new[] { "idempotencyKey", "optionId", "stateVersion" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
        foreach (var forbidden in new[] { "scenarioId", "phase", "isCorrect", "isDangerous", "score", "weight", "risk", "correctRemediationId" }) Assert.False(json.RootElement.TryGetProperty(forbidden, out _));
    }

    [Fact]
    public async Task Attempt_view_exposes_verification_options_only_during_verify()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareRemediateAttempt(client); var remediating = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.Empty(remediating!.AvailableVerifications);
        var transition = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(remediating.AvailableRemediations[0].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
        var verifyingResult = await ReadAnswerResultAsync(transition); var verifying = verifyingResult.Attempt; Assert.Equal("Correct", verifyingResult.Outcome); Assert.NotEmpty(verifying.AvailableVerifications);
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(verifying, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        foreach (var item in json.RootElement.GetProperty("availableVerifications").EnumerateArray()) { Assert.Equal(new[] { "id", "label" }, item.EnumerateObject().Select(x => x.Name).Order()); foreach (var forbidden in new[] { "correct", "success", "expectedResult", "actualResult", "score", "weight", "explanation" }) Assert.False(item.TryGetProperty(forbidden, out _)); }
        var completed = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest([verifying.AvailableVerifications[0].Id], verifying.StateVersion, Guid.NewGuid().ToString("N")));
        var reviewedResult = await ReadAnswerResultAsync(completed); var reviewed = reviewedResult.Attempt; Assert.Equal("Correct", reviewedResult.Outcome); Assert.Equal(5, reviewed.Phase); Assert.Empty(reviewed.AvailableVerifications);
    }

    [Fact]
    public async Task Verification_response_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before); Assert.Equal(4, before!.Phase); var verification = before.AvailableVerifications[0];
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest([verification.Id], before.StateVersion, Guid.NewGuid().ToString("N")));
        var result = await ReadAnswerResultAsync(response);
        Assert.Equal("Correct", result.Outcome); Assert.Equal(verification.Id, result.SelectedOptionId); Assert.Equal(5, result.Attempt.Phase); Assert.Equal(before.StateVersion + 1, result.Attempt.StateVersion); Assert.False(string.IsNullOrWhiteSpace(result.Feedback));
    }

    [Fact]
    public async Task Registered_verification_option_advances_to_review()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}"); var choice = before!.AvailableVerifications[0];
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest([choice.Id], before.StateVersion, Guid.NewGuid().ToString("N"))); var result = await ReadAnswerResultAsync(response);
        Assert.Equal("Correct", result.Outcome); Assert.Equal(choice.Id, result.SelectedOptionId); Assert.Equal(5, result.Attempt.Phase); Assert.Equal(before.StateVersion + 1, result.Attempt.StateVersion); Assert.Empty(result.Attempt.AvailableVerifications);
        Assert.Equal(1, result.Attempt.Status); Assert.False(string.IsNullOrWhiteSpace(result.Feedback)); var review = await client.GetAsync($"/api/attempts/{id}/review"); Assert.Equal(HttpStatusCode.OK, review.StatusCode);
    }

    [Fact]
    public async Task Unknown_verification_option_returns_422_and_leaves_attempt_unchanged()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        await using var db = fixture.CreateContext(); var stored = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var count = await db.AttemptEvents.CountAsync(x => x.AttemptId == id); var key = Guid.NewGuid().ToString("N");
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(["unknown-option"], before!.StateVersion, key)); Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); Assert.Equal(stored.StateJson, after.StateJson); Assert.Equal(stored.StateVersion, after.StateVersion); Assert.Equal(stored.Status, after.Status); Assert.Equal(stored.ScoreJson, after.ScoreJson); Assert.Equal(count, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id)); Assert.DoesNotContain(await afterDb.AttemptEvents.Where(x => x.AttemptId == id).ToListAsync(), x => x.IdempotencyKey == key);
    }

    [Fact]
    public void Verification_request_contract_preserves_existing_json_shape()
    {
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(new SubmitVerificationRequest(["option"], 9, "key"), new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        Assert.Equal(new[] { "idempotencyKey", "optionIds", "stateVersion" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
    }

    [Fact]
    public async Task Attempt_result_returns_only_public_result_fields()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        var response = await client.GetAsync($"/api/attempts/{id}/result"); Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(new[] { "attemptId", "scenarioId", "scenarioTitle", "score", "status" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
        Assert.Equal(new[] { "diagnosis", "investigation", "remediation", "safety", "total", "verification" }, json.RootElement.GetProperty("score").EnumerateObject().Select(x => x.Name).Order());
        AssertNoPrivateProperty(json.RootElement);
    }

    [Fact]
    public async Task Attempt_result_is_not_available_before_completion()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client);
        await using var beforeDb = fixture.CreateContext(); var before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var count = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == id);
        var response = await client.GetAsync($"/api/attempts/{id}/result"); Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); Assert.Equal(before.StateVersion, after.StateVersion); Assert.Equal(before.Status, after.Status); Assert.Equal(before.CurrentPhase, after.CurrentPhase); Assert.Equal(count, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id));
    }

    [Fact]
    public async Task Attempt_result_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        var result = JsonSerializer.Deserialize<PublicAttemptResult>(await client.GetStringAsync($"/api/attempts/{id}/result"), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(result); Assert.Equal(id, result.AttemptId); Assert.False(string.IsNullOrWhiteSpace(result.ScenarioId)); Assert.False(string.IsNullOrWhiteSpace(result.ScenarioTitle)); Assert.Equal(1, result.Status); Assert.True(result.Score.Total >= 0);
    }

    [Fact]
    public async Task Completed_attempt_result_matches_persisted_scoring_outcome()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        await using var beforeDb = fixture.CreateContext(); var attempt = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var count = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == id); var version = attempt.StateVersion;
        var score = JsonSerializer.Deserialize<ScoreBreakdown>(attempt.ScoreJson!, new JsonSerializerOptions(JsonSerializerDefaults.Web)); var result = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{id}/result"); Assert.NotNull(result); Assert.NotNull(score); Assert.Equal(score.Total, result.Score.Total); Assert.Equal(score.Diagnosis, result.Score.Diagnosis); Assert.Equal(score.Remediation, result.Score.Remediation); Assert.Equal(score.Verification, result.Score.Verification);
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); Assert.Equal(version, after.StateVersion); Assert.Equal(count, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id));
    }

    [Fact]
    public async Task Starting_same_scenario_after_completion_creates_fresh_attempt()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var completedId = await PrepareCompletedAttempt(client);
        var result = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{completedId}/result");
        Assert.NotNull(result);

        var response = await client.PostAsync($"/api/scenarios/{Uri.EscapeDataString(result.ScenarioId)}/attempts", null);
        var fresh = await response.Content.ReadFromJsonAsync<PublicAttemptView>();

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        Assert.NotNull(fresh);
        Assert.NotEqual(completedId, fresh.Id);
        Assert.Equal(result.ScenarioId, fresh.ScenarioId);
        Assert.Equal(0, fresh.Phase);
        Assert.Equal(0, fresh.Status);
        Assert.Equal(0, fresh.StateVersion);
        Assert.Empty(fresh.RevealedEvidence);
        Assert.Empty(fresh.ExecutedActions);
        await using var db = fixture.CreateContext();
        var stored = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == fresh.Id);
        Assert.Null(stored.ScoreJson);
        Assert.Null(stored.CompletedAt);
        Assert.Empty(await db.AttemptEvents.Where(x => x.AttemptId == fresh.Id).ToListAsync());
    }

    [Fact]
    public async Task Starting_retry_does_not_modify_completed_attempt()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var completedId = await PrepareCompletedAttempt(client);
        var result = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{completedId}/result");
        Assert.NotNull(result);
        AttemptEntity before;
        List<AttemptEventEntity> beforeEvents;
        await using (var beforeDb = fixture.CreateContext())
        {
            before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == completedId);
            beforeEvents = await beforeDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == completedId).OrderBy(x => x.Sequence).ToListAsync();
        }

        var response = await client.PostAsync($"/api/scenarios/{Uri.EscapeDataString(result.ScenarioId)}/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);

        await using var afterDb = fixture.CreateContext();
        var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == completedId);
        var afterEvents = await afterDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == completedId).OrderBy(x => x.Sequence).ToListAsync();
        Assert.Equal(before.CurrentPhase, after.CurrentPhase);
        Assert.Equal(before.Status, after.Status);
        Assert.Equal(before.StateVersion, after.StateVersion);
        Assert.Equal(before.ScoreJson, after.ScoreJson);
        Assert.Equal(beforeEvents.Count, afterEvents.Count);
        Assert.Equal(beforeEvents.Select(x => x.ResultAttemptJson), afterEvents.Select(x => x.ResultAttemptJson));
    }

    [Fact]
    public async Task Completed_attempt_review_returns_public_labels_and_correctness()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareCompletedAttempt(client);
        var response = await client.GetAsync($"/api/attempts/{id}/review");
        var review = await response.Content.ReadFromJsonAsync<PublicAttemptReview>();
        await using var db = fixture.CreateContext(); var persisted = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var score = JsonSerializer.Deserialize<ScoreBreakdown>(persisted.ScoreJson!, new JsonSerializerOptions(JsonSerializerDefaults.Web));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.NotNull(review); Assert.NotNull(score);
        Assert.All(new[] { review.Diagnosis, review.Remediation }, entry => { Assert.NotNull(entry); Assert.False(string.IsNullOrWhiteSpace(entry.SelectedLabel)); Assert.False(string.IsNullOrWhiteSpace(entry.ExpectedLabel)); });
        Assert.NotNull(review.Verification); Assert.NotEmpty(review.Verification.SelectedLabels); Assert.NotEmpty(review.Verification.ExpectedLabels);
        Assert.Equal(score.Diagnosis > 0, review.Diagnosis!.IsCorrect);
        Assert.Equal(score.Remediation > 0, review.Remediation!.IsCorrect);
        Assert.Equal(score.Verification > 0, review.Verification!.IsCorrect);
        Assert.Equal(score.Diagnosis, review.Diagnosis.EarnedScore);
        Assert.Equal(score.Remediation, review.Remediation.EarnedScore);
        Assert.Equal(score.Verification, review.Verification.EarnedScore);
    }

    [Fact]
    public async Task Attempt_review_returns_only_public_review_fields()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        var response = await client.GetAsync($"/api/attempts/{id}/review");
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(new[] { "attemptId", "diagnosis", "remediation", "scenarioId", "scenarioTitle", "verification" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
        AssertPublicReviewEntry(json.RootElement.GetProperty("diagnosis"));
        AssertPublicReviewEntry(json.RootElement.GetProperty("remediation"));
        AssertPublicVerificationReviewEntry(json.RootElement.GetProperty("verification"));
    }

    [Fact]
    public async Task Attempt_review_is_not_available_before_completion()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var started = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null); var attempt = await started.Content.ReadFromJsonAsync<PublicAttemptView>(); Assert.NotNull(attempt);
        await using var beforeDb = fixture.CreateContext(); var before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == attempt.Id); var count = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == attempt.Id);
        var response = await client.GetAsync($"/api/attempts/{attempt.Id}/review");
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == attempt.Id);

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        Assert.Equal(before.StateVersion, after.StateVersion); Assert.Equal(before.CurrentPhase, after.CurrentPhase); Assert.Equal(before.Status, after.Status); Assert.Equal(count, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == attempt.Id));
    }

    [Fact]
    public async Task Attempt_review_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        var review = JsonSerializer.Deserialize<PublicAttemptReview>(await client.GetStringAsync($"/api/attempts/{id}/review"), new JsonSerializerOptions(JsonSerializerDefaults.Web));

        Assert.NotNull(review); Assert.Equal(id, review.AttemptId); Assert.False(string.IsNullOrWhiteSpace(review.ScenarioId)); Assert.False(string.IsNullOrWhiteSpace(review.ScenarioTitle));
        Assert.All(new[] { review.Diagnosis, review.Remediation }, entry => { Assert.NotNull(entry); Assert.False(string.IsNullOrWhiteSpace(entry.SelectedLabel)); Assert.NotNull(entry.EarnedScore); });
        Assert.NotNull(review.Verification); Assert.NotEmpty(review.Verification.SelectedLabels); Assert.NotNull(review.Verification.EarnedScore);
    }

    [Fact]
    public async Task Attempt_review_get_is_read_only()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareCompletedAttempt(client);
        AttemptEntity before; List<AttemptEventEntity> beforeEvents;
        await using (var beforeDb = fixture.CreateContext()) { before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); beforeEvents = await beforeDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == id).OrderBy(x => x.Sequence).ToListAsync(); }
        var response = await client.GetAsync($"/api/attempts/{id}/review");
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var afterEvents = await afterDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == id).OrderBy(x => x.Sequence).ToListAsync();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(before.CurrentPhase, after.CurrentPhase); Assert.Equal(before.Status, after.Status); Assert.Equal(before.StateVersion, after.StateVersion); Assert.Equal(before.ScoreJson, after.ScoreJson); Assert.Equal(beforeEvents.Select(x => x.ResultAttemptJson), afterEvents.Select(x => x.ResultAttemptJson));
    }

    private static void AssertNoPrivateProperty(JsonElement element)
    {
        var forbidden = new[] { "scoreJson", "resultAttemptJson", "correctDiagnosisId", "correctRemediationId", "correctVerificationId", "requiredActions", "scoring", "modelPath", "idempotencyKey", "stateJson", "eventJson", "explanation", "privateScenario" };
        if (element.ValueKind == JsonValueKind.Object) foreach (var property in element.EnumerateObject()) { Assert.DoesNotContain(property.Name, forbidden, StringComparer.OrdinalIgnoreCase); AssertNoPrivateProperty(property.Value); }
        else if (element.ValueKind == JsonValueKind.Array) foreach (var item in element.EnumerateArray()) AssertNoPrivateProperty(item);
    }

    private static void AssertPublicReviewEntry(JsonElement entry)
    {
        Assert.Equal(new[] { "earnedScore", "expectedLabel", "explanation", "incorrectCount", "isCorrect", "maxScore", "selectedLabel" }, entry.EnumerateObject().Select(x => x.Name).Order());
        foreach (var property in entry.EnumerateObject())
        {
            Assert.True(property.Value.ValueKind is JsonValueKind.String or JsonValueKind.Number or JsonValueKind.True or JsonValueKind.False or JsonValueKind.Null);
        }
    }

    private static void AssertPublicVerificationReviewEntry(JsonElement entry)
    {
        Assert.Equal(new[] { "earnedScore", "expectedLabels", "explanation", "incorrectCount", "isCorrect", "maxScore", "selectedLabels" }, entry.EnumerateObject().Select(x => x.Name).Order());
        Assert.All(entry.GetProperty("selectedLabels").EnumerateArray(), value => Assert.Equal(JsonValueKind.String, value.ValueKind));
        Assert.All(entry.GetProperty("expectedLabels").EnumerateArray(), value => Assert.Equal(JsonValueKind.String, value.ValueKind));
    }

    [Fact]
    public async Task Action_request_contract_preserves_existing_json_shape()
    {
        var request = new ActionRequest("app-status", null, 0, "request-key");
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(request, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        Assert.Equal(new[] { "actionId", "idempotencyKey", "input", "stateVersion" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        using var start = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        using var started = JsonDocument.Parse(await start.Content.ReadAsStringAsync()); var id = started.RootElement.GetProperty("id").GetGuid();
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task Unknown_diagnosis_option_returns_422_and_leaves_attempt_unchanged()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareDiagnoseAttempt(client);
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest("unknown-option", before!.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        var after = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.Equal(before.StateVersion, after!.StateVersion); Assert.Equal(before.Phase, after.Phase); Assert.DoesNotContain("unknown-option", after.ExecutedActions);
    }

    [Fact]
    public void Diagnosis_request_contract_preserves_existing_json_shape()
    {
        var request = new SubmitDiagnosisRequest("option", 9, "key");
        using var json = JsonDocument.Parse(JsonSerializer.Serialize(request, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        Assert.Equal(new[] { "idempotencyKey", "optionId", "stateVersion" }, json.RootElement.EnumerateObject().Select(x => x.Name).Order());
        foreach (var forbidden in new[] { "scenarioId", "phase", "score", "correct", "isCorrect", "correctDiagnosisId" }) Assert.False(json.RootElement.TryGetProperty(forbidden, out _));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public async Task Empty_or_whitespace_diagnosis_option_returns_422_without_mutation(string optionId)
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        var id = await PrepareDiagnoseAttempt(client);
        await using var beforeDb = fixture.CreateContext(); var before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); var eventCount = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == id);
        var key = Guid.NewGuid().ToString("N"); var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(optionId, before.StateVersion, key));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        await using var afterDb = fixture.CreateContext(); var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        Assert.Equal(before.StateJson, after.StateJson); Assert.Equal(before.StateVersion, after.StateVersion); Assert.Equal(before.Status, after.Status); Assert.Equal(before.ScoreJson, after.ScoreJson); Assert.Equal(eventCount, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id)); Assert.DoesNotContain(await afterDb.AttemptEvents.Where(x => x.AttemptId == id).ToListAsync(), x => x.IdempotencyKey == key);
    }

    [Fact]
    public async Task Action_response_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        using var startedJson = JsonDocument.Parse(await (await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null)).Content.ReadAsStringAsync());
        var id = startedJson.RootElement.GetProperty("id").GetGuid();
        var request = new ActionRequest("app-status", null, 0, Guid.NewGuid().ToString("N"));
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", request, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        var attempt = JsonSerializer.Deserialize<PublicAttemptView>(await response.Content.ReadAsStringAsync(), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(attempt); Assert.Equal(id, attempt.Id); Assert.Equal(1, attempt.StateVersion); Assert.NotEmpty(attempt.Symptoms); Assert.NotEmpty(attempt.AvailableActions); Assert.Contains("app-status", attempt.ExecutedActions); Assert.NotEmpty(attempt.RevealedEvidence);
    }

    [Fact]
    public async Task Command_response_deserializes_with_client_json_options()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient();
        using var startedJson = JsonDocument.Parse(await (await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null)).Content.ReadAsStringAsync());
        var id = startedJson.RootElement.GetProperty("id").GetGuid();
        var request = new ActionRequest(null, "systemctl status app.service", 0, Guid.NewGuid().ToString("N"));
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/commands", request, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var attempt = JsonSerializer.Deserialize<PublicAttemptView>(await response.Content.ReadAsStringAsync(), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(attempt); Assert.Equal(id, attempt.Id); Assert.Equal("linux-systemd-203-001", attempt.ScenarioId); Assert.False(string.IsNullOrWhiteSpace(attempt.ScenarioTitle)); Assert.Equal(0, attempt.Status); Assert.Equal(1, attempt.Phase); Assert.Equal(1, attempt.StateVersion); Assert.NotEmpty(attempt.RevealedEvidence); Assert.NotEmpty(attempt.AvailableActions); Assert.Contains("app-status", attempt.ExecutedActions);
    }

    [Fact]
    public async Task Command_persists_resolved_action_id_in_attempt_event()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var start = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var id = (await start.Content.ReadFromJsonAsync<JsonDocument>())!.RootElement.GetProperty("id").GetGuid();
        var key = Guid.NewGuid().ToString("N");
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/commands", new { input = "  sudo   systemctl status app.service  ", stateVersion = 0, idempotencyKey = key });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        await using var db = fixture.CreateContext();
        var entry = await db.AttemptEvents.SingleAsync(x => x.AttemptId == id);
        Assert.Equal("app-status", entry.ActionOrAnswerId);
        Assert.Equal(key, entry.IdempotencyKey);
        Assert.Equal(entry.Sequence, entry.ResultStateVersion);
        Assert.DoesNotContain("sudo", entry.PayloadJson ?? string.Empty, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("correct", entry.PayloadJson ?? string.Empty, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Dangerous_wrong_remediation_is_rejected_without_persisting_a_mutation()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var start = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var id = (await start.Content.ReadFromJsonAsync<JsonDocument>())!.RootElement.GetProperty("id").GetGuid();
        var version = 0;
        foreach (var action in new[] { "app-status", "app-journal", "unit-cat", "ls-permissions" })
            version = await AssertMutation(client, id, "/actions", new { actionId = action, stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/advance-to-diagnosis", new { stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/diagnosis", new { optionId = "not-executable", stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        var before = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(before);
        await using var beforeDb = fixture.CreateContext();
        var eventCount = await beforeDb.AttemptEvents.CountAsync(x => x.AttemptId == id);
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new { optionId = "chmod-777", stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });

        var answer = await ReadAnswerResultAsync(response);
        Assert.Equal("Incorrect", answer.Outcome);
        Assert.Equal("chmod-777", answer.SelectedOptionId);
        Assert.False(string.IsNullOrWhiteSpace(answer.Feedback));
        Assert.Equal(before.StateVersion + 1, answer.Attempt.StateVersion);
        Assert.Equal(before.Phase, answer.Attempt.Phase);
        Assert.NotNull(answer.Attempt.IncorrectOptionIds);
        Assert.Contains("chmod-777", answer.Attempt.IncorrectOptionIds!);
        var after = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(after);
        Assert.Equal(before.Phase, after!.Phase);
        Assert.Equal(before.StateVersion + 1, after.StateVersion);
        await using var afterDb = fixture.CreateContext();
        Assert.Equal(eventCount + 1, await afterDb.AttemptEvents.CountAsync(x => x.AttemptId == id));
    }

    [Fact]
    public async Task Completed_attempt_rejection_leaves_persisted_state_unchanged()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var start = await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null);
        var id = (await start.Content.ReadFromJsonAsync<JsonDocument>())!.RootElement.GetProperty("id").GetGuid();
        var version = 0;
        foreach (var action in new[] { "app-status", "app-journal", "unit-cat", "ls-permissions" })
            version = await AssertMutation(client, id, "/actions", new { actionId = action, stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/advance-to-diagnosis", new { stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/diagnosis", new { optionId = "not-executable", stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/remediation", new { optionId = "chmod-and-restart", stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertVerificationMutation(client, id, version, Guid.NewGuid().ToString("N"));

        AttemptEntity before;
        List<AttemptEventEntity> beforeEvents;
        await using (var beforeDb = fixture.CreateContext())
        {
            before = await beforeDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
            beforeEvents = await beforeDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == id).OrderBy(x => x.Sequence).ToListAsync();
            Assert.Equal(AttemptStatus.Completed, before.Status);
            Assert.NotNull(before.ScoreJson);
            Assert.NotNull(before.CompletedAt);
        }
        var rejectedKey = Guid.NewGuid().ToString("N");
        var rejected = await client.PostAsJsonAsync($"/api/attempts/{id}/actions", new { actionId = "app-status", stateVersion = version, idempotencyKey = rejectedKey });
        Assert.Equal(HttpStatusCode.UnprocessableEntity, rejected.StatusCode);
        var error = await rejected.Content.ReadAsStringAsync();
        Assert.DoesNotContain("Npgsql", error, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("AttemptEntity", error, StringComparison.Ordinal);

        await using var afterDb = fixture.CreateContext();
        var after = await afterDb.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        var afterEvents = await afterDb.AttemptEvents.AsNoTracking().Where(x => x.AttemptId == id).OrderBy(x => x.Sequence).ToListAsync();
        Assert.Equal(before.StateJson, after.StateJson);
        Assert.Equal(before.ScoreJson, after.ScoreJson);
        Assert.Equal(before.StateVersion, after.StateVersion);
        Assert.Equal(before.CompletedAt, after.CompletedAt);
        Assert.Equal(before.Status, after.Status);
        Assert.Equal(beforeEvents.Select(x => (x.Id, x.Sequence, x.EventType, x.ActionOrAnswerId, x.PreviousStateVersion, x.ResultStateVersion, x.IdempotencyKey, x.ResultAttemptJson)), afterEvents.Select(x => (x.Id, x.Sequence, x.EventType, x.ActionOrAnswerId, x.PreviousStateVersion, x.ResultStateVersion, x.IdempotencyKey, x.ResultAttemptJson)));
        Assert.DoesNotContain(afterEvents, x => x.IdempotencyKey == rejectedKey);
        var result = await client.GetFromJsonAsync<JsonDocument>($"/api/attempts/{id}/result");
        using var score = JsonDocument.Parse(after.ScoreJson!);
        Assert.Equal(score.RootElement.GetProperty("total").GetInt32(), result!.RootElement.GetProperty("score").GetProperty("total").GetInt32());
    }

    private static async Task<PublicAnswerResult> ReadAnswerResultAsync(HttpResponseMessage response)
    {
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("application/json", response.Content.Headers.ContentType!.MediaType);
        var result = await response.Content.ReadFromJsonAsync<PublicAnswerResult>(new JsonSerializerOptions(JsonSerializerDefaults.Web));
        Assert.NotNull(result);
        Assert.NotNull(result!.Attempt);
        Assert.Contains(result.Outcome, new[] { "Correct", "Incorrect" });
        Assert.False(string.IsNullOrWhiteSpace(result.SelectedOptionId));
        return result;
    }

    private static async Task<int> AssertMutation(HttpClient client, Guid id, string route, object body)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}{route}", body);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = await response.Content.ReadFromJsonAsync<JsonDocument>();
        var state = json!.RootElement.TryGetProperty("attempt", out var attempt) ? attempt : json.RootElement;
        return state.GetProperty("stateVersion").GetInt32();
    }

    [Fact] public async Task Null_verification_option_list_returns_422_without_mutation() => await AssertInvalidVerificationAsync(null);
    [Fact] public async Task Empty_verification_option_list_returns_422_without_mutation() => await AssertInvalidVerificationAsync([]);
    [Fact] public async Task Duplicate_verification_ids_return_422_without_mutation()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); var view = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}"); Assert.NotNull(view);
        await AssertRejectedVerificationAsync(client, id, [view.AvailableVerifications[0].Id, view.AvailableVerifications[0].Id]);
    }
    [Fact] public async Task Single_verification_idempotency_replays_same_choice()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); var view = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}"); Assert.NotNull(view); var ids = new[] { view!.AvailableVerifications[0].Id }; var key = Guid.NewGuid().ToString("N");
        var first = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(ids, view.StateVersion, key)); var firstResult = await ReadAnswerResultAsync(first); var before = await SnapshotAttemptAsync(id); var replay = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(ids, view.StateVersion, key)); var replayResult = await ReadAnswerResultAsync(replay);
        Assert.Equal("Correct", firstResult.Outcome); Assert.Equal(firstResult.Outcome, replayResult.Outcome); Assert.Equal(firstResult.SelectedOptionId, replayResult.SelectedOptionId); Assert.Equal(firstResult.Feedback, replayResult.Feedback); Assert.Equal(firstResult.Attempt.StateVersion, replayResult.Attempt.StateVersion); Assert.Equal(firstResult.Attempt.Phase, replayResult.Attempt.Phase); await AssertAttemptUnchangedAsync(id, before, key, true);
    }
    [Fact] public async Task Verification_idempotency_rejects_different_set_with_same_key()
    {
        await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); var view = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}"); Assert.NotNull(view); Assert.True(view!.AvailableVerifications.Length >= 2); var ids = new[] { view.AvailableVerifications[0].Id }; var key = Guid.NewGuid().ToString("N");
        Assert.Equal(HttpStatusCode.OK, (await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(ids, view.StateVersion, key))).StatusCode); var before = await SnapshotAttemptAsync(id); var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest([view.AvailableVerifications[1].Id], view.StateVersion, key));
        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode); await AssertAttemptUnchangedAsync(id, before, key, true);
    }
    private async Task AssertInvalidVerificationAsync(IReadOnlyList<string>? ids) { await using var factory = new TestServerFactory(); using var client = factory.CreateClient(); var id = await PrepareVerifyAttempt(client); await AssertRejectedVerificationAsync(client, id, ids); }
    private async Task AssertRejectedVerificationAsync(HttpClient client, Guid id, IReadOnlyList<string>? ids) { var view = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}"); Assert.NotNull(view); var key = Guid.NewGuid().ToString("N"); var before = await SnapshotAttemptAsync(id); var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest(ids!, view.StateVersion, key)); Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode); await AssertAttemptUnchangedAsync(id, before, key, false); }
    private async Task<(AttemptEntity Attempt, int Events)> SnapshotAttemptAsync(Guid id) { await using var db = fixture.CreateContext(); return (await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id), await db.AttemptEvents.CountAsync(x => x.AttemptId == id)); }
    private async Task AssertAttemptUnchangedAsync(Guid id, (AttemptEntity Attempt, int Events) before, string key, bool keyExists) { await using var db = fixture.CreateContext(); var after = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id); Assert.Equal(before.Attempt.StateVersion, after.StateVersion); Assert.Equal(before.Attempt.ScoreJson, after.ScoreJson); Assert.Equal(before.Events, await db.AttemptEvents.CountAsync(x => x.AttemptId == id)); Assert.Equal(keyExists, await db.AttemptEvents.AnyAsync(x => x.AttemptId == id && x.IdempotencyKey == key)); }

    private static async Task<int> AssertVerificationMutation(HttpClient client, Guid id, int stateVersion, string idempotencyKey)
    {
        var attempt = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        Assert.NotNull(attempt);
        return await AssertMutation(client, id, "/verification", new { optionIds = new[] { attempt.AvailableVerifications[0].Id }, stateVersion, idempotencyKey });
    }

    private static async Task<Guid> PrepareDiagnoseAttempt(HttpClient client)
    {
        using var started = JsonDocument.Parse(await (await client.PostAsync("/api/scenarios/linux-systemd-203-001/attempts", null)).Content.ReadAsStringAsync()); var id = started.RootElement.GetProperty("id").GetGuid(); var version = 0;
        foreach (var action in new[] { "app-status", "app-journal", "unit-cat", "ls-permissions" }) version = await AssertMutation(client, id, "/actions", new { actionId = action, stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        version = await AssertMutation(client, id, "/advance-to-diagnosis", new { stateVersion = version, idempotencyKey = Guid.NewGuid().ToString("N") });
        return id;
    }

    private static async Task<Guid> PrepareRemediateAttempt(HttpClient client)
    {
        var id = await PrepareDiagnoseAttempt(client);
        var diagnosing = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        var diagnosis = diagnosing!.AvailableDiagnoses[0];
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/diagnosis", new SubmitDiagnosisRequest(diagnosis.Id, diagnosing.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return id;
    }

    private static async Task<Guid> PrepareVerifyAttempt(HttpClient client)
    {
        var id = await PrepareRemediateAttempt(client); var remediating = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/remediation", new SubmitRemediationRequest(remediating!.AvailableRemediations[0].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode); return id;
    }

    private static async Task<Guid> PrepareCompletedAttempt(HttpClient client)
    {
        var id = await PrepareVerifyAttempt(client); var verifying = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{id}");
        var response = await client.PostAsJsonAsync($"/api/attempts/{id}/verification", new SubmitVerificationRequest([verifying!.AvailableVerifications[0].Id], verifying.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode); return id;
    }
}

public sealed class TestServerFactory : WebApplicationFactory<Program>
{
    private readonly ILoggerProvider? loggerProvider;

    public TestServerFactory(ILoggerProvider? loggerProvider = null) => this.loggerProvider = loggerProvider;

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Development");
        builder.UseSetting("ConnectionStrings:DefaultConnection", TestDatabase.ConnectionString);
        builder.ConfigureLogging(logging =>
        {
            logging.ClearProviders();
            if (loggerProvider is not null) logging.AddProvider(loggerProvider);
        });
    }
}
