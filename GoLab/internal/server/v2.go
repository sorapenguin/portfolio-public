package server

import (
	"bytes"
	"encoding/json"
	"io"
	"mime"
	"net/http"
	"strings"

	scenariov2 "golab/internal/scenario/v2"
	simulationv2 "golab/internal/simulation/v2"
)

const v2RequestLimit = 64 << 10

type v2Error struct {
	Error v2ErrorBody `json:"error"`
}
type v2ErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
type v2Summary struct {
	ID          string                `json:"id"`
	Title       string                `json:"title"`
	Description string                `json:"description"`
	Difficulty  scenariov2.Difficulty `json:"difficulty"`
	Category    string                `json:"category"`
}
type v2Node struct {
	ID       string              `json:"id"`
	Type     scenariov2.NodeType `json:"type"`
	Label    string              `json:"label"`
	Settings any                 `json:"settings,omitempty"`
}
type v2Topology struct {
	Nodes []v2Node          `json:"nodes"`
	Edges []scenariov2.Edge `json:"edges"`
}
type v2Detail struct {
	ID               string                `json:"id"`
	Title            string                `json:"title"`
	Description      string                `json:"description"`
	Difficulty       scenariov2.Difficulty `json:"difficulty"`
	Category         string                `json:"category"`
	LearningGoals    []string              `json:"learning_goals"`
	Topology         v2Topology            `json:"topology"`
	CloudConceptRefs []string              `json:"cloud_concept_refs,omitempty"`
}
type v2SimRequest struct {
	ScenarioID                    string `json:"scenario_id"`
	PredictedResponseOriginNodeID string `json:"predicted_response_origin_node_id"`
}
type v2Event struct {
	AtMS       int                    `json:"at_ms"`
	NodeID     string                 `json:"node_id"`
	Kind       simulationv2.EventKind `json:"kind"`
	MessageKey string                 `json:"message_key"`
	Details    map[string]int         `json:"details"`
}
type v2Result struct {
	ResponseOriginNodeID string                   `json:"response_origin_node_id"`
	HTTPStatus           int                      `json:"http_status"`
	Outcome              simulationv2.Outcome     `json:"outcome"`
	RuleID               simulationv2.RuleID      `json:"rule_id"`
	ElapsedMS            int                      `json:"elapsed_ms"`
	Events               []v2Event                `json:"events"`
	NodeStates           []simulationv2.NodeState `json:"node_states"`
}
type v2SimulationResponse struct {
	ScenarioID                    string   `json:"scenario_id"`
	PredictedResponseOriginNodeID string   `json:"predicted_response_origin_node_id"`
	Correct                       bool     `json:"correct"`
	Result                        v2Result `json:"result"`
	Explanation                   string   `json:"explanation"`
	WrongExplanation              string   `json:"wrong_explanation,omitempty"`
}

