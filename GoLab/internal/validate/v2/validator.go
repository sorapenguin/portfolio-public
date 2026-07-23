// Package v2 validates the semantic constraints of Schema v2 scenario input.
package v2

import (
	"fmt"
	"sort"
	"strings"

	scenario "golab/internal/scenario/v2"
)

const (
	CodeRequired              = "required"
	CodeInvalidValue          = "invalid_value"
	CodeDuplicateID           = "duplicate_id"
	CodeDuplicateNodeType     = "duplicate_node_type"
	CodeUnknownReference      = "unknown_reference"
	CodeInvalidTopology       = "invalid_topology"
	CodeCycleDetected         = "cycle_detected"
	CodeOrphanNode            = "orphan_node"
	CodeUnsupportedNodeType   = "unsupported_node_type"
	CodeRuleConflict          = "rule_conflict"
	CodeCoreReferenceMismatch = "core_reference_mismatch"
	CodeOverrideTargetMissing = "override_target_not_found"
	CodeOverrideTypeMismatch  = "override_type_mismatch"
	CodeMVPUnsupported        = "mvp_unsupported"
)

type Issue struct {
	Code    string
	Path    string
	Message string
}

type Result struct {
	Issues []Issue
}

func (r Result) Valid() bool { return len(r.Issues) == 0 }

// ValidateDocument validates whichever single document is present.
func ValidateDocument(doc scenario.Document) Result {
	issues := make([]Issue, 0)
	if doc.Type != scenario.DocumentTypeCore && doc.Type != scenario.DocumentTypeDrill {
		issues = append(issues, issue(CodeInvalidValue, "type", "document type must be core or drill"))
	}
	switch doc.Type {
	case scenario.DocumentTypeCore:
		if doc.Core == nil || doc.Drill != nil {
			issues = append(issues, issue(CodeInvalidValue, "document", "core document must contain only Core"))
			return Result{Issues: issues}
		}
		issues = append(issues, ValidateCore(doc.Core).Issues...)
	case scenario.DocumentTypeDrill:
		if doc.Drill == nil || doc.Core != nil {
			issues = append(issues, issue(CodeInvalidValue, "document", "drill document must contain only Drill"))
			return Result{Issues: issues}
		}
		issues = append(issues, ValidateDrill(doc.Drill).Issues...)
	}
	return Result{Issues: issues}
}

func ValidateCore(core *scenario.CoreScenario) Result {
	if core == nil {
		return Result{Issues: []Issue{issue(CodeRequired, "core", "core scenario is required")}}
	}
	issues := validateCoreFields(core)
	issues = append(issues, validateTopology(&core.Topology)...)
	issues = append(issues, validateRulePrerequisitesAndConflicts(core)...)
	return Result{Issues: issues}
}

func ValidateDrill(drill *scenario.DrillScenario) Result {
	if drill == nil {
		return Result{Issues: []Issue{issue(CodeRequired, "drill", "drill scenario is required")}}
	}
	issues := make([]Issue, 0)
	if drill.SchemaVersion != scenario.SchemaVersion {
		issues = append(issues, issue(CodeInvalidValue, "schema_version", "schema_version must be 2"))
	}
	if drill.Type != scenario.DocumentTypeDrill {
		issues = append(issues, issue(CodeInvalidValue, "type", "type must be drill"))
	}
	if blank(drill.ID) {
		issues = append(issues, issue(CodeRequired, "id", "id is required"))
	}
	if blank(drill.CoreID) {
		issues = append(issues, issue(CodeRequired, "core_id", "core_id is required"))
	}
	if !validDifficulty(drill.Difficulty) {
		issues = append(issues, issue(CodeInvalidValue, "difficulty", "difficulty is invalid"))
	}
	if drill.VariantNote != "" && blank(drill.VariantNote) {
		issues = append(issues, issue(CodeInvalidValue, "variant_note", "variant_note must not be whitespace only"))
	}
	if len(drill.Overrides) == 0 {
		issues = append(issues, issue(CodeRequired, "overrides", "overrides must not be empty"))
	}
	for _, id := range sortedOverrideIDs(drill.Overrides) {
		path := "overrides." + id
		if blank(id) {
			issues = append(issues, issue(CodeRequired, path, "override node id is required"))
		}
		issues = append(issues, validateOverride(path, drill.Overrides[id])...)
	}
	return Result{Issues: issues}
}

