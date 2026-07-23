package v2_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	scenario "golab/internal/scenario/v2"
)

func TestCategoryContractMatchesSchema(t *testing.T) {
	data, err := os.ReadFile(filepath.Join("..", "..", "..", "schema", "scenario-v2.schema.json"))
	if err != nil {
		t.Fatal(err)
	}
	var schema map[string]any
	if err := json.Unmarshal(data, &schema); err != nil {
		t.Fatal(err)
	}
	values := coreCategoryEnum(t, schema)
	fromSchema := make(map[string]struct{}, len(values))
	for _, value := range values {
		if _, duplicate := fromSchema[value]; duplicate {
			t.Fatalf("duplicate Schema category %q", value)
		}
		fromSchema[value] = struct{}{}
	}
	goCategories := scenario.Categories()
	if len(values) != len(goCategories) {
		t.Fatalf("Schema category count = %d, Go category count = %d", len(values), len(goCategories))
	}
	for _, category := range goCategories {
		if !category.Valid() {
			t.Fatalf("Go category %q is not valid", category)
		}
		if _, ok := fromSchema[string(category)]; !ok {
			t.Fatalf("Go category %q is absent from Schema", category)
		}
	}
	for _, old := range []scenario.Category{"http_status", "load_balancer", "malformed_response", "waf", "unknown", "", "   "} {
		if old.Valid() {
			t.Fatalf("obsolete category %q is valid", old)
		}
	}
}

func coreCategoryEnum(t *testing.T, schema map[string]any) []string {
	t.Helper()
	defs, ok := schema["$defs"].(map[string]any)
	if !ok {
		t.Fatal("Schema $defs is missing")
	}
	core, ok := defs["CoreScenario"].(map[string]any)
	if !ok {
		t.Fatal("Schema CoreScenario is missing")
	}
	properties, ok := core["properties"].(map[string]any)
	if !ok {
		t.Fatal("Schema CoreScenario properties are missing")
	}
	category, ok := properties["category"].(map[string]any)
	if !ok {
		t.Fatal("Schema CoreScenario category is missing")
	}
	rawEnum, ok := category["enum"].([]any)
	if !ok {
		t.Fatal("Schema CoreScenario category enum is missing")
	}
	values := make([]string, 0, len(rawEnum))
	for _, raw := range rawEnum {
		value, ok := raw.(string)
		if !ok {
			t.Fatalf("Schema category enum value has type %T", raw)
		}
		values = append(values, value)
	}
	return values
}
