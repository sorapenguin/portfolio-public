package server_test

import (
	"net/http"
	"strings"
	"testing"
)

func TestGuidedLabBrowserRoutesAndAssets(t *testing.T) {
	s := newTestServer(t)
	for _, path := range []string{"/labs", "/labs/aws-waf-alb-ecs-001"} {
		w := labRequest(s, http.MethodGet, path, "", "")
		if w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Content-Type"), "text/html") || !strings.Contains(w.Body.String(), "<main") || !strings.Contains(w.Body.String(), "/labs/assets/labs.js") || w.Header().Get("X-Content-Type-Options") != "nosniff" {
			t.Fatalf("page %s: %d %s", path, w.Code, w.Body.String())
		}
	}
	for _, asset := range []string{"/labs/assets/labs.css", "/labs/assets/labs.js"} {
		w := labRequest(s, http.MethodGet, asset, "", "")
		if w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Content-Type"), "text/") && !strings.Contains(w.Header().Get("Content-Type"), "javascript") {
			t.Fatalf("asset %s: %d %s", asset, w.Code, w.Header().Get("Content-Type"))
		}
		if strings.Contains(w.Body.String(), "innerHTML") || strings.Contains(w.Body.String(), "document.write") || strings.Contains(w.Body.String(), "eval(") || strings.Contains(w.Body.String(), "aws-waf-alb-ecs-001") || strings.Contains(w.Body.String(), "waf_action") {
			t.Fatalf("unsafe or lab-specific asset %s", asset)
		}
	}
	if w := labRequest(s, http.MethodGet, "/labs/assets/missing.css", "", ""); w.Code != http.StatusNotFound {
		t.Fatalf("missing asset: %d", w.Code)
	}
	if w := labRequest(s, http.MethodGet, "/labs/a/b", "", ""); w.Code != http.StatusNotFound {
		t.Fatalf("extra route: %d", w.Code)
	}
	if w := labRequest(s, http.MethodGet, "/api/v2/labs", "", ""); w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Content-Type"), "application/json") {
		t.Fatal("API collision")
	}
}
