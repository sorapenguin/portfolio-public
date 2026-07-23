// Package validate provides semantic validation for GoLab scenario definitions.
package validate

import (
	"fmt"

	"golab/internal/scenario"
)

var allowedNodeTypes = map[string]bool{
	"client": true,
	"dns":    true,
	"edge":   true,
	"waf":    true,
	"lb":     true,
	"proxy":  true,
	"app":    true,
	"db":     true,
}

var allowedDifficulties = map[string]bool{
	"beginner":     true,
	"intermediate": true,
	"advanced":     true,
}

var allowedEventStatuses = map[string]bool{
	"ok":      true,
	"error":   true,
	"timeout": true,
}

// Scenario validates a scenario definition and returns error strings.
// An empty slice means the scenario passed all checks.
func Scenario(sc scenario.Scenario) []string {
	var errs []string

	if sc.ID == "" {
		errs = append(errs, "id is required")
	}
	if sc.Title == "" {
		errs = append(errs, "title is required")
	}
	if sc.SchemaVersion == "" {
		errs = append(errs, "schemaVersion is required")
	}
	if sc.Description == "" {
		errs = append(errs, "description is required")
	}
	if !allowedDifficulties[sc.Difficulty] {
		errs = append(errs, fmt.Sprintf("invalid difficulty %q (allowed: beginner, intermediate, advanced)", sc.Difficulty))
	}
	if sc.ExpectedStatus < 100 || sc.ExpectedStatus > 599 {
		errs = append(errs, fmt.Sprintf("invalid expectedStatus %d (must be 100-599)", sc.ExpectedStatus))
	}
	if len(sc.Choices) == 0 {
		errs = append(errs, "choices must not be empty")
	}
	if sc.Explanation == "" {
		errs = append(errs, "explanation is required")
	}

	// Build node index and validate types
	nodeIDs := make(map[string]bool, len(sc.Nodes))
	for i, n := range sc.Nodes {
		if n.ID == "" {
			errs = append(errs, fmt.Sprintf("nodes[%d].id is required", i))
		}
		if nodeIDs[n.ID] {
			errs = append(errs, fmt.Sprintf("duplicate node id %q", n.ID))
		}
		nodeIDs[n.ID] = true
		if !allowedNodeTypes[n.Type] {
			errs = append(errs, fmt.Sprintf("node %q has unknown type %q", n.ID, n.Type))
		}
	}

	// Connections must reference known nodes
	for i, c := range sc.Connections {
		if !nodeIDs[c.From] {
			errs = append(errs, fmt.Sprintf("connections[%d].from references unknown node %q", i, c.From))
		}
		if !nodeIDs[c.To] {
			errs = append(errs, fmt.Sprintf("connections[%d].to references unknown node %q", i, c.To))
		}
	}

	// Events must reference known nodes
	for i, e := range sc.Events {
		if !nodeIDs[e.NodeID] {
			errs = append(errs, fmt.Sprintf("events[%d] references unknown node %q", i, e.NodeID))
		}
		if !allowedEventStatuses[e.Status] {
			errs = append(errs, fmt.Sprintf("events[%d] has unknown status %q", i, e.Status))
		}
	}

	// failPoint must be a known node (if set)
	if sc.FailPoint != "" && !nodeIDs[sc.FailPoint] {
		errs = append(errs, fmt.Sprintf("failPoint %q is not a known node", sc.FailPoint))
	}

	// choices IDs that match a node are preferred but not required
	// (some choices may represent categories of nodes)

	return errs
}
