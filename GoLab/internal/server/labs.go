package server

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"net/http"
	"sort"
	"strings"

	"golab/internal/http_path"
	"golab/internal/lab"
	"golab/internal/labcatalog"
	"golab/internal/labrun"
	"golab/internal/learningmodule"
	"golab/labs"
	"golab/learning-modules"
)

const labRunRequestLimit = 64 << 10

type labSummary struct {
	ID             lab.LabID           `json:"id"`
	Title          string              `json:"title"`
	Provider       lab.Provider        `json:"provider"`
	Difficulty     lab.Difficulty      `json:"difficulty"`
	Mode           lab.LabMode         `json:"mode"`
	EngineID       lab.EngineID        `json:"engine"`
	DomainID       lab.DomainID        `json:"domain_id"`
	SubcategoryIDs []lab.SubcategoryID `json:"subcategory_ids"`
}

type labListResponse struct {
	Labs []labSummary `json:"labs"`
}

type labSelectionBody map[lab.ControlID]json.RawMessage

type labRunRequestBody struct {
	TestID     *lab.TestID       `json:"test_id"`
	Selections *labSelectionBody `json:"selections"`
}

func buildEmbeddedLabService() (*labcatalog.Catalog, *learningmodule.Catalog, *labrun.Service, error) {
	paths, err := fs.Glob(labs.FS, "*.json")
	if err != nil {
		return nil, nil, nil, fmt.Errorf("list embedded definitions: %w", err)
	}
	if len(paths) == 0 {
		return nil, nil, nil, errors.New("no embedded lab definitions")
	}
	sort.Strings(paths)
	definitions := make([]lab.Lab, 0, len(paths))
	for _, path := range paths {
		file, err := labs.FS.Open(path)
		if err != nil {
			return nil, nil, nil, fmt.Errorf("open embedded definition: %w", err)
		}
		definition, decodeErr := labcatalog.Decode(file)
		closeErr := file.Close()
		if decodeErr != nil {
			return nil, nil, nil, fmt.Errorf("decode embedded definition: %w", decodeErr)
		}
		if closeErr != nil {
			return nil, nil, nil, fmt.Errorf("close embedded definition: %w", closeErr)
		}
		definitions = append(definitions, definition)
	}
	catalog, err := labcatalog.New(definitions)
	if err != nil {
		return nil, nil, nil, fmt.Errorf("validate embedded catalog: %w", err)
	}
	modulePaths, err := fs.Glob(learningmodules.FS, "*.json")
	if err != nil || len(modulePaths) == 0 {
		return nil, nil, nil, errors.New("list embedded learning modules")
	}
	sort.Strings(modulePaths)
	modules := make([]learningmodule.LearningModule, 0, len(modulePaths))
	for _, path := range modulePaths {
		file, openErr := learningmodules.FS.Open(path)
		if openErr != nil {
			return nil, nil, nil, errors.New("open embedded learning module")
		}
		module, decodeErr := learningmodule.Decode(file)
		closeErr := file.Close()
		if decodeErr != nil {
			return nil, nil, nil, fmt.Errorf("decode embedded learning module: %w", decodeErr)
		}
		if closeErr != nil {
			return nil, nil, nil, errors.New("close embedded learning module")
		}
		if path != string(module.LabID)+".json" {
			return nil, nil, nil, errors.New("learning module filename mismatch")
		}
		modules = append(modules, module)
	}
	learningCatalog, err := learningmodule.New(modules)
	if err != nil {
		return nil, nil, nil, fmt.Errorf("validate embedded learning modules: %w", err)
	}
	if err := learningmodule.ValidateCatalog(catalog, learningCatalog); err != nil {
		return nil, nil, nil, fmt.Errorf("cross-validate learning modules: %w", err)
	}
	runner, err := labrun.New(catalog, []http_path.Definition{http_path.HeroDefinition()})
	if err != nil {
		return nil, nil, nil, fmt.Errorf("validate embedded execution definitions: %w", err)
	}
	return catalog, learningCatalog, runner, nil
}

func (s *Server) handleLabList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeLabMethodNotAllowed(w, http.MethodGet)
		return
	}
	publicLabs := s.labCatalog.List()
	out := make([]labSummary, 0, len(publicLabs))
	for _, definition := range publicLabs {
		out = append(out, labSummary{
			ID:             definition.ID,
			Title:          definition.Title,
			Provider:       definition.Provider,
			Difficulty:     definition.Difficulty,
			Mode:           definition.Mode,
			EngineID:       definition.EngineID,
			DomainID:       definition.DomainID,
			SubcategoryIDs: append([]lab.SubcategoryID(nil), definition.SubcategoryIDs...),
		})
	}
	writeJSON(w, http.StatusOK, labListResponse{Labs: out})
}

