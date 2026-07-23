package labrun

import (
	"errors"
	"os"
	"path/filepath"
	"testing"

	"golab/internal/http_path"
	"golab/internal/lab"
	"golab/internal/labcatalog"
)

func TestHeroLabPatterns(t *testing.T) {
	service := heroService(t)
	tests := []struct {
		name       string
		selections lab.SelectionSet
		status     int
		origin     lab.NodeID
		outcome    lab.OutcomeKind
		reason     string
		failure    bool
		kinds      []lab.EventKind
		nodes      []lab.NodeID
		states     []lab.NodeStateKind
	}{
		{"200", selects("allow", "ready", "ok_200"), 200, "ecs_app", lab.OutcomeKindApplicationResponse, "application_response", false, []lab.EventKind{"request_sent", "request_forwarded", "request_forwarded", "request_forwarded", "processing_started", "response_returned", "response_forwarded", "response_forwarded", "response_forwarded", "response_received"}, []lab.NodeID{"client", "waf_policy", "alb", "alb", "ecs_app", "ecs_app", "alb", "alb", "waf_policy", "client"}, []lab.NodeStateKind{"completed", "completed", "generated_response"}},
		{"403", selects("block", "ready", "ok_200"), 403, "waf_policy", lab.OutcomeKindBlocked, "waf_rule_block", true, []lab.EventKind{"request_sent", "blocked", "response_received"}, []lab.NodeID{"client", "waf_policy", "client"}, []lab.NodeStateKind{"completed", "not_reached", "not_reached"}},
		{"503", selects("allow", "empty_or_unused", "ok_200"), 503, "alb", lab.OutcomeKindUnavailable, "no_available_targets", true, []lab.EventKind{"request_sent", "request_forwarded", "no_healthy_target", "response_received"}, []lab.NodeID{"client", "waf_policy", "alb", "client"}, []lab.NodeStateKind{"completed", "generated_response", "not_reached"}},
		{"404", selects("allow", "ready", "not_found_404"), 404, "ecs_app", lab.OutcomeKindApplicationResponse, "application_response", false, []lab.EventKind{"request_sent", "request_forwarded", "request_forwarded", "request_forwarded", "processing_started", "response_returned", "response_forwarded", "response_forwarded", "response_forwarded", "response_received"}, []lab.NodeID{"client", "waf_policy", "alb", "alb", "ecs_app", "ecs_app", "alb", "alb", "waf_policy", "client"}, []lab.NodeStateKind{"completed", "completed", "generated_response"}},
		{"500", selects("allow", "ready", "error_500"), 500, "ecs_app", lab.OutcomeKindApplicationResponse, "application_response", false, []lab.EventKind{"request_sent", "request_forwarded", "request_forwarded", "request_forwarded", "processing_started", "response_returned", "response_forwarded", "response_forwarded", "response_forwarded", "response_received"}, []lab.NodeID{"client", "waf_policy", "alb", "alb", "ecs_app", "ecs_app", "alb", "alb", "waf_policy", "client"}, []lab.NodeStateKind{"completed", "completed", "generated_response"}},
		{"502", selects("allow", "ready", "malformed_response"), 502, "alb", lab.OutcomeKindInvalidUpstreamResponse, "invalid_upstream_response", true, []lab.EventKind{"request_sent", "request_forwarded", "request_forwarded", "request_forwarded", "processing_started", "malformed_response_sent", "malformed_received", "response_received"}, []lab.NodeID{"client", "waf_policy", "alb", "alb", "ecs_app", "ecs_app", "alb", "client"}, []lab.NodeStateKind{"completed", "generated_response", "completed"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			first, err := service.Run("aws-waf-alb-ecs-001", test.selections, "client_to_application")
			if err != nil {
				t.Fatal(err)
			}
			if *first.HTTPStatus != test.status || *first.ResponseOriginNodeID != test.origin || first.OutcomeKind != test.outcome || first.TerminationReason != test.reason || (first.FailurePoint != nil) != test.failure {
				t.Fatalf("result=%#v", first)
			}
			if len(first.Events) != len(test.kinds) || len(first.NodeStates) != 3 {
				t.Fatalf("events/states=%#v/%#v", first.Events, first.NodeStates)
			}
			for i, event := range first.Events {
				if event.Kind != test.kinds[i] || event.NodeID != test.nodes[i] || event.NodeID == "reverse_proxy" {
					t.Fatalf("event[%d]=%#v", i, event)
				}
			}
			for i, state := range first.NodeStates {
				if state.State != test.states[i] || state.NodeID == "reverse_proxy" {
					t.Fatalf("state[%d]=%#v", i, state)
				}
			}
			second, err := service.Run("aws-waf-alb-ecs-001", test.selections, "client_to_application")
			if err != nil || *first.HTTPStatus != *second.HTTPStatus || first.OutcomeKind != second.OutcomeKind || first.ElapsedMS != second.ElapsedMS || len(first.Events) != len(second.Events) {
				t.Fatalf("non deterministic: %v", err)
			}
		})
	}
}

