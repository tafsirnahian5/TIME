package com.example.timeapp.ui.me

import com.example.timeapp.data.L

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Settings › Profile — edits the identity fields [AppViewModel.setIdentity] already
 * persists, which until now were only ever writable once, during onboarding.
 */
@Composable
fun ProfileSettingsScreen(onBack: () -> Unit, viewModel: AppViewModel) {
    val colors = TimeThemeTokens.colors
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsState()
    var name by remember(ui.userName) { mutableStateOf(ui.userName) }
    var title by remember(ui.userTitle) { mutableStateOf(ui.userTitle) }
    var avatarUri by remember(ui.userAvatarUri) { mutableStateOf(ui.userAvatarUri) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarUri = uri.toString()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                avatarBitmap = BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }
    }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = L("Profile"), onBack = onBack,
                actions = {
                    TextButton(onClick = { viewModel.setIdentity(name.trim(), title, avatarUri) }) {
                        Text(L("Save"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s24)) {
                Box(
                    modifier = Modifier.clickable {
                        pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                value = name, onValueChange = { name = it }, label = { Text(L("Display name")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20), singleLine = true,
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text(L("Title (optional)")) },
                placeholder = { Text(L("e.g. Builder, Student")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

        }
    }
}
