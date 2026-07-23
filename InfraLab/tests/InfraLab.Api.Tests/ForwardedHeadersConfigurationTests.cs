using System.Net;
using Microsoft.AspNetCore.HttpOverrides;

namespace InfraLab.Api.Tests;

public sealed class ForwardedHeadersConfigurationTests
{
    [Fact]
    public void Explicit_trusted_network_is_the_only_forwarded_header_trust_boundary()
    {
        var options = ForwardedHeadersConfiguration.Create("10.0.0.0/8");

        Assert.Equal(ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto, options.ForwardedHeaders);
        Assert.Equal(1, options.ForwardLimit);
        Assert.True(options.RequireHeaderSymmetry);
        Assert.Empty(options.KnownProxies);
        var network = Assert.Single(options.KnownIPNetworks);
        Assert.True(network.Contains(IPAddress.Parse("10.1.2.3")));
        Assert.False(network.Contains(IPAddress.Parse("192.0.2.10")));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("not-a-network")]
    public void Missing_or_invalid_configuration_refuses_to_create_an_unrestricted_fallback(string? configuredNetworks)
    {
        Assert.Throws<InvalidOperationException>(() => ForwardedHeadersConfiguration.Create(configuredNetworks));
    }
}
