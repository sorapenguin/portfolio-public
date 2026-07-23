package v2

import (
	"path/filepath"
	"testing"

	scenario "golab/internal/scenario/v2"
)

func TestValidateCore_validCases(t *testing.T) {
	fixturePath := filepath.Join("..", "..", "..", "testdata", "scenarios", "v2", "core-proxy-timeout-001.json")
	fixture, err := scenario.LoadFile(fixturePath)
	if err != nil {
		t.Fatalf("LoadFile() error = %v", err)
	}

	tests := []struct {
		name string
		core *scenario.CoreScenario
	}{
		{"P1-1 fixture", fixture.Core},
		{"application status", validCore()},
		{"equal timeout is not timeout", withProxy(10, 10, scenario.ResponseTypeNormal)},
		{"lb no healthy target", withLB(0)},
		{"waf block", withWAF(true)},
		{"malformed upstream with proxy", withProxy(10, 0, scenario.ResponseTypeMalformed)},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if result := ValidateCore(test.core); !result.Valid() {
				t.Fatalf("ValidateCore() issues = %#v", result.Issues)
			}
		})
	}
}

func TestValidateCore_basicAndSettingsIssues(t *testing.T) {
	tests := []struct {
		name, code, path string
		mutate           func(*scenario.CoreScenario)
	}{
		{"empty id", CodeRequired, "id", func(c *scenario.CoreScenario) { c.ID = "" }},
		{"blank title", CodeRequired, "title", func(c *scenario.CoreScenario) { c.Title = "  " }},
		{"invalid difficulty", CodeInvalidValue, "difficulty", func(c *scenario.CoreScenario) { c.Difficulty = "invalid" }},
		{"blank learning goal", CodeRequired, "learning_goals[0]", func(c *scenario.CoreScenario) { c.LearningGoals[0] = " " }},
		{"blank explanation", CodeRequired, "explanation", func(c *scenario.CoreScenario) { c.Explanation = " " }},
		{"negative healthy targets", CodeInvalidValue, "topology.nodes[1].settings.healthy_targets", func(c *scenario.CoreScenario) { *c = *withLB(-1) }},
		{"zero timeout", CodeInvalidValue, "topology.nodes[1].settings.timeout_ms", func(c *scenario.CoreScenario) { c.Topology.Nodes[1].ReverseProxy.TimeoutMS = 0 }},
		{"negative response delay", CodeInvalidValue, "topology.nodes[2].settings.response_delay_ms", func(c *scenario.CoreScenario) { c.Topology.Nodes[2].Application.ResponseDelayMS = -1 }},
		{"status out of range", CodeInvalidValue, "topology.nodes[2].settings.status_code", func(c *scenario.CoreScenario) { status := 600; c.Topology.Nodes[2].Application.StatusCode = &status }},
		{"invalid response type", CodeInvalidValue, "topology.nodes[2].settings.response_type", func(c *scenario.CoreScenario) { c.Topology.Nodes[2].Application.ResponseType = "bad" }},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			core := validCore()
			test.mutate(core)
			requireIssue(t, ValidateCore(core), test.code, test.path)
		})
	}
}

func TestValidateCore_category(t *testing.T) {
	for _, category := range scenario.Categories() {
		t.Run(string(category), func(t *testing.T) {
			core := validCore()
			core.Category = category
			for _, item := range ValidateCore(core).Issues {
				if item.Path == "category" {
					t.Fatalf("valid category issue = %#v", item)
				}
			}
		})
	}
	for _, category := range []scenario.Category{"http_status", "load_balancer", "malformed_response", "waf", "unknown", "", "   "} {
		t.Run("invalid_"+string(category), func(t *testing.T) {
			core := validCore()
			core.Category = category
			requireIssue(t, ValidateCore(core), CodeInvalidValue, "category")
		})
	}
}

