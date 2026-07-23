package learningmodule

import (
	"bytes"
	"strings"
	"testing"
)

func TestDecodeRejectsNonStrictBodies(t *testing.T) {
	for _, body := range []string{"", " ", "null", `{"schema_version":1,"unknown":true}`, `{} {}`} {
		if _, err := Decode(strings.NewReader(body)); err == nil {
			t.Fatalf("accepted %q", body)
		}
	}
}

func TestDecodeRejectsStrictJSONCategories(t *testing.T) {
	for _, tc := range []struct{ name, body string }{
		{"whitespace", "\t\n"},
		{"syntax", "{"},
		{"multiple", "{} {}"},
		{"trailing", "{} []"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := Decode(strings.NewReader(tc.body)); err == nil {
				t.Fatal("accepted invalid JSON")
			}
		})
	}
	if _, err := Decode(bytes.NewReader(bytes.Repeat([]byte(" "), MaxModuleBytes+1))); err == nil {
		t.Fatal("accepted oversized module")
	}
	module, err := Decode(strings.NewReader("{}"))
	if err != nil {
		t.Fatalf("decode missing schema version: %v", err)
	}
	if err := Validate(module); err == nil {
		t.Fatal("accepted missing schema version")
	}
}
