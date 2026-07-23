package server_test

import (
	"bytes"
	"encoding/json"
	"golab/internal/server"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
)

func v2Request(s *server.Server, method, path, body, contentType string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if contentType != "" {
		r.Header.Set("Content-Type", contentType)
	}
	w := httptest.NewRecorder()
	s.ServeHTTP(w, r)
	return w
}
func TestV2API(t *testing.T) {
	s := newTestServer(t)
	w := v2Request(s, "GET", "/api/v2/scenarios", "", " ")
	if w.Code != 200 || !strings.Contains(w.Header().Get("Content-Type"), "application/json") || strings.Contains(w.Body.String(), "explanation") {
		t.Fatalf("list %d %s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "core-proxy-timeout-001") {
		t.Fatal("missing scenario")
	}
	w = v2Request(s, "GET", "/api/v2/scenarios/core-proxy-timeout-001", "", "")
	if w.Code != 200 || !strings.Contains(w.Body.String(), `"settings":{"timeout_ms":5000}`) || strings.Contains(w.Body.String(), "reverse_proxy_settings") || strings.Contains(w.Body.String(), "explanation") {
		t.Fatalf("detail %d %s", w.Code, w.Body.String())
	}
	if v2Request(s, "GET", "/api/v2/scenarios/nope", "", "").Code != 404 {
		t.Fatal("missing detail")
	}
	correct := v2Request(s, "POST", "/api/v2/simulate", `{"scenario_id":"core-proxy-timeout-001","predicted_response_origin_node_id":"proxy"}`, "application/json")
	if correct.Code != 200 || !strings.Contains(correct.Body.String(), `"correct":true`) || !strings.Contains(correct.Body.String(), "reverse_proxy_timeout") || strings.Contains(correct.Body.String(), `"kind":"response_sent"`) {
		t.Fatalf("correct %d %s", correct.Code, correct.Body.String())
	}
	wrong := v2Request(s, "POST", "/api/v2/simulate", `{"scenario_id":"core-proxy-timeout-001","predicted_response_origin_node_id":"app"}`, "application/json")
	if wrong.Code != 200 || !strings.Contains(wrong.Body.String(), `"correct":false`) || !strings.Contains(wrong.Body.String(), "wrong_explanation") {
		t.Fatalf("wrong %s", wrong.Body.String())
	}
	for _, tt := range []struct {
		body, ct string
		code     int
	}{{"{", "application/json", 400}, {`{"scenario_id":"core-proxy-timeout-001","predicted_response_origin_node_id":"proxy","x":1}`, "application/json", 400}, {`{"scenario_id":"core-proxy-timeout-001","predicted_response_origin_node_id":"proxy"} {}`, "application/json", 400}, {`{"scenario_id":"","predicted_response_origin_node_id":"proxy"}`, "application/json", 400}, {`{"scenario_id":"nope","predicted_response_origin_node_id":"proxy"}`, "application/json", 404}, {`{"scenario_id":"core-proxy-timeout-001","predicted_response_origin_node_id":"nope"}`, "application/json", 422}, {`{}`, "text/plain", 415}} {
		if got := v2Request(s, "POST", "/api/v2/simulate", tt.body, tt.ct).Code; got != tt.code {
			t.Fatalf("got %d want %d", got, tt.code)
		}
	}
	if v2Request(s, "GET", "/api/v2/simulate", "", "").Code != 405 {
		t.Fatal("method")
	}
	big := bytes.Repeat([]byte("x"), 65537)
	r := httptest.NewRequest("POST", "/api/v2/simulate", bytes.NewReader(big))
	r.Header.Set("Content-Type", "application/json")
	w = httptest.NewRecorder()
	s.ServeHTTP(w, r)
	if w.Code != 413 {
		t.Fatalf("large=%d", w.Code)
	}
}
func TestV2Static(t *testing.T) {
	s := newTestServer(t)
	for _, path := range []string{"/v2/", "/v2/style.css", "/v2/app.js"} {
		w := v2Request(s, "GET", path, "", "")
		if w.Code != 200 {
			t.Fatalf("%s=%d", path, w.Code)
		}
	}
	w := v2Request(s, "GET", "/v2/", "", "")
	if !strings.Contains(w.Body.String(), "scenario-list") || !strings.Contains(w.Body.String(), "/v2/app.js") {
		t.Fatal("v2 html")
	}
	w = v2Request(s, "GET", "/v2/app.js", "", "")
	if !strings.Contains(w.Body.String(), "/api/v2/scenarios") || strings.Contains(w.Body.String(), "'/api/scenarios'") {
		t.Fatal("v2 js endpoints")
	}
}

func TestV2RuntimeScenarios(t *testing.T) {
	s := newTestServer(t)
	wants := []struct {
		id, prediction, category, rule string
		status                         int
		event                          string
		state                          string
	}{{"core-application-404-001", "app", "error", "application_http_status", 404, "response_returned", "generated_response"}, {"core-lb-no-healthy-target-001", "lb", "availability", "lb_no_healthy_target", 503, "no_healthy_target", "not_reached"}, {"core-malformed-upstream-001", "proxy", "error", "malformed_upstream_response", 502, "malformed_received", "generated_response"}, {"core-proxy-timeout-001", "proxy", "timeout", "reverse_proxy_timeout", 504, "timeout_fired", "processing_at_termination"}, {"core-waf-block-001", "waf", "security", "waf_block", 403, "blocked", "not_reached"}}
	w := v2Request(s, "GET", "/api/v2/scenarios", "", "")
	if w.Code != 200 || !strings.Contains(w.Body.String(), "core-application-404-001") || !strings.Contains(w.Body.String(), "core-lb-no-healthy-target-001") || !strings.Contains(w.Body.String(), "core-malformed-upstream-001") || !strings.Contains(w.Body.String(), "core-proxy-timeout-001") || !strings.Contains(w.Body.String(), "core-waf-block-001") {
		t.Fatalf("list=%s", w.Body.String())
	}
	if strings.Index(w.Body.String(), "core-application-404-001") > strings.Index(w.Body.String(), "core-lb-no-healthy-target-001") {
		t.Fatal("list is not ID-sorted")
	}
	var list struct {
		Scenarios []struct {
			ID       string `json:"id"`
			Category string `json:"category"`
		} `json:"scenarios"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &list); err != nil {
		t.Fatal(err)
	}
	if len(list.Scenarios) != len(wants) {
		t.Fatalf("list count = %d", len(list.Scenarios))
	}
	for i, want := range wants {
		if list.Scenarios[i].ID != want.id || list.Scenarios[i].Category != want.category {
			t.Fatalf("list[%d] = %#v, want %s/%s", i, list.Scenarios[i], want.id, want.category)
		}
	}
	for _, want := range wants {
		t.Run(want.id, func(t *testing.T) {
			detail := v2Request(s, "GET", "/api/v2/scenarios/"+want.id, "", "")
			if detail.Code != 200 || strings.Contains(detail.Body.String(), "explanation") || !strings.Contains(detail.Body.String(), `"category":"`+want.category+`"`) {
				t.Fatalf("detail=%s", detail.Body.String())
			}
			body := `{"scenario_id":"` + want.id + `","predicted_response_origin_node_id":"` + want.prediction + `"}`
			result := v2Request(s, "POST", "/api/v2/simulate", body, "application/json")
			if result.Code != 200 || !strings.Contains(result.Body.String(), `"correct":true`) || !strings.Contains(result.Body.String(), want.rule) || !strings.Contains(result.Body.String(), `"http_status":`+strconv.Itoa(want.status)) || !strings.Contains(result.Body.String(), want.event) || !strings.Contains(result.Body.String(), want.state) || strings.Contains(result.Body.String(), `"kind":"response_sent"`) {
				t.Fatalf("result=%s", result.Body.String())
			}
			wrong := v2Request(s, "POST", "/api/v2/simulate", `{"scenario_id":"`+want.id+`","predicted_response_origin_node_id":"client"}`, "application/json")
			if wrong.Code != 200 || !strings.Contains(wrong.Body.String(), `"correct":false`) || !strings.Contains(wrong.Body.String(), "wrong_explanation") {
				t.Fatalf("wrong=%s", wrong.Body.String())
			}
		})
	}
}
