package server_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"golab/internal/lab"
	"golab/internal/server"
)

const heroLabID = "aws-waf-alb-ecs-001"

func labRequest(s *server.Server, method, path, body, contentType string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if contentType != "" {
		r.Header.Set("Content-Type", contentType)
	}
	w := httptest.NewRecorder()
	s.ServeHTTP(w, r)
	return w
}

func runBody(waf, targets, application string) string {
	return `{"test_id":"client_to_application","selections":{"waf_action":"` + waf + `","target_group_state":"` + targets + `","application_outcome":"` + application + `"}}`
}

func decodeRun(t *testing.T, w *httptest.ResponseRecorder) lab.RunResult {
	t.Helper()
	var result lab.RunResult
	if err := json.Unmarshal(w.Body.Bytes(), &result); err != nil {
		t.Fatalf("decode run result: %v: %s", err, w.Body.String())
	}
	if result.HTTPStatus == nil || result.ResponseOriginNodeID == nil {
		t.Fatalf("missing public result fields: %s", w.Body.String())
	}
	if len(result.NodeStates) != 3 {
		t.Fatalf("node states = %d", len(result.NodeStates))
	}
	for _, state := range result.NodeStates {
		if !state.State.Valid() || state.NodeID == "reverse_proxy" || state.NodeID == "waf_policy" {
			t.Fatalf("invalid public node state: %#v", state)
		}
	}
	if len(result.Events) == 0 {
		t.Fatal("missing event timeline")
	}
	previousAtMS := -1
	for _, event := range result.Events {
		if !event.Kind.Valid() || event.NodeID == "" || event.NodeID == "reverse_proxy" || event.AtMS < previousAtMS {
			t.Fatalf("invalid public event: %#v", event)
		}
		previousAtMS = event.AtMS
	}
	if strings.Contains(w.Body.String(), "reverse_proxy") || strings.Contains(w.Body.String(), "compiled_input") || strings.Contains(w.Body.String(), "effect_id") || strings.Contains(w.Body.String(), "correct") {
		t.Fatalf("internal data leaked: %s", w.Body.String())
	}
	return result
}

func TestLabAPIListAndDetail(t *testing.T) {
	s := newTestServer(t)
	list := labRequest(s, http.MethodGet, "/api/v2/labs", "", "")
	if list.Code != http.StatusOK || !strings.Contains(list.Header().Get("Content-Type"), "application/json") || list.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("list response: %d %s", list.Code, list.Body.String())
	}
	if !strings.Contains(list.Body.String(), heroLabID) || strings.Contains(list.Body.String(), "controls") || strings.Contains(list.Body.String(), "effects") {
		t.Fatalf("list public contract: %s", list.Body.String())
	}
	detail := labRequest(s, http.MethodGet, "/api/v2/labs/"+heroLabID, "", "")
	if detail.Code != http.StatusOK || strings.Contains(detail.Body.String(), "healthy_targets") || strings.Contains(detail.Body.String(), "reverse_proxy_timeout") || strings.Contains(detail.Body.String(), "effect_id") {
		t.Fatalf("detail response: %d %s", detail.Code, detail.Body.String())
	}
	var definition lab.Lab
	if err := json.Unmarshal(detail.Body.Bytes(), &definition); err != nil {
		t.Fatal(err)
	}
	if len(definition.Topology.Nodes) != 3 || len(definition.Controls) != 3 || len(definition.Tests) != 1 || len(definition.Simplifications) == 0 {
		t.Fatalf("unexpected public lab: %#v", definition)
	}
	options := 0
	for _, control := range definition.Controls {
		options += len(control.Options)
	}
	if options != 8 || definition.Topology.Nodes[1].ID != "alb" || len(definition.Topology.Nodes[1].Display.AttachedPolicies) != 1 {
		t.Fatalf("topology/control contract violated")
	}
	missing := labRequest(s, http.MethodGet, "/api/v2/labs/unknown-lab", "", "")
	if missing.Code != http.StatusNotFound || !strings.Contains(missing.Body.String(), "lab_not_found") {
		t.Fatalf("missing: %d %s", missing.Code, missing.Body.String())
	}
}

