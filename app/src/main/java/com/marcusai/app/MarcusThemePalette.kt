package com.marcusai.app

import androidx.compose.ui.graphics.Color

data class MarcusPalette(val background: Color, val surface: Color, val primary: Color, val accent: Color, val gradientEnd: Color)

object MarcusThemePalette {
    fun from(name: String): MarcusPalette = when (name) {
        "Crimson Night" -> MarcusPalette(Color(0xFF090306), Color(0xFF211017), Color(0xFFE0003A), Color(0xFFFF647F), Color(0xFF26000D))
        "Obsidian Gold" -> MarcusPalette(Color(0xFF080807), Color(0xFF1B1A15), Color(0xFFB58A35), Color(0xFFE9C875), Color(0xFF211A0B))
        "Abyss Blue" -> MarcusPalette(Color(0xFF050810), Color(0xFF111C2B), Color(0xFF276BCE), Color(0xFF83C7FF), Color(0xFF071B35))
        "Toxic Green" -> MarcusPalette(Color(0xFF050906), Color(0xFF142018), Color(0xFF36A852), Color(0xFF9CE6A5), Color(0xFF092512))
        "Demonic Violet" -> MarcusPalette(Color(0xFF08050D), Color(0xFF1B1226), Color(0xFF873BE0), Color(0xFFC69AFF), Color(0xFF260C3D))
        else -> MarcusPalette(Color(0xFF070509), Color(0xFF19131D), Color(0xFFB0002A), Color(0xFFD6A64B), Color(0xFF080D18))
    }
}