func (s *Server) handleV2Scenarios(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeV2Error(w, http.StatusMethodNotAllowed, "method_not_allowed", "method is not allowed")
		return
	}
	list := s.catalogV2.List()
	out := make([]v2Summary, 0, len(list))
	for _, core := range list {
		out = append(out, v2Summary{core.ID, core.Title, core.Description, core.Difficulty, string(core.Category)})
	}
	writeJSON(w, http.StatusOK, struct {
		Scenarios []v2Summary `json:"scenarios"`
	}{out})
}
func (s *Server) handleV2Scenario(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeV2Error(w, http.StatusMethodNotAllowed, "method_not_allowed", "method is not allowed")
		return
	}
	core, ok := s.catalogV2.Get(r.PathValue("id"))
	if !ok {
		writeV2Error(w, http.StatusNotFound, "scenario_not_found", "scenario was not found")
		return
	}
	writeJSON(w, http.StatusOK, detailDTO(core))
}
func (s *Server) handleV2Simulate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeV2Error(w, http.StatusMethodNotAllowed, "method_not_allowed", "method is not allowed")
		return
	}
	mediaType, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || mediaType != "application/json" {
		writeV2Error(w, http.StatusUnsupportedMediaType, "unsupported_media_type", "Content-Type must be application/json")
		return
	}
	if r.ContentLength > v2RequestLimit {
		writeV2Error(w, http.StatusRequestEntityTooLarge, "request_too_large", "request body is too large")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, v2RequestLimit)
	var req v2SimRequest
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&req); err != nil {
		if strings.Contains(err.Error(), "request body too large") {
			writeV2Error(w, http.StatusRequestEntityTooLarge, "request_too_large", "request body is too large")
		} else {
			writeV2Error(w, http.StatusBadRequest, "invalid_request", "request body is invalid")
		}
		return
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		writeV2Error(w, http.StatusBadRequest, "invalid_request", "request body has trailing data")
		return
	}
	if strings.TrimSpace(req.ScenarioID) == "" || strings.TrimSpace(req.PredictedResponseOriginNodeID) == "" {
		writeV2Error(w, http.StatusBadRequest, "invalid_request", "scenario_id and predicted_response_origin_node_id are required")
		return
	}
	core, ok := s.catalogV2.Get(req.ScenarioID)
	if !ok {
		writeV2Error(w, http.StatusNotFound, "scenario_not_found", "scenario was not found")
		return
	}
	if !hasNode(core, req.PredictedResponseOriginNodeID) {
		writeV2Error(w, http.StatusUnprocessableEntity, "unknown_prediction_node", "predicted node is not in the scenario")
		return
	}
	result, err := simulationv2.Simulate(core)
	if err != nil {
		writeV2Error(w, http.StatusInternalServerError, "simulation_failed", "simulation could not be completed")
		return
	}
	response := v2SimulationResponse{ScenarioID: req.ScenarioID, PredictedResponseOriginNodeID: req.PredictedResponseOriginNodeID, Correct: req.PredictedResponseOriginNodeID == result.ResponseOriginNodeID, Result: resultDTO(result), Explanation: core.Explanation}
	if !response.Correct {
		response.WrongExplanation = core.WrongExplanation
	}
	writeJSON(w, http.StatusOK, response)
}
func writeV2Error(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, v2Error{v2ErrorBody{code, message}})
}
func hasNode(core *scenariov2.CoreScenario, id string) bool {
	for _, node := range core.Topology.Nodes {
		if node.ID == id {
			return true
		}
	}
	return false
}
func detailDTO(core *scenariov2.CoreScenario) v2Detail {
	nodes := make([]v2Node, 0, len(core.Topology.Nodes))
	for _, n := range core.Topology.Nodes {
		nodes = append(nodes, v2Node{n.ID, n.Type, n.Label, nodeSettings(n)})
	}
	return v2Detail{core.ID, core.Title, core.Description, core.Difficulty, string(core.Category), append([]string(nil), core.LearningGoals...), v2Topology{nodes, append([]scenariov2.Edge(nil), core.Topology.Edges...)}, append([]string(nil), core.CloudConceptRefs...)}
}
func nodeSettings(n scenariov2.Node) any {
	if n.WAF != nil {
		return n.WAF
	}
	if n.LoadBalancer != nil {
		return n.LoadBalancer
	}
	if n.ReverseProxy != nil {
		return n.ReverseProxy
	}
	if n.Application != nil {
		return n.Application
	}
	return nil
}
func resultDTO(in simulationv2.Result) v2Result {
	events := make([]v2Event, 0, len(in.Events))
	for _, e := range in.Events {
		events = append(events, v2Event{e.AtMS, e.NodeID, e.Kind, e.MessageKey, e.Details})
	}
	return v2Result{in.ResponseOriginNodeID, in.HTTPStatus, in.Outcome, in.RuleID, in.ElapsedMS, events, append([]simulationv2.NodeState(nil), in.NodeStates...)}
}

var _ = bytes.NewBuffer
