using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.ComponentModel.DataAnnotations;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Shoura.API.Data;
using Shoura.API.Models;

namespace Shoura.API.Controllers;

[Route("api/[controller]")]
[ApiController]
public class PostsController : ControllerBase
{
    private readonly AppDbContext _context;

    public PostsController(AppDbContext context)
    {
        _context = context;
    }

    // جلب جميع التغريدات والمنشورات (Timeline)
    [HttpGet]
    public async Task<ActionResult<FeedPageDto>> GetPosts([FromQuery] int? beforeId, [FromQuery] DateTime? beforeCreatedAt, [FromQuery] int limit = 20, CancellationToken cancellationToken = default)
    {
        limit = Math.Clamp(limit, 1, 50);
        var query = _context.Posts.AsNoTracking();
        if (beforeId is not null && beforeCreatedAt is not null)
        {
            var cursorDate = DateTime.SpecifyKind(beforeCreatedAt.Value, DateTimeKind.Utc);
            query = query.Where(p => p.CreatedAt < cursorDate || (p.CreatedAt == cursorDate && p.Id < beforeId.Value));
        }
        else if (beforeId is not null || beforeCreatedAt is not null) return BadRequest(new { message = "مؤشر الصفحة غير مكتمل." });
        var rows = await query
            .OrderByDescending(p => p.CreatedAt).ThenByDescending(p => p.Id)
            .Take(limit + 1)
            .Select(p => new PostDto(
                p.Id, p.Content, p.MediaUrl, p.MediaType, p.LikesCount, p.RetweetsCount, p.CreatedAt, p.UserId,
                p.User == null ? null : new PublicUserDto(p.User.Id, p.User.Name, p.User.Handle, p.User.Bio)))
            .ToListAsync(cancellationToken);
        var hasMore = rows.Count > limit;
        var items = rows.Take(limit).ToArray();
        return new FeedPageDto(items, hasMore ? items[^1].Id : null, hasMore ? items[^1].CreatedAt : null, hasMore);
    }

    // إنشاء تغريدة أو منشور جديد
    [HttpPost]
    [Authorize]
    public async Task<ActionResult<PostDto>> CreatePost([FromBody] CreatePostDto model, CancellationToken cancellationToken)
    {
        if (!int.TryParse(User.FindFirstValue(ClaimTypes.NameIdentifier), out var currentUserId) ||
            !await _context.Users.AnyAsync(u => u.Id == currentUserId, cancellationToken))
            return BadRequest(new { message = "الحساب غير موجود." });

        var post = new Post
        {
            Content = model.Content,
            MediaUrl = model.MediaUrl,
            MediaType = model.MediaType,
            UserId = currentUserId,
            CreatedAt = DateTime.UtcNow
        };

        _context.Posts.Add(post);
        await _context.SaveChangesAsync(cancellationToken);

        var createdPost = await _context.Posts
            .Include(p => p.User)
            .Where(p => p.Id == post.Id)
            .Select(p => new PostDto(
                p.Id, p.Content, p.MediaUrl, p.MediaType, p.LikesCount, p.RetweetsCount, p.CreatedAt, p.UserId,
                p.User == null ? null : new PublicUserDto(p.User.Id, p.User.Name, p.User.Handle, p.User.Bio)))
            .FirstOrDefaultAsync(cancellationToken);

        return Ok(createdPost);
    }
}

public class CreatePostDto
{
    [StringLength(10_000)]
    public string Content { get; set; } = string.Empty;

    [StringLength(2_048)]
    public string? MediaUrl { get; set; }

    [RegularExpression("^(text|image|video|audio)$")]
    public string MediaType { get; set; } = "text";

}

public sealed record PublicUserDto(int Id, string Name, string Handle, string Bio);
public sealed record PostDto(int Id, string Content, string? MediaUrl, string MediaType, int LikesCount, int RetweetsCount, DateTime CreatedAt, int UserId, PublicUserDto? User);
public sealed record FeedPageDto(IReadOnlyList<PostDto> Items, int? NextCursor, DateTime? NextCursorCreatedAt, bool HasMore);
