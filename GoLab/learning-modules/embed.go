// Package learningmodules embeds the data-only guided learning modules.
package learningmodules

import "embed"

//go:embed *.json
var FS embed.FS