func ValidateDrillAgainstCore(drill *scenario.DrillScenario, core *scenario.CoreScenario) Result {
	issues := ValidateDrill(drill).Issues
	if core == nil {
		return Result{Issues: append(issues, issue(CodeRequired, "core", "core scenario is required"))}
	}
	if drill == nil {
		return Result{Issues: issues}
	}
	if drill.CoreID != core.ID {
		issues = append(issues, issue(CodeCoreReferenceMismatch, "core_id", "core_id does not match the supplied core scenario"))
	}
	nodes := make(map[string]scenario.Node, len(core.Topology.Nodes))
	for _, node := range core.Topology.Nodes {
		if _, exists := nodes[node.ID]; !exists {
			nodes[node.ID] = node
		}
	}
	for _, id := range sortedOverrideIDs(drill.Overrides) {
		node, exists := nodes[id]
		if !exists {
			issues = append(issues, issue(CodeOverrideTargetMissing, "overrides."+id, "override target does not exist in core"))
			continue
		}
		if !overrideMatchesNode(drill.Overrides[id], node.Type) {
			issues = append(issues, issue(CodeOverrideTypeMismatch, "overrides."+id, "override settings do not match the core node type"))
		}
	}
	return Result{Issues: issues}
}

func validateCoreFields(core *scenario.CoreScenario) []Issue {
	issues := make([]Issue, 0)
	if core.SchemaVersion != scenario.SchemaVersion {
		issues = append(issues, issue(CodeInvalidValue, "schema_version", "schema_version must be 2"))
	}
	if core.Type != scenario.DocumentTypeCore {
		issues = append(issues, issue(CodeInvalidValue, "type", "type must be core"))
	}
	for _, field := range []struct{ path, value string }{{"id", core.ID}, {"title", core.Title}, {"description", core.Description}, {"category", string(core.Category)}, {"explanation", core.Explanation}, {"wrong_explanation", core.WrongExplanation}} {
		if blank(field.value) {
			issues = append(issues, issue(CodeRequired, field.path, field.path+" is required"))
		}
	}
	if !core.Category.Valid() {
		issues = append(issues, issue(CodeInvalidValue, "category", "category must be timeout, error, redirect, security, or availability"))
	}
	if !validDifficulty(core.Difficulty) {
		issues = append(issues, issue(CodeInvalidValue, "difficulty", "difficulty is invalid"))
	}
	if len(core.LearningGoals) == 0 {
		issues = append(issues, issue(CodeRequired, "learning_goals", "learning_goals must not be empty"))
	}
	for i, goal := range core.LearningGoals {
		if blank(goal) {
			issues = append(issues, issue(CodeRequired, fmt.Sprintf("learning_goals[%d]", i), "learning goal is required"))
		}
	}
	for i, ref := range core.CloudConceptRefs {
		if blank(ref) {
			issues = append(issues, issue(CodeInvalidValue, fmt.Sprintf("cloud_concept_refs[%d]", i), "cloud concept reference must not be blank"))
		}
	}
	return issues
}

