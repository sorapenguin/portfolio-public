package http_path

import (
	"errors"
	"os"
	"path/filepath"
	"testing"

	"golab/internal/lab"
	"golab/internal/labcatalog"
	scenario "golab/internal/scenario/v2"
	simulation "golab/internal/simulation/v2"
	validate "golab/internal/validate/v2"
)

func TestCompileHeroInputs(t *testing.T) {
	public := heroLab(t)
	def := HeroDefinition()
	cases := []struct {
		name       string
		selections lab.SelectionSet
		targets    int
		response   scenario.ResponseType
		status     *int
		block      bool
	}{
		{"200", selection("allow", "ready", "ok_200"), 1, scenario.ResponseTypeNormal, intPointer(200), false}, {"404", selection("allow", "ready", "not_found_404"), 1, scenario.ResponseTypeNormal, intPointer(404), false}, {"500", selection("allow", "ready", "error_500"), 1, scenario.ResponseTypeNormal, intPointer(500), false}, {"malformed", selection("allow", "ready", "malformed_response"), 1, scenario.ResponseTypeMalformed, nil, false}, {"block", selection("block", "ready", "ok_200"), 1, scenario.ResponseTypeNormal, intPointer(200), true},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			got, err := Compile(public, def, test.selections, "client_to_application")
			if err != nil {
				t.Fatal(err)
			}
			if got.HealthyTargets != test.targets || got.ResponseType != test.response || got.WAFBlock != test.block || got.ProxyTimeoutMS != 5000 || got.ResponseDelayMS != 100 {
				t.Fatalf("input=%#v", got)
			}
			if (got.StatusCode == nil) != (test.status == nil) || (got.StatusCode != nil && *got.StatusCode != *test.status) {
				t.Fatalf("status=%v", got.StatusCode)
			}
			core, err := ToScenario(got)
			if err != nil {
				t.Fatal(err)
			}
			if core.Topology.Nodes[3].ID != "reverse_proxy" || core.Topology.Nodes[4].ID != "ecs_app" {
				t.Fatalf("core=%#v", core.Topology)
			}
		})
	}
}
func TestDefinitionMismatchAndSelectionErrors(t *testing.T) {
	public := heroLab(t)
	def := HeroDefinition()
	def.WAF.Selection = map[OptionKey]WAFEffect{}
	if !errors.Is(ValidateDefinition(public, def), ErrInternalDefinition) {
		t.Fatal("missing mapping accepted")
	}
	_, err := Compile(public, HeroDefinition(), lab.SelectionSet{"waf_action": "allow"}, "client_to_application")
	if !errors.Is(err, ErrMissingSelection) {
		t.Fatalf("error=%v", err)
	}
}

func TestDefinitionRejectsInvalidSourcesAndProjection(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Definition)
	}{
		{"missing-waf-source", func(d *Definition) { d.WAF = WAFEffectSource{} }},
		{"missing-target-source", func(d *Definition) { d.Targets = TargetEffectSource{} }},
		{"missing-application-source", func(d *Definition) { d.Application = ApplicationEffectSource{} }},
		{"source-fixed-and-selection", func(d *Definition) { value := WAFAllow; d.WAF.Fixed = &value }},
		{"extra-option-mapping", func(d *Definition) { d.WAF.Selection[OptionKey{"unknown", "unknown"}] = WAFAllow }},
		{"unknown-client-projection", func(d *Definition) { d.Projection.ClientNodeID = "missing" }},
		{"unknown-load-balancer-projection", func(d *Definition) { d.Projection.LoadBalancerNodeID = "missing" }},
		{"unknown-application-projection", func(d *Definition) { d.Projection.ApplicationNodeID = "missing" }},
		{"unknown-policy-projection", func(d *Definition) { value := lab.NodeID("missing"); d.Projection.WAFPolicyNodeID = &value }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			def := HeroDefinition()
			tc.mutate(&def)
			if !errors.Is(ValidateDefinition(heroLab(t), def), ErrInternalDefinition) {
				t.Fatal("invalid definition accepted")
			}
		})
	}
}
func TestAllCombinationsProduceValidCore(t *testing.T) {
	public := heroLab(t)
	for _, waf := range []lab.OptionID{"allow", "block"} {
		for _, targets := range []lab.OptionID{"ready", "empty_or_unused"} {
			for _, application := range []lab.OptionID{"ok_200", "not_found_404", "error_500", "malformed_response"} {
				input, err := Compile(public, HeroDefinition(), selection(waf, targets, application), "client_to_application")
				if err != nil {
					t.Fatal(err)
				}
				core, err := ToScenario(input)
				if err != nil {
					t.Fatal(err)
				}
				if result := validate.ValidateCore(core); !result.Valid() {
					t.Fatalf("%s/%s/%s: %#v", waf, targets, application, result.Issues)
				}
			}
		}
	}
}