func TestLabLearningAPI(t *testing.T) {
	s := newTestServer(t)
	w := labRequest(s, http.MethodGet, "/api/v2/labs/"+heroLabID+"/learning", "", "")
	if w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Content-Type"), "application/json") || !strings.Contains(w.Body.String(), `"schema_version":1`) || !strings.Contains(w.Body.String(), `"stage_id":"stage-1"`) {
		t.Fatalf("learning response: %d %s", w.Code, w.Body.String())
	}
	if strings.Contains(w.Body.String(), "compiled_input") || strings.Contains(w.Body.String(), "effect_id") || strings.Contains(w.Body.String(), "reverse_proxy") {
		t.Fatalf("internal data leaked: %s", w.Body.String())
	}
	missing := labRequest(s, http.MethodGet, "/api/v2/labs/unknown-lab/learning", "", "")
	if missing.Code != http.StatusNotFound || !strings.Contains(missing.Body.String(), "lab_not_found") {
		t.Fatalf("missing learning: %d %s", missing.Code, missing.Body.String())
	}
	method := labRequest(s, http.MethodPost, "/api/v2/labs/"+heroLabID+"/learning", "", "")
	if method.Code != http.StatusMethodNotAllowed || method.Header().Get("Allow") != http.MethodGet {
		t.Fatalf("method: %d %s", method.Code, method.Body.String())
	}
}

func TestLabAPIRunSixAndAllSelections(t *testing.T) {
	s := newTestServer(t)
	cases := []struct {
		waf, targets, application string
		status                    int
		origin                    lab.NodeID
		outcome                   lab.OutcomeKind
		failure                   bool
	}{
		{"allow", "ready", "ok_200", 200, "ecs_app", lab.OutcomeKindApplicationResponse, false},
		{"block", "ready", "ok_200", 403, "waf_policy", lab.OutcomeKindBlocked, true},
		{"allow", "empty_or_unused", "ok_200", 503, "alb", lab.OutcomeKindUnavailable, true},
		{"allow", "ready", "not_found_404", 404, "ecs_app", lab.OutcomeKindApplicationResponse, false},
		{"allow", "ready", "error_500", 500, "ecs_app", lab.OutcomeKindApplicationResponse, false},
		{"allow", "ready", "malformed_response", 502, "alb", lab.OutcomeKindInvalidUpstreamResponse, true},
	}
	for _, tc := range cases {
		t.Run(tc.waf+"/"+tc.targets+"/"+tc.application, func(t *testing.T) {
			w := labRequest(s, http.MethodPost, "/api/v2/labs/"+heroLabID+"/runs", runBody(tc.waf, tc.targets, tc.application), "application/json; charset=utf-8")
			if w.Code != http.StatusOK || w.Header().Get("Cache-Control") != "no-store" {
				t.Fatalf("run: %d %s", w.Code, w.Body.String())
			}
			result := decodeRun(t, w)
			if *result.HTTPStatus != tc.status || *result.ResponseOriginNodeID != tc.origin || result.OutcomeKind != tc.outcome || (result.FailurePoint != nil) != tc.failure {
				t.Fatalf("result: %#v", result)
			}
		})
	}
	for _, waf := range []string{"allow", "block"} {
		for _, targets := range []string{"ready", "empty_or_unused"} {
			for _, application := range []string{"ok_200", "not_found_404", "error_500", "malformed_response"} {
				first := labRequest(s, http.MethodPost, "/api/v2/labs/"+heroLabID+"/runs", runBody(waf, targets, application), "application/json")
				second := labRequest(s, http.MethodPost, "/api/v2/labs/"+heroLabID+"/runs", runBody(waf, targets, application), "application/json")
				if first.Code != http.StatusOK || second.Code != http.StatusOK || !bytes.Equal(first.Body.Bytes(), second.Body.Bytes()) {
					t.Fatalf("nondeterministic %s/%s/%s", waf, targets, application)
				}
				result := decodeRun(t, first)
				if waf == "block" && *result.HTTPStatus != 403 {
					t.Fatal("WAF precedence")
				}
				if waf == "allow" && targets == "empty_or_unused" && *result.HTTPStatus != 503 {
					t.Fatal("target precedence")
				}
			}
		}
	}
}

