// Package lab defines the stable, engine-neutral contracts for guided labs.
package lab

type LabID string
type EngineID string
type ControlID string
type OptionID string
type TestID string
type NodeID string
type DomainID string
type SubcategoryID string
type Tag string

type Provider string

const (
	ProviderAWS   Provider = "aws"
	ProviderAzure Provider = "azure"
	ProviderGCP   Provider = "gcp"
	ProviderLinux Provider = "linux"
	ProviderAll   Provider = "all"
)

func (p Provider) Valid() bool {
	switch p {
	case ProviderAWS, ProviderAzure, ProviderGCP, ProviderLinux, ProviderAll:
		return true
	default:
		return false
	}
}

type Difficulty string

const (
	DifficultyBeginner     Difficulty = "beginner"
	DifficultyIntermediate Difficulty = "intermediate"
	DifficultyAdvanced     Difficulty = "advanced"
)

func (d Difficulty) Valid() bool {
	switch d {
	case DifficultyBeginner, DifficultyIntermediate, DifficultyAdvanced:
		return true
	default:
		return false
	}
}

type LabMode string

const (
	LabModeExplore   LabMode = "explore"
	LabModeChallenge LabMode = "challenge"
)

func (m LabMode) Valid() bool {
	return m == LabModeExplore || m == LabModeChallenge
}

type ControlType string

const ControlTypeSingleChoice ControlType = "single_choice"

func (t ControlType) Valid() bool {
	return t == ControlTypeSingleChoice
}

type OutcomeKind string

const (
	OutcomeKindApplicationResponse     OutcomeKind = "application_response"
	OutcomeKindBlocked                 OutcomeKind = "blocked"
	OutcomeKindUnavailable             OutcomeKind = "unavailable"
	OutcomeKindInvalidUpstreamResponse OutcomeKind = "invalid_upstream_response"
	OutcomeKindTimeout                 OutcomeKind = "timeout"
)

func (k OutcomeKind) Valid() bool {
	switch k {
	case OutcomeKindApplicationResponse, OutcomeKindBlocked, OutcomeKindUnavailable, OutcomeKindInvalidUpstreamResponse, OutcomeKindTimeout:
		return true
	default:
		return false
	}
}

type NodeStateKind string

const (
	NodeStateNotReached              NodeStateKind = "not_reached"
	NodeStateCompleted               NodeStateKind = "completed"
	NodeStateGeneratedResponse       NodeStateKind = "generated_response"
	NodeStateProcessingAtTermination NodeStateKind = "processing_at_termination"
)

func (k NodeStateKind) Valid() bool {
	switch k {
	case NodeStateNotReached, NodeStateCompleted, NodeStateGeneratedResponse, NodeStateProcessingAtTermination:
		return true
	default:
		return false
	}
}

type EventKind string

const (
	EventKindRequestSent       EventKind = "request_sent"
	EventKindRequestForwarded  EventKind = "request_forwarded"
	EventKindProcessingStarted EventKind = "processing_started"
	EventKindResponseReturned  EventKind = "response_returned"
	EventKindResponseForwarded EventKind = "response_forwarded"
	EventKindResponseReceived  EventKind = "response_received"
	EventKindNoHealthyTarget   EventKind = "no_healthy_target"
	EventKindTimeoutFired      EventKind = "timeout_fired"
	EventKindBlocked           EventKind = "blocked"
	EventKindMalformedSent     EventKind = "malformed_response_sent"
	EventKindMalformedReceived EventKind = "malformed_received"
)

func (k EventKind) Valid() bool {
	switch k {
	case EventKindRequestSent, EventKindRequestForwarded, EventKindProcessingStarted, EventKindResponseReturned,
		EventKindResponseForwarded, EventKindResponseReceived, EventKindNoHealthyTarget, EventKindTimeoutFired,
		EventKindBlocked, EventKindMalformedSent, EventKindMalformedReceived:
		return true
	default:
		return false
	}
}