func validateTopology(topology *scenario.Topology) []Issue {
	issues := make([]Issue, 0)
	if len(topology.Nodes) == 0 {
		return append(issues, issue(CodeRequired, "topology.nodes", "nodes must not be empty"))
	}
	nodes := make(map[string]int, len(topology.Nodes))
	types := make(map[scenario.NodeType]int, len(topology.Nodes))
	for i, node := range topology.Nodes {
		path := fmt.Sprintf("topology.nodes[%d]", i)
		if blank(node.ID) {
			issues = append(issues, issue(CodeRequired, path+".id", "node id is required"))
		} else if _, exists := nodes[node.ID]; exists {
			issues = append(issues, issue(CodeDuplicateID, path+".id", "node id is duplicated"))
		} else {
			nodes[node.ID] = i
		}
		if !validNodeType(node.Type) {
			issues = append(issues, issue(CodeUnsupportedNodeType, path+".type", "node type is unsupported"))
		} else {
			types[node.Type]++
		}
		if node.Type == scenario.NodeTypeDB {
			issues = append(issues, issue(CodeMVPUnsupported, path+".type", "database nodes are not supported in the MVP"))
		}
		issues = append(issues, validateNodeSettings(path, node)...)
	}
	for _, nodeType := range []scenario.NodeType{scenario.NodeTypeClient, scenario.NodeTypeDNS, scenario.NodeTypeEdge, scenario.NodeTypeWAF, scenario.NodeTypeLB, scenario.NodeTypeProxy, scenario.NodeTypeApp, scenario.NodeTypeDB} {
		if types[nodeType] > 1 {
			issues = append(issues, issue(CodeDuplicateNodeType, "topology.nodes", "node type "+string(nodeType)+" occurs more than once"))
		}
	}
	if types[scenario.NodeTypeClient] != 1 {
		issues = append(issues, issue(CodeInvalidTopology, "topology.nodes", "exactly one client node is required"))
	}
	if types[scenario.NodeTypeApp] != 1 {
		issues = append(issues, issue(CodeInvalidTopology, "topology.nodes", "exactly one application node is required"))
	}

	in := make(map[string]int, len(nodes))
	out := make(map[string]int, len(nodes))
	next := make(map[string][]string, len(nodes))
	edgeSeen := make(map[string]bool, len(topology.Edges))
	for i, edge := range topology.Edges {
		path := fmt.Sprintf("topology.edges[%d]", i)
		_, fromOK := nodes[edge.From]
		_, toOK := nodes[edge.To]
		if !fromOK {
			issues = append(issues, issue(CodeUnknownReference, path+".from", "edge source node does not exist"))
		}
		if !toOK {
			issues = append(issues, issue(CodeUnknownReference, path+".to", "edge destination node does not exist"))
		}
		if edge.From == edge.To && edge.From != "" {
			issues = append(issues, issue(CodeInvalidTopology, path, "self-referential edge is not allowed"))
		}
		key := edge.From + "\x00" + edge.To
		if edgeSeen[key] {
			issues = append(issues, issue(CodeDuplicateID, path, "edge is duplicated"))
		} else {
			edgeSeen[key] = true
		}
		if fromOK && toOK {
			out[edge.From]++
			in[edge.To]++
			next[edge.From] = append(next[edge.From], edge.To)
		}
	}
	if clientIndex, ok := nodesByType(topology.Nodes, scenario.NodeTypeClient); ok {
		issues = append(issues, validateSinglePath(topology.Nodes, topology.Nodes[clientIndex].ID, in, out, next)...)
	}
	return issues
}

func validateSinglePath(nodes []scenario.Node, client string, in, out map[string]int, next map[string][]string) []Issue {
	issues := make([]Issue, 0)
	for _, node := range nodes {
		path := "topology.nodes[" + node.ID + "]"
		if node.Type == scenario.NodeTypeClient {
			if in[node.ID] != 0 || out[node.ID] != 1 {
				issues = append(issues, issue(CodeInvalidTopology, path, "client must have indegree 0 and outdegree 1"))
			}
			continue
		}
		if node.Type == scenario.NodeTypeApp {
			if in[node.ID] != 1 || out[node.ID] != 0 {
				issues = append(issues, issue(CodeInvalidTopology, path, "application must have indegree 1 and outdegree 0"))
			}
			continue
		}
		if in[node.ID] != 1 || out[node.ID] != 1 {
			issues = append(issues, issue(CodeInvalidTopology, path, "intermediate node must have indegree 1 and outdegree 1"))
		}
	}
	visited := make(map[string]bool, len(nodes))
	current := client
	for current != "" {
		if visited[current] {
			issues = append(issues, issue(CodeCycleDetected, "topology", "cycle detected while walking from client"))
			break
		}
		visited[current] = true
		if len(next[current]) != 1 {
			break
		}
		current = next[current][0]
	}
	for _, node := range nodes {
		if !visited[node.ID] {
			issues = append(issues, issue(CodeOrphanNode, "topology.nodes["+node.ID+"]", "node is not on the client path"))
		}
	}
	return issues
}

