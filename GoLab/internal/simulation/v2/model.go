// Package v2 calculates deterministic results for validated Schema v2 cores.
package v2

import validate "golab/internal/validate/v2"

type RuleID string

const (
	RuleApplicationHTTPStatus RuleID = "application_http_status"
	RuleReverseProxyTimeout   RuleID = "reverse_proxy_timeout"
	RuleLBNoHealthyTarget     RuleID = "lb_no_healthy_target"
	RuleWAFBlock              RuleID = "waf_block"
	RuleMalformedUpstream     RuleID = "malformed_upstream_response"
)

type Outcome string

const (
	OutcomeSuccess Outcome = "success"
	OutcomeError   Outcome = "error"
)

type EventKind string

const (
	EventRequestSent       EventKind = "request_sent"
	EventRequestForwarded  EventKind = "request_forwarded"
	EventProcessingStarted EventKind = "processing_started"
	EventResponseReturned  EventKind = "response_returned"
	EventResponseForwarded EventKind = "response_forwarded"
	EventResponseReceived  EventKind = "response_received"
	EventNoHealthyTarget   EventKind = "no_healthy_target"
	EventTimeoutFired      EventKind = "timeout_fired"
	EventBlocked           EventKind = "blocked"
	EventMalformedSent     EventKind = "malformed_response_sent"
	EventMalformedReceived EventKind = "malformed_received"
)

type NodeStateKind string

const (
	NodeStateNotReached              NodeStateKind = "not_reached"
	NodeStateCompleted               NodeStateKind = "completed"
	NodeStateGeneratedResponse       NodeStateKind = "generated_response"
	NodeStateProcessingAtTermination NodeStateKind = "processing_at_termination"
)

type Event struct {
	AtMS       int            `json:"at_ms"`
	NodeID     string         `json:"node_id"`
	Kind       EventKind      `json:"kind"`
	MessageKey string         `json:"message_key"`
	Details    map[string]int `json:"details"`
}

type NodeState struct {
	NodeID string        `json:"node_id"`
	State  NodeStateKind `json:"state"`
}

type Result struct {
	ResponseOriginNodeID string      `json:"response_origin_node_id"`
	HTTPStatus           int         `json:"http_status"`
	Outcome              Outcome     `json:"outcome"`
	RuleID               RuleID      `json:"rule_id"`
	Events               []Event     `json:"events"`
	NodeStates           []NodeState `json:"node_states"`
	ElapsedMS            int         `json:"elapsed_ms"`
}

// ValidationError preserves every semantic issue that prevented simulation.
type ValidationError struct{ Issues []validate.Issue }

func (e *ValidationError) Error() string {
	if len(e.Issues) == 0 {
		return "validate core scenario"
	}
	return "validate core scenario: " + e.Issues[0].Code + " at " + e.Issues[0].Path
}
