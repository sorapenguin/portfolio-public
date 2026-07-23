using System.Net;

namespace InfraLab.Api.Tests;

public sealed class StaticHostingTests
{
    [Fact]
    public async Task Attempt_deep_link_returns_the_blazor_host_page()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        var response = await client.GetAsync($"/attempts/{Guid.NewGuid()}");
        var document = await response.Content.ReadAsStringAsync();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("text/html", response.Content.Headers.ContentType!.MediaType);
        Assert.Contains("id=\"app\"", document, StringComparison.Ordinal);
    }
}