func TestLabAPIRequestAndRoutingErrors(t *testing.T) {
	s := newTestServer(t)
	path := "/api/v2/labs/" + heroLabID + "/runs"
	for _, body := range []string{"", "   ", "null", "{", `{} {}`, `{"unknown":true}`, `{"test_id":true,"selections":{}}`, `{"test_id":"x","selections":[]}`, `{"test_id":"x","selections":{"waf_action":null}}`} {
		if got := labRequest(s, http.MethodPost, path, body, "application/json"); got.Code != http.StatusBadRequest {
			t.Fatalf("body %q: %d", body, got.Code)
		}
	}
	for _, contentType := range []string{"", "text/plain", "application/problem+json"} {
		if got := labRequest(s, http.MethodPost, path, runBody("allow", "ready", "ok_200"), contentType); got.Code != http.StatusUnsupportedMediaType {
			t.Fatalf("content type %q: %d", contentType, got.Code)
		}
	}
	big := strings.Repeat("x", 65537)
	if got := labRequest(s, http.MethodPost, path, big, "application/json"); got.Code != http.StatusRequestEntityTooLarge || !strings.Contains(got.Body.String(), "request_body_too_large") {
		t.Fatal("size limit")
	}
	for _, body := range []string{`{"test_id":"missing","selections":{"waf_action":"allow","target_group_state":"ready","application_outcome":"ok_200"}}`, `{"test_id":"client_to_application","selections":{}}`, `{"test_id":"client_to_application","selections":{"waf_action":"ready","target_group_state":"ready","application_outcome":"ok_200"}}`, `{"test_id":"client_to_application","selections":{"unknown":"x","waf_action":"allow","target_group_state":"ready","application_outcome":"ok_200"}}`, `{"test_id":"client_to_application","selections":{"":"allow","target_group_state":"ready","application_outcome":"ok_200"}}`, `{"test_id":"client_to_application","selections":{"waf_action":"","target_group_state":"ready","application_outcome":"ok_200"}}`, `{"test_id":"client_to_application","selections":{"waf_action":"allow","target_group_state":"ready","application_outcome":"ok_200","effect_id":"x"}}`} {
		if got := labRequest(s, http.MethodPost, path, body, "application/json"); got.Code != http.StatusUnprocessableEntity {
			t.Fatalf("selection %s: %d", body, got.Code)
		}
	}
	for _, tc := range []struct{ method, path, allow string }{{http.MethodPost, "/api/v2/labs", http.MethodGet}, {http.MethodPut, "/api/v2/labs/" + heroLabID, http.MethodGet}, {http.MethodGet, path, http.MethodPost}, {http.MethodDelete, "/api/v2/labs/" + heroLabID, http.MethodGet}} {
		got := labRequest(s, tc.method, tc.path, "", "")
		if got.Code != http.StatusMethodNotAllowed || got.Header().Get("Allow") != tc.allow || !strings.Contains(got.Body.String(), "method_not_allowed") {
			t.Fatalf("route %s %s = %d %s", tc.method, tc.path, got.Code, got.Body.String())
		}
	}
	if got := labRequest(s, http.MethodGet, "/api/v2/labs/"+heroLabID+"/extra", "", ""); got.Code != http.StatusNotFound {
		t.Fatalf("extra path = %d", got.Code)
	}
}
