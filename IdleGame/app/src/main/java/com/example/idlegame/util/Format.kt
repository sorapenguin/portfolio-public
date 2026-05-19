package com.example.idlegame.util

fun formatNumber(n: Long): String = when {
    n >= 1_000_000_000L -> String.format("%.2fB", n / 1_000_000_000.0)
    n >= 1_000_000L     -> String.format("%.2fM", n / 1_000_000.0)
    n >= 1_000L         -> String.format("%.2fK", n / 1_000.0)
    else                -> n.toString()
}
