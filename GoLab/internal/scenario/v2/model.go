// Package v2 contains the isolated Schema v2 scenario input model.
package v2

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
)

type DocumentType string

const (
	DocumentTypeCore  DocumentType = "core"
	DocumentTypeDrill DocumentType = "drill"
)

type Difficulty string

const (
	DifficultyBeginner     Difficulty = "beginner"
	DifficultyIntermediate Difficulty = "intermediate"
	DifficultyAdvanced     Difficulty = "advanced"
)

// Category is the stable broad teaching classification for a core scenario.
// It is neither a node type nor a rule identifier.
type Category string

const (
	CategoryTimeout      Category = "timeout"
	CategoryError        Category = "error"
	CategoryRedirect     Category = "redirect"
	CategorySecurity     Category = "security"
	CategoryAvailability Category = "availability"
)

var validCategories = [...]Category{
	CategoryTimeout,
	CategoryError,
	CategoryRedirect,
	CategorySecurity,
	CategoryAvailability,
}

// Categories returns the permitted Schema v2 category values in stable order.
func Categories() []Category {
	return append([]Category(nil), validCategories[:]...)
}

// Valid reports whether c is a category permitted by Schema v2.
func (c Category) Valid() bool {
	for _, category := range validCategories {
		if c == category {
			return true
		}
	}
	return false
}

type NodeType string

const (
	NodeTypeClient NodeType = "client"
	NodeTypeDNS    NodeType = "dns"
	NodeTypeEdge   NodeType = "edge"
	NodeTypeWAF    NodeType = "waf"
	NodeTypeLB     NodeType = "lb"
	NodeTypeProxy  NodeType = "proxy"
	NodeTypeApp    NodeType = "app"
	NodeTypeDB     NodeType = "db"
)

type ResponseType string

const (
	ResponseTypeNormal    ResponseType = "normal"
	ResponseTypeMalformed ResponseType = "malformed"
)

// Document holds exactly one Schema v2 document kind.
type Document struct {
	Type  DocumentType
	Core  *CoreScenario
	Drill *DrillScenario
}

func (d Document) ID() string {
	if d.Core != nil {
		return d.Core.ID
	}
	if d.Drill != nil {
		return d.Drill.ID
	}
	return ""
}

type CoreScenario struct {
	SchemaVersion    int          `json:"schema_version"`
	Type             DocumentType `json:"type"`
	ID               string       `json:"id"`
	Title            string       `json:"title"`
	Description      string       `json:"description"`
	Difficulty       Difficulty   `json:"difficulty"`
	Category         Category     `json:"category"`
	LearningGoals    []string     `json:"learning_goals"`
	Topology         Topology     `json:"topology"`
	Explanation      string       `json:"explanation"`
	WrongExplanation string       `json:"wrong_explanation"`
	CloudConceptRefs []string     `json:"cloud_concept_refs"`
}

type DrillScenario struct {
	SchemaVersion int            `json:"schema_version"`
	Type          DocumentType   `json:"type"`
	ID            string         `json:"id"`
	CoreID        string         `json:"core_id"`
	Title         string         `json:"title"`
	Description   string         `json:"description"`
	Difficulty    Difficulty     `json:"difficulty"`
	VariantNote   string         `json:"variant_note"`
	Overrides     DrillOverrides `json:"overrides"`
}

type Topology struct {
	Nodes []Node `json:"nodes"`
	Edges []Edge `json:"edges"`
}

type Edge struct {
	From string `json:"from"`
	To   string `json:"to"`
}

// Node stores settings in the one typed field matching Type. Nodes with no
// settings leave all settings fields nil.
type Node struct {
	ID    string   `json:"id"`
	Type  NodeType `json:"type"`
	Label string   `json:"label"`

	WAF          *WAFSettings          `json:"-"`
	LoadBalancer *LoadBalancerSettings `json:"-"`
	ReverseProxy *ReverseProxySettings `json:"-"`
	Application  *ApplicationSettings  `json:"-"`
}

