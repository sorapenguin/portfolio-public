package v2

import (
	"fmt"
	"io/fs"
	"sort"
)

// Catalog is an immutable, read-only collection of validated Core scenarios.
type Catalog struct {
	byID map[string]*CoreScenario
	list []*CoreScenario
}

// LoadCatalog decodes, validates, and indexes every document matching pattern.
func LoadCatalog(fsys fs.FS, pattern string, validateCore func(*CoreScenario) error) (*Catalog, error) {
	paths, err := fs.Glob(fsys, pattern)
	if err != nil {
		return nil, fmt.Errorf("glob v2 scenarios: %w", err)
	}
	byID := make(map[string]*CoreScenario, len(paths))
	for _, path := range paths {
		file, err := fsys.Open(path)
		if err != nil {
			return nil, fmt.Errorf("open v2 scenario %q: %w", path, err)
		}
		doc, decodeErr := Decode(file)
		closeErr := file.Close()
		if decodeErr != nil {
			return nil, fmt.Errorf("load v2 scenario %q: %w", path, decodeErr)
		}
		if closeErr != nil {
			return nil, fmt.Errorf("close v2 scenario %q: %w", path, closeErr)
		}
		if doc.Core == nil {
			return nil, fmt.Errorf("load v2 scenario %q: runtime catalog accepts Core only", path)
		}
		if _, exists := byID[doc.Core.ID]; exists {
			return nil, fmt.Errorf("duplicate v2 scenario id %q", doc.Core.ID)
		}
		if err := validateCore(doc.Core); err != nil {
			return nil, fmt.Errorf("validate v2 scenario %q: %w", path, err)
		}
		byID[doc.Core.ID] = doc.Core
	}
	list := make([]*CoreScenario, 0, len(byID))
	for _, core := range byID {
		list = append(list, core)
	}
	sort.Slice(list, func(i, j int) bool { return list[i].ID < list[j].ID })
	return &Catalog{byID: byID, list: list}, nil
}

func (c *Catalog) List() []*CoreScenario               { return append([]*CoreScenario(nil), c.list...) }
func (c *Catalog) Get(id string) (*CoreScenario, bool) { core, ok := c.byID[id]; return core, ok }
