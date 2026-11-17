using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Shoura.API.Data;

namespace Shoura.API.Controllers;

[ApiController]
[Authorize]
[Route("api/users")]
public sealed class UsersController(AppDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<ContactDto>>> GetContacts(CancellationToken cancellationToken)
    {
        if (!int.TryParse(User.FindFirstValue(ClaimTypes.NameIdentifier), out var currentUserId)) return Unauthorized();
        var users = await db.Users.AsNoTracking()
            .Where(u => u.Id != currentUserId)
            .OrderBy(u => u.Handle)
            .Take(100)
            .Select(u => new ContactDto(u.Id, u.Name, u.Handle, u.Bio))
            .ToListAsync(cancellationToken);
        return Ok(users);
    }
}

public sealed record ContactDto(int Id, string Name, string Handle, string Bio);
