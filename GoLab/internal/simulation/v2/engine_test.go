package v2

import (
	"errors"
	"path/filepath"
	"reflect"
	"testing"

	scenario "golab/internal/scenario/v2"
	validate "golab/internal/validate/v2"
)

func TestSimulate_fixtureTimeoutTrace(t *testing.T) {
	path := filepath.Join("..", "..", "..", "testdata", "scenarios", "v2", "core-proxy-timeout-001.json")
	document, err := scenario.LoadFile(path)
	if err != nil {
		t.Fatalf("LoadFile() error = %v", err)
	}
	before := cloneCore(t, document.Core)
	result, err := Simulate(document.Core)
	if err != nil {
		t.Fatalf("Simulate() error = %v", err)
	}
	if !reflect.DeepEqual(document.Core, before) {
		t.Fatal("Simulate() mutated Core")
	}
	if result.RuleID != RuleReverseProxyTimeout || result.ResponseOriginNodeID != "proxy" || result.HTTPStatus != 504 || result.Outcome != OutcomeError || result.ElapsedMS != 5050 {
		t.Fatalf("result = %#v", result)
	}
	want := []Event{{0, "client", EventRequestSent, "client.request_sent", map[string]int{}}, {50, "proxy", EventRequestForwarded, "proxy.request_forwarded", map[string]int{}}, {50, "app", EventProcessingStarted, "app.processing_started", map[string]int{"response_delay_ms": 6000}}, {5050, "proxy", EventTimeoutFired, "proxy.timeout_fired", map[string]int{"timeout_ms": 5000, "application_delay_ms": 6000, "http_status": 504}}, {5050, "client", EventResponseReceived, "client.response_received", map[string]int{"http_status": 504}}}
	if !reflect.DeepEqual(result.Events, want) {
		t.Fatalf("events = %#v\nwant %#v", result.Events, want)
	}
	wantStates := []NodeState{{"client", NodeStateCompleted}, {"proxy", NodeStateGeneratedResponse}, {"app", NodeStateProcessingAtTermination}}
	if !reflect.DeepEqual(result.NodeStates, wantStates) {
		t.Fatalf("states = %#v", result.NodeStates)
	}
	assertEventInvariants(t, document.Core, result)
}

func TestSimulate_implementedRules(t *testing.T) {
	tests := []struct {
		name    string
		core    *scenario.CoreScenario
		rule    RuleID
		origin  string
		status  int
		outcome Outcome
	}{
		{"application 200", coreWithStatus(200), RuleApplicationHTTPStatus, "app", 200, OutcomeSuccess},
		{"application 404", coreWithStatus(404), RuleApplicationHTTPStatus, "app", 404, OutcomeError},
		{"application 500", coreWithStatus(500), RuleApplicationHTTPStatus, "app", 500, OutcomeError},
		{"timeout equality falls back", coreWithTiming(10, 10), RuleApplicationHTTPStatus, "app", 200, OutcomeSuccess},
		{"proxy timeout", coreWithTiming(10, 11), RuleReverseProxyTimeout, "proxy", 504, OutcomeError},
		{"lb no target", coreWithLB(0), RuleLBNoHealthyTarget, "lb", 503, OutcomeError},
		{"waf block", coreWithWAF(true), RuleWAFBlock, "waf", 403, OutcomeError},
		{"malformed upstream", coreWithMalformed(), RuleMalformedUpstream, "proxy", 502, OutcomeError},
		{"waf false", coreWithWAF(false), RuleApplicationHTTPStatus, "app", 200, OutcomeSuccess},
		{"lb healthy", coreWithLB(1), RuleApplicationHTTPStatus, "app", 200, OutcomeSuccess},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			result, err := Simulate(test.core)
			if err != nil {
				t.Fatalf("Simulate() error=%v", err)
			}
			if result.RuleID != test.rule || result.ResponseOriginNodeID != test.origin || result.HTTPStatus != test.status || result.Outcome != test.outcome {
				t.Fatalf("result=%#v", result)
			}
			assertEventInvariants(t, test.core, result)
			if test.rule == RuleReverseProxyTimeout && stateOf(result, "app") != NodeStateProcessingAtTermination {
				t.Fatalf("app state=%q", stateOf(result, "app"))
			}
			if test.rule == RuleLBNoHealthyTarget && stateOf(result, "app") != NodeStateNotReached {
				t.Fatalf("app state=%q", stateOf(result, "app"))
			}
		})
	}
}

