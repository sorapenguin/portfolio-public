package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"golab/internal/lab"
	"golab/internal/labcatalog"
	"golab/internal/learningmodule"
	"golab/internal/scenario"
	scenariov2 "golab/internal/scenario/v2"
	"golab/internal/simulation"
	"golab/internal/validate"
	validatev2 "golab/internal/validate/v2"
	golabweb "golab/internal/web"
	runtimev2 "golab/scenarios/v2"
)

// Config holds server configuration.
type Config struct {
	Port         string
	ScenariosDir string
}

// Server handles HTTP requests for GoLab.
type Server struct {
	cfg             Config
	catalog         *scenario.Catalog
	catalogV2       *scenariov2.Catalog
	labCatalog      *labcatalog.Catalog
	learningCatalog *learningmodule.Catalog
	labRun          labRunFunc
	mux             *http.ServeMux
}

// labRunFunc is the narrow HTTP boundary dependency used to execute a verified lab.
type labRunFunc func(lab.LabID, lab.SelectionSet, lab.TestID) (lab.RunResult, error)

// New creates and initializes a Server.
func New(cfg Config) (*Server, error) {
	catalog, err := scenario.LoadCatalog(cfg.ScenariosDir)
	if err != nil {
		return nil, fmt.Errorf("load catalog from %s: %w", cfg.ScenariosDir, err)
	}
	catalogV2, err := scenariov2.LoadCatalog(runtimev2.FS, "*.json", func(core *scenariov2.CoreScenario) error {
		result := validatev2.ValidateCore(core)
		if result.Valid() {
			return nil
		}
		return fmt.Errorf("%s at %s", result.Issues[0].Code, result.Issues[0].Path)
	})
	if err != nil {
		return nil, fmt.Errorf("load v2 catalog: %w", err)
	}
	labCatalog, learningCatalog, runner, err := buildEmbeddedLabService()
	if err != nil {
		return nil, fmt.Errorf("build embedded lab catalog: %w", err)
	}

	for _, sc := range catalog.All() {
		if errs := validate.Scenario(sc); len(errs) > 0 {
			slog.Warn("scenario has validation errors", "id", sc.ID, "errors", errs)
		}
	}
	slog.Info("catalog loaded", "count", len(catalog.All()))

	s := &Server{cfg: cfg, catalog: catalog, catalogV2: catalogV2, labCatalog: labCatalog, learningCatalog: learningCatalog, labRun: runner.Run, mux: http.NewServeMux()}
	s.routes()
	return s, nil
}

// ServeHTTP implements http.Handler, used in tests.
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.mux.ServeHTTP(w, r)
}

func (s *Server) routes() {
	s.mux.HandleFunc("GET /healthz", s.handleHealthz)
	s.mux.HandleFunc("GET /api/scenarios", s.handleListScenarios)
	s.mux.HandleFunc("GET /api/scenarios/{id}", s.handleGetScenario)
	s.mux.HandleFunc("POST /api/simulate", s.handleSimulate)
	s.mux.HandleFunc("/api/v2/scenarios", s.handleV2Scenarios)
	s.mux.HandleFunc("/api/v2/scenarios/{id}", s.handleV2Scenario)
	s.mux.HandleFunc("/api/v2/simulate", s.handleV2Simulate)
	s.mux.HandleFunc("/api/v2/labs", s.handleLabList)
	s.mux.HandleFunc("/api/v2/labs/{id}", s.handleLabDetail)
	s.mux.HandleFunc("/api/v2/labs/{id}/learning", s.handleLabLearning)
	s.mux.HandleFunc("/api/v2/labs/{id}/runs", s.handleLabRun)
	s.mux.HandleFunc("GET /labs", s.handleLabsPage)
	s.mux.HandleFunc("GET /labs/{id}", s.handleLabsPage)

	sub, err := fs.Sub(golabweb.StaticFS, "static")
	if err != nil {
		panic("web embed error: " + err.Error())
	}
	s.mux.Handle("/", http.FileServer(http.FS(sub)))
	v2sub, err := fs.Sub(golabweb.StaticFS, "static/v2")
	if err != nil {
		panic("v2 web embed error: " + err.Error())
	}
	s.mux.Handle("GET /v2/", http.StripPrefix("/v2/", http.FileServer(http.FS(v2sub))))
	s.mux.HandleFunc("GET /v2", func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, "/v2/", http.StatusMovedPermanently) })
	labsSub, err := fs.Sub(golabweb.StaticFS, "static/labs")
	if err != nil {
		panic("labs web embed error: " + err.Error())
	}
	s.mux.Handle("GET /labs/assets/{asset...}", http.StripPrefix("/labs/assets/", http.FileServer(http.FS(labsSub))))
}

func (s *Server) handleLabsPage(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/labs" && len(r.PathValue("id")) == 0 {
		http.NotFound(w, r)
		return
	}
	data, err := golabweb.StaticFS.ReadFile("static/labs/index.html")
	if err != nil {
		http.Error(w, "labs UI unavailable", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("X-Frame-Options", "DENY")
	_, _ = w.Write(data)
}

// Start begins listening and blocks until graceful shutdown.
func (s *Server) Start() error {
	srv := &http.Server{
		Addr:         ":" + s.cfg.Port,
		Handler:      s.mux,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)

	errCh := make(chan error, 1)
	go func() {
		slog.Info("listening", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			errCh <- err
		}
		close(errCh)
	}()

	select {
	case err := <-errCh:
		return err
	case <-quit:
		slog.Info("shutting down")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		return srv.Shutdown(ctx)
	}
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) handleListScenarios(w http.ResponseWriter, _ *http.Request) {
	all := s.catalog.All()
	summaries := make([]scenario.Summary, 0, len(all))
	for _, sc := range all {
		summaries = append(summaries, sc.Summary())
	}
	writeJSON(w, http.StatusOK, summaries)
}

func (s *Server) handleGetScenario(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	sc, ok := s.catalog.Get(id)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "scenario not found"})
		return
	}
	writeJSON(w, http.StatusOK, sc.Public())
}

func (s *Server) handleSimulate(w http.ResponseWriter, r *http.Request) {
	if r.ContentLength > 1<<16 { // 64 KB
		writeJSON(w, http.StatusRequestEntityTooLarge, map[string]string{"error": "request too large"})
		return
	}

	var req simulation.Request
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid request body"})
		return
	}

	sc, ok := s.catalog.Get(req.ScenarioID)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "scenario not found"})
		return
	}

	result := simulation.Run(sc, req)
	writeJSON(w, http.StatusOK, result)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("X-Frame-Options", "DENY")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("json encode error", "err", err)
	}
}
