package labcatalog

import (
	"errors"
	"strings"
	"testing"

	"golab/internal/lab"
)

func TestDecodeStrictAndCatalog(t *testing.T) {
	valid := `{"id":"lab-1","title":"Lab","description":"d","difficulty":"beginner","mode":"explore","provider":"aws","domain_id":"cloud","subcategory_ids":["security"],"engine":"http_path","mission":"m","learning_objectives":["g"],"topology":{"nodes":[{"id":"client","type":"client","display":{"label":"Client","service_name":"Client","simplification":"s"}}]},"controls":[{"id":"choice","label":"Choice","type":"single_choice","options":[{"id":"one","label":"One"}],"default_option_id":"one","required":true}],"tests":[{"id":"test","label":"Test"}],"simplifications":[{"id":"s","title":"S","description":"d"}]}`
	item, err := Decode(strings.NewReader(valid))
	if err != nil {
		t.Fatal(err)
	}
	catalog, err := New([]lab.Lab{item})
	if err != nil {
		t.Fatal(err)
	}
	got, err := catalog.Get("lab-1")
	if err != nil || got.ID != "lab-1" {
		t.Fatalf("get=%#v err=%v", got, err)
	}
	got.Title = "changed"
	again, _ := catalog.Get("lab-1")
	if again.Title == "changed" {
		t.Fatal("catalog leaked mutable value")
	}
	if _, err := New([]lab.Lab{item, item}); !errors.Is(err, ErrDuplicateLab) {
		t.Fatalf("duplicate error=%v", err)
	}
	if _, err := catalog.Get("missing"); !errors.Is(err, ErrUnknownLab) {
		t.Fatalf("unknown error=%v", err)
	}
	for _, input := range []string{"", `{"unknown":true}`, `{`, valid + valid, strings.Replace(valid, `"engine":"http_path"`, `"effect":"block"`, 1), strings.Replace(valid, `"engine":"http_path"`, `"engine_settings":{"x":1}`, 1)} {
		if _, err := Decode(strings.NewReader(input)); err == nil {
			t.Fatalf("Decode(%q) succeeded", input)
		}
	}
}
func TestValidateRejectsSemanticErrors(t *testing.T) {
	item := validLab()
	item.ID = "BAD"
	if err := Validate(item); err == nil {
		t.Fatal("invalid id accepted")
	}
	item = validLab()
	item.Controls[0].Options = nil
	if err := Validate(item); err == nil {
		t.Fatal("empty options accepted")
	}
	item = validLab()
	item.Simplifications = nil
	if err := Validate(item); err == nil {
		t.Fatal("missing simplification accepted")
	}
	item = validLab()
	item.Topology.Nodes = append(item.Topology.Nodes, item.Topology.Nodes[0])
	if err := Validate(item); err == nil {
		t.Fatal("duplicate node accepted")
	}
}
func validLab() lab.Lab {
	item, err := Decode(strings.NewReader(`{"id":"lab-1","title":"Lab","description":"d","difficulty":"beginner","mode":"explore","provider":"aws","domain_id":"cloud","subcategory_ids":["security"],"engine":"http_path","mission":"m","learning_objectives":["g"],"topology":{"nodes":[{"id":"client","type":"client","display":{"label":"Client","service_name":"Client","simplification":"s"}}]},"controls":[{"id":"choice","label":"Choice","type":"single_choice","options":[{"id":"one","label":"One"}],"default_option_id":"one","required":true}],"tests":[{"id":"test","label":"Test"}],"simplifications":[{"id":"s","title":"S","description":"d"}]}`))
	if err != nil {
		panic(err)
	}
	return item
}
