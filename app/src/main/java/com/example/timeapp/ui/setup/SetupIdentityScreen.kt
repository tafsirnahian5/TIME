package com.example.timeapp.ui.setup

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** A1 — Setup 1: identity. Just you — no account and no server. */
@Composable
fun SetupIdentityScreen(onNext: (name: String, title: String, avatarUri: String?) -> Unit) {
    val colors = TimeThemeTokens.colors
    val context = LocalContext.current
    // Empty with a hint — it used to be pre-filled with the sample name "Builder".
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                avatarBitmap = BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }
    }

    Scaffold { padding ->
        // Scrolls, with Continue kept above the keyboard — typing a name used to hide it.
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(Dimens.screenH)) {
          Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s14)) {
                com.example.timeapp.ui.components.TimeMark(size = 28.dp, label = null)
                Text("T!ME", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Dimens.s8))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s24),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s6),
            ) {
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.ring, Shapes.xs))
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.ring, Shapes.xs))
            }
            Text(L("STEP 1 OF 3"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s12))
            Text(L("Who is working?"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s8))
            Text(
                L("Just you. No account and no server — everything is written to this phone's storage."),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Dimens.s22),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s24)) {
                Box(
                    modifier = Modifier.clickable {
                        pickAvatar.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    val bitmap = avatarBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap, contentDescription = L("Avatar"),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).background(colors.accC, CircleShape).clip(CircleShape),
                        )
                    } else {
                        Box(modifier = Modifier.size(84.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
                            Text(name.trim().take(1).ifBlank { "?" }, style = MaterialTheme.typography.displaySmall, color = colors.acc)
                        }
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(30.dp).background(colors.acc, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                    }
                }
                Text(
                    L("Pick a photo from this device,\nor keep the initial."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Dimens.s16),
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(L("Your name")) },
                placeholder = { Text(L("What should T!ME call you?")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20), singleLine = true,
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text(L("Title (optional)")) },
                placeholder = { Text(L("e.g. Builder, Student")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
          }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s14)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    L("You can add a PIN or fingerprint in step 3."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s8),
                )
            }
            androidx.compose.material3.Button(
                onClick = { onNext(name.trim(), title, avatarUri) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(L("Continue")) }
        }
    }
}
