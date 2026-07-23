package http_path

import (
	"errors"
	"fmt"

	"golab/internal/lab"
)

type WAFEffect bool

const (
	WAFAllow WAFEffect = false
	WAFBlock WAFEffect = true
)

type TargetGroupEffect int

const (
	TargetsReady         TargetGroupEffect = 1
	TargetsEmptyOrUnused TargetGroupEffect = 0
)

type ApplicationEffect string

const (
	ApplicationOK200             ApplicationEffect = "ok_200"
	ApplicationNotFound404       ApplicationEffect = "not_found_404"
	ApplicationError500          ApplicationEffect = "error_500"
	ApplicationMalformedResponse ApplicationEffect = "malformed_response"
)

type OptionKey struct {
	ControlID lab.ControlID
	OptionID  lab.OptionID
}

// Each source is typed by effect. Exactly one of Fixed and Selection is valid.
// Selection keys are public allowlisted control/option identifiers, never request data.
type WAFEffectSource struct {
	Fixed     *WAFEffect
	Selection map[OptionKey]WAFEffect
}
type TargetEffectSource struct {
	Fixed     *TargetGroupEffect
	Selection map[OptionKey]TargetGroupEffect
}
type ApplicationEffectSource struct {
	Fixed     *ApplicationEffect
	Selection map[OptionKey]ApplicationEffect
}
type Projection struct {
	ClientNodeID       lab.NodeID
	LoadBalancerNodeID lab.NodeID
	ApplicationNodeID  lab.NodeID
	WAFPolicyNodeID    *lab.NodeID
}
type Definition struct {
	LabID           lab.LabID
	EngineID        lab.EngineID
	TestID          lab.TestID
	WAF             WAFEffectSource
	Targets         TargetEffectSource
	Application     ApplicationEffectSource
	Projection      Projection
	ProxyTimeoutMS  int
	ResponseDelayMS int
}

var ErrInternalDefinition = errors.New("internal http path definition is inconsistent")

func HeroDefinition() Definition {
	allow, block := WAFAllow, WAFBlock
	ready, empty := TargetsReady, TargetsEmptyOrUnused
	ok, notFound, serverError, malformed := ApplicationOK200, ApplicationNotFound404, ApplicationError500, ApplicationMalformedResponse
	policy := lab.NodeID("waf_policy")
	return Definition{LabID: "aws-waf-alb-ecs-001", EngineID: "http_path", TestID: "client_to_application", ProxyTimeoutMS: 5000, ResponseDelayMS: 100,
		WAF:         WAFEffectSource{Selection: map[OptionKey]WAFEffect{{"waf_action", "allow"}: allow, {"waf_action", "block"}: block}},
		Targets:     TargetEffectSource{Selection: map[OptionKey]TargetGroupEffect{{"target_group_state", "ready"}: ready, {"target_group_state", "empty_or_unused"}: empty}},
		Application: ApplicationEffectSource{Selection: map[OptionKey]ApplicationEffect{{"application_outcome", "ok_200"}: ok, {"application_outcome", "not_found_404"}: notFound, {"application_outcome", "error_500"}: serverError, {"application_outcome", "malformed_response"}: malformed}},
		Projection:  Projection{ClientNodeID: "client", LoadBalancerNodeID: "alb", ApplicationNodeID: "ecs_app", WAFPolicyNodeID: &policy},
	}
}

func ValidateDefinition(public lab.Lab, def Definition) error {
	if public.ID != def.LabID || public.EngineID != def.EngineID || def.EngineID != "http_path" || def.ProxyTimeoutMS <= def.ResponseDelayMS || def.ResponseDelayMS < 0 {
		return fmt.Errorf("%w: metadata or fixed input", ErrInternalDefinition)
	}
	if len(public.Tests) != 1 || public.Tests[0].ID != def.TestID {
		return fmt.Errorf("%w: test", ErrInternalDefinition)
	}
	if !validWAFSource(def.WAF) || !validTargetSource(def.Targets) || !validApplicationSource(def.Application) {
		return fmt.Errorf("%w: effect source", ErrInternalDefinition)
	}
	nodes, policies := publicIDs(public)
	if !nodes[def.Projection.ClientNodeID] || !nodes[def.Projection.LoadBalancerNodeID] || !nodes[def.Projection.ApplicationNodeID] {
		return fmt.Errorf("%w: projection node", ErrInternalDefinition)
	}
	if def.Projection.WAFPolicyNodeID != nil && !policies[*def.Projection.WAFPolicyNodeID] {
		return fmt.Errorf("%w: projection policy", ErrInternalDefinition)
	}
	if def.Projection.WAFPolicyNodeID == nil && ((def.WAF.Fixed != nil && *def.WAF.Fixed == WAFBlock) || sourceHasWAFBlock(def.WAF)) {
		return fmt.Errorf("%w: blocking WAF needs public policy", ErrInternalDefinition)
	}
	seen := map[OptionKey]bool{}
	for _, control := range public.Controls {
		if !control.Required {
			return fmt.Errorf("%w: required control", ErrInternalDefinition)
		}
		for _, option := range control.Options {
			key := OptionKey{control.ID, option.ID}
			count := 0
			if _, ok := def.WAF.Selection[key]; ok {
				count++
			}
			if _, ok := def.Targets.Selection[key]; ok {
				count++
			}
			if _, ok := def.Application.Selection[key]; ok {
				count++
			}
			if count != 1 {
				return fmt.Errorf("%w: option mapping", ErrInternalDefinition)
			}
			seen[key] = true
		}
	}
	if len(def.WAF.Selection)+len(def.Targets.Selection)+len(def.Application.Selection) != len(seen) {
		return fmt.Errorf("%w: extra option mapping", ErrInternalDefinition)
	}
	return nil
}
func validWAFSource(s WAFEffectSource) bool       { return (s.Fixed != nil) != (len(s.Selection) > 0) }
func validTargetSource(s TargetEffectSource) bool { return (s.Fixed != nil) != (len(s.Selection) > 0) }
func validApplicationSource(s ApplicationEffectSource) bool {
	return (s.Fixed != nil) != (len(s.Selection) > 0)
}
func sourceHasWAFBlock(s WAFEffectSource) bool {
	for _, value := range s.Selection {
		if value == WAFBlock {
			return true
		}
	}
	return false
}
func publicIDs(public lab.Lab) (map[lab.NodeID]bool, map[lab.NodeID]bool) {
	nodes := map[lab.NodeID]bool{}
	policies := map[lab.NodeID]bool{}
	for _, node := range public.Topology.Nodes {
		nodes[node.ID] = true
		for _, policy := range node.Display.AttachedPolicies {
			policies[policy.NodeID] = true
		}
	}
	return nodes, policies
}
