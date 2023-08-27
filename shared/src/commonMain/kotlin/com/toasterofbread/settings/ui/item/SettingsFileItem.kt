package com.toasterofbread.settings.ui.item

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.toasterofbread.composesettings.ui.SettingsPage
import com.toasterofbread.composesettings.ui.item.BasicSettingsValueState
import com.toasterofbread.composesettings.ui.item.SettingsItem
import com.toasterofbread.spmp.platform.PlatformPreferences
import com.toasterofbread.spmp.platform.composable.PlatformAlertDialog
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.ui.theme.Theme
import com.toasterofbread.utils.composable.SubtleLoadingIndicator
import com.toasterofbread.utils.composable.WidthShrinkText
import com.toasterofbread.utils.toFloat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class SettingsFileItem(
    val state: BasicSettingsValueState<String>,
    val title: String,
    val subtitle: String?,
    val getPathLabel: (String) -> String,
    val onSelectRequested: (
        setValue: (String) -> Unit,
        showDialog: (title: String, body: String?, onAccept: suspend () -> Unit) -> Unit,
    ) -> Unit,
): SettingsItem() {
    override fun initialiseValueStates(prefs: PlatformPreferences, default_provider: (String) -> Any) {
        state.init(prefs, default_provider)
    }

    override fun releaseValueStates(prefs: PlatformPreferences) {
        state.release(prefs)
    }

    override fun resetValues() {
        state.reset()
    }

    private var current_dialog: Triple<String, String?, suspend () -> Unit>? by mutableStateOf(null)
    private val coroutine_scope = CoroutineScope(Job())

    @Composable
    override fun GetItem(theme: Theme, openPage: (Int, Any?) -> Unit, openCustomPage: (SettingsPage) -> Unit) {
        var action_in_progress: Boolean by remember { mutableStateOf(false) }
        LaunchedEffect(current_dialog) {
            action_in_progress = false
            coroutine_scope.coroutineContext.job.cancelChildren()
        }

        current_dialog?.also { dialog ->
            PlatformAlertDialog(
                { current_dialog = null },
                title = {
                    WidthShrinkText(dialog.first)
                },
                text = dialog.second?.let { body ->
                    { Text(body) }
                },
                confirmButton = {
                    Button(
                        {
                            coroutine_scope.launch(Dispatchers.Default) {
                                action_in_progress = true
                                dialog.third.invoke()
                                action_in_progress = false

                                if (current_dialog == dialog) {
                                    current_dialog = null
                                }
                            }
                        },
                        enabled = !action_in_progress
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val alpha: Float by animateFloatAsState(action_in_progress.toFloat())
                            SubtleLoadingIndicator(Modifier.alpha(alpha))
                            Text(
                                getString("action_confirm_action"),
                                Modifier.alpha(1f - alpha)
                            )
                        }
                    }
                },
                dismissButton = {
                    Crossfade(!action_in_progress) { enabled ->
                        Button(
                            {
                                if (enabled) {
                                    current_dialog = null
                                }
                            },
                            enabled = enabled
                        ) {
                            Text(getString("action_deny_action"))
                        }
                    }
                }
            )
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ItemTitleText(title, theme)
                    ItemText(subtitle, theme)
                }

                IconButton({
                    onSelectRequested(
                        { path ->
                            state.set(path)
                        },
                        { title, body, onAccept ->
                            current_dialog = Triple(title, body, onAccept)
                        }
                    )
                }) {
                    Icon(Icons.Default.Folder, null)
                }

                IconButton({
                    state.reset()
                }) {
                    Icon(Icons.Default.Refresh, null)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(theme.accent, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Text(
                    getPathLabel(state.get()),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.on_accent
                )
            }
        }
    }
}