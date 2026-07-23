package http_path

import (
	"errors"
	"fmt"

	"golab/internal/lab"
	scenario "golab/internal/scenario/v2"
)

var (
	ErrUnknownTest      = errors.New("unknown test")
	ErrMissingSelection = errors.New("missing selection")
	ErrInvalidSelection = errors.New("invalid selection")
)

type CompiledInput struct {
	TestID          lab.TestID
	WAFBlock        bool
	HealthyTargets  int
	ProxyTimeoutMS  int
	ResponseDelayMS int
	ResponseType    scenario.ResponseType
	StatusCode      *int
}

func Compile(public lab.Lab, def Definition, selections lab.SelectionSet, testID lab.TestID) (CompiledInput, error) {
	if err := ValidateDefinition(public, def); err != nil {
		return CompiledInput{}, err
	}
	if testID != def.TestID {
		return CompiledInput{}, fmt.Errorf("%w: %s", ErrUnknownTest, testID)
	}
	input := CompiledInput{TestID: testID, ProxyTimeoutMS: def.ProxyTimeoutMS, ResponseDelayMS: def.ResponseDelayMS}
	seenWAF := def.WAF.Fixed != nil
	seenTargets := def.Targets.Fixed != nil
	seenApp := def.Application.Fixed != nil
	if def.WAF.Fixed != nil {
		input.WAFBlock = bool(*def.WAF.Fixed)
	}
	if def.Targets.Fixed != nil {
		input.HealthyTargets = int(*def.Targets.Fixed)
	}
	if def.Application.Fixed != nil {
		if err := applyApplication(&input, *def.Application.Fixed); err != nil {
			return CompiledInput{}, err
		}
	}
	controls := map[lab.ControlID]lab.Control{}
	for _, control := range public.Controls {
		controls[control.ID] = control
	}
	for controlID := range selections {
		if _, ok := controls[controlID]; !ok {
			return CompiledInput{}, fmt.Errorf("%w: unknown control", ErrInvalidSelection)
		}
	}
	if len(selections) != len(public.Controls) {
		return CompiledInput{}, fmt.Errorf("%w: control count", ErrMissingSelection)
	}
	for controlID, optionID := range selections {
		control, ok := controls[controlID]
		if !ok || optionID == "" {
			return CompiledInput{}, fmt.Errorf("%w: control or option", ErrInvalidSelection)
		}
		allowed := false
		for _, option := range control.Options {
			if option.ID == optionID {
				allowed = true
				break
			}
		}
		if !allowed {
			return CompiledInput{}, fmt.Errorf("%w: option does not belong to control", ErrInvalidSelection)
		}
		key := OptionKey{controlID, optionID}
		if effect, ok := def.WAF.Selection[key]; ok {
			input.WAFBlock = bool(effect)
			seenWAF = true
		}
		if effect, ok := def.Targets.Selection[key]; ok {
			input.HealthyTargets = int(effect)
			seenTargets = true
		}
		if effect, ok := def.Application.Selection[key]; ok {
			seenApp = true
			if err := applyApplication(&input, effect); err != nil {
				return CompiledInput{}, err
			}
		}
	}
	if !seenWAF || !seenTargets || !seenApp {
		return CompiledInput{}, fmt.Errorf("%w: required control", ErrMissingSelection)
	}
	// The engine validator rejects cores with multiple terminal conditions.
	// Keep the public SelectionSet untouched and project only the first terminal
	// condition into the typed engine input.
	if input.WAFBlock {
		input.HealthyTargets = int(TargetsReady)
		input.ResponseType = scenario.ResponseTypeNormal
		input.StatusCode = intPointer(200)
	} else if input.HealthyTargets == int(TargetsEmptyOrUnused) {
		input.ResponseType = scenario.ResponseTypeNormal
		input.StatusCode = intPointer(200)
	}
	return input, nil
}
func intPointer(value int) *int { return &value }

func applyApplication(input *CompiledInput, effect ApplicationEffect) error {
	switch effect {
	case ApplicationOK200:
		input.ResponseType = scenario.ResponseTypeNormal
		input.StatusCode = intPointer(200)
	case ApplicationNotFound404:
		input.ResponseType = scenario.ResponseTypeNormal
		input.StatusCode = intPointer(404)
	case ApplicationError500:
		input.ResponseType = scenario.ResponseTypeNormal
		input.StatusCode = intPointer(500)
	case ApplicationMalformedResponse:
		input.ResponseType = scenario.ResponseTypeMalformed
		input.StatusCode = nil
	default:
		return fmt.Errorf("%w: application effect", ErrInternalDefinition)
	}
	return nil
}