type WAFSettings struct {
	Block bool `json:"block"`
}

type LoadBalancerSettings struct {
	HealthyTargets int `json:"healthy_targets"`
}

type ReverseProxySettings struct {
	TimeoutMS int `json:"timeout_ms"`
}

type ApplicationSettings struct {
	ResponseDelayMS int          `json:"response_delay_ms"`
	ResponseType    ResponseType `json:"response_type"`
	StatusCode      *int         `json:"status_code,omitempty"`
}

type DrillOverrides map[string]SettingsOverride

// SettingsOverride stores exactly one supported partial settings shape.
type SettingsOverride struct {
	WAF          *WAFSettings
	LoadBalancer *LoadBalancerSettings
	ReverseProxy *ReverseProxySettings
	Application  *ApplicationSettings
}

func (t *Topology) UnmarshalJSON(data []byte) error {
	type rawTopology Topology
	var raw rawTopology
	if err := decodeStrict(data, &raw); err != nil {
		return fmt.Errorf("decode topology: %w", err)
	}
	if err := requireFields(data, "nodes", "edges"); err != nil {
		return fmt.Errorf("decode topology: %w", err)
	}
	*t = Topology(raw)
	return nil
}

func (e *Edge) UnmarshalJSON(data []byte) error {
	type rawEdge Edge
	var raw rawEdge
	if err := decodeStrict(data, &raw); err != nil {
		return fmt.Errorf("decode edge: %w", err)
	}
	if err := requireFields(data, "from", "to"); err != nil {
		return fmt.Errorf("decode edge: %w", err)
	}
	*e = Edge(raw)
	return nil
}

func (n *Node) UnmarshalJSON(data []byte) error {
	var raw struct {
		ID       string          `json:"id"`
		Type     NodeType        `json:"type"`
		Label    string          `json:"label"`
		Settings json.RawMessage `json:"settings"`
	}
	if err := decodeStrict(data, &raw); err != nil {
		return fmt.Errorf("decode node: %w", err)
	}
	if err := requireFields(data, "id", "type", "label"); err != nil {
		return fmt.Errorf("decode node: %w", err)
	}

	*n = Node{ID: raw.ID, Type: raw.Type, Label: raw.Label}
	if isSettingsForbidden(raw.Type) {
		if raw.Settings != nil {
			return fmt.Errorf("decode node %q: settings are not allowed for type %q", raw.ID, raw.Type)
		}
		return nil
	}
	if raw.Settings == nil {
		return fmt.Errorf("decode node %q: settings are required for type %q", raw.ID, raw.Type)
	}

	switch raw.Type {
	case NodeTypeWAF:
		settings := new(WAFSettings)
		if err := decodeRequiredSettings(raw.Settings, settings, "block"); err != nil {
			return fmt.Errorf("decode settings for node %q: %w", raw.ID, err)
		}
		n.WAF = settings
	case NodeTypeLB:
		settings := new(LoadBalancerSettings)
		if err := decodeRequiredSettings(raw.Settings, settings, "healthy_targets"); err != nil {
			return fmt.Errorf("decode settings for node %q: %w", raw.ID, err)
		}
		n.LoadBalancer = settings
	case NodeTypeProxy:
		settings := new(ReverseProxySettings)
		if err := decodeRequiredSettings(raw.Settings, settings, "timeout_ms"); err != nil {
			return fmt.Errorf("decode settings for node %q: %w", raw.ID, err)
		}
		n.ReverseProxy = settings
	case NodeTypeApp:
		settings := new(ApplicationSettings)
		if err := decodeRequiredSettings(raw.Settings, settings, "response_delay_ms", "response_type"); err != nil {
			return fmt.Errorf("decode settings for node %q: %w", raw.ID, err)
		}
		if err := validateApplicationSettings(settings); err != nil {
			return fmt.Errorf("decode settings for node %q: %w", raw.ID, err)
		}
		n.Application = settings
	default:
		return fmt.Errorf("unsupported node type %q", raw.Type)
	}
	return nil
}

