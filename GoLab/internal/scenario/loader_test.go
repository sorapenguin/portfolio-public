package scenario_test

import (
	"os"
	"path/filepath"
	"testing"

	"golab/internal/scenario"
)

const minimalScenario = `{
	"schemaVersion":"1","id":"test-001","title":"Test","description":"desc",
	"difficulty":"beginner","category":"lab1","learningGoals":[],
	"nodes":[{"id":"client","type":"client","label":"Client"},{"id":"app","type":"app","label":"App"}],
	"connections":[{"from":"client","to":"app"}],
	"choices":[{"id":"app","label":"Application"}],
	"events":[{"nodeId":"client","delayMs":0,"description":"request sent","status":"ok"}],
	"expectedStatus":404,"failPoint":"app","reachedNodes":["client"],
	"explanation":"app returned 404","wrongExplanation":"wrong"
}`

func TestLoadCatalog_missing_dir(t *testing.T) {
	_, err := scenario.LoadCatalog("testdata_nonexistent_dir")
	if err == nil {
		t.Fatal("expected error for missing directory")
	}
}

func TestLoadCatalog_empty_dir(t *testing.T) {
	cat, err := scenario.LoadCatalog(t.TempDir())
	if err != nil {
		t.Fatalf("unexpected error for empty dir: %v", err)
	}
	if len(cat.All()) != 0 {
		t.Errorf("expected 0 scenarios, got %d", len(cat.All()))
	}
}

func TestLoadCatalog_single(t *testing.T) {
	dir := t.TempDir()
	writeJSON(t, filepath.Join(dir, "test-001.json"), minimalScenario)

	cat, err := scenario.LoadCatalog(dir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(cat.All()) != 1 {
		t.Fatalf("expected 1 scenario, got %d", len(cat.All()))
	}
	sc, ok := cat.Get("test-001")
	if !ok {
		t.Fatal("scenario not found by ID")
	}
	if sc.Title != "Test" {
		t.Errorf("unexpected title: %q", sc.Title)
	}
}

func TestLoadCatalog_skips_schema_json(t *testing.T) {
	dir := t.TempDir()
	writeJSON(t, filepath.Join(dir, "schema.json"), `{"$schema":"draft-07"}`)
	writeJSON(t, filepath.Join(dir, "test-001.json"), minimalScenario)

	cat, err := scenario.LoadCatalog(dir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(cat.All()) != 1 {
		t.Errorf("expected 1 scenario (schema.json excluded), got %d", len(cat.All()))
	}
}

func TestLoadCatalog_duplicate_id(t *testing.T) {
	dir := t.TempDir()
	writeJSON(t, filepath.Join(dir, "a.json"), minimalScenario)
	writeJSON(t, filepath.Join(dir, "b.json"), minimalScenario)

	_, err := scenario.LoadCatalog(dir)
	if err == nil {
		t.Fatal("expected duplicate ID error")
	}
}

func TestScenario_public_hides_answer(t *testing.T) {
	dir := t.TempDir()
	writeJSON(t, filepath.Join(dir, "test-001.json"), minimalScenario)

	cat, _ := scenario.LoadCatalog(dir)
	sc, _ := cat.Get("test-001")
	pub := sc.Public()

	if pub.ID != "test-001" {
		t.Errorf("unexpected id: %q", pub.ID)
	}
}

func writeJSON(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}
