package learningmodule

import (
	"errors"
	"golab/internal/lab"
	"golab/internal/labcatalog"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func heroModule(t *testing.T) LearningModule {
	t.Helper()
	f, err := os.Open(filepath.Join("..", "..", "learning-modules", "aws-waf-alb-ecs-001.json"))
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	m, err := Decode(f)
	if err != nil {
		t.Fatal(err)
	}
	return m
}
func heroPublic(t *testing.T) lab.Lab {
	t.Helper()
	f, err := os.Open(filepath.Join("..", "..", "labs", "aws-waf-alb-ecs-001.json"))
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	v, err := labcatalog.Decode(f)
	if err != nil {
		t.Fatal(err)
	}
	return v
}
func reject(t *testing.T, err error, want string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), want) {
		t.Fatalf("error=%v want %q", err, want)
	}
}
func TestDecodeRejectsLegacyAndNestedUnknownFields(t *testing.T) {
	for _, tc := range []struct {
		name string
		body string
	}{
		{"module", `{"schema_version":1,"legacy":true}`},
		{"legacy-recommended-selection", `{"stages":[{"recommended_selection":{}}]}`},
		{"legacy-alternate-selections", `{"stages":[{"alternate_selections":[]}]}`},
		{"legacy-conclusions-by-control", `{"stages":[{"conclusions_by_control":{}}]}`},
		{"legacy-conclusion", `{"stages":[{"conclusion":"old"}]}`},
		{"experiment", `{"stages":[{"experiments":[{"unknown":true}]}]}`},
		{"copy", `{"controls":{"x":{"options":{"y":{"unknown":true}}}}}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := Decode(strings.NewReader(tc.body)); err == nil {
				t.Fatalf("accepted %s", tc.body)
			}
		})
	}
}

func TestNewRejectsDuplicateModule(t *testing.T) {
	m := heroModule(t)
	if _, err := New([]LearningModule{m, m}); err == nil {
		t.Fatal("accepted duplicate learning module")
	}
}

func TestCatalogLookupAndCrossValidation(t *testing.T) {
	module := heroModule(t)
	catalog, err := New([]LearningModule{module})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := catalog.Get(module.LabID); err != nil {
		t.Fatal(err)
	}
	if _, err := catalog.Get("missing"); !errors.Is(err, ErrUnknownModule) {
		t.Fatalf("error=%v", err)
	}
	public, err := labcatalog.New([]lab.Lab{heroPublic(t)})
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateCatalog(public, catalog); err != nil {
		t.Fatal(err)
	}
	if err := ValidateCatalog(public, &Catalog{byID: map[lab.LabID]LearningModule{}}); !errors.Is(err, ErrUnknownModule) {
		t.Fatalf("error=%v", err)
	}
}

func TestValidateRejectsModuleAndCopyBranches(t *testing.T) {
	for _, tc := range []struct {
		name, want string
		mutate     func(*LearningModule)
	}{
		{"empty-outcome", "empty learning outcome", func(m *LearningModule) { m.LearningOutcomes[0] = "" }},
		{"invalid-role", "invalid service role", func(m *LearningModule) { m.ServiceRoles[0].Label = "" }},
		{"missing-control-copy", "missing control copy", func(m *LearningModule) { m.Controls = nil }},
		{"invalid-option-copy", "invalid option copy", func(m *LearningModule) { m.Controls["waf_action"].Options["allow"] = OptionCopy{} }},
		{"non-contiguous-order", "stage order is not continuous", func(m *LearningModule) { m.Stages[1].Order = 8 }},
		{"multiple-terminals", "invalid stage endpoints", func(m *LearningModule) { m.Stages[5].NextStageID = "" }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			m := heroModule(t)
			tc.mutate(&m)
			reject(t, Validate(m), tc.want)
		})
	}
}
func TestValidateRejectsStageFlowFixtures(t *testing.T) {
	for _, tc := range []struct {
		name, want string
		mutate     func(*LearningModule)
	}{{"duplicate-order", "invalid stage", func(m *LearningModule) { m.Stages[1].Order = 1 }}, {"bad-next", "invalid next stage", func(m *LearningModule) { m.Stages[0].NextStageID = "missing" }}, {"self-loop", "invalid next stage", func(m *LearningModule) { m.Stages[0].NextStageID = m.Stages[0].StageID }}, {"cycle", "invalid next stage", func(m *LearningModule) { m.Stages[1].NextStageID = m.Stages[0].StageID }}, {"run-none-experiment", "run-free stage has experiments", func(m *LearningModule) { m.Stages[0].Experiments = []Experiment{m.Stages[1].Experiments[0]} }}, {"run-stage-empty", "run stage has no experiments", func(m *LearningModule) { m.Stages[1].Experiments = nil }}} {
		t.Run(tc.name, func(t *testing.T) { m := heroModule(t); tc.mutate(&m); reject(t, Validate(m), tc.want) })
	}
}
func TestValidateRejectsExperimentFixtures(t *testing.T) {
	for _, tc := range []struct {
		name, want string
		mutate     func(*LearningModule)
	}{{"empty-id", "invalid experiment", func(m *LearningModule) { m.Stages[1].Experiments[0].ExperimentID = "" }}, {"empty-test", "invalid experiment", func(m *LearningModule) { m.Stages[1].Experiments[0].TestID = "" }}, {"empty-selection", "invalid experiment", func(m *LearningModule) { m.Stages[1].Experiments[0].RecommendedSelection = nil }}, {"empty-result", "invalid experiment", func(m *LearningModule) { m.Stages[1].Experiments[0].ResultCopy = "" }}, {"duplicate-selection", "duplicate experiment selection", func(m *LearningModule) {
		m.Stages[4].Experiments[1].RecommendedSelection = m.Stages[4].Experiments[0].RecommendedSelection
	}}} {
		t.Run(tc.name, func(t *testing.T) { m := heroModule(t); tc.mutate(&m); reject(t, Validate(m), tc.want) })
	}
}
func TestCrossValidateRejectsReferencesAndMissingCopies(t *testing.T) {
	for _, tc := range []struct {
		name, want string
		mutate     func(*LearningModule)
	}{{"unknown-test", "unknown experiment test", func(m *LearningModule) { m.Stages[1].Experiments[0].TestID = "missing" }}, {"missing-selection", "recommended selection is incomplete", func(m *LearningModule) { delete(m.Stages[1].Experiments[0].RecommendedSelection, "waf_action") }}, {"unknown-option", "invalid recommended selection", func(m *LearningModule) { m.Stages[1].Experiments[0].RecommendedSelection["waf_action"] = "missing" }}, {"unknown-focus", "unknown focus control", func(m *LearningModule) { m.Stages[1].FocusControlIDs = []lab.ControlID{"missing"} }}, {"missing-copy", "required control copy is missing", func(m *LearningModule) { delete(m.Controls, "waf_action") }}} {
		t.Run(tc.name, func(t *testing.T) {
			m := heroModule(t)
			tc.mutate(&m)
			reject(t, ValidateAgainstLab(heroPublic(t), m), tc.want)
		})
	}
}

func TestCrossValidateRejectsRemainingCopyAndSelectionBranches(t *testing.T) {
	for _, tc := range []struct {
		name, want string
		mutate     func(*LearningModule)
	}{
		{"unknown-copied-option", "unknown copied option", func(m *LearningModule) {
			m.Controls["waf_action"].Options["missing"] = OptionCopy{Label: "x", Description: "x"}
		}},
		{"missing-option-copy", "required option copy is missing", func(m *LearningModule) { delete(m.Controls["waf_action"].Options, "allow") }},
		{"extra-selection-control", "recommended selection is incomplete", func(m *LearningModule) { m.Stages[1].Experiments[0].RecommendedSelection["extra"] = "value" }},
		{"unknown-selection-control", "invalid recommended selection", func(m *LearningModule) {
			delete(m.Stages[1].Experiments[0].RecommendedSelection, "waf_action")
			m.Stages[1].Experiments[0].RecommendedSelection["missing"] = "allow"
		}},
		{"option-owned-by-another-control", "invalid recommended selection", func(m *LearningModule) { m.Stages[1].Experiments[0].RecommendedSelection["waf_action"] = "ready" }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			m := heroModule(t)
			tc.mutate(&m)
			reject(t, ValidateAgainstLab(heroPublic(t), m), tc.want)
		})
	}
}
