package v2

import (
	"fmt"

	scenario "golab/internal/scenario/v2"
	validate "golab/internal/validate/v2"
)

// ForwardDelayMS and ResponseForwardDelayMS are GoLab teaching-time constants,
// not measurements or cloud-service defaults.
const (
	ForwardDelayMS           = 50
	ResponseForwardDelayMS   = 10
	LoadBalancerCheckDelayMS = 32
	WAFInspectionDelayMS     = 16
	MalformedReceiveDelayMS  = 10
)

// Simulate calculates one deterministic result without external I/O or waits.
func Simulate(core *scenario.CoreScenario) (Result, error) {
	if core == nil {
		return Result{}, fmt.Errorf("simulate core: core is nil")
	}
	validation := validate.ValidateCore(core)
	if !validation.Valid() {
		return Result{}, &ValidationError{Issues: append([]validate.Issue(nil), validation.Issues...)}
	}
	app := applicationNode(core)
	if app == nil || app.Application == nil {
		return Result{}, fmt.Errorf("simulate core: application settings are unavailable")
	}
	path, err := requestPath(core)
	if err != nil {
		return Result{}, fmt.Errorf("build request path: %w", err)
	}
	if waf := firstNode(path, scenario.NodeTypeWAF); waf != nil && waf.WAF != nil && waf.WAF.Block {
		return simulateWAFBlock(core, path, waf)
	}
	if lb := firstNode(path, scenario.NodeTypeLB); lb != nil && lb.LoadBalancer != nil && lb.LoadBalancer.HealthyTargets == 0 {
		return simulateNoHealthyTarget(core, path, lb)
	}
	if proxy := firstNode(path, scenario.NodeTypeProxy); proxy != nil && proxy.ReverseProxy != nil && proxy.ReverseProxy.TimeoutMS < app.Application.ResponseDelayMS {
		return simulateTimeout(core, path, proxy, app)
	}
	if app.Application.ResponseType == scenario.ResponseTypeMalformed {
		proxy := firstNode(path, scenario.NodeTypeProxy)
		if proxy == nil || proxy.ReverseProxy == nil {
			return Result{}, fmt.Errorf("simulate malformed_upstream_response: reverse proxy settings are unavailable")
		}
		return simulateMalformedUpstream(core, path, proxy, app)
	}
	return simulateApplicationStatus(core, path, app)
}

func simulateWAFBlock(core *scenario.CoreScenario, path []scenario.Node, waf *scenario.Node) (Result, error) {
	events := []Event{event(0, path[0].ID, EventRequestSent, "client.request_sent", nil)}
	time := WAFInspectionDelayMS
	events = append(events,
		event(time, waf.ID, EventBlocked, "waf.blocked", map[string]int{"http_status": 403}),
		event(time, path[0].ID, EventResponseReceived, "client.response_received", map[string]int{"http_status": 403}),
	)
	return result(core, path, waf.ID, 403, OutcomeError, RuleWAFBlock, events, stateForTerminal(core, path, waf.ID, false)), nil
}

func simulateNoHealthyTarget(core *scenario.CoreScenario, path []scenario.Node, lb *scenario.Node) (Result, error) {
	events := []Event{event(0, path[0].ID, EventRequestSent, "client.request_sent", nil)}
	time := 0
	for _, node := range path[1:] {
		if node.ID == lb.ID {
			time += LoadBalancerCheckDelayMS
			break
		}
		time += ForwardDelayMS
		events = append(events, event(time, node.ID, EventRequestForwarded, string(node.Type)+".request_forwarded", nil))
	}
	events = append(events, event(time, lb.ID, EventNoHealthyTarget, "lb.no_healthy_target", map[string]int{"healthy_targets": 0, "http_status": 503}), event(time, path[0].ID, EventResponseReceived, "client.response_received", map[string]int{"http_status": 503}))
	return result(core, path, lb.ID, 503, OutcomeError, RuleLBNoHealthyTarget, events, stateForTerminal(core, path, lb.ID, false)), nil
}