func TestValidateCore_topologyIssues(t *testing.T) {
	tests := []struct {
		name, code string
		mutate     func(*scenario.CoreScenario)
	}{
		{"duplicate node id", CodeDuplicateID, func(c *scenario.CoreScenario) { c.Topology.Nodes[2].ID = "proxy" }},
		{"missing client", CodeInvalidTopology, func(c *scenario.CoreScenario) { c.Topology.Nodes[0].Type = scenario.NodeTypeEdge }},
		{"two clients", CodeInvalidTopology, func(c *scenario.CoreScenario) {
			c.Topology.Nodes[1].Type = scenario.NodeTypeClient
			c.Topology.Nodes[1].ReverseProxy = nil
		}},
		{"missing app", CodeInvalidTopology, func(c *scenario.CoreScenario) {
			c.Topology.Nodes[2].Type = scenario.NodeTypeEdge
			c.Topology.Nodes[2].Application = nil
		}},
		{"duplicate type", CodeDuplicateNodeType, func(c *scenario.CoreScenario) {
			c.Topology.Nodes[1].Type = scenario.NodeTypeApp
			c.Topology.Nodes[1].ReverseProxy = nil
			c.Topology.Nodes[1].Application = appSettings(0, scenario.ResponseTypeNormal)
		}},
		{"database", CodeMVPUnsupported, func(c *scenario.CoreScenario) {
			c.Topology.Nodes[1].Type = scenario.NodeTypeDB
			c.Topology.Nodes[1].ReverseProxy = nil
		}},
		{"unknown edge source", CodeUnknownReference, func(c *scenario.CoreScenario) { c.Topology.Edges[0].From = "ghost" }},
		{"unknown edge target", CodeUnknownReference, func(c *scenario.CoreScenario) { c.Topology.Edges[0].To = "ghost" }},
		{"self edge", CodeInvalidTopology, func(c *scenario.CoreScenario) { c.Topology.Edges[0].To = "client" }},
		{"duplicate edge", CodeDuplicateID, func(c *scenario.CoreScenario) { c.Topology.Edges = append(c.Topology.Edges, c.Topology.Edges[0]) }},
		{"branch", CodeInvalidTopology, func(c *scenario.CoreScenario) {
			c.Topology.Edges = append(c.Topology.Edges, scenario.Edge{From: "client", To: "app"})
		}},
		{"convergence", CodeInvalidTopology, func(c *scenario.CoreScenario) {
			c.Topology.Nodes = append(c.Topology.Nodes, scenario.Node{ID: "edge", Type: scenario.NodeTypeEdge, Label: "Edge"})
			c.Topology.Edges = []scenario.Edge{{From: "client", To: "proxy"}, {From: "client", To: "edge"}, {From: "proxy", To: "edge"}, {From: "edge", To: "app"}}
		}},
		{"cycle", CodeCycleDetected, func(c *scenario.CoreScenario) {
			c.Topology.Edges = append(c.Topology.Edges, scenario.Edge{From: "app", To: "client"})
		}},
		{"orphan", CodeOrphanNode, func(c *scenario.CoreScenario) {
			c.Topology.Nodes = append(c.Topology.Nodes, scenario.Node{ID: "edge", Type: scenario.NodeTypeEdge, Label: "Edge"})
		}},
		{"path ends before app", CodeInvalidTopology, func(c *scenario.CoreScenario) { c.Topology.Edges = c.Topology.Edges[:1] }},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			core := validCore()
			test.mutate(core)
			requireCode(t, ValidateCore(core), test.code)
		})
	}
}

func TestValidateCore_ruleConflicts(t *testing.T) {
	tests := []struct {
		name                        string
		waf, lb, timeout, malformed bool
		want                        string
	}{
		{"waf lb", true, true, false, false, "waf_block, lb_no_healthy_target"},
		{"waf timeout", true, false, true, false, "waf_block, reverse_proxy_timeout"},
		{"waf malformed", true, false, false, true, "waf_block, malformed_upstream_response"},
		{"lb timeout", false, true, true, false, "lb_no_healthy_target, reverse_proxy_timeout"},
		{"lb malformed", false, true, false, true, "lb_no_healthy_target, malformed_upstream_response"},
		{"timeout malformed", false, false, true, true, "reverse_proxy_timeout, malformed_upstream_response"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			core := conflictCore(test.waf, test.lb, test.timeout, test.malformed)
			result := ValidateCore(core)
			found := false
			for _, item := range result.Issues {
				if item.Code == CodeRuleConflict {
					found = true
					if item.Message != "conflicting terminal rules: "+test.want {
						t.Fatalf("message = %q", item.Message)
					}
				}
			}
			if !found {
				t.Fatalf("issues = %#v", result.Issues)
			}
		})
	}
}

