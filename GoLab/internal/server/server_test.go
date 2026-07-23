package server_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"golab/internal/server"
)

const sampleScenario = `{
	"schemaVersion":"1","id":"lab1-404","title":"404 Test","description":"desc",
	"difficulty":"beginner","category":"lab1","learningGoals":[],
	"nodes":[
		{"id":"client","type":"client","label":"Client"},
		{"id":"app","type":"app","label":"Application"}
	],
	"connections":[{"from":"client","to":"app"}],
	"choices":[{"id":"app","label":"Application が返した"}],
	"events":[
		{"nodeId":"client","delayMs":0,"description":"request sent","status":"ok"},
		{"nodeId":"app","delayMs":10,"description":"404 returned","status":"error"}
	],
	"expectedStatus":404,"failPoint":"app","reachedNodes":["client"],
	"explanation":"app returned 404","wrongExplanation":"wrong"
}`

func newTestServer(t *testing.T) *server.Server {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "lab1-404.json"), []byte(sampleScenario), 0644); err != nil {
		t.Fatalf("write sample scenario: %v", err)
	}
	srv, err := server.New(server.Config{Port: "0", ScenariosDir: dir})
	if err != nil {
		t.Fatalf("server.New: %v", err)
	}
	return srv
}

func TestHealthz(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest("GET", "/healthz", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", w.Code)
	}
	var body map[string]string
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["status"] != "ok" {
		t.Errorf("expected status=ok, got %q", body["status"])
	}
}

func TestListScenarios(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest("GET", "/api/scenarios", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", w.Code)
	}
	var body []map[string]interface{}
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if len(body) != 1 {
		t.Errorf("expected 1 scenario, got %d", len(body))
	}
}

func TestGetScenario_found(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest("GET", "/api/scenarios/lab1-404", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", w.Code)
	}
}

func TestGetScenario_not_found(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest("GET", "/api/scenarios/nonexistent", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected 404, got %d", w.Code)
	}
}

func TestSimulate_correct(t *testing.T) {
	srv := newTestServer(t)
	body := strings.NewReader(`{"scenarioId":"lab1-404","choiceId":"app"}`)
	req := httptest.NewRequest("POST", "/api/simulate", body)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", w.Code)
	}
	var result map[string]interface{}
	if err := json.NewDecoder(w.Body).Decode(&result); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if result["correct"] != true {
		t.Errorf("expected correct=true, got %v", result["correct"])
	}
}

func TestSimulate_unknown_scenario(t *testing.T) {
	srv := newTestServer(t)
	body := strings.NewReader(`{"scenarioId":"ghost","choiceId":"app"}`)
	req := httptest.NewRequest("POST", "/api/simulate", body)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected 404, got %d", w.Code)
	}
}

func TestSimulate_bad_json(t *testing.T) {
	srv := newTestServer(t)
	body := strings.NewReader(`not json`)
	req := httptest.NewRequest("POST", "/api/simulate", body)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", w.Code)
	}
}
