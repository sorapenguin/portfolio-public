// Package simulation implements deterministic event replay for GoLab scenarios.
// All virtual timing (DelayMs) is computed without real waiting.
package simulation

import "golab/internal/scenario"

// Request is the input to a simulation run.
type Request struct {
	ScenarioID string `json:"scenarioId"`
	ChoiceID   string `json:"choiceId"` // user's prediction (node ID)
}

// Result is the output of a simulation run.
type Result struct {
	ScenarioID  string        `json:"scenarioId"`
	Events      []EventResult `json:"events"`
	HTTPStatus  int           `json:"httpStatus"`
	Correct     bool          `json:"correct"`
	Explanation string        `json:"explanation"`
}

// EventResult is one step in the simulation timeline.
type EventResult struct {
	NodeID      string `json:"nodeId"`
	NodeLabel   string `json:"nodeLabel"`
	DelayMs     int    `json:"delayMs"`
	Description string `json:"description"`
	Status      string `json:"status"` // ok | error | timeout
}

// Run executes the scenario and returns the result.
// No real I/O or sleeps occur; all timing is virtual.
func Run(sc scenario.Scenario, req Request) Result {
	labels := make(map[string]string, len(sc.Nodes))
	for _, n := range sc.Nodes {
		labels[n.ID] = n.Label
	}

	events := make([]EventResult, 0, len(sc.Events))
	for _, e := range sc.Events {
		events = append(events, EventResult{
			NodeID:      e.NodeID,
			NodeLabel:   labels[e.NodeID],
			DelayMs:     e.DelayMs,
			Description: e.Description,
			Status:      e.Status,
		})
	}

	correct := req.ChoiceID != "" && req.ChoiceID == sc.FailPoint
	explanation := sc.Explanation
	if !correct && sc.WrongExplanation != "" {
		explanation = sc.WrongExplanation
	}

	return Result{
		ScenarioID:  sc.ID,
		Events:      events,
		HTTPStatus:  sc.ExpectedStatus,
		Correct:     correct,
		Explanation: explanation,
	}
}