func TestValidateCore_malformedRequiresProxy(t *testing.T) {
	core := validCore()
	core.Topology.Nodes[1].Type = scenario.NodeTypeEdge
	core.Topology.Nodes[1].ReverseProxy = nil
	core.Topology.Nodes[2].Application.ResponseType = scenario.ResponseTypeMalformed
	core.Topology.Nodes[2].Application.StatusCode = nil
	requireIssue(t, ValidateCore(core), CodeInvalidTopology, "topology")
}

func TestValidateDrillAndCoreIntegrity(t *testing.T) {
	core := validCore()
	drill := validDrill()
	if result := ValidateDrill(drill); !result.Valid() {
		t.Fatalf("ValidateDrill() = %#v", result.Issues)
	}
	if result := ValidateDrillAgainstCore(drill, core); !result.Valid() {
		t.Fatalf("ValidateDrillAgainstCore() = %#v", result.Issues)
	}

	tests := []struct {
		name, code string
		mutate     func(*scenario.DrillScenario)
	}{
		{"empty id", CodeRequired, func(d *scenario.DrillScenario) { d.ID = "" }},
		{"empty core id", CodeRequired, func(d *scenario.DrillScenario) { d.CoreID = "" }},
		{"invalid difficulty", CodeInvalidValue, func(d *scenario.DrillScenario) { d.Difficulty = "bad" }},
		{"blank variant note", CodeInvalidValue, func(d *scenario.DrillScenario) { d.VariantNote = " " }},
		{"empty overrides", CodeRequired, func(d *scenario.DrillScenario) { d.Overrides = scenario.DrillOverrides{} }},
		{"negative override", CodeInvalidValue, func(d *scenario.DrillScenario) {
			d.Overrides["proxy"] = scenario.SettingsOverride{ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 0}}
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			drill := validDrill()
			test.mutate(drill)
			requireCode(t, ValidateDrill(drill), test.code)
		})
	}

	drill = validDrill()
	drill.CoreID = "other"
	requireCode(t, ValidateDrillAgainstCore(drill, core), CodeCoreReferenceMismatch)
	drill = validDrill()
	drill.Overrides["ghost"] = drill.Overrides["proxy"]
	requireCode(t, ValidateDrillAgainstCore(drill, core), CodeOverrideTargetMissing)
	drill = validDrill()
	drill.Overrides["proxy"] = scenario.SettingsOverride{Application: appSettings(0, scenario.ResponseTypeNormal)}
	requireCode(t, ValidateDrillAgainstCore(drill, core), CodeOverrideTypeMismatch)
	drill = validDrill()
	drill.Overrides["client"] = scenario.SettingsOverride{ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 1}}
	requireCode(t, ValidateDrillAgainstCore(drill, core), CodeOverrideTypeMismatch)
}

func TestValidateDrill_issueOrderIsStable(t *testing.T) {
	drill := validDrill()
	drill.Overrides = scenario.DrillOverrides{
		"z": {ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 0}},
		"a": {ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 0}},
	}
	result := ValidateDrill(drill)
	if len(result.Issues) < 2 || result.Issues[0].Path != "overrides.a.timeout_ms" || result.Issues[1].Path != "overrides.z.timeout_ms" {
		t.Fatalf("issue order = %#v, want overrides.a before overrides.z", result.Issues)
	}
}

