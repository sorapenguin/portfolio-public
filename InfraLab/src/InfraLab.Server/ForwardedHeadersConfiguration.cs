using System.Net;
using Microsoft.AspNetCore.HttpOverrides;

public static class ForwardedHeadersConfiguration
{
    public const string TrustedProxyNetworksConfigurationKey = "InfraLab:TrustedProxyNetworks";

    public static ForwardedHeadersOptions Create(string? configuredNetworks)
    {
        if (string.IsNullOrWhiteSpace(configuredNetworks))
            throw new InvalidOperationException("Production reverse-proxy trust configuration is required.");

        var networks = configuredNetworks
            .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .Select(value =>
            {
                if (!System.Net.IPNetwork.TryParse(value, out var network))
                    throw new InvalidOperationException("Production reverse-proxy trust configuration contains an invalid network.");

                return network;
            })
            .ToArray();

        if (networks.Length == 0)
            throw new InvalidOperationException("Production reverse-proxy trust configuration is required.");

        var options = new ForwardedHeadersOptions
        {
            ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto,
            ForwardLimit = 1,
            RequireHeaderSymmetry = true,
        };
        options.KnownIPNetworks.Clear();
        options.KnownProxies.Clear();

        foreach (var network in networks)
            options.KnownIPNetworks.Add(network);

        return options;
    }
}