func (o *SettingsOverride) UnmarshalJSON(data []byte) error {
	var fields map[string]json.RawMessage
	if err := decodeStrict(data, &fields); err != nil {
		return fmt.Errorf("decode override settings: %w", err)
	}
	if len(fields) == 0 {
		return fmt.Errorf("decode override settings: empty object")
	}
	*o = SettingsOverride{}

	if hasOnly(fields, "block") {
		settings := new(WAFSettings)
		if err := decodeRequiredSettings(data, settings, "block"); err != nil {
			return fmt.Errorf("decode override WAF settings: %w", err)
		}
		o.WAF = settings
		return nil
	}
	if hasOnly(fields, "healthy_targets") {
		settings := new(LoadBalancerSettings)
		if err := decodeRequiredSettings(data, settings, "healthy_targets"); err != nil {
			return fmt.Errorf("decode override load balancer settings: %w", err)
		}
		o.LoadBalancer = settings
		return nil
	}
	if hasOnly(fields, "timeout_ms") {
		settings := new(ReverseProxySettings)
		if err := decodeRequiredSettings(data, settings, "timeout_ms"); err != nil {
			return fmt.Errorf("decode override reverse proxy settings: %w", err)
		}
		o.ReverseProxy = settings
		return nil
	}
	if hasOnly(fields, "response_delay_ms", "response_type", "status_code") {
		settings := new(ApplicationSettings)
		if err := decodeStrict(data, settings); err != nil {
			return fmt.Errorf("decode override application settings: %w", err)
		}
		if settings.ResponseType == ResponseTypeMalformed && settings.StatusCode != nil {
			return fmt.Errorf("decode override application settings: status_code is not allowed for malformed response_type")
		}
		o.Application = settings
		return nil
	}
	return fmt.Errorf("decode override settings: unsupported or mixed settings keys")
}

func decodeStrict(data []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := ensureEOF(decoder); err != nil {
		return err
	}
	return nil
}

func ensureEOF(decoder *json.Decoder) error {
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return fmt.Errorf("unexpected trailing JSON data")
		}
		return fmt.Errorf("unexpected trailing data: %w", err)
	}
	return nil
}

func requireFields(data []byte, names ...string) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	for _, name := range names {
		value, ok := fields[name]
		if !ok || bytes.Equal(value, []byte("null")) {
			return fmt.Errorf("missing required field %q", name)
		}
	}
	return nil
}

func decodeRequiredSettings(data []byte, target any, required ...string) error {
	if err := decodeStrict(data, target); err != nil {
		return err
	}
	return requireFields(data, required...)
}

func isSettingsForbidden(nodeType NodeType) bool {
	switch nodeType {
	case NodeTypeClient, NodeTypeDNS, NodeTypeEdge, NodeTypeDB:
		return true
	default:
		return false
	}
}

func validateApplicationSettings(settings *ApplicationSettings) error {
	switch settings.ResponseType {
	case ResponseTypeNormal:
		if settings.StatusCode == nil {
			return fmt.Errorf("status_code is required for normal response_type")
		}
	case ResponseTypeMalformed:
		if settings.StatusCode != nil {
			return fmt.Errorf("status_code is not allowed for malformed response_type")
		}
	}
	return nil
}

func hasOnly(fields map[string]json.RawMessage, allowed ...string) bool {
	if len(fields) == 0 {
		return false
	}
	allowedSet := make(map[string]struct{}, len(allowed))
	for _, key := range allowed {
		allowedSet[key] = struct{}{}
	}
	for key := range fields {
		if _, ok := allowedSet[key]; !ok {
			return false
		}
	}
	return true
}
