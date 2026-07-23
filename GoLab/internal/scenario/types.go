package scenario

// Scenario holds a complete scenario definition loaded from JSON.
type Scenario struct {
	SchemaVersion    string            `json:"schemaVersion"`
	ID               string            `json:"id"`
	Title            string            `json:"title"`
	Description      string            `json:"description"`
	Difficulty       string            `json:"difficulty"`
	Category         string            `json:"category"`
	LearningGoals    []string          `json:"learningGoals"`
	Nodes            []Node            `json:"nodes"`
	Connections      []Connection      `json:"connections"`
	Choices          []Choice          `json:"choices"`
	Events           []Event           `json:"events"`
	ExpectedStatus   int               `json:"expectedStatus"`
	FailPoint        string            `json:"failPoint"`
	ReachedNodes     []string          `json:"reachedNodes"`
	Explanation      string            `json:"explanation"`
	WrongExplanation string            `json:"wrongExplanation"`
	CloudMapping     map[string]string `json:"cloudMapping,omitempty"`
}

// Node is a component in the request path (e.g. dns, lb, app).
type Node struct {
	ID       string                 `json:"id"`
	Type     string                 `json:"type"`
	Label    string                 `json:"label"`
	Settings map[string]interface{} `json:"settings,omitempty"`
}

// Connection is a directed edge between two nodes.
type Connection struct {
	From string `json:"from"`
	To   string `json:"to"`
}

// Choice is one of the options the user can predict.
type Choice struct {
	ID    string `json:"id"`
	Label string `json:"label"`
}

// Event is a single step in the deterministic simulation timeline.
// DelayMs is virtual time — no real sleep occurs.
type Event struct {
	NodeID      string `json:"nodeId"`
	DelayMs     int    `json:"delayMs"`
	Description string `json:"description"`
	Status      string `json:"status"` // ok | error | timeout
}

// Summary is returned in list responses (no answer fields).
type Summary struct {
	ID            string   `json:"id"`
	Title         string   `json:"title"`
	Description   string   `json:"description"`
	Difficulty    string   `json:"difficulty"`
	Category      string   `json:"category"`
	LearningGoals []string `json:"learningGoals"`
}

// PublicScenario omits answer fields (explanation, failPoint, reachedNodes).
type PublicScenario struct {
	SchemaVersion  string            `json:"schemaVersion"`
	ID             string            `json:"id"`
	Title          string            `json:"title"`
	Description    string            `json:"description"`
	Difficulty     string            `json:"difficulty"`
	Category       string            `json:"category"`
	LearningGoals  []string          `json:"learningGoals"`
	Nodes          []Node            `json:"nodes"`
	Connections    []Connection      `json:"connections"`
	Choices        []Choice          `json:"choices"`
	ExpectedStatus int               `json:"expectedStatus"`
	CloudMapping   map[string]string `json:"cloudMapping,omitempty"`
}

// Summary returns list-safe fields.
func (s Scenario) Summary() Summary {
	return Summary{
		ID:            s.ID,
		Title:         s.Title,
		Description:   s.Description,
		Difficulty:    s.Difficulty,
		Category:      s.Category,
		LearningGoals: s.LearningGoals,
	}
}

// Public returns scenario fields safe to expose before the user submits an answer.
func (s Scenario) Public() PublicScenario {
	return PublicScenario{
		SchemaVersion:  s.SchemaVersion,
		ID:             s.ID,
		Title:          s.Title,
		Description:    s.Description,
		Difficulty:     s.Difficulty,
		Category:       s.Category,
		LearningGoals:  s.LearningGoals,
		Nodes:          s.Nodes,
		Connections:    s.Connections,
		Choices:        s.Choices,
		ExpectedStatus: s.ExpectedStatus,
		CloudMapping:   s.CloudMapping,
	}
}
