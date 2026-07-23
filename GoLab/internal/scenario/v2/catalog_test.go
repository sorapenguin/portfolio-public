package v2_test

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"testing/fstest"

	scenario "golab/internal/scenario/v2"
	simulation "golab/internal/simulation/v2"
	validate "golab/internal/validate/v2"
	runtime "golab/scenarios/v2"
)

func validator(core *scenario.CoreScenario) error {
	r := validate.ValidateCore(core)
	if r.Valid() {
		return nil
	}
	return fmt.Errorf("%s", r.Issues[0].Code)
}
func TestLoadCatalogRuntime(t *testing.T) {
	c, err := scenario.LoadCatalog(runtime.FS, "*.json", validator)
	if err != nil {
		t.Fatal(err)
	}
	list := c.List()
	if len(list) != 5 || list[0].ID != "core-application-404-001" || list[1].ID != "core-lb-no-healthy-target-001" || list[2].ID != "core-malformed-upstream-001" || list[3].ID != "core-proxy-timeout-001" || list[4].ID != "core-waf-block-001" {
		t.Fatalf("list=%#v", list)
	}
	list[0] = nil
	if c.List()[0] == nil {
		t.Fatal("List exposed catalog slice")
	}
	for _, want := range []struct {
		id       string
		category scenario.Category
		rule     simulation.RuleID
		origin   string
		status   int
	}{{"core-application-404-001", scenario.CategoryError, simulation.RuleApplicationHTTPStatus, "app", 404}, {"core-lb-no-healthy-target-001", scenario.CategoryAvailability, simulation.RuleLBNoHealthyTarget, "lb", 503}, {"core-malformed-upstream-001", scenario.CategoryError, simulation.RuleMalformedUpstream, "proxy", 502}, {"core-proxy-timeout-001", scenario.CategoryTimeout, simulation.RuleReverseProxyTimeout, "proxy", 504}, {"core-waf-block-001", scenario.CategorySecurity, simulation.RuleWAFBlock, "waf", 403}} {
		core, ok := c.Get(want.id)
		if !ok {
			t.Fatalf("missing %s", want.id)
		}
		if core.Category != want.category || !core.Category.Valid() {
			t.Fatalf("%s category = %q, want %q", want.id, core.Category, want.category)
		}
		r, err := simulation.Simulate(core)
		if err != nil || r.RuleID != want.rule || r.ResponseOriginNodeID != want.origin || r.HTTPStatus != want.status {
			t.Fatalf("%s result=%#v err=%v", want.id, r, err)
		}
	}
}
func TestRuntimeScenarioMatchesFixture(t *testing.T) {
	runtimeJSON, err := runtime.FS.ReadFile("core-proxy-timeout-001.json")
	if err != nil {
		t.Fatal(err)
	}
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "testdata", "scenarios", "v2", "core-proxy-timeout-001.json"))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(runtimeJSON, fixture) {
		t.Fatal("runtime scenario diverged from fixture")
	}
}
func TestLoadCatalogRejectsInvalidDocuments(t *testing.T) {
	raw, err := runtime.FS.ReadFile("core-proxy-timeout-001.json")
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name string
		fs   fstest.MapFS
	}{
		{"duplicate", fstest.MapFS{"a.json": {Data: raw}, "b.json": {Data: raw}}},
		{"drill", fstest.MapFS{"d.json": {Data: []byte(`{"schema_version":2,"type":"drill","id":"d","core_id":"x","title":"d","description":"d","difficulty":"beginner","overrides":{"proxy":{"timeout_ms":1}}}`)}}},
		{"malformed", fstest.MapFS{"bad.json": {Data: []byte(`{`)}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := scenario.LoadCatalog(tt.fs, "*.json", validator); err == nil {
				t.Fatal("expected error")
			}
		})
	}
}
