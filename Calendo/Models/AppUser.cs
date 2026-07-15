using Microsoft.AspNetCore.Identity;

namespace Calendo.Models;

public class AppUser : IdentityUser
{
    public string DisplayName { get; set; } = string.Empty;
    public string Color { get; set; } = "#4A90D9";
    public bool IsAdmin { get; set; } = false;
}
