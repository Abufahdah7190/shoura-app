using Microsoft.EntityFrameworkCore;
using Shoura.API.Models;

namespace Shoura.API.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<Post> Posts => Set<Post>();
    public DbSet<Comment> Comments => Set<Comment>();
    public DbSet<Message> Messages => Set<Message>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // تخصيصات إضافية للجداول إن وجدت (مثل تفرد اسم المستخدم Handle)
        modelBuilder.Entity<User>()
            .HasIndex(u => u.Handle)
            .IsUnique();
        modelBuilder.Entity<User>()
            .HasIndex(u => u.Email)
            .IsUnique();
        modelBuilder.Entity<Message>()
            .HasIndex(m => new { m.SenderId, m.ReceiverId, m.SentAt });
        modelBuilder.Entity<Post>().HasIndex(p => new { p.CreatedAt, p.Id });
        modelBuilder.Entity<Message>().HasIndex(m => m.ReceiverId);
        modelBuilder.Entity<Message>()
            .HasOne<User>().WithMany().HasForeignKey(m => m.SenderId).OnDelete(DeleteBehavior.Cascade);
        modelBuilder.Entity<Message>()
            .HasOne<User>().WithMany().HasForeignKey(m => m.ReceiverId).OnDelete(DeleteBehavior.Cascade);
    }
}