func TestAllSelectionCombinationsProjectToOneTerminal(t *testing.T) {
	service := heroService(t)
	for _, waf := range []lab.OptionID{"allow", "block"} {
		for _, targets := range []lab.OptionID{"ready", "empty_or_unused"} {
			for _, application := range []lab.OptionID{"ok_200", "not_found_404", "error_500", "malformed_response"} {
				selections := selects(waf, targets, application)
				before := selects(waf, targets, application)
				result, err := service.Run("aws-waf-alb-ecs-001", selections, "client_to_application")
				if err != nil {
					t.Fatalf("%v/%v/%v: %v", waf, targets, application, err)
				}
				if selections["waf_action"] != before["waf_action"] || selections["target_group_state"] != before["target_group_state"] || selections["application_outcome"] != before["application_outcome"] {
					t.Fatal("selection mutated")
				}
				if waf == "block" && (*result.HTTPStatus != 403 || result.OutcomeKind != lab.OutcomeKindBlocked) {
					t.Fatalf("WAF projection=%#v", result)
				}
				if waf == "allow" && targets == "empty_or_unused" && (*result.HTTPStatus != 503 || result.OutcomeKind != lab.OutcomeKindUnavailable) {
					t.Fatalf("target projection=%#v", result)
				}
				if waf == "allow" && targets == "ready" {
					want := map[lab.OptionID]int{"ok_200": 200, "not_found_404": 404, "error_500": 500, "malformed_response": 502}[application]
					if *result.HTTPStatus != want {
						t.Fatalf("application projection=%#v", result)
					}
				}
				for _, state := range result.NodeStates {
					if !state.State.Valid() || state.NodeID == "reverse_proxy" || state.NodeID == "waf_policy" {
						t.Fatalf("public state=%#v", state)
					}
				}
				for _, event := range result.Events {
					if !event.Kind.Valid() || event.NodeID == "reverse_proxy" || event.NodeID == "" {
						t.Fatalf("public event=%#v", event)
					}
				}
			}
		}
	}
}
func TestPriorityAndInvalidSelections(t *testing.T) {
	service := heroService(t)
	result, err := service.Run("aws-waf-alb-ecs-001", selects("block", "empty_or_unused", "malformed_response"), "client_to_application")
	if err != nil || *result.HTTPStatus != 403 {
		t.Fatalf("priority=%#v err=%v", result, err)
	}
	cases := []struct {
		s    lab.SelectionSet
		test lab.TestID
		want error
	}{{selects("allow", "ready", "ok_200"), "unknown", http_path.ErrUnknownTest}, {lab.SelectionSet{"waf_action": "allow"}, "client_to_application", http_path.ErrMissingSelection}, {lab.SelectionSet{"waf_action": "allow", "target_group_state": "ready", "application_outcome": "block"}, "client_to_application", http_path.ErrInvalidSelection}, {lab.SelectionSet{"extra": "x", "waf_action": "allow", "target_group_state": "ready", "application_outcome": "ok_200"}, "client_to_application", http_path.ErrInvalidSelection}}
	for _, item := range cases {
		_, err := service.Run("aws-waf-alb-ecs-001", item.s, item.test)
		if !errors.Is(err, item.want) {
			t.Fatalf("error=%v want=%v", err, item.want)
		}
	}
}
func heroService(t *testing.T) *Service {
	t.Helper()
	file, err := os.Open(filepath.Join("..", "..", "labs", "aws-waf-alb-ecs-001.json"))
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	item, err := labcatalog.Decode(file)
	if err != nil {
		t.Fatal(err)
	}
	catalog, err := labcatalog.New([]lab.Lab{item})
	if err != nil {
		t.Fatal(err)
	}
	service, err := New(catalog, []http_path.Definition{http_path.HeroDefinition()})
	if err != nil {
		t.Fatal(err)
	}
	return service
}
func selects(waf, target, application lab.OptionID) lab.SelectionSet {
	return lab.SelectionSet{"waf_action": waf, "target_group_state": target, "application_outcome": application}
}