func validateNodeSettings(path string, node scenario.Node) []Issue {
	issues := make([]Issue, 0)
	count := 0
	if node.WAF != nil {
		count++
	}
	if node.LoadBalancer != nil {
		count++
	}
	if node.ReverseProxy != nil {
		count++
	}
	if node.Application != nil {
		count++
	}
	if count > 1 {
		return append(issues, issue(CodeInvalidValue, path+".settings", "multiple settings variants are set"))
	}
	expected := false
	switch node.Type {
	case scenario.NodeTypeWAF:
		expected = node.WAF != nil
	case scenario.NodeTypeLB:
		expected = node.LoadBalancer != nil
		if node.LoadBalancer != nil && node.LoadBalancer.HealthyTargets < 0 {
			issues = append(issues, issue(CodeInvalidValue, path+".settings.healthy_targets", "healthy_targets must be non-negative"))
		}
	case scenario.NodeTypeProxy:
		expected = node.ReverseProxy != nil
		if node.ReverseProxy != nil && node.ReverseProxy.TimeoutMS <= 0 {
			issues = append(issues, issue(CodeInvalidValue, path+".settings.timeout_ms", "timeout_ms must be positive"))
		}
	case scenario.NodeTypeApp:
		expected = node.Application != nil
		if node.Application != nil {
			issues = append(issues, validateApplication(path+".settings", node.Application, true)...)
		}
	case scenario.NodeTypeClient, scenario.NodeTypeDNS, scenario.NodeTypeEdge, scenario.NodeTypeDB:
		expected = count == 0
	}
	if !expected {
		issues = append(issues, issue(CodeInvalidValue, path+".settings", "settings do not match node type"))
	}
	return issues
}

func validateApplication(path string, settings *scenario.ApplicationSettings, complete bool) []Issue {
	issues := make([]Issue, 0)
	if settings.ResponseDelayMS < 0 {
		issues = append(issues, issue(CodeInvalidValue, path+".response_delay_ms", "response_delay_ms must be non-negative"))
	}
	if settings.ResponseType != scenario.ResponseTypeNormal && settings.ResponseType != scenario.ResponseTypeMalformed {
		issues = append(issues, issue(CodeInvalidValue, path+".response_type", "response_type is invalid"))
	}
	if settings.ResponseType == scenario.ResponseTypeNormal && complete && settings.StatusCode == nil {
		issues = append(issues, issue(CodeRequired, path+".status_code", "status_code is required for normal responses"))
	}
	if settings.ResponseType == scenario.ResponseTypeMalformed && settings.StatusCode != nil {
		issues = append(issues, issue(CodeInvalidValue, path+".status_code", "status_code is not allowed for malformed responses"))
	}
	if settings.StatusCode != nil && (*settings.StatusCode < 100 || *settings.StatusCode > 599) {
		issues = append(issues, issue(CodeInvalidValue, path+".status_code", "status_code must be 100-599"))
	}
	return issues
}

