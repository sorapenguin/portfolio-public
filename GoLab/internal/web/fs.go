// Package web embeds the static frontend files into the binary.
package web

import "embed"

//go:embed static
var StaticFS embed.FS
