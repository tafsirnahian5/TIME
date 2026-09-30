package com.example.timeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.theme.Dimens

enum class MarkerKind { TASK, EVENT, MONEY }

/** 8dp semantic dot used on record rows and the month grid. */
@Composable
fun MarkerDot(kind: MarkerKind, modifier: Modifier = Modifier) {
    val color = when (kind) {
        MarkerKind.TASK -> com.example.timeapp.ui.theme.MarkerTask
        MarkerKind.EVENT -> com.example.timeapp.ui.theme.MarkerEvent
        MarkerKind.MONEY -> com.example.timeapp.ui.theme.MarkerMoney
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(Dimens.markerDot)
            .background(color, CircleShape)
    )
}
