package validate_test

import (
	"testing"

	"golab/internal/scenario"
	"golab/internal/validate"
)

func validScenario() scenario.Scenario {
	return scenario.Scenario{
		SchemaVersion:    "1",
		ID:               "test-001",
		Title:            "Test Scenario",
		Description:      "A description",
		Difficulty:       "beginner",
		Category:         "lab1",
		ExpectedStatus:   404,
		FailPoint:        "app",
		Explanation:      "app returned 404",
		WrongExplanation: "wrong",
		Nodes: []scenario.Node{
			{ID: "client", Type: "client", Label: "Client"},
			{ID: "app", Type: "app", Label: "Application"},
		},
		Connections: []scenario.Connection{
			{From: "client", To: "app"},
		},
		Choices: []scenario.Choice{
			{ID: "app", Label: "Application が返した"},
		},
		Events: []scenario.Event{
			{NodeID: "client", DelayMs: 0, Description: "request sent", Status: "ok"},
			{NodeID: "app", DelayMs: 10, Description: "404 returned", Status: "error"},
		},
	}
}

func TestValidate_valid(t *testing.T) {
	errs := validate.Scenario(validScenario())
	if len(errs) != 0 {
		t.Errorf("expected no errors for valid scenario, got: %v", errs)
	}
}

func TestValidate_missing_id(t *testing.T) {
	sc := validScenario()
	sc.ID = ""
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for missing id")
	}
}

func TestValidate_missing_title(t *testing.T) {
	sc := validScenario()
	sc.Title = ""
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for missing title")
	}
}

func TestValidate_invalid_difficulty(t *testing.T) {
	sc := validScenario()
	sc.Difficulty = "super-hard"
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for invalid difficulty")
	}
}

func TestValidate_unknown_node_type(t *testing.T) {
	sc := validScenario()
	sc.Nodes[0].Type = "unknown-type"
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for unknown node type")
	}
}

func TestValidate_connection_to_unknown_node(t *testing.T) {
	sc := validScenario()
	sc.Connections = append(sc.Connections, scenario.Connection{From: "client", To: "ghost"})
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for connection to unknown node")
	}
}

func TestValidate_event_references_unknown_node(t *testing.T) {
	sc := validScenario()
	sc.Events = append(sc.Events, scenario.Event{NodeID: "ghost", DelayMs: 5, Description: "x", Status: "ok"})
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for event referencing unknown node")
	}
}

func TestValidate_invalid_event_status(t *testing.T) {
	sc := validScenario()
	sc.Events[0].Status = "bad-status"
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for invalid event status")
	}
}

func TestValidate_invalid_http_status(t *testing.T) {
	sc := validScenario()
	sc.ExpectedStatus = 99
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for HTTP status < 100")
	}
}

func TestValidate_empty_choices(t *testing.T) {
	sc := validScenario()
	sc.Choices = nil
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for empty choices")
	}
}

func TestValidate_failpoint_unknown_node(t *testing.T) {
	sc := validScenario()
	sc.FailPoint = "ghost"
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for failPoint referencing unknown node")
	}
}

func TestValidate_duplicate_node_id(t *testing.T) {
	sc := validScenario()
	sc.Nodes = append(sc.Nodes, scenario.Node{ID: "client", Type: "client", Label: "Client2"})
	if len(validate.Scenario(sc)) == 0 {
		t.Error("expected error for duplicate node id")
	}
}