func TestSimulate_WAFBlockAndMalformedTraces(t *testing.T) {
	tests := []struct {
		name       string
		core       *scenario.CoreScenario
		rule       RuleID
		origin     string
		status     int
		wantEvents []Event
		wantStates []NodeState
	}{
		{
			name:       "waf block",
			core:       coreWithWAF(true),
			rule:       RuleWAFBlock,
			origin:     "waf",
			status:     403,
			wantEvents: []Event{{0, "client", EventRequestSent, "client.request_sent", map[string]int{}}, {16, "waf", EventBlocked, "waf.blocked", map[string]int{"http_status": 403}}, {16, "client", EventResponseReceived, "client.response_received", map[string]int{"http_status": 403}}},
			wantStates: []NodeState{{"client", NodeStateCompleted}, {"waf", NodeStateGeneratedResponse}, {"proxy", NodeStateNotReached}, {"app", NodeStateNotReached}},
		},
		{
			name:       "malformed upstream",
			core:       coreWithMalformed(),
			rule:       RuleMalformedUpstream,
			origin:     "proxy",
			status:     502,
			wantEvents: []Event{{0, "client", EventRequestSent, "client.request_sent", map[string]int{}}, {50, "proxy", EventRequestForwarded, "proxy.request_forwarded", map[string]int{}}, {50, "app", EventProcessingStarted, "app.processing_started", map[string]int{"response_delay_ms": 10}}, {60, "app", EventMalformedSent, "app.malformed_response_sent", map[string]int{}}, {70, "proxy", EventMalformedReceived, "proxy.malformed_received", map[string]int{"http_status": 502}}, {70, "client", EventResponseReceived, "client.response_received", map[string]int{"http_status": 502}}},
			wantStates: []NodeState{{"client", NodeStateCompleted}, {"proxy", NodeStateGeneratedResponse}, {"app", NodeStateCompleted}},
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			before := cloneCore(t, test.core)
			got, err := Simulate(test.core)
			if err != nil {
				t.Fatal(err)
			}
			if got.RuleID != test.rule || got.ResponseOriginNodeID != test.origin || got.HTTPStatus != test.status || got.Outcome != OutcomeError {
				t.Fatalf("result=%#v", got)
			}
			if !reflect.DeepEqual(got.Events, test.wantEvents) || !reflect.DeepEqual(got.NodeStates, test.wantStates) {
				t.Fatalf("events=%#v states=%#v", got.Events, got.NodeStates)
			}
			if !reflect.DeepEqual(test.core, before) {
				t.Fatal("Simulate() mutated Core")
			}
			for i := 0; i < 2; i++ {
				next, err := Simulate(test.core)
				if err != nil || !reflect.DeepEqual(got, next) {
					t.Fatalf("determinism run %d: result=%#v err=%v", i, next, err)
				}
			}
			assertEventInvariants(t, test.core, got)
		})
	}
}

func TestSimulate_timeoutBoundaryAgreesWithValidator(t *testing.T) {
	tests := []struct {
		name              string
		delay             int
		rule              RuleID
		origin            string
		status            int
		validatorConflict bool
	}{
		{"application faster", 4999, RuleApplicationHTTPStatus, "app", 200, false},
		{"equal timeout", 5000, RuleApplicationHTTPStatus, "app", 200, false},
		{"application slower", 5001, RuleReverseProxyTimeout, "proxy", 504, true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			core := coreWithTiming(5000, test.delay)
			result, err := Simulate(core)
			if err != nil {
				t.Fatalf("Simulate() error = %v", err)
			}
			if result.RuleID != test.rule || result.ResponseOriginNodeID != test.origin || result.HTTPStatus != test.status {
				t.Fatalf("result = %#v", result)
			}
			if test.delay == 5000 {
				for _, event := range result.Events {
					if event.Kind == EventTimeoutFired {
						t.Fatal("equal timeout produced timeout_fired")
					}
				}
				if stateOf(result, "app") != NodeStateGeneratedResponse || stateOf(result, "proxy") == NodeStateGeneratedResponse {
					t.Fatalf("equal states = %#v", result.NodeStates)
				}
				if result.ElapsedMS != 5070 {
					t.Fatalf("equal elapsed_ms = %d, want 5070", result.ElapsedMS)
				}
			}

			probe := coreWithWAF(true)
			probe.Topology.Nodes[2].ReverseProxy.TimeoutMS = 5000
			probe.Topology.Nodes[3].Application.ResponseDelayMS = test.delay
			issues := validate.ValidateCore(probe).Issues
			conflict := false
			for _, issue := range issues {
				if issue.Code == validate.CodeRuleConflict {
					conflict = true
				}
			}
			if conflict != test.validatorConflict {
				t.Fatalf("validator conflict=%v issues=%#v", conflict, issues)
			}
		})
	}
}

