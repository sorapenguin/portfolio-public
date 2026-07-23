package simulation_test

import (
	"testing"

	"golab/internal/scenario"
	"golab/internal/simulation"
)

func makeScenario() scenario.Scenario {
	return scenario.Scenario{
		ID:               "test-001",
		FailPoint:        "app",
		ExpectedStatus:   404,
		Explanation:      "app returned 404",
		WrongExplanation: "wrong guess",
		Nodes: []scenario.Node{
			{ID: "client", Type: "client", Label: "Client"},
			{ID: "app", Type: "app", Label: "Application"},
		},
		Events: []scenario.Event{
			{NodeID: "client", DelayMs: 0, Description: "request sent", Status: "ok"},
			{NodeID: "app", DelayMs: 10, Description: "404 returned", Status: "error"},
		},
	}
}

func TestRun_correct_choice(t *testing.T) {
	sc := makeScenario()
	result := simulation.Run(sc, simulation.Request{ScenarioID: sc.ID, ChoiceID: "app"})

	if !result.Correct {
		t.Error("expected correct=true")
	}
	if result.HTTPStatus != 404 {
		t.Errorf("expected 404, got %d", result.HTTPStatus)
	}
	if len(result.Events) != 2 {
		t.Errorf("expected 2 events, got %d", len(result.Events))
	}
	if result.Events[1].NodeLabel != "Application" {
		t.Errorf("expected label 'Application', got %q", result.Events[1].NodeLabel)
	}
	if result.Explanation != "app returned 404" {
		t.Errorf("unexpected explanation: %q", result.Explanation)
	}
}

func TestRun_wrong_choice(t *testing.T) {
	sc := makeScenario()
	result := simulation.Run(sc, simulation.Request{ScenarioID: sc.ID, ChoiceID: "lb"})

	if result.Correct {
		t.Error("expected correct=false")
	}
	if result.Explanation != "wrong guess" {
		t.Errorf("unexpected explanation: %q", result.Explanation)
	}
}

func TestRun_no_choice(t *testing.T) {
	sc := makeScenario()
	result := simulation.Run(sc, simulation.Request{ScenarioID: sc.ID, ChoiceID: ""})

	if result.Correct {
		t.Error("empty choice should not be correct")
	}
}

func TestRun_events_preserve_order(t *testing.T) {
	sc := makeScenario()
	result := simulation.Run(sc, simulation.Request{ScenarioID: sc.ID, ChoiceID: "app"})

	if result.Events[0].NodeID != "client" {
		t.Errorf("first event should be client, got %q", result.Events[0].NodeID)
	}
	if result.Events[1].NodeID != "app" {
		t.Errorf("second event should be app, got %q", result.Events[1].NodeID)
	}
}
