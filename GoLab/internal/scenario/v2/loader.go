package v2

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
)

const SchemaVersion = 2

// Decode reads one complete Schema v2 Core or Drill document from r.
func Decode(r io.Reader) (Document, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return Document{}, fmt.Errorf("read scenario document: %w", err)
	}

	var envelope struct {
		SchemaVersion *int          `json:"schema_version"`
		Type          *DocumentType `json:"type"`
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	if err := decoder.Decode(&envelope); err != nil {
		return Document{}, fmt.Errorf("decode scenario envelope: %w", err)
	}
	if err := ensureEOF(decoder); err != nil {
		return Document{}, fmt.Errorf("decode scenario envelope: %w", err)
	}
	if envelope.SchemaVersion == nil {
		return Document{}, fmt.Errorf("decode scenario envelope: missing schema_version")
	}
	if *envelope.SchemaVersion != SchemaVersion {
		return Document{}, fmt.Errorf("unsupported schema_version %d", *envelope.SchemaVersion)
	}
	if envelope.Type == nil {
		return Document{}, fmt.Errorf("decode scenario envelope: missing type")
	}

	switch *envelope.Type {
	case DocumentTypeCore:
		var core CoreScenario
		if err := decodeStrict(data, &core); err != nil {
			return Document{}, fmt.Errorf("decode core scenario: %w", err)
		}
		if err := requireFields(data, "schema_version", "type", "id", "title", "description", "difficulty", "category", "learning_goals", "topology", "explanation", "wrong_explanation"); err != nil {
			return Document{}, fmt.Errorf("decode core scenario: %w", err)
		}
		return Document{Type: DocumentTypeCore, Core: &core}, nil
	case DocumentTypeDrill:
		var drill DrillScenario
		if err := decodeStrict(data, &drill); err != nil {
			return Document{}, fmt.Errorf("decode drill scenario: %w", err)
		}
		if err := requireFields(data, "schema_version", "type", "id", "core_id", "title", "description", "difficulty", "overrides"); err != nil {
			return Document{}, fmt.Errorf("decode drill scenario: %w", err)
		}
		return Document{Type: DocumentTypeDrill, Drill: &drill}, nil
	default:
		return Document{}, fmt.Errorf("unsupported document type %q", *envelope.Type)
	}
}

// LoadFile opens and decodes one Schema v2 document.
func LoadFile(path string) (Document, error) {
	file, err := os.Open(path)
	if err != nil {
		return Document{}, fmt.Errorf("load scenario file %q: %w", path, err)
	}
	defer file.Close()

	document, err := Decode(file)
	if err != nil {
		return Document{}, fmt.Errorf("load scenario file %q: %w", path, err)
	}
	return document, nil
}