func TestSimulate_responseReturnedEventContract(t *testing.T) {
	tests := []struct {
		name                string
		core                *scenario.CoreScenario
		wantReturned        bool
		wantTimeout         bool
		wantNoHealthyTarget bool
	}{
		{"application 200", coreWithStatus(200), true, false, false},
		{"application 404", coreWithStatus(404), true, false, false},
		{"application 500", coreWithStatus(500), true, false, false},
		{"timeout equality", coreWithTiming(10, 10), true, false, false},
		{"proxy timeout", coreWithTiming(10, 11), false, true, false},
		{"lb no healthy target", coreWithLB(0), false, false, true},
		{"waf block", coreWithWAF(true), false, false, false},
		{"malformed upstream", coreWithMalformed(), false, false, false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			result, err := Simulate(test.core)
			if err != nil {
				t.Fatalf("Simulate() error = %v", err)
			}
			returned := 0
			seenTimeout := false
			seenNoHealthyTarget := false
			for _, event := range result.Events {
				switch event.Kind {
				case EventResponseReturned:
					returned++
					if event.NodeID != "app" || event.MessageKey != "app.response_returned" {
						t.Fatalf("response_returned event = %#v", event)
					}
				case EventTimeoutFired:
					seenTimeout = true
				case EventNoHealthyTarget:
					seenNoHealthyTarget = true
				case EventKind("response_sent"):
					t.Fatalf("obsolete response_sent event = %#v", event)
				}
			}
			if (returned == 1) != test.wantReturned || seenTimeout != test.wantTimeout || seenNoHealthyTarget != test.wantNoHealthyTarget {
				t.Fatalf("events = %#v", result.Events)
			}
			if result.Events[len(result.Events)-1].Kind != EventResponseReceived {
				t.Fatalf("last event = %#v", result.Events[len(result.Events)-1])
			}
		})
	}
}

func TestSimulate_rejectsInvalidAndUnsupportedInput(t *testing.T) {
	tests := []struct {
		name  string
		core  *scenario.CoreScenario
		check func(error) bool
	}{
		{"nil", nil, func(err error) bool { return err != nil }},
		{"invalid core", func() *scenario.CoreScenario { c := coreWithStatus(200); c.ID = ""; return c }(), func(err error) bool {
			var target *ValidationError
			return errors.As(err, &target) && len(target.Issues) > 0
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			result, err := Simulate(test.core)
			if err == nil || !test.check(err) {
				t.Fatalf("result=%#v error=%v", result, err)
			}
		})
	}
}

func TestSimulate_isDeterministic(t *testing.T) {
	core := coreWithStatus(404)
	first, err := Simulate(core)
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 2; i++ {
		next, err := Simulate(core)
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(first, next) {
			t.Fatalf("run %d differs: %#v %#v", i, first, next)
		}
	}
}

