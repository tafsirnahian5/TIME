package com.example.timeapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timeapp.R

/**
 * The app's own mark inside the app: the owner's T! (silver T, orange dot) on the launcher
 * icon's dark plate, so the header, setup, lock screen and About look like the icon on the
 * home screen — never a generic clock icon. Keeps its own
 * colours in every theme and colourway, exactly like the launcher icon does.
 */
@Composable
fun TimeMark(size: Dp = 32.dp, modifier: Modifier = Modifier, label: String? = "T!ME") {
    Box(
        modifier = modifier
            .size(size)
            .background(colorResource(R.color.ic_launcher_background), RoundedCornerShape(size * 0.26f))
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_time_mark), contentDescription = null, modifier = Modifier.size(size * 0.72f))
    }
}
