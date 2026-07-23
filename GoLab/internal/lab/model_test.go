package lab

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestEnumValid(t *testing.T) {
	tests := []struct {
		name string
		got  bool
		want bool
	}{
		{"provider", ProviderAWS.Valid(), true},
		{"difficulty", DifficultyIntermediate.Valid(), true},
		{"mode", LabModeExplore.Valid(), true},
		{"control type", ControlTypeSingleChoice.Valid(), true},
		{"application response", OutcomeKindApplicationResponse.Valid(), true},
		{"blocked", OutcomeKindBlocked.Valid(), true},
		{"unavailable", OutcomeKindUnavailable.Valid(), true},
		{"invalid upstream response", OutcomeKindInvalidUpstreamResponse.Valid(), true},
		{"node state", NodeStateCompleted.Valid(), true},
		{"event kind", EventKindBlocked.Valid(), true},
		{"invalid provider", Provider("").Valid(), false},
		{"invalid difficulty", Difficulty("expert").Valid(), false},
		{"invalid mode", LabMode("").Valid(), false},
		{"invalid control type", ControlType("slider").Valid(), false},
		{"invalid outcome", OutcomeKind("").Valid(), false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if test.got != test.want {
				t.Fatalf("Valid() = %t, want %t", test.got, test.want)
			}
		})
	}
}

func TestSelectionSetJSONUsesOnlyStringOptionIDs(t *testing.T) {
	in := SelectionSet{ControlID("waf_action"): OptionID("block")}
	data, err := json.Marshal(in)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if string(data) != `{"waf_action":"block"}` {
		t.Fatalf("marshal = %s", data)
	}
	var out SelectionSet
	if err := json.Unmarshal(data, &out); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if out[ControlID("waf_action")] != OptionID("block") {
		t.Fatalf("selection = %#v", out)
	}
	for _, invalid := range []string{
		`{"waf_action":true}`,
		`{"waf_action":1}`,
		`{"waf_action":{"id":"block"}}`,
		`{"waf_action":{"nested":{"id":"block"}}}`,
	} {
		var selections SelectionSet
		if err := json.Unmarshal([]byte(invalid), &selections); err == nil {
			t.Fatalf("unmarshal %s succeeded", invalid)
		}
	}
}

func TestRunRequestJSONContract(t *testing.T) {
	in := RunRequest{Selections: SelectionSet{ControlID("waf_action"): OptionID("allow")}, TestID: TestID("client_to_application")}
	data, err := json.Marshal(in)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if strings.Contains(string(data), "predicted_response_origin_node_id") {
		t.Fatalf("unexpected prediction field: %s", data)
	}
	var out RunRequest
	if err := json.Unmarshal(data, &out); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if out.TestID != in.TestID || out.Selections[ControlID("waf_action")] != OptionID("allow") {
		t.Fatalf("round trip = %#v", out)
	}
}

func TestRunResultOptionalFields(t *testing.T) {
	status := 403
	origin := NodeID("waf_policy")
	blocked := RunResult{LabID: "aws-waf-alb-ecs-001", TestID: "client_to_application", EngineID: "http_path", OutcomeKind: OutcomeKindBlocked, ResponseOriginNodeID: &origin, HTTPStatus: &status, TerminationReason: "waf_rule_block", FailurePoint: &FailurePoint{NodeID: "waf_policy", Reason: "waf_rule_block"}}
	data, err := json.Marshal(blocked)
	if err != nil {
		t.Fatalf("marshal block: %v", err)
	}
	for _, field := range []string{"failure_point", "response_origin_node_id", "http_status"} {
		if !strings.Contains(string(data), field) {
			t.Fatalf("block result omitted %s: %s", field, data)
		}
	}
	app404 := RunResult{LabID: "aws-waf-alb-ecs-001", TestID: "client_to_application", EngineID: "http_path", OutcomeKind: OutcomeKindApplicationResponse, TerminationReason: "application_response"}
	data, err = json.Marshal(app404)
	if err != nil {
		t.Fatalf("marshal 404 shape: %v", err)
	}
	for _, field := range []string{"failure_point", "response_origin_node_id", "http_status"} {
		if strings.Contains(string(data), field) {
			t.Fatalf("optional field present: %s", data)
		}
	}
}

func TestHeroLabRoundTrip(t *testing.T) {
	lab := heroLab()
	data, err := json.Marshal(lab)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var out Lab
	if err := json.Unmarshal(data, &out); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if out.Mode != LabModeExplore || out.EngineID != EngineID("http_path") || len(out.Controls) != 3 || len(out.Tests) != 1 || len(out.Simplifications) == 0 {
		t.Fatalf("round trip lab = %#v", out)
	}
	options := 0
	for _, control := range out.Controls {
		options += len(control.Options)
	}
	if options != 8 {
		t.Fatalf("options = %d, want 8", options)
	}
	if len(out.Topology.Nodes) < 2 || len(out.Topology.Nodes[1].Display.AttachedPolicies) != 1 {
		t.Fatalf("WAF attachment was not preserved: %#v", out.Topology)
	}
}

func heroLab() Lab {
	return Lab{
		ID: "aws-waf-alb-ecs-001", Title: "AWS WAF, ALB, and ECS", Description: "Hero lab contract fixture", Difficulty: DifficultyBeginner, Mode: LabModeExplore, Provider: ProviderAWS, DomainID: "security", SubcategoryIDs: []SubcategoryID{"waf"}, EngineID: "http_path", Mission: "Observe the request path.", LearningObjectives: []string{"Identify the response origin."},
		Topology: Topology{Nodes: []TopologyNode{
			{ID: "client", Type: "client", Display: NodeDisplay{Label: "Client", ServiceName: "Browser", Simplification: "One request."}},
			{ID: "alb", Type: "lb", Display: NodeDisplay{Label: "ALB", ServiceName: "Application Load Balancer", Simplification: "One listener.", AttachedPolicies: []AttachedPolicy{{Label: "AWS WAF Web ACL attached", NodeID: "waf_policy", DisplayOnly: true}}}},
		}, Edges: []TopologyEdge{{From: "client", To: "alb"}}},
		Controls: []Control{
			{ID: "waf_action", Label: "WAF action", Type: ControlTypeSingleChoice, Options: []Option{{ID: "allow", Label: "ALLOW"}, {ID: "block", Label: "BLOCK"}}, DefaultOptionID: "allow", Required: true},
			{ID: "target_group_state", Label: "Target group state", Type: ControlTypeSingleChoice, Options: []Option{{ID: "ready", Label: "Ready"}, {ID: "unavailable", Label: "Unavailable"}, {ID: "draining", Label: "Draining"}}, DefaultOptionID: "ready", Required: true},
			{ID: "application_outcome", Label: "Application outcome", Type: ControlTypeSingleChoice, Options: []Option{{ID: "ok_200", Label: "200 OK"}, {ID: "not_found_404", Label: "404 Not Found"}, {ID: "error_500", Label: "500 Error"}}, DefaultOptionID: "ok_200", Required: true},
		},
		Tests:           []TestDefinition{{ID: "client_to_application", Label: "Client to application"}},
		Simplifications: []Simplification{{ID: "single_request", Title: "Single request", Description: "The lab evaluates one virtual request."}},
	}
}