func simulateTimeout(core *scenario.CoreScenario, path []scenario.Node, proxy, app *scenario.Node) (Result, error) {
	events := []Event{event(0, path[0].ID, EventRequestSent, "client.request_sent", nil)}
	time := 0
	for _, node := range path[1:] {
		if node.ID == app.ID {
			events = append(events, event(time, node.ID, EventProcessingStarted, "app.processing_started", map[string]int{"response_delay_ms": app.Application.ResponseDelayMS}))
			break
		}
		time += ForwardDelayMS
		events = append(events, event(time, node.ID, EventRequestForwarded, string(node.Type)+".request_forwarded", nil))
	}
	time += proxy.ReverseProxy.TimeoutMS
	details := map[string]int{"timeout_ms": proxy.ReverseProxy.TimeoutMS, "application_delay_ms": app.Application.ResponseDelayMS, "http_status": 504}
	events = append(events, event(time, proxy.ID, EventTimeoutFired, "proxy.timeout_fired", details), event(time, path[0].ID, EventResponseReceived, "client.response_received", map[string]int{"http_status": 504}))
	return result(core, path, proxy.ID, 504, OutcomeError, RuleReverseProxyTimeout, events, stateForTerminal(core, path, proxy.ID, true)), nil
}

func simulateMalformedUpstream(core *scenario.CoreScenario, path []scenario.Node, proxy, app *scenario.Node) (Result, error) {
	events := []Event{event(0, path[0].ID, EventRequestSent, "client.request_sent", nil)}
	time := 0
	for _, node := range path[1:] {
		if node.ID == app.ID {
			events = append(events, event(time, node.ID, EventProcessingStarted, "app.processing_started", map[string]int{"response_delay_ms": app.Application.ResponseDelayMS}))
			break
		}
		time += ForwardDelayMS
		events = append(events, event(time, node.ID, EventRequestForwarded, string(node.Type)+".request_forwarded", nil))
	}
	time += app.Application.ResponseDelayMS
	events = append(events, event(time, app.ID, EventMalformedSent, "app.malformed_response_sent", nil))
	time += MalformedReceiveDelayMS
	events = append(events,
		event(time, proxy.ID, EventMalformedReceived, "proxy.malformed_received", map[string]int{"http_status": 502}),
		event(time, path[0].ID, EventResponseReceived, "client.response_received", map[string]int{"http_status": 502}),
	)
	return result(core, path, proxy.ID, 502, OutcomeError, RuleMalformedUpstream, events, stateForMalformed(core, path, proxy.ID)), nil
}

func simulateApplicationStatus(core *scenario.CoreScenario, path []scenario.Node, app *scenario.Node) (Result, error) {
	if app.Application.StatusCode == nil {
		return Result{}, fmt.Errorf("simulate application_http_status: status_code is unavailable")
	}
	events := []Event{event(0, path[0].ID, EventRequestSent, "client.request_sent", nil)}
	time := 0
	for _, node := range path[1:] {
		if node.ID == app.ID {
			events = append(events, event(time, node.ID, EventProcessingStarted, "app.processing_started", map[string]int{"response_delay_ms": app.Application.ResponseDelayMS}))
			break
		}
		time += ForwardDelayMS
		events = append(events, event(time, node.ID, EventRequestForwarded, string(node.Type)+".request_forwarded", nil))
	}
	time += app.Application.ResponseDelayMS
	status := *app.Application.StatusCode
	events = append(events, event(time, app.ID, EventResponseReturned, "app.response_returned", map[string]int{"response_delay_ms": app.Application.ResponseDelayMS, "http_status": status}))
	for i := len(path) - 2; i >= 1; i-- {
		time += ResponseForwardDelayMS
		node := path[i]
		events = append(events, event(time, node.ID, EventResponseForwarded, string(node.Type)+".response_forwarded", map[string]int{"http_status": status}))
	}
	time += ResponseForwardDelayMS
	events = append(events, event(time, path[0].ID, EventResponseReceived, "client.response_received", map[string]int{"http_status": status}))
	outcome := OutcomeSuccess
	if status >= 400 {
		outcome = OutcomeError
	}
	return result(core, path, app.ID, status, outcome, RuleApplicationHTTPStatus, events, stateForTerminal(core, path, app.ID, false)), nil
}

