package main

import (
	"log/slog"
	"os"

	"golab/internal/server"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	scenariosDir := os.Getenv("SCENARIOS_DIR")
	if scenariosDir == "" {
		scenariosDir = "scenarios"
	}

	srv, err := server.New(server.Config{
		Port:         port,
		ScenariosDir: scenariosDir,
	})
	if err != nil {
		slog.Error("failed to initialize server", "err", err)
		os.Exit(1)
	}

	if err := srv.Start(); err != nil {
		slog.Error("server error", "err", err)
		os.Exit(1)
	}
}
