package com.marcusai.app

import androidx.compose.ui.graphics.Color

data class MarcusPalette(val background: Color, val surface: Color, val primary: Color, val accent: Color, val gradientEnd: Color)

object MarcusThemePalette {
    fun from(name: String): MarcusPalette = when (name) {
        "Crimson Night" -> MarcusPalette(Color(0xFF090306), Color(0xFF211017), Color(0xFFE0003A), Color(0xFFFF647F), Color(0xFF26000D))
        "Obsidian Gold" -> MarcusPalette(Color(0xFF080807), Color(0xFF1B1A15), Color(0xFFB58A35), Color(0xFFE9C875), Color(0xFF211A0B))
        "Abyss Blue" -> MarcusPalette(Color(0xFF050810), Color(0xFF111C2B), Color(0xFF276BCE), Color(0xFF83C7FF), Color(0xFF071B35))
        "Toxic Green" -> MarcusPalette(Color(0xFF050906), Color(0xFF142018), Color(0xFF36A852), Color(0xFF9CE6A5), Color(0xFF092512))
        "Titanium Cyan" -> MarcusPalette(Color(0xFF080E15), Color(0xFF172432), Color(0xFF36D5F2), Color(0xFFB2F4FF), Color(0xFF0B2637))
        "Neon Blue" -> MarcusPalette(Color(0xFF050A16), Color(0xFF111D35), Color(0xFF397BFF), Color(0xFF9FC1FF), Color(0xFF0A1D45))
        "Stealth Graphite" -> MarcusPalette(Color(0xFF080A0D), Color(0xFF191F26), Color(0xFF8DA5B8), Color(0xFFD7E3EC), Color(0xFF111820))
        "Arctic Silver" -> MarcusPalette(Color(0xFF081018), Color(0xFF1A2B38), Color(0xFF79DDF4), Color(0xFFE0FAFF), Color(0xFF102B3C))
        else -> MarcusPalette(Color(0xFF050B14), Color(0xFF101D2B), Color(0xFF00BDEB), Color(0xFF8CEEFF), Color(0xFF071A2D))
    }
}
