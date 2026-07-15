using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace InfraLab.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class InitialAttemptPersistence : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "attempts",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AnonymousSessionId = table.Column<Guid>(type: "uuid", nullable: false),
                    ScenarioId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    ScenarioVersionId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Status = table.Column<int>(type: "integer", nullable: false),
                    CurrentPhase = table.Column<int>(type: "integer", nullable: false),
                    StateVersion = table.Column<int>(type: "integer", nullable: false),
                    StateJson = table.Column<string>(type: "jsonb", nullable: false),
                    ScoreJson = table.Column<string>(type: "jsonb", nullable: true),
                    StartedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    CompletedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    CreatedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_attempts", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "attempt_events",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    AttemptId = table.Column<Guid>(type: "uuid", nullable: false),
                    Sequence = table.Column<int>(type: "integer", nullable: false),
                    EventType = table.Column<string>(type: "character varying(80)", maxLength: 80, nullable: false),
                    Phase = table.Column<int>(type: "integer", nullable: false),
                    ActionOrAnswerId = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    PreviousStateVersion = table.Column<int>(type: "integer", nullable: false),
                    ResultStateVersion = table.Column<int>(type: "integer", nullable: false),
                    IdempotencyKey = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    Outcome = table.Column<string>(type: "character varying(80)", maxLength: 80, nullable: false),
                    PayloadJson = table.Column<string>(type: "jsonb", nullable: true),
                    ResultAttemptJson = table.Column<string>(type: "jsonb", nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_attempt_events", x => x.Id);
                    table.ForeignKey(
                        name: "FK_attempt_events_attempts_AttemptId",
                        column: x => x.AttemptId,
                        principalTable: "attempts",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "IX_attempt_events_AttemptId_IdempotencyKey",
                table: "attempt_events",
                columns: new[] { "AttemptId", "IdempotencyKey" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_attempt_events_AttemptId_Sequence",
                table: "attempt_events",
                columns: new[] { "AttemptId", "Sequence" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_attempts_AnonymousSessionId_UpdatedAt",
                table: "attempts",
                columns: new[] { "AnonymousSessionId", "UpdatedAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "attempt_events");

            migrationBuilder.DropTable(
                name: "attempts");
        }
    }
}