func result(core *scenario.CoreScenario, path []scenario.Node, origin string, status int, outcome Outcome, rule RuleID, events []Event, states []NodeState) Result {
	return Result{ResponseOriginNodeID: origin, HTTPStatus: status, Outcome: outcome, RuleID: rule, Events: events, NodeStates: states, ElapsedMS: events[len(events)-1].AtMS}
}
func event(at int, nodeID string, kind EventKind, key string, details map[string]int) Event {
	if details == nil {
		details = map[string]int{}
	}
	return Event{AtMS: at, NodeID: nodeID, Kind: kind, MessageKey: key, Details: details}
}
func applicationNode(core *scenario.CoreScenario) *scenario.Node {
	return firstNode(core.Topology.Nodes, scenario.NodeTypeApp)
}
func firstNode(nodes []scenario.Node, nodeType scenario.NodeType) *scenario.Node {
	for i := range nodes {
		if nodes[i].Type == nodeType {
			return &nodes[i]
		}
	}
	return nil
}
func requestPath(core *scenario.CoreScenario) ([]scenario.Node, error) {
	client := firstNode(core.Topology.Nodes, scenario.NodeTypeClient)
	if client == nil {
		return nil, fmt.Errorf("client node is unavailable")
	}
	path := []scenario.Node{*client}
	current := client.ID
	for current != "" {
		var next string
		for _, edge := range core.Topology.Edges {
			if edge.From == current {
				next = edge.To
				break
			}
		}
		if next == "" {
			break
		}
		node := nodeByID(core.Topology.Nodes, next)
		if node == nil {
			return nil, fmt.Errorf("node %q is unavailable", next)
		}
		path = append(path, *node)
		current = next
		if node.Type == scenario.NodeTypeApp {
			break
		}
	}
	if len(path) == 0 || path[len(path)-1].Type != scenario.NodeTypeApp {
		return nil, fmt.Errorf("path does not terminate at application")
	}
	return path, nil
}

func stateForMalformed(core *scenario.CoreScenario, path []scenario.Node, proxyID string) []NodeState {
	positions := make(map[string]int, len(path))
	for index, node := range path {
		positions[node.ID] = index
	}
	proxyPosition := positions[proxyID]
	states := make([]NodeState, 0, len(core.Topology.Nodes))
	for _, node := range core.Topology.Nodes {
		position, reached := positions[node.ID]
		state := NodeStateNotReached
		if reached && position < proxyPosition {
			state = NodeStateCompleted
		}
		if node.ID == proxyID {
			state = NodeStateGeneratedResponse
		}
		if reached && position > proxyPosition && node.Type == scenario.NodeTypeApp {
			state = NodeStateCompleted
		}
		states = append(states, NodeState{NodeID: node.ID, State: state})
	}
	return states
}
func nodeByID(nodes []scenario.Node, id string) *scenario.Node {
	for i := range nodes {
		if nodes[i].ID == id {
			return &nodes[i]
		}
	}
	return nil
}
func stateForTerminal(core *scenario.CoreScenario, path []scenario.Node, origin string, timeout bool) []NodeState {
	positions := make(map[string]int, len(path))
	for index, node := range path {
		positions[node.ID] = index
	}
	originPosition := positions[origin]
	states := make([]NodeState, 0, len(core.Topology.Nodes))
	for _, node := range core.Topology.Nodes {
		position, reached := positions[node.ID]
		state := NodeStateNotReached
		if reached && position < originPosition {
			state = NodeStateCompleted
		}
		if node.ID == origin {
			state = NodeStateGeneratedResponse
		}
		if timeout && reached && position > originPosition && node.Type == scenario.NodeTypeApp {
			state = NodeStateProcessingAtTermination
		}
		states = append(states, NodeState{NodeID: node.ID, State: state})
	}
	return states
}
