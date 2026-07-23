// Package labs exposes the fixed public lab definitions embedded in the binary.
package labs

import "embed"

// FS contains only public Lab JSON definitions. It is read during server startup.
//
//go:embed *.json
var FS embed.FS
