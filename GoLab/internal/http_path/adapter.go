package http_path

import (
	"fmt"
	"golab/internal/lab"
	scenario "golab/internal/scenario/v2"
	simulation "golab/internal/simulation/v2"
)

func ToScenario(in CompiledInput) (*scenario.CoreScenario, error) {
	if in.ProxyTimeoutMS <= in.ResponseDelayMS || in.HealthyTargets < 0 || (in.ResponseType == scenario.ResponseTypeNormal && in.StatusCode == nil) || (in.ResponseType == scenario.ResponseTypeMalformed && in.StatusCode != nil) {
		return nil, fmt.Errorf("invalid compiled http path input")
	}
	return &scenario.CoreScenario{SchemaVersion: 2, Type: scenario.DocumentTypeCore, ID: "compiled-http-path", Title: "Compiled HTTP Path", Description: "Internal compiled input", Difficulty: scenario.DifficultyBeginner, Category: scenario.CategorySecurity, LearningGoals: []string{"observe response origin"}, Topology: scenario.Topology{Nodes: []scenario.Node{{ID: "client", Type: scenario.NodeTypeClient, Label: "Client"}, {ID: "waf_policy", Type: scenario.NodeTypeWAF, Label: "WAF policy", WAF: &scenario.WAFSettings{Block: in.WAFBlock}}, {ID: "alb", Type: scenario.NodeTypeLB, Label: "Application Load Balancer", LoadBalancer: &scenario.LoadBalancerSettings{HealthyTargets: in.HealthyTargets}}, {ID: "reverse_proxy", Type: scenario.NodeTypeProxy, Label: "Internal reverse proxy", ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: in.ProxyTimeoutMS}}, {ID: "ecs_app", Type: scenario.NodeTypeApp, Label: "ECS Application", Application: &scenario.ApplicationSettings{ResponseDelayMS: in.ResponseDelayMS, ResponseType: in.ResponseType, StatusCode: in.StatusCode}}}, Edges: []scenario.Edge{{From: "client", To: "waf_policy"}, {From: "waf_policy", To: "alb"}, {From: "alb", To: "reverse_proxy"}, {From: "reverse_proxy", To: "ecs_app"}}}, Explanation: "internal", WrongExplanation: "internal"}, nil
}

func ToRunResult(public lab.Lab, definition Definition, in CompiledInput, result simulation.Result, labels map[lab.ControlID]string) (lab.RunResult, error) {
	outcome, reason, failure, err := resultFields(result, definition.Projection)
	if err != nil {
		return lab.RunResult{}, err
	}
	origin, ok := publicNode(result.ResponseOriginNodeID, definition.Projection)
	if !ok {
		return lab.RunResult{}, fmt.Errorf("unprojectable response origin")
	}
	events := make([]lab.Event, 0, len(result.Events))
	for _, event := range result.Events {
		node, visible := publicNode(event.NodeID, definition.Projection)
		if !visible {
			continue
		}
		events = append(events, lab.Event{AtMS: event.AtMS, NodeID: node, Kind: lab.EventKind(event.Kind), MessageKey: publicMessageKey(event.MessageKey, node, definition.Projection)})
	}
	status := result.HTTPStatus
	return lab.RunResult{LabID: public.ID, TestID: in.TestID, EngineID: public.EngineID, OutcomeKind: outcome, ResponseOriginNodeID: &origin, HTTPStatus: &status, TerminationReason: reason, FailurePoint: failure, Events: events, NodeStates: publicStates(public, result.NodeStates, definition.Projection), ElapsedMS: result.ElapsedMS, AppliedLabels: labels, Explanation: explanation(reason)}, nil
}
func resultFields(in simulation.Result, p Projection) (lab.OutcomeKind, string, *lab.FailurePoint, error) {
	switch in.RuleID {
	case simulation.RuleWAFBlock:
		if p.WAFPolicyNodeID == nil {
			return "", "", nil, fmt.Errorf("unprojectable WAF failure")
		}
		return lab.OutcomeKindBlocked, "waf_rule_block", &lab.FailurePoint{NodeID: *p.WAFPolicyNodeID, Reason: "waf_rule_block"}, nil
	case simulation.RuleLBNoHealthyTarget:
		return lab.OutcomeKindUnavailable, "no_available_targets", &lab.FailurePoint{NodeID: p.LoadBalancerNodeID, Reason: "no_available_targets"}, nil
	case simulation.RuleReverseProxyTimeout:
		return lab.OutcomeKindTimeout, "upstream_timeout", &lab.FailurePoint{NodeID: p.LoadBalancerNodeID, Reason: "upstream_timeout"}, nil
	case simulation.RuleMalformedUpstream:
		return lab.OutcomeKindInvalidUpstreamResponse, "invalid_upstream_response", &lab.FailurePoint{NodeID: p.LoadBalancerNodeID, Reason: "invalid_upstream_response"}, nil
	case simulation.RuleApplicationHTTPStatus:
		return lab.OutcomeKindApplicationResponse, "application_response", nil, nil
	default:
		return "", "", nil, fmt.Errorf("unknown engine rule")
	}
}
func publicNode(id string, p Projection) (lab.NodeID, bool) {
	switch id {
	case "client":
		return p.ClientNodeID, true
	case "alb", "reverse_proxy":
		return p.LoadBalancerNodeID, true
	case "ecs_app":
		return p.ApplicationNodeID, true
	case "waf_policy":
		if p.WAFPolicyNodeID != nil {
			return *p.WAFPolicyNodeID, true
		}
	}
	return "", false
}
func publicMessageKey(key string, node lab.NodeID, p Projection) string {
	if node == p.LoadBalancerNodeID && (key == "proxy.request_forwarded" || key == "proxy.response_forwarded" || key == "proxy.timeout_fired" || key == "proxy.malformed_received") {
		return "alb." + key[len("proxy."):]
	}
	return key
}
func publicStates(public lab.Lab, in []simulation.NodeState, p Projection) []lab.NodeState {
	order := make([]lab.NodeID, 0, len(public.Topology.Nodes))
	states := map[lab.NodeID]lab.NodeStateKind{}
	for _, node := range public.Topology.Nodes {
		order = append(order, node.ID)
		states[node.ID] = lab.NodeStateNotReached
	}
	for _, state := range in {
		id, visible := publicNode(state.NodeID, p)
		if !visible {
			continue
		}
		if _, exists := states[id]; !exists {
			continue
		}
		value := lab.NodeStateKind(state.State)
		if stateRank(value) > stateRank(states[id]) {
			states[id] = value
		}
	}
	out := make([]lab.NodeState, 0, len(order))
	for _, id := range order {
		out = append(out, lab.NodeState{NodeID: id, State: states[id]})
	}
	return out
}
func stateRank(state lab.NodeStateKind) int {
	switch state {
	case lab.NodeStateGeneratedResponse:
		return 3
	case lab.NodeStateProcessingAtTermination:
		return 2
	case lab.NodeStateCompleted:
		return 1
	default:
		return 0
	}
}
func explanation(reason string) string { return "HTTP Path evaluation completed: " + reason }
