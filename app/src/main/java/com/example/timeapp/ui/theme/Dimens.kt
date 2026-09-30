package com.example.timeapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Dimens {
    // spacing — 4dp base; odd steps are intentional optical values from the mock
    val s2 = 2.dp;  val s4 = 4.dp;   val s6 = 6.dp;   val s7 = 7.dp
    val s8 = 8.dp;  val s10 = 10.dp; val s12 = 12.dp; val s14 = 14.dp
    val s16 = 16.dp; val s20 = 20.dp; val s22 = 22.dp; val s24 = 24.dp
    val s26 = 26.dp; val s34 = 34.dp

    val screenH = 24.dp        // form screens (A1, A2)
    val screenHList = 12.dp    // list screens
    val appBarH = 56.dp
    val navBarH = 74.dp
    val cardPad = 16.dp
    val cardGap = 12.dp
    val sectionGap = 20.dp
    val listRowMinH = 56.dp
    val listRowTwoLineH = 72.dp
    val touchMin = 48.dp
    val markerDot = 8.dp
    val hairline = 1.dp
}

object Shapes {
    val xs = RoundedCornerShape(5.dp)
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(14.dp)
    val xl = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val pill = RoundedCornerShape(100.dp)
}