func TestCompileWAFlessDefinitionWithFixedAllow(t *testing.T) {
	public := heroLab(t)
	public.ID = "test-alb-target-health"
	public.Controls = public.Controls[1:]
	public.Topology.Nodes[1].Display.AttachedPolicies = nil
	def := HeroDefinition()
	def.LabID = public.ID
	allow := WAFAllow
	def.WAF = WAFEffectSource{Fixed: &allow}
	def.Projection.WAFPolicyNodeID = nil
	for _, targets := range []lab.OptionID{"ready", "empty_or_unused"} {
		for _, application := range []lab.OptionID{"ok_200", "not_found_404", "error_500", "malformed_response"} {
			input, err := Compile(public, def, lab.SelectionSet{"target_group_state": targets, "application_outcome": application}, "client_to_application")
			if err != nil || input.WAFBlock {
				t.Fatalf("%s/%s: %#v %v", targets, application, input, err)
			}
		}
	}
}

func TestWAFlessDefinitionProjectsOnlyPublicNodes(t *testing.T) {
	public := heroLab(t)
	public.ID = "test-alb-target-health"
	public.Controls = public.Controls[1:]
	public.Topology.Nodes[1].Display.AttachedPolicies = nil
	public.Topology.Nodes[2].ID = "application"
	def := HeroDefinition()
	def.LabID = public.ID
	allow := WAFAllow
	def.WAF = WAFEffectSource{Fixed: &allow}
	def.Projection.ApplicationNodeID = "application"
	def.Projection.WAFPolicyNodeID = nil
	for _, targets := range []lab.OptionID{"ready", "empty_or_unused"} {
		for _, application := range []lab.OptionID{"ok_200", "not_found_404", "error_500"} {
			selection := lab.SelectionSet{"target_group_state": targets, "application_outcome": application}
			input, err := Compile(public, def, selection, "client_to_application")
			if err != nil {
				t.Fatal(err)
			}
			core, err := ToScenario(input)
			if err != nil {
				t.Fatal(err)
			}
			engine, err := simulation.Simulate(core)
			if err != nil {
				t.Fatal(err)
			}
			result, err := ToRunResult(public, def, input, engine, map[lab.ControlID]string{})
			if err != nil {
				t.Fatal(err)
			}
			if targets == "empty_or_unused" {
				if result.HTTPStatus == nil || *result.HTTPStatus != 503 || *result.ResponseOriginNodeID != "alb" {
					t.Fatalf("empty result %#v", result)
				}
			} else if result.ResponseOriginNodeID == nil || *result.ResponseOriginNodeID != "application" {
				t.Fatalf("ready result %#v", result)
			}
			for _, state := range result.NodeStates {
				if state.NodeID == "waf_policy" || state.NodeID == "reverse_proxy" {
					t.Fatalf("internal node leaked %#v", result.NodeStates)
				}
			}
			for _, event := range result.Events {
				if event.NodeID == "waf_policy" || event.NodeID == "reverse_proxy" {
					t.Fatalf("internal event leaked %#v", result.Events)
				}
			}
		}
	}
}
func heroLab(t *testing.T) lab.Lab {
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
	return item
}
func selection(waf, target, app lab.OptionID) lab.SelectionSet {
	return lab.SelectionSet{"waf_action": waf, "target_group_state": target, "application_outcome": app}
}
