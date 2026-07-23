package labrun

import (
	"fmt"

	"golab/internal/http_path"
	"golab/internal/lab"
	"golab/internal/labcatalog"
	simulation "golab/internal/simulation/v2"
)

type Service struct {
	catalog     *labcatalog.Catalog
	definitions map[lab.LabID]http_path.Definition
}

func New(catalog *labcatalog.Catalog, definitions []http_path.Definition) (*Service, error) {
	defs := map[lab.LabID]http_path.Definition{}
	for _, definition := range definitions {
		if _, ok := defs[definition.LabID]; ok {
			return nil, fmt.Errorf("duplicate internal definition")
		}
		public, err := catalog.Get(definition.LabID)
		if err != nil {
			return nil, err
		}
		if err := http_path.ValidateDefinition(public, definition); err != nil {
			return nil, err
		}
		defs[definition.LabID] = definition
	}
	return &Service{catalog: catalog, definitions: defs}, nil
}
func (s *Service) Run(labID lab.LabID, selections lab.SelectionSet, testID lab.TestID) (lab.RunResult, error) {
	public, err := s.catalog.Get(labID)
	if err != nil {
		return lab.RunResult{}, err
	}
	definition, ok := s.definitions[labID]
	if !ok {
		return lab.RunResult{}, fmt.Errorf("%w: missing definition", http_path.ErrInternalDefinition)
	}
	input, err := http_path.Compile(public, definition, selections, testID)
	if err != nil {
		return lab.RunResult{}, err
	}
	core, err := http_path.ToScenario(input)
	if err != nil {
		return lab.RunResult{}, fmt.Errorf("scenario adapter: %w", err)
	}
	engine, err := simulation.Simulate(core)
	if err != nil {
		return lab.RunResult{}, fmt.Errorf("engine execution: %w", err)
	}
	return http_path.ToRunResult(public, definition, input, engine, labels(public, selections))
}
func labels(public lab.Lab, selections lab.SelectionSet) map[lab.ControlID]string {
	out := map[lab.ControlID]string{}
	for _, control := range public.Controls {
		optionID := selections[control.ID]
		for _, option := range control.Options {
			if option.ID == optionID {
				out[control.ID] = option.Label
				break
			}
		}
	}
	return out
}
