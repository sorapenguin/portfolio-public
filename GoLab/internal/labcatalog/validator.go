package labcatalog

import (
	"fmt"
	"strings"

	"golab/internal/lab"
)

const (
	MaxNodes    = 16
	MaxControls = 16
	MaxOptions  = 64
	MaxTests    = 16
)

func Validate(item lab.Lab) error {
	if blank(string(item.ID)) || !validID(string(item.ID)) {
		return fmt.Errorf("invalid lab id")
	}
	if blank(item.Title) || !item.Provider.Valid() || !item.Difficulty.Valid() || !item.Mode.Valid() || blank(string(item.EngineID)) || blank(string(item.DomainID)) {
		return fmt.Errorf("invalid lab metadata")
	}
	if item.EngineID != "http_path" {
		return fmt.Errorf("unknown engine")
	}
	if len(item.SubcategoryIDs) == 0 || len(item.Topology.Nodes) == 0 || len(item.Controls) == 0 || len(item.Tests) == 0 || len(item.Simplifications) == 0 {
		return fmt.Errorf("required lab content is missing")
	}
	if len(item.Topology.Nodes) > MaxNodes || len(item.Controls) > MaxControls || len(item.Tests) > MaxTests {
		return fmt.Errorf("lab count exceeds limit")
	}
	subs := map[lab.SubcategoryID]bool{}
	for _, id := range item.SubcategoryIDs {
		if blank(string(id)) || subs[id] {
			return fmt.Errorf("invalid subcategory")
		}
		subs[id] = true
	}
	nodes := map[lab.NodeID]bool{}
	policies := map[lab.NodeID]bool{}
	for _, node := range item.Topology.Nodes {
		if blank(string(node.ID)) || nodes[node.ID] {
			return fmt.Errorf("invalid topology node")
		}
		nodes[node.ID] = true
		if blank(node.Type) || blank(node.Display.Label) || blank(node.Display.ServiceName) || blank(node.Display.Simplification) {
			return fmt.Errorf("invalid node display")
		}
		for _, policy := range node.Display.AttachedPolicies {
			if blank(string(policy.NodeID)) || policies[policy.NodeID] {
				return fmt.Errorf("invalid attached policy")
			}
			policies[policy.NodeID] = true
		}
	}
	edges := map[string]bool{}
	for _, edge := range item.Topology.Edges {
		if !nodes[edge.From] || !nodes[edge.To] || edge.From == edge.To {
			return fmt.Errorf("invalid topology edge")
		}
		key := string(edge.From) + "\x00" + string(edge.To)
		if edges[key] {
			return fmt.Errorf("duplicate topology edge")
		}
		edges[key] = true
	}
	controls := map[lab.ControlID]bool{}
	optionCount := 0
	for _, control := range item.Controls {
		if blank(string(control.ID)) || controls[control.ID] || !control.Type.Valid() || len(control.Options) == 0 {
			return fmt.Errorf("invalid control")
		}
		controls[control.ID] = true
		options := map[lab.OptionID]bool{}
		for _, option := range control.Options {
			optionCount++
			if blank(string(option.ID)) || blank(option.Label) || options[option.ID] {
				return fmt.Errorf("invalid option")
			}
			options[option.ID] = true
		}
		if !options[control.DefaultOptionID] {
			return fmt.Errorf("default option is missing")
		}
	}
	if optionCount > MaxOptions {
		return fmt.Errorf("option count exceeds limit")
	}
	tests := map[lab.TestID]bool{}
	for _, test := range item.Tests {
		if blank(string(test.ID)) || tests[test.ID] || blank(test.Label) {
			return fmt.Errorf("invalid test")
		}
		tests[test.ID] = true
	}
	for _, s := range item.Simplifications {
		if blank(s.ID) || blank(s.Title) || blank(s.Description) {
			return fmt.Errorf("invalid simplification")
		}
	}
	return nil
}
func blank(value string) bool { return strings.TrimSpace(value) == "" }
func validID(value string) bool {
	for _, r := range value {
		if !(r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || r == '-') {
			return false
		}
	}
	return true
}
