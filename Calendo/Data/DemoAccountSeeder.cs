using Calendo.Models;
using Microsoft.AspNetCore.Identity;

namespace Calendo.Data;

public static class DemoAccountSeeder
{
    public const string AdminRole = "Admin";
    public const string AdminEmail = "admin-demo@calendo.local";
    public const string UserEmail = "user-demo@calendo.local";

    public static async Task SeedAsync(IServiceProvider services)
    {
        using var scope = services.CreateScope();
        var userManager = scope.ServiceProvider.GetRequiredService<UserManager<AppUser>>();
        var roleManager = scope.ServiceProvider.GetRequiredService<RoleManager<IdentityRole>>();

        if (!await roleManager.RoleExistsAsync(AdminRole))
        {
            var roleResult = await roleManager.CreateAsync(new IdentityRole(AdminRole));
            EnsureSucceeded(roleResult, "管理者ロールを作成できませんでした。");
        }

        var admin = await EnsureUserAsync(
            userManager,
            AdminEmail,
            "管理者デモ",
            "#C0392B",
            isAdmin: true);
        await EnsureAdminRoleAsync(userManager, admin);

        var user = await EnsureUserAsync(
            userManager,
            UserEmail,
            "一般ユーザーデモ",
            "#4A90D9",
            isAdmin: false);
        if (await userManager.IsInRoleAsync(user, AdminRole))
        {
            EnsureSucceeded(
                await userManager.RemoveFromRoleAsync(user, AdminRole),
                "一般デモユーザーから管理者ロールを解除できませんでした。");
        }
    }

    private static async Task<AppUser> EnsureUserAsync(
        UserManager<AppUser> userManager,
        string email,
        string displayName,
        string color,
        bool isAdmin)
    {
        var user = await userManager.FindByEmailAsync(email);
        if (user == null)
        {
            user = new AppUser
            {
                UserName = email,
                Email = email,
                EmailConfirmed = true,
                DisplayName = displayName,
                Color = color,
                IsAdmin = isAdmin
            };
            EnsureSucceeded(
                await userManager.CreateAsync(user),
                $"{displayName}を作成できませんでした。");
            return user;
        }

        user.DisplayName = displayName;
        user.Color = color;
        user.IsAdmin = isAdmin;
        user.EmailConfirmed = true;
        EnsureSucceeded(
            await userManager.UpdateAsync(user),
            $"{displayName}を更新できませんでした。");
        return user;
    }

    private static async Task EnsureAdminRoleAsync(UserManager<AppUser> userManager, AppUser admin)
    {
        if (!await userManager.IsInRoleAsync(admin, AdminRole))
        {
            EnsureSucceeded(
                await userManager.AddToRoleAsync(admin, AdminRole),
                "管理者デモユーザーへ管理者ロールを付与できませんでした。");
        }
    }

    private static void EnsureSucceeded(IdentityResult result, string message)
    {
        if (result.Succeeded)
        {
            return;
        }

        throw new InvalidOperationException(
            $"{message} {string.Join(" / ", result.Errors.Select(error => error.Description))}");
    }
}