func (s *Server) handleLabDetail(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeLabMethodNotAllowed(w, http.MethodGet)
		return
	}
	definition, err := s.labCatalog.Get(lab.LabID(r.PathValue("id")))
	if err != nil {
		if errors.Is(err, labcatalog.ErrUnknownLab) {
			writeLabError(w, http.StatusNotFound, "lab_not_found", "Lab was not found.")
			return
		}
		writeLabInternalError(w)
		return
	}
	writeJSON(w, http.StatusOK, definition)
}

func (s *Server) handleLabLearning(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeLabMethodNotAllowed(w, http.MethodGet)
		return
	}
	labID := lab.LabID(r.PathValue("id"))
	if _, err := s.labCatalog.Get(labID); err != nil {
		writeLabError(w, http.StatusNotFound, "lab_not_found", "Lab was not found.")
		return
	}
	module, err := s.learningCatalog.Get(labID)
	if err != nil {
		writeLabError(w, http.StatusNotFound, "learning_not_found", "Learning material was not found.")
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, module)
}

func (s *Server) handleLabRun(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeLabMethodNotAllowed(w, http.MethodPost)
		return
	}
	if !isJSONContentType(r.Header.Get("Content-Type")) {
		writeLabError(w, http.StatusUnsupportedMediaType, "unsupported_media_type", "Content-Type must be application/json.")
		return
	}
	if r.ContentLength > labRunRequestLimit {
		writeLabError(w, http.StatusRequestEntityTooLarge, "request_body_too_large", "Request body is too large.")
		return
	}
	req, err := decodeLabRunRequest(w, r)
	if err != nil {
		if errors.Is(err, errLabRunBodyTooLarge) {
			writeLabError(w, http.StatusRequestEntityTooLarge, "request_body_too_large", "Request body is too large.")
			return
		}
		writeLabError(w, http.StatusBadRequest, "invalid_request", "Request body is invalid.")
		return
	}
	labID := lab.LabID(r.PathValue("id"))
	if _, err := s.labCatalog.Get(labID); err != nil {
		if errors.Is(err, labcatalog.ErrUnknownLab) {
			writeLabError(w, http.StatusNotFound, "lab_not_found", "Lab was not found.")
			return
		}
		writeLabInternalError(w)
		return
	}
	result, err := s.labRun(labID, req.Selections, req.TestID)
	if err != nil {
		switch {
		case errors.Is(err, http_path.ErrUnknownTest):
			writeLabError(w, http.StatusUnprocessableEntity, "test_not_found", "Test was not found.")
		case errors.Is(err, http_path.ErrMissingSelection), errors.Is(err, http_path.ErrInvalidSelection):
			writeLabError(w, http.StatusUnprocessableEntity, "invalid_selection", "The submitted selections are invalid.")
		default:
			writeLabInternalError(w)
		}
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, result)
}

var errLabRunBodyTooLarge = errors.New("lab run body too large")

func decodeLabRunRequest(w http.ResponseWriter, r *http.Request) (lab.RunRequest, error) {
	r.Body = http.MaxBytesReader(w, r.Body, labRunRequestLimit)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	var wire labRunRequestBody
	if err := decoder.Decode(&wire); err != nil {
		if strings.Contains(err.Error(), "request body too large") {
			return lab.RunRequest{}, errLabRunBodyTooLarge
		}
		return lab.RunRequest{}, err
	}
	var trailing struct{}
	if err := decoder.Decode(&trailing); err != io.EOF {
		return lab.RunRequest{}, errors.New("trailing JSON value")
	}
	if wire.TestID == nil || wire.Selections == nil || strings.TrimSpace(string(*wire.TestID)) == "" {
		return lab.RunRequest{}, errors.New("required run fields missing")
	}
	selections := make(lab.SelectionSet, len(*wire.Selections))
	for controlID, raw := range *wire.Selections {
		if len(raw) < 2 || raw[0] != '"' || raw[len(raw)-1] != '"' {
			return lab.RunRequest{}, errors.New("selection option must be a string")
		}
		var optionID lab.OptionID
		if err := json.Unmarshal(raw, &optionID); err != nil {
			return lab.RunRequest{}, err
		}
		selections[controlID] = optionID
	}
	return lab.RunRequest{TestID: *wire.TestID, Selections: selections}, nil
}

func isJSONContentType(contentType string) bool {
	mediaType, _, err := mime.ParseMediaType(contentType)
	return err == nil && mediaType == "application/json"
}

func writeLabError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, v2Error{Error: v2ErrorBody{Code: code, Message: message}})
}

func writeLabInternalError(w http.ResponseWriter) {
	writeLabError(w, http.StatusInternalServerError, "internal_error", "An internal error occurred.")
}

func writeLabMethodNotAllowed(w http.ResponseWriter, allow string) {
	w.Header().Set("Allow", allow)
	writeLabError(w, http.StatusMethodNotAllowed, "method_not_allowed", "Method is not allowed.")
}