func coreWithStatus(status int) *scenario.CoreScenario { return coreWithTimingAndStatus(10, 0, status) }
func coreWithTiming(timeout, delay int) *scenario.CoreScenario {
	return coreWithTimingAndStatus(timeout, delay, 200)
}
func coreWithTimingAndStatus(timeout, delay, status int) *scenario.CoreScenario {
	return &scenario.CoreScenario{SchemaVersion: 2, Type: scenario.DocumentTypeCore, ID: "core", Title: "Core", Description: "Description", Difficulty: scenario.DifficultyBeginner, Category: "timeout", LearningGoals: []string{"goal"}, Topology: scenario.Topology{Nodes: []scenario.Node{{ID: "client", Type: scenario.NodeTypeClient, Label: "Client"}, {ID: "proxy", Type: scenario.NodeTypeProxy, Label: "Proxy", ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: timeout}}, {ID: "app", Type: scenario.NodeTypeApp, Label: "App", Application: app(delay, status)}}, Edges: []scenario.Edge{{From: "client", To: "proxy"}, {From: "proxy", To: "app"}}}, Explanation: "Explanation", WrongExplanation: "Wrong"}
}
func coreWithLB(healthy int) *scenario.CoreScenario {
	c := coreWithStatus(200)
	c.Topology.Nodes = []scenario.Node{c.Topology.Nodes[0], {ID: "lb", Type: scenario.NodeTypeLB, Label: "LB", LoadBalancer: &scenario.LoadBalancerSettings{HealthyTargets: healthy}}, c.Topology.Nodes[1], c.Topology.Nodes[2]}
	c.Topology.Edges = []scenario.Edge{{From: "client", To: "lb"}, {From: "lb", To: "proxy"}, {From: "proxy", To: "app"}}
	return c
}
func coreWithWAF(block bool) *scenario.CoreScenario {
	c := coreWithStatus(200)
	c.Topology.Nodes = []scenario.Node{c.Topology.Nodes[0], {ID: "waf", Type: scenario.NodeTypeWAF, Label: "WAF", WAF: &scenario.WAFSettings{Block: block}}, c.Topology.Nodes[1], c.Topology.Nodes[2]}
	c.Topology.Edges = []scenario.Edge{{From: "client", To: "waf"}, {From: "waf", To: "proxy"}, {From: "proxy", To: "app"}}
	return c
}
func coreWithMalformed() *scenario.CoreScenario {
	c := coreWithTiming(5000, 10)
	c.Topology.Nodes[2].Application.ResponseType = scenario.ResponseTypeMalformed
	c.Topology.Nodes[2].Application.StatusCode = nil
	return c
}
func app(delay, status int) *scenario.ApplicationSettings {
	return &scenario.ApplicationSettings{ResponseDelayMS: delay, ResponseType: scenario.ResponseTypeNormal, StatusCode: &status}
}
func cloneCore(t *testing.T, core *scenario.CoreScenario) *scenario.CoreScenario {
	t.Helper()
	copy := *core
	copy.LearningGoals = append([]string(nil), core.LearningGoals...)
	copy.CloudConceptRefs = append([]string(nil), core.CloudConceptRefs...)
	copy.Topology.Edges = append([]scenario.Edge(nil), core.Topology.Edges...)
	copy.Topology.Nodes = append([]scenario.Node(nil), core.Topology.Nodes...)
	for i := range copy.Topology.Nodes {
		node := &copy.Topology.Nodes[i]
		if node.WAF != nil {
			value := *node.WAF
			node.WAF = &value
		}
		if node.LoadBalancer != nil {
			value := *node.LoadBalancer
			node.LoadBalancer = &value
		}
		if node.ReverseProxy != nil {
			value := *node.ReverseProxy
			node.ReverseProxy = &value
		}
		if node.Application != nil {
			value := *node.Application
			if value.StatusCode != nil {
				status := *value.StatusCode
				value.StatusCode = &status
			}
			node.Application = &value
		}
	}
	return &copy
}
func stateOf(result Result, id string) NodeStateKind {
	for _, state := range result.NodeStates {
		if state.NodeID == id {
			return state.State
		}
	}
	return ""
}
func assertEventInvariants(t *testing.T, core *scenario.CoreScenario, result Result) {
	t.Helper()
	if len(result.Events) == 0 || result.Events[0].Kind != EventRequestSent || result.Events[len(result.Events)-1].Kind != EventResponseReceived {
		t.Fatalf("event boundary=%#v", result.Events)
	}
	ids := map[string]bool{}
	for _, node := range core.Topology.Nodes {
		ids[node.ID] = true
	}
	last := -1
	for _, event := range result.Events {
		if event.AtMS < last || !ids[event.NodeID] || event.MessageKey == "" {
			t.Fatalf("invalid event=%#v", event)
		}
		last = event.AtMS
	}
	if last != result.ElapsedMS {
		t.Fatalf("elapsed=%d last=%d", result.ElapsedMS, last)
	}
	if len(result.NodeStates) != len(core.Topology.Nodes) {
		t.Fatalf("states=%#v", result.NodeStates)
	}
}