// Lab is the static definition of one guided pseudo hands-on exercise.
// A Lab always selects exactly one engine through EngineID.
type Lab struct {
	ID                 LabID            `json:"id"`
	Title              string           `json:"title"`
	Description        string           `json:"description"`
	Difficulty         Difficulty       `json:"difficulty"`
	Mode               LabMode          `json:"mode"`
	Provider           Provider         `json:"provider"`
	DomainID           DomainID         `json:"domain_id"`
	SubcategoryIDs     []SubcategoryID  `json:"subcategory_ids"`
	Tags               []Tag            `json:"tags,omitempty"`
	EngineID           EngineID         `json:"engine"`
	Mission            string           `json:"mission"`
	LearningObjectives []string         `json:"learning_objectives"`
	Topology           Topology         `json:"topology"`
	Controls           []Control        `json:"controls"`
	Tests              []TestDefinition `json:"tests"`
	Simplifications    []Simplification `json:"simplifications"`
}

// Topology describes the learner-visible architecture, not engine evaluation stages.
type Topology struct {
	Nodes []TopologyNode `json:"nodes"`
	Edges []TopologyEdge `json:"edges,omitempty"`
}

type TopologyNode struct {
	ID      NodeID      `json:"id"`
	Type    string      `json:"type"`
	Display NodeDisplay `json:"display"`
}

type NodeDisplay struct {
	Label            string           `json:"label"`
	ServiceName      string           `json:"service_name"`
	Simplification   string           `json:"simplification"`
	OfficialNote     string           `json:"official_note,omitempty"`
	IconKey          string           `json:"icon_key,omitempty"`
	AttachedPolicies []AttachedPolicy `json:"attached_policies,omitempty"`
}

// AttachedPolicy represents a display-only association such as a WAF Web ACL on an ALB.
type AttachedPolicy struct {
	Label       string `json:"label"`
	NodeID      NodeID `json:"node_id"`
	DisplayOnly bool   `json:"display_only"`
}

type TopologyEdge struct {
	From NodeID `json:"from"`
	To   NodeID `json:"to"`
}

type Control struct {
	ID              ControlID   `json:"id"`
	Label           string      `json:"label"`
	Description     string      `json:"description,omitempty"`
	Type            ControlType `json:"type"`
	Options         []Option    `json:"options"`
	DefaultOptionID OptionID    `json:"default_option_id"`
	Required        bool        `json:"required"`
}

// Option intentionally contains only an allowlisted identifier and display text.
type Option struct {
	ID          OptionID `json:"id"`
	Label       string   `json:"label"`
	Description string   `json:"description,omitempty"`
}

type SelectionSet map[ControlID]OptionID

type TestDefinition struct {
	ID          TestID `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description,omitempty"`
}

type Simplification struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Description string `json:"description"`
}

// RunRequest contains only selections and the requested allowlisted test.
// The LabID is taken from the request path by the future handler.
type RunRequest struct {
	Selections SelectionSet `json:"selections"`
	TestID     TestID       `json:"test_id"`
}

type Event struct {
	AtMS       int       `json:"at_ms"`
	NodeID     NodeID    `json:"node_id"`
	Kind       EventKind `json:"kind"`
	MessageKey string    `json:"message_key"`
}

type NodeState struct {
	NodeID NodeID        `json:"node_id"`
	State  NodeStateKind `json:"state"`
}

type FailurePoint struct {
	NodeID NodeID `json:"node_id"`
	Reason string `json:"reason"`
}

// RunResult is the common, engine-neutral result returned after a lab run.
type RunResult struct {
	LabID                LabID                `json:"lab_id"`
	TestID               TestID               `json:"test_id"`
	EngineID             EngineID             `json:"engine_id"`
	OutcomeKind          OutcomeKind          `json:"outcome_kind"`
	ResponseOriginNodeID *NodeID              `json:"response_origin_node_id,omitempty"`
	HTTPStatus           *int                 `json:"http_status,omitempty"`
	TerminationReason    string               `json:"termination_reason"`
	FailurePoint         *FailurePoint        `json:"failure_point,omitempty"`
	Events               []Event              `json:"events"`
	NodeStates           []NodeState          `json:"node_states"`
	ElapsedMS            int                  `json:"elapsed_ms"`
	AppliedLabels        map[ControlID]string `json:"applied_labels"`
	Explanation          string               `json:"explanation"`
	ExamPoints           []string             `json:"exam_points,omitempty"`
}
