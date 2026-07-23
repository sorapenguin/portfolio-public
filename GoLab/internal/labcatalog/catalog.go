package labcatalog

import (
	"errors"
	"fmt"
	"sort"

	"golab/internal/lab"
)

var ErrUnknownLab = errors.New("unknown lab")
var ErrDuplicateLab = errors.New("duplicate lab")

type Catalog struct {
	byID map[lab.LabID]lab.Lab
	list []lab.Lab
}

func New(labs []lab.Lab) (*Catalog, error) {
	byID := make(map[lab.LabID]lab.Lab, len(labs))
	for _, item := range labs {
		if err := Validate(item); err != nil {
			return nil, err
		}
		if _, ok := byID[item.ID]; ok {
			return nil, fmt.Errorf("%w: %s", ErrDuplicateLab, item.ID)
		}
		byID[item.ID] = cloneLab(item)
	}
	list := make([]lab.Lab, 0, len(byID))
	for _, item := range byID {
		list = append(list, cloneLab(item))
	}
	sort.Slice(list, func(i, j int) bool { return list[i].ID < list[j].ID })
	return &Catalog{byID: byID, list: list}, nil
}
func (c *Catalog) Get(id lab.LabID) (lab.Lab, error) {
	item, ok := c.byID[id]
	if !ok {
		return lab.Lab{}, fmt.Errorf("%w: %s", ErrUnknownLab, id)
	}
	return cloneLab(item), nil
}
func (c *Catalog) List() []lab.Lab {
	out := make([]lab.Lab, len(c.list))
	for i := range c.list {
		out[i] = cloneLab(c.list[i])
	}
	return out
}

func cloneLab(in lab.Lab) lab.Lab {
	out := in
	out.SubcategoryIDs = append([]lab.SubcategoryID(nil), in.SubcategoryIDs...)
	out.Tags = append([]lab.Tag(nil), in.Tags...)
	out.LearningObjectives = append([]string(nil), in.LearningObjectives...)
	out.Controls = append([]lab.Control(nil), in.Controls...)
	out.Tests = append([]lab.TestDefinition(nil), in.Tests...)
	out.Simplifications = append([]lab.Simplification(nil), in.Simplifications...)
	out.Topology.Nodes = append([]lab.TopologyNode(nil), in.Topology.Nodes...)
	out.Topology.Edges = append([]lab.TopologyEdge(nil), in.Topology.Edges...)
	for i := range out.Controls {
		out.Controls[i].Options = append([]lab.Option(nil), in.Controls[i].Options...)
	}
	for i := range out.Topology.Nodes {
		out.Topology.Nodes[i].Display.AttachedPolicies = append([]lab.AttachedPolicy(nil), in.Topology.Nodes[i].Display.AttachedPolicies...)
	}
	return out
}
