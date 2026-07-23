package server

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"golab/internal/lab"
)

func TestLabAPIRedactsInternalRunFailure(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "sample.json"), []byte(`{"schemaVersion":"1","id":"x","title":"x","description":"x","difficulty":"beginner","category":"x","learningGoals":[],"nodes":[{"id":"client","type":"client","label":"Client"},{"id":"app","type":"app","label":"App"}],"connections":[{"from":"client","to":"app"}],"choices":[{"id":"app","label":"App"}],"events":[{"nodeId":"client","delayMs":0,"description":"sent","status":"ok"}],"expectedStatus":200,"failPoint":"app","reachedNodes":["client"],"explanation":"x"}`), 0644); err != nil {
		t.Fatal(err)
	}
	s, err := New(Config{Port: "0", ScenariosDir: dir})
	if err != nil {
		t.Fatal(err)
	}
	s.labRun = func(lab.LabID, lab.SelectionSet, lab.TestID) (lab.RunResult, error) {
		return lab.RunResult{}, errors.New("sensitive compiled input C:\\secret")
	}
	r := httptest.NewRequest(http.MethodPost, "/api/v2/labs/aws-waf-alb-ecs-001/runs", strings.NewReader(`{"test_id":"client_to_application","selections":{"waf_action":"allow","target_group_state":"ready","application_outcome":"ok_200"}}`))
	r.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, r)
	if w.Code != http.StatusInternalServerError || strings.Contains(w.Body.String(), "sensitive") || strings.Contains(w.Body.String(), "compiled") || strings.Contains(w.Body.String(), "secret") || !strings.Contains(w.Body.String(), "internal_error") {
		t.Fatalf("redaction: %d %s", w.Code, w.Body.String())
	}
}
