using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace InfraLab.Infrastructure;

public sealed class InfraLabDbContextFactory : IDesignTimeDbContextFactory<InfraLabDbContext>
{
    public InfraLabDbContext CreateDbContext(string[] args)
    {
        var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__DefaultConnection");
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new InvalidOperationException("ConnectionStrings__DefaultConnection must be supplied for EF tooling.");
        return new InfraLabDbContext(new DbContextOptionsBuilder<InfraLabDbContext>().UseNpgsql(connectionString).Options);
    }
}