func validateOverride(path string, override scenario.SettingsOverride) []Issue {
	issues := make([]Issue, 0)
	count := 0
	if override.WAF != nil {
		count++
	}
	if override.LoadBalancer != nil {
		count++
	}
	if override.ReverseProxy != nil {
		count++
	}
	if override.Application != nil {
		count++
	}
	if count != 1 {
		return append(issues, issue(CodeInvalidValue, path, "override must contain exactly one settings variant"))
	}
	if override.LoadBalancer != nil && override.LoadBalancer.HealthyTargets < 0 {
		issues = append(issues, issue(CodeInvalidValue, path+".healthy_targets", "healthy_targets must be non-negative"))
	}
	if override.ReverseProxy != nil && override.ReverseProxy.TimeoutMS <= 0 {
		issues = append(issues, issue(CodeInvalidValue, path+".timeout_ms", "timeout_ms must be positive"))
	}
	if override.Application != nil {
		issues = append(issues, validateApplication(path, override.Application, false)...)
	}
	return issues
}

func validateRulePrerequisitesAndConflicts(core *scenario.CoreScenario) []Issue {
	var waf *scenario.WAFSettings
	var lb *scenario.LoadBalancerSettings
	var proxy *scenario.ReverseProxySettings
	var app *scenario.ApplicationSettings
	for _, node := range core.Topology.Nodes {
		switch node.Type {
		case scenario.NodeTypeWAF:
			waf = node.WAF
		case scenario.NodeTypeLB:
			lb = node.LoadBalancer
		case scenario.NodeTypeProxy:
			proxy = node.ReverseProxy
		case scenario.NodeTypeApp:
			app = node.Application
		}
	}
	issues := make([]Issue, 0)
	if app != nil && app.ResponseType == scenario.ResponseTypeMalformed && proxy == nil {
		issues = append(issues, issue(CodeInvalidTopology, "topology", "malformed application response requires a reverse proxy node"))
	}
	rules := make([]string, 0, 4)
	if waf != nil && waf.Block {
		rules = append(rules, "waf_block")
	}
	if lb != nil && lb.HealthyTargets == 0 {
		rules = append(rules, "lb_no_healthy_target")
	}
	if proxy != nil && app != nil && proxy.TimeoutMS < app.ResponseDelayMS {
		rules = append(rules, "reverse_proxy_timeout")
	}
	if app != nil && app.ResponseType == scenario.ResponseTypeMalformed {
		rules = append(rules, "malformed_upstream_response")
	}
	if len(rules) >= 2 {
		issues = append(issues, issue(CodeRuleConflict, "topology", "conflicting terminal rules: "+strings.Join(rules, ", ")))
	}
	return issues
}

func overrideMatchesNode(override scenario.SettingsOverride, nodeType scenario.NodeType) bool {
	switch nodeType {
	case scenario.NodeTypeWAF:
		return override.WAF != nil
	case scenario.NodeTypeLB:
		return override.LoadBalancer != nil
	case scenario.NodeTypeProxy:
		return override.ReverseProxy != nil
	case scenario.NodeTypeApp:
		return override.Application != nil
	default:
		return false
	}
}
func nodesByType(nodes []scenario.Node, nodeType scenario.NodeType) (int, bool) {
	for i, node := range nodes {
		if node.Type == nodeType {
			return i, true
		}
	}
	return 0, false
}
func sortedOverrideIDs(overrides scenario.DrillOverrides) []string {
	ids := make([]string, 0, len(overrides))
	for id := range overrides {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids
}
func validDifficulty(d scenario.Difficulty) bool {
	return d == scenario.DifficultyBeginner || d == scenario.DifficultyIntermediate || d == scenario.DifficultyAdvanced
}
func validNodeType(t scenario.NodeType) bool {
	switch t {
	case scenario.NodeTypeClient, scenario.NodeTypeDNS, scenario.NodeTypeEdge, scenario.NodeTypeWAF, scenario.NodeTypeLB, scenario.NodeTypeProxy, scenario.NodeTypeApp, scenario.NodeTypeDB:
		return true
	}
	return false
}
func blank(value string) bool                { return strings.TrimSpace(value) == "" }
func issue(code, path, message string) Issue { return Issue{Code: code, Path: path, Message: message} }
