@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.chiragjjjks.credu1ar.R
import io.github.chiragjjjks.credu1ar.core.model.Group

/** "Signal Noir" tokens. The single source of truth is docs/DESIGN.md: change it there first. */
object C {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF050505)
    val Lift = Color(0xFF0A0A0A)
    val Line = Color(0x14FFFFFF)     // 8%
    val LineHot = Color(0x2EFFFFFF)  // 18%

    val Ink = Color(0xFFEDEDED)
    val Mid = Color(0xFFA3A3A3)
    val Dim = Color(0xFF858585)
    val Faint = Color(0xFF6B6B6B)

    val Cyan = Color(0xFF46C1DE)
    val Blue = Color(0xFF3B7FE0)
    val Indigo = Color(0xFF2C3FB8)
    val MoneyIn = Color(0xFF68D98F)
    val Attention = Color(0xFFD8A548)

    val Brand = Brush.linearGradient(listOf(Color(0xFF5FD0E6), Color(0xFF3B7FE0), Color(0xFF2C3FB8)))

    /** Validated dark categorical palette (dataviz skill). Slot order is a colour-blind-safety mechanism: never reorder. */
    private val slots = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )

    fun of(g: Group): Color = when {
        g.slot in 1..8 -> slots[g.slot - 1]
        g == Group.INCOME -> MoneyIn
        else -> Faint
    }
}

object Ease {
    val Out = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}

/** True when amounts should be hidden ("privacy mode"). */
val LocalHideAmounts = compositionLocalOf { false }

object Fonts {
    private fun geist(weight: Int) = Font(R.font.geist, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    private fun mono(weight: Int) = Font(R.font.geist_mono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

    val Sans = FontFamily(geist(400), geist(500), geist(600), geist(700))
    val Mono = FontFamily(mono(400), mono(500), mono(600))
}

object T {
    private val tabular = "tnum"

    val Display = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-2.2).sp, color = C.Ink, fontFeatureSettings = tabular)
    val Headline = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.8).sp, color = C.Ink)
    val Title = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, color = C.Ink)
    val Body = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = C.Ink)
    val Small = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = C.Mid)
    val Amount = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = C.Ink, fontFeatureSettings = tabular)
    val AmountBig = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = C.Ink, fontFeatureSettings = tabular)
    val Eyebrow = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.3.sp, color = C.Dim)
    val MonoSmall = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = C.Mid, fontFeatureSettings = tabular)
}
