package scenario

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Catalog holds all loaded scenarios indexed by ID.
type Catalog struct {
	scenarios map[string]Scenario
	ordered   []Scenario
}

// LoadCatalog reads all *.json files from dir.
// schema.json is skipped. Duplicate IDs are rejected.
func LoadCatalog(dir string) (*Catalog, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("read directory %s: %w", dir, err)
	}

	c := &Catalog{scenarios: make(map[string]Scenario)}

	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") || e.Name() == "schema.json" {
			continue
		}

		path := filepath.Join(dir, e.Name())
		sc, err := loadFile(path)
		if err != nil {
			return nil, fmt.Errorf("load %s: %w", path, err)
		}
		if _, dup := c.scenarios[sc.ID]; dup {
			return nil, fmt.Errorf("duplicate scenario ID %q in %s", sc.ID, path)
		}
		c.scenarios[sc.ID] = sc
		c.ordered = append(c.ordered, sc)
	}

	return c, nil
}

func loadFile(path string) (Scenario, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Scenario{}, err
	}
	var sc Scenario
	if err := json.Unmarshal(data, &sc); err != nil {
		return Scenario{}, fmt.Errorf("parse JSON: %w", err)
	}
	return sc, nil
}

// Get returns a scenario by ID.
func (c *Catalog) Get(id string) (Scenario, bool) {
	sc, ok := c.scenarios[id]
	return sc, ok
}

// All returns all scenarios in load order.
func (c *Catalog) All() []Scenario {
	return c.ordered
}
