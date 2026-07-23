package learningmodule

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"sort"
	"strings"

	"golab/internal/lab"
	"golab/internal/labcatalog"
)

const MaxModuleBytes = 256 << 10

var ErrUnknownModule = errors.New("unknown learning module")

type Catalog struct{ byID map[lab.LabID]LearningModule }

func Decode(reader io.Reader) (LearningModule, error) {
	data, err := io.ReadAll(io.LimitReader(reader, MaxModuleBytes+1))
	if err != nil {
		return LearningModule{}, err
	}
	if len(data) == 0 || len(data) > MaxModuleBytes || len(bytes.TrimSpace(data)) == 0 || bytes.Equal(bytes.TrimSpace(data), []byte("null")) {
		return LearningModule{}, errors.New("invalid learning module body")
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var module LearningModule
	if err := decoder.Decode(&module); err != nil {
		return LearningModule{}, err
	}
	var trailing struct{}
	if err := decoder.Decode(&trailing); err != io.EOF {
		return LearningModule{}, errors.New("trailing JSON value")
	}
	return module, nil
}

func New(modules []LearningModule) (*Catalog, error) {
	byID := make(map[lab.LabID]LearningModule, len(modules))
	for _, module := range modules {
		if err := Validate(module); err != nil {
			return nil, err
		}
		if _, exists := byID[module.LabID]; exists {
			return nil, fmt.Errorf("duplicate learning module: %s", module.LabID)
		}
		byID[module.LabID] = module
	}
	return &Catalog{byID: byID}, nil
}
func (c *Catalog) Get(id lab.LabID) (LearningModule, error) {
	module, ok := c.byID[id]
	if !ok {
		return LearningModule{}, fmt.Errorf("%w: %s", ErrUnknownModule, id)
	}
	return module, nil
}

func Validate(module LearningModule) error {
	if module.SchemaVersion != SchemaVersion || blank(string(module.LabID)) || module.ModuleVersion < 1 || blank(module.Introduction) || len(module.LearningOutcomes) == 0 || len(module.ServiceRoles) == 0 || len(module.Stages) == 0 {
		return errors.New("invalid learning module metadata")
	}
	for _, item := range module.LearningOutcomes {
		if blank(item) {
			return errors.New("empty learning outcome")
		}
	}
	for _, role := range module.ServiceRoles {
		if blank(role.Label) || blank(role.Description) {
			return errors.New("invalid service role")
		}
	}
	if len(module.Controls) == 0 {
		return errors.New("missing control copy")
	}
	for id, copy := range module.Controls {
		if blank(string(id)) || blank(copy.Label) || len(copy.Options) == 0 {
			return errors.New("invalid control copy")
		}
		for option, optionCopy := range copy.Options {
			if blank(string(option)) || blank(optionCopy.Label) || blank(optionCopy.Description) {
				return errors.New("invalid option copy")
			}
		}
	}
	ids := map[string]bool{}
	orders := map[int]bool{}
	experimentIDs := map[string]bool{}
	for _, stage := range module.Stages {
		if blank(stage.StageID) || ids[stage.StageID] || stage.Order < 1 || orders[stage.Order] || blank(stage.Title) || blank(stage.Purpose) || blank(stage.SettingText) || blank(stage.ApplyLabel) {
			return errors.New("invalid stage")
		}
		ids[stage.StageID] = true
		orders[stage.Order] = true
		focus := map[lab.ControlID]bool{}
		for _, id := range stage.FocusControlIDs {
			if blank(string(id)) || focus[id] {
				return errors.New("invalid focus control")
			}
			focus[id] = true
		}
		switch stage.RunRequirement {
		case RunNone:
			if len(stage.Experiments) != 0 {
				return errors.New("run-free stage has experiments")
			}
		case RunAllExperiments:
			if len(stage.Experiments) == 0 {
				return errors.New("run stage has no experiments")
			}
		default:
			return errors.New("invalid run requirement")
		}
		selections := map[string]bool{}
		for _, experiment := range stage.Experiments {
			if blank(experiment.ExperimentID) || experimentIDs[experiment.ExperimentID] || blank(string(experiment.TestID)) || blank(experiment.Title) || len(experiment.RecommendedSelection) == 0 || blank(experiment.PreRunQuestion) || blank(experiment.ResultCopy) || len(experiment.Observations) == 0 || len(experiment.Misconceptions) == 0 || len(experiment.ExamPoints) == 0 {
				return errors.New("invalid experiment")
			}
			experimentIDs[experiment.ExperimentID] = true
			key := selectionKey(experiment.RecommendedSelection)
			if selections[key] {
				return errors.New("duplicate experiment selection")
			}
			selections[key] = true
			for _, values := range [][]string{experiment.Observations, experiment.Misconceptions, experiment.ExamPoints} {
				for _, value := range values {
					if blank(value) {
						return errors.New("empty experiment copy")
					}
				}
			}
		}
	}
	if len(orders) != len(module.Stages) {
		return errors.New("invalid stage order")
	}
	for order := 1; order <= len(module.Stages); order++ {
		if !orders[order] {
			return errors.New("stage order is not continuous")
		}
	}
	if err := validateFlow(module.Stages); err != nil {
		return err
	}
	return nil
}

func ValidateAgainstLab(public lab.Lab, module LearningModule) error {
	if module.LabID != public.ID {
		return errors.New("learning module lab mismatch")
	}
	controls := map[lab.ControlID]map[lab.OptionID]bool{}
	for _, control := range public.Controls {
		options := map[lab.OptionID]bool{}
		for _, option := range control.Options {
			options[option.ID] = true
		}
		controls[control.ID] = options
	}
	tests := map[lab.TestID]bool{}
	for _, test := range public.Tests {
		tests[test.ID] = true
	}
	for controlID, copy := range module.Controls {
		options, ok := controls[controlID]
		if !ok {
			return errors.New("unknown copied control")
		}
		for optionID := range copy.Options {
			if !options[optionID] {
				return errors.New("unknown copied option")
			}
		}
	}
	for controlID, options := range controls {
		copy, ok := module.Controls[controlID]
		if !ok {
			return errors.New("required control copy is missing")
		}
		for optionID := range options {
			if _, ok := copy.Options[optionID]; !ok {
				return errors.New("required option copy is missing")
			}
		}
	}
	for _, stage := range module.Stages {
		for _, id := range stage.FocusControlIDs {
			if _, ok := controls[id]; !ok {
				return errors.New("unknown focus control")
			}
		}
		for _, experiment := range stage.Experiments {
			if !tests[experiment.TestID] {
				return errors.New("unknown experiment test")
			}
			selection := experiment.RecommendedSelection
			if len(selection) != len(controls) {
				return errors.New("recommended selection is incomplete")
			}
			for controlID, optionID := range selection {
				options, ok := controls[controlID]
				if !ok || !options[optionID] {
					return errors.New("invalid recommended selection")
				}
			}
		}
	}
	return nil
}

func ValidateCatalog(public *labcatalog.Catalog, learning *Catalog) error {
	for _, definition := range public.List() {
		module, err := learning.Get(definition.ID)
		if err != nil {
			return err
		}
		if err := ValidateAgainstLab(definition, module); err != nil {
			return err
		}
	}
	return nil
}
func blank(value string) bool { return strings.TrimSpace(value) == "" }
func selectionKey(selection lab.SelectionSet) string {
	keys := make([]string, 0, len(selection))
	for id := range selection {
		keys = append(keys, string(id))
	}
	sort.Strings(keys)
	var b strings.Builder
	for _, id := range keys {
		b.WriteString(id)
		b.WriteByte('=')
		b.WriteString(string(selection[lab.ControlID(id)]))
		b.WriteByte(0)
	}
	return b.String()
}
func validateFlow(stages []Stage) error {
	byID := map[string]Stage{}
	starts := 0
	terminals := 0
	for _, stage := range stages {
		byID[stage.StageID] = stage
		if stage.Order == 1 {
			starts++
		}
		if stage.NextStageID == "" {
			terminals++
		}
	}
	if starts != 1 || terminals != 1 {
		return errors.New("invalid stage endpoints")
	}
	seen := map[string]bool{}
	current := byOrder(stages, 1)
	for {
		if seen[current.StageID] {
			return errors.New("stage cycle")
		}
		seen[current.StageID] = true
		if current.NextStageID == "" {
			break
		}
		next, ok := byID[current.NextStageID]
		if !ok || next.StageID == current.StageID || next.Order <= current.Order {
			return errors.New("invalid next stage")
		}
		current = next
	}
	if len(seen) != len(stages) {
		return errors.New("unreachable stage")
	}
	return nil
}
func byOrder(stages []Stage, order int) Stage {
	for _, stage := range stages {
		if stage.Order == order {
			return stage
		}
	}
	return Stage{}
}
