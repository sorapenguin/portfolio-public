using Calendo.Data;
using Calendo.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.EntityFrameworkCore;

namespace Calendo.Pages.Admin;

[Authorize(Roles = DemoAccountSeeder.AdminRole)]
public class UsersModel : PageModel
{
    private readonly UserManager<AppUser> _userManager;

    public UsersModel(UserManager<AppUser> userManager)
    {
        _userManager = userManager;
    }

    public List<AppUser> Users { get; set; } = new();
    public string CurrentUserId { get; set; } = string.Empty;

    public async Task<IActionResult> OnGetAsync()
    {
        await LoadAsync();
        return Page();
    }

    public async Task<IActionResult> OnPostToggleAdminAsync(string id)
    {
        var user = await _userManager.FindByIdAsync(id);
        if (user != null)
        {
            if (IsProtectedDemoAccount(user))
            {
                return BadRequest();
            }

            user.IsAdmin = !user.IsAdmin;
            var updateResult = await _userManager.UpdateAsync(user);
            if (!updateResult.Succeeded)
            {
                return BadRequest();
            }

            var isInRole = await _userManager.IsInRoleAsync(user, DemoAccountSeeder.AdminRole);
            var roleResult = user.IsAdmin && !isInRole
                ? await _userManager.AddToRoleAsync(user, DemoAccountSeeder.AdminRole)
                : !user.IsAdmin && isInRole
                    ? await _userManager.RemoveFromRoleAsync(user, DemoAccountSeeder.AdminRole)
                    : IdentityResult.Success;
            if (!roleResult.Succeeded)
            {
                return BadRequest();
            }
        }
        return RedirectToPage();
    }

    public async Task<IActionResult> OnPostDeleteAsync(string id)
    {
        var currentUser = await _userManager.GetUserAsync(User);
        if (currentUser == null || currentUser.Id == id)
        {
            return RedirectToPage();
        }

        var user = await _userManager.FindByIdAsync(id);
        if (user != null)
        {
            if (IsProtectedDemoAccount(user))
            {
                return BadRequest();
            }

            await _userManager.DeleteAsync(user);
        }
        return RedirectToPage();
    }

    private async Task LoadAsync()
    {
        var currentUser = await _userManager.GetUserAsync(User);
        CurrentUserId = currentUser?.Id ?? string.Empty;
        Users = await _userManager.Users.OrderBy(u => u.DisplayName).ToListAsync();
    }

    public static bool IsProtectedDemoAccount(AppUser user) =>
        user.Email is DemoAccountSeeder.AdminEmail or DemoAccountSeeder.UserEmail;
}
