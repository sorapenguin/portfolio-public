package labcatalog

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"

	"golab/internal/lab"
)

// MaxLabDefinitionBytes limits one embedded public lab definition to 256 KiB.
const MaxLabDefinitionBytes = 256 << 10

func Decode(r io.Reader) (lab.Lab, error) {
	data, err := io.ReadAll(io.LimitReader(r, MaxLabDefinitionBytes+1))
	if err != nil {
		return lab.Lab{}, fmt.Errorf("read lab: %w", err)
	}
	if len(data) == 0 {
		return lab.Lab{}, fmt.Errorf("decode lab: empty input")
	}
	if len(data) > MaxLabDefinitionBytes {
		return lab.Lab{}, fmt.Errorf("decode lab: definition exceeds %d bytes", MaxLabDefinitionBytes)
	}
	var out lab.Lab
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&out); err != nil {
		return lab.Lab{}, fmt.Errorf("decode lab: %w", err)
	}
	var extra struct{}
	if err := decoder.Decode(&extra); err != io.EOF {
		return lab.Lab{}, fmt.Errorf("decode lab: trailing data")
	}
	if err := Validate(out); err != nil {
		return lab.Lab{}, err
	}
	return out, nil
}
