package v2

import (
	"path/filepath"
	"strings"
	"testing"
)

func TestLoadFile_coreFixture(t *testing.T) {
	path := filepath.Join("..", "..", "..", "testdata", "scenarios", "v2", "core-proxy-timeout-001.json")
	document, err := LoadFile(path)
	if err != nil {
		t.Fatalf("LoadFile() error = %v", err)
	}
	if document.Type != DocumentTypeCore || document.Core == nil || document.Drill != nil {
		t.Fatalf("document kind = %#v, want Core only", document)
	}
	if document.ID() != "core-proxy-timeout-001" {
		t.Fatalf("ID() = %q", document.ID())
	}
	if len(document.Core.Topology.Nodes) != 3 {
		t.Fatalf("node count = %d, want 3", len(document.Core.Topology.Nodes))
	}
	if len(document.Core.CloudConceptRefs) != 3 {
		t.Fatalf("cloud concept refs = %d, want 3", len(document.Core.CloudConceptRefs))
	}

	proxy := document.Core.Topology.Nodes[1]
	if proxy.Type != NodeTypeProxy || proxy.ReverseProxy == nil || proxy.ReverseProxy.TimeoutMS != 5000 {
		t.Fatalf("proxy = %#v, want typed timeout_ms=5000", proxy)
	}
	app := document.Core.Topology.Nodes[2]
	if app.Type != NodeTypeApp || app.Application == nil {
		t.Fatalf("application = %#v, want typed application settings", app)
	}
	if app.Application.ResponseDelayMS != 6000 || app.Application.ResponseType != ResponseTypeNormal || app.Application.StatusCode == nil || *app.Application.StatusCode != 200 {
		t.Fatalf("application settings = %#v, want delay=6000 type=normal status=200", app.Application)
	}
}

func TestDecode_drillAndTrailingWhitespace(t *testing.T) {
	document, err := Decode(strings.NewReader(validDrillJSON + "\n\t "))
	if err != nil {
		t.Fatalf("Decode() error = %v", err)
	}
	if document.Type != DocumentTypeDrill || document.Drill == nil || document.Core != nil {
		t.Fatalf("document kind = %#v, want Drill only", document)
	}
	if document.Drill.CoreID != "core-proxy-timeout-001" || len(document.Drill.Overrides) != 2 {
		t.Fatalf("drill = %#v", document.Drill)
	}
	if override := document.Drill.Overrides["proxy"]; override.ReverseProxy == nil || override.ReverseProxy.TimeoutMS != 2000 {
		t.Fatalf("proxy override = %#v", override)
	}
	if override := document.Drill.Overrides["app"]; override.Application == nil || override.Application.ResponseDelayMS != 3500 {
		t.Fatalf("app override = %#v", override)
	}
}

func TestDecode_rejectsInvalidDocuments(t *testing.T) {
	tests := []struct {
		name string
		json string
		want string
	}{
		{"malformed JSON", `{`, "decode scenario envelope"},
		{"missing schema version", strings.Replace(validCoreJSON, `"schema_version":2,`, "", 1), "missing schema_version"},
		{"unsupported schema version", strings.Replace(validCoreJSON, `"schema_version":2`, `"schema_version":3`, 1), "unsupported schema_version"},
		{"missing type", strings.Replace(validCoreJSON, `"type":"core",`, "", 1), "missing type"},
		{"unknown type", strings.Replace(validCoreJSON, `"type":"core"`, `"type":"other"`, 1), "unsupported document type"},
		{"unknown core field", strings.Replace(validCoreJSON, `"id":"core-test",`, `"id":"core-test","events":[],`, 1), "unknown field"},
		{"unknown drill field", strings.Replace(validDrillJSON, `"id":"drill-test",`, `"id":"drill-test","events":[],`, 1), "unknown field"},
		{"unknown node field", strings.Replace(validCoreJSON, `"label":"Proxy",`, `"label":"Proxy","extra":true,`, 1), "unknown field"},
		{"unknown settings field", strings.Replace(validCoreJSON, `"timeout_ms":5000`, `"timeout_ms":5000,"extra":true`, 1), "unknown field"},
		{"unknown node type", strings.Replace(validCoreJSON, `"type":"proxy"`, `"type":"unknown"`, 1), "unsupported node type"},
		{"forbidden client settings", strings.Replace(validCoreJSON, `"label":"Client"`, `"label":"Client","settings":{}`, 1), "settings are not allowed"},
		{"missing proxy settings", strings.Replace(validCoreJSON, `,"settings":{"timeout_ms":5000}`, "", 1), "settings are required"},
		{"node settings mismatch", strings.Replace(validCoreJSON, `"timeout_ms":5000`, `"block":true`, 1), "unknown field"},
		{"unknown drill override", strings.Replace(validDrillJSON, `"timeout_ms":2000`, `"unknown":2000`, 1), "unsupported or mixed settings keys"},
		{"mixed drill override", strings.Replace(validDrillJSON, `"timeout_ms":2000`, `"timeout_ms":2000,"block":true`, 1), "unsupported or mixed settings keys"},
		{"second JSON document", validCoreJSON + validCoreJSON, "unexpected trailing JSON data"},
		{"trailing non-whitespace", validCoreJSON + " trailing", "unexpected trailing data"},
		{"normal without status", strings.Replace(validCoreJSON, `,"status_code":200`, "", 1), "status_code is required"},
		{"malformed with status", strings.Replace(strings.Replace(validCoreJSON, `"response_type":"normal"`, `"response_type":"malformed"`, 1), `"status_code":200`, `"status_code":200`, 1), "status_code is not allowed"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := Decode(strings.NewReader(test.json))
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Fatalf("Decode() error = %v, want containing %q", err, test.want)
			}
		})
	}
}

const validCoreJSON = `{
  "schema_version":2,
  "type":"core",
  "id":"core-test",
  "title":"Core test",
  "description":"Test document",
  "difficulty":"beginner",
  "category":"timeout",
  "learning_goals":["goal"],
  "topology":{
    "nodes":[
      {"id":"client","type":"client","label":"Client"},
      {"id":"proxy","type":"proxy","label":"Proxy","settings":{"timeout_ms":5000}},
      {"id":"app","type":"app","label":"Application","settings":{"response_delay_ms":6000,"response_type":"normal","status_code":200}}
    ],
    "edges":[{"from":"client","to":"proxy"},{"from":"proxy","to":"app"}]
  },
  "explanation":"Explanation",
  "wrong_explanation":"Wrong explanation",
  "cloud_concept_refs":["aws.alb.idle_timeout"]
}`

const validDrillJSON = `{
  "schema_version":2,
  "type":"drill",
  "id":"drill-test",
  "core_id":"core-proxy-timeout-001",
  "title":"Drill test",
  "description":"Test drill",
  "difficulty":"beginner",
  "overrides":{
    "proxy":{"timeout_ms":2000},
    "app":{"response_delay_ms":3500}
  }
}`