func validCore() *scenario.CoreScenario {
	return &scenario.CoreScenario{SchemaVersion: 2, Type: scenario.DocumentTypeCore, ID: "core", Title: "Core", Description: "Description", Difficulty: scenario.DifficultyBeginner, Category: "timeout", LearningGoals: []string{"goal"}, Topology: scenario.Topology{Nodes: []scenario.Node{{ID: "client", Type: scenario.NodeTypeClient, Label: "Client"}, {ID: "proxy", Type: scenario.NodeTypeProxy, Label: "Proxy", ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 10}}, {ID: "app", Type: scenario.NodeTypeApp, Label: "App", Application: appSettings(0, scenario.ResponseTypeNormal)}}, Edges: []scenario.Edge{{From: "client", To: "proxy"}, {From: "proxy", To: "app"}}}, Explanation: "Explanation", WrongExplanation: "Wrong", CloudConceptRefs: []string{"aws.example"}}
}
func appSettings(delay int, responseType scenario.ResponseType) *scenario.ApplicationSettings {
	settings := &scenario.ApplicationSettings{ResponseDelayMS: delay, ResponseType: responseType}
	if responseType == scenario.ResponseTypeNormal {
		code := 200
		settings.StatusCode = &code
	}
	return settings
}
func withProxy(timeout, delay int, responseType scenario.ResponseType) *scenario.CoreScenario {
	core := validCore()
	core.Topology.Nodes[1].ReverseProxy.TimeoutMS = timeout
	core.Topology.Nodes[2].Application = appSettings(delay, responseType)
	return core
}
func withLB(healthy int) *scenario.CoreScenario {
	core := validCore()
	core.Topology.Nodes = append([]scenario.Node{core.Topology.Nodes[0], {ID: "lb", Type: scenario.NodeTypeLB, Label: "LB", LoadBalancer: &scenario.LoadBalancerSettings{HealthyTargets: healthy}}}, core.Topology.Nodes[1:]...)
	core.Topology.Edges = []scenario.Edge{{From: "client", To: "lb"}, {From: "lb", To: "proxy"}, {From: "proxy", To: "app"}}
	return core
}
func withWAF(block bool) *scenario.CoreScenario {
	core := validCore()
	core.Topology.Nodes = append([]scenario.Node{core.Topology.Nodes[0], {ID: "waf", Type: scenario.NodeTypeWAF, Label: "WAF", WAF: &scenario.WAFSettings{Block: block}}}, core.Topology.Nodes[1:]...)
	core.Topology.Edges = []scenario.Edge{{From: "client", To: "waf"}, {From: "waf", To: "proxy"}, {From: "proxy", To: "app"}}
	return core
}
func conflictCore(waf, lb, timeout, malformed bool) *scenario.CoreScenario {
	core := validCore()
	if waf {
		core = withWAF(true)
	}
	if lb {
		if waf {
			core.Topology.Nodes = append([]scenario.Node{core.Topology.Nodes[0], {ID: "lb", Type: scenario.NodeTypeLB, Label: "LB", LoadBalancer: &scenario.LoadBalancerSettings{HealthyTargets: 0}}}, core.Topology.Nodes[1:]...)
			core.Topology.Edges = []scenario.Edge{{From: "client", To: "lb"}, {From: "lb", To: "waf"}, {From: "waf", To: "proxy"}, {From: "proxy", To: "app"}}
		} else {
			core = withLB(0)
		}
	}
	if timeout {
		for i := range core.Topology.Nodes {
			if core.Topology.Nodes[i].ReverseProxy != nil {
				core.Topology.Nodes[i].ReverseProxy.TimeoutMS = 1
			}
			if core.Topology.Nodes[i].Application != nil {
				core.Topology.Nodes[i].Application.ResponseDelayMS = 10
			}
		}
	}
	if malformed {
		for i := range core.Topology.Nodes {
			if core.Topology.Nodes[i].Application != nil {
				core.Topology.Nodes[i].Application.ResponseType = scenario.ResponseTypeMalformed
				core.Topology.Nodes[i].Application.StatusCode = nil
			}
		}
	}
	return core
}
func validDrill() *scenario.DrillScenario {
	return &scenario.DrillScenario{SchemaVersion: 2, Type: scenario.DocumentTypeDrill, ID: "drill", CoreID: "core", Title: "Drill", Description: "Description", Difficulty: scenario.DifficultyBeginner, VariantNote: "note", Overrides: scenario.DrillOverrides{"proxy": {ReverseProxy: &scenario.ReverseProxySettings{TimeoutMS: 2}}}}
}
func requireCode(t *testing.T, result Result, code string) {
	t.Helper()
	for _, item := range result.Issues {
		if item.Code == code {
			return
		}
	}
	t.Fatalf("issues %#v do not contain code %q", result.Issues, code)
}
func requireIssue(t *testing.T, result Result, code, path string) {
	t.Helper()
	for _, item := range result.Issues {
		if item.Code == code && item.Path == path {
			return
		}
	}
	t.Fatalf("issues %#v do not contain %s at %s", result.Issues, code, path)
}
