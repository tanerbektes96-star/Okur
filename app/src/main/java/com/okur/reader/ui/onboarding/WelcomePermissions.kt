package com.okur.reader.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.okur.reader.AppContainer
import com.okur.reader.share.NearbyPermissions
import kotlinx.coroutines.launch

/**
 * İlk açılışta gösterilir. Android izinleri kurulum anında veremediği için gerekli izinler
 * burada tek seferde istenir.
 */
@Composable
fun WelcomePermissions(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var withCamera by remember { mutableStateOf(true) }

    val finish: (Boolean) -> Unit = { cameraGranted ->
        scope.launch {
            if (cameraGranted) container.settings.updateEye { it.copy(smartEye = true) }
            container.settings.updateApp { it.copy(onboardingDone = true) }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        finish(result[Manifest.permission.CAMERA] == true)
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
        title = { Text("Okur'a hoş geldin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Uygulamanın tüm özelliklerini kullanabilmen için birkaç izin gerekiyor:")
                PermissionRow(
                    Icons.Filled.SwapHoriz,
                    "Yakındaki cihazlar",
                    "Kitapları internetsiz, Bluetooth ve Wi-Fi ile yakındaki Okur kullanıcılarına göndermek ve almak için.",
                )
                Row(
                    Modifier.fillMaxWidth().clickable { withCamera = !withCamera },
                    verticalAlignment = Alignment.Top,
                ) {
                    PermissionRow(
                        Icons.Filled.CameraFront,
                        "Kamera (isteğe bağlı)",
                        "Akıllı Göz için: ekrana bakarken ekranı açık tutar, mesafe ve göz kırpma uyarıları verir. Görüntüler cihazda işlenir, kaydedilmez.",
                        Modifier.weight(1f),
                    )
                    Checkbox(checked = withCamera, onCheckedChange = { withCamera = it })
                }
                Text(
                    "İzinleri daha sonra telefon ayarlarından değiştirebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val perms = NearbyPermissions.required.toMutableList()
                if (withCamera) perms += Manifest.permission.CAMERA
                launcher.launch(perms.toTypedArray())
            }) { Text("İzinleri ver") }
        },
        dismissButton = { TextButton(onClick = { finish(false) }) { Text("Şimdi değil") } },
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
