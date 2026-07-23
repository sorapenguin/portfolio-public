// Package learningmodule defines the public, data-only guided-learning contract.
package learningmodule

import "golab/internal/lab"

const SchemaVersion = 1

type LearningModule struct {
	SchemaVersion    int                           `json:"schema_version"`
	LabID            lab.LabID                     `json:"lab_id"`
	ModuleVersion    int                           `json:"module_version"`
	Introduction     string                        `json:"introduction"`
	ServiceRoles     []ServiceRole                 `json:"service_roles"`
	LearningOutcomes []string                      `json:"learning_outcomes"`
	Controls         map[lab.ControlID]ControlCopy `json:"controls"`
	Display          DisplayMaps                   `json:"display"`
	Stages           []Stage                       `json:"stages"`
}

type ServiceRole struct {
	Label       string `json:"label"`
	Description string `json:"description"`
}
type ControlCopy struct {
	Label       string                      `json:"label"`
	Description string                      `json:"description"`
	Options     map[lab.OptionID]OptionCopy `json:"options"`
}
type OptionCopy struct {
	Label       string `json:"label"`
	Description string `json:"description"`
}
type DisplayMaps struct {
	NodeStates  map[string]string `json:"node_states"`
	Events      map[string]string `json:"events"`
	Outcomes    map[string]string `json:"outcomes"`
	Termination map[string]string `json:"termination"`
}
type RunRequirement string

const (
	RunNone           RunRequirement = "none"
	RunAllExperiments RunRequirement = "all_experiments"
)

type Stage struct {
	StageID         string          `json:"stage_id"`
	Order           int             `json:"order"`
	Title           string          `json:"title"`
	Purpose         string          `json:"purpose"`
	RunRequirement  RunRequirement  `json:"run_requirement"`
	FocusControlIDs []lab.ControlID `json:"focus_control_ids,omitempty"`
	SettingText     string          `json:"setting_text"`
	ApplyLabel      string          `json:"apply_label"`
	Experiments     []Experiment    `json:"experiments"`
	NextStageID     string          `json:"next_stage_id,omitempty"`
	NextStageLabel  string          `json:"next_stage_label,omitempty"`
}
type Experiment struct {
	ExperimentID         string           `json:"experiment_id"`
	TestID               lab.TestID       `json:"test_id"`
	Title                string           `json:"title"`
	RecommendedSelection lab.SelectionSet `json:"recommended_selection"`
	PreRunQuestion       string           `json:"pre_run_question"`
	ResultCopy           string           `json:"result_copy"`
	Observations         []string         `json:"observation_points"`
	Misconceptions       []string         `json:"common_misconceptions"`
	ExamPoints           []string         `json:"exam_judgment_points"`
	NextCopy             string           `json:"next_copy,omitempty"`
}
