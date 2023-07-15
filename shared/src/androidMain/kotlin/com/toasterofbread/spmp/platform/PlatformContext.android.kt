package com.toasterofbread.spmp.platform

import SpMp
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Context.MODE_APPEND
import android.content.Context.MODE_PRIVATE
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Window
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.toasterofbread.spmp.model.Settings
import com.toasterofbread.spmp.resources.getString
import com.toasterofbread.spmp.resources.getStringTODO
import com.toasterofbread.spmp.ui.layout.mainpage.PlayerStateImpl
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream


private const val DEFAULT_NOTIFICATION_CHANNEL_ID = "default_channel"
private const val ERROR_NOTIFICATION_CHANNEL_ID = "download_error_channel"

fun getAppName(context: Context): String {
    val info = context.applicationInfo
    val string_id = info.labelRes
    return if (string_id == 0) info.nonLocalizedLabel.toString() else context.getString(string_id)
}

actual class PlatformContext(private val context: Context, onInit: ((PlatformContext) -> Unit)? = null) {
    init {
        onInit?.invoke(this)
    }

    actual val player_state = PlayerStateImpl(this)

//    private val context: WeakReference<Context> = WeakReference(context)
    val ctx: Context get() = context

    actual fun getPrefs(): ProjectPreferences = ProjectPreferences.getInstance(ctx)

    actual fun getFilesDir(): File = ctx.filesDir
    actual fun getCacheDir(): File = ctx.cacheDir

    actual fun isAppInForeground(): Boolean = ctx.isAppInForeground()

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @Composable
    actual fun getStatusBarHeight(): Dp {
        var height: Dp? by remember { mutableStateOf(null) }
        if (height != null) {
            return height!!
        }

        val resource_id: Int = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resource_id > 0) {
            with(LocalDensity.current) {
                height = ctx.resources.getDimensionPixelSize(resource_id).toDp()
                return height!!
            }
        }

        throw RuntimeException()
    }
    actual fun setStatusBarColour(colour: Color, dark_icons: Boolean) {
        ctx.findWindow()?.also { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(if (dark_icons) APPEARANCE_LIGHT_STATUS_BARS else 0, APPEARANCE_LIGHT_STATUS_BARS)
            }
            window.statusBarColor = colour.toArgb()
        }
    }

    @Composable
    actual fun getImeInsets(): WindowInsets? = WindowInsets.ime
    @Composable
    actual fun getSystemInsets(): WindowInsets? = WindowInsets.systemGestures

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    actual fun getNavigationBarHeight(): Int {
        val resources = SpMp.context.ctx.resources
        val resource_id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resource_id > 0) resources.getDimensionPixelSize(resource_id) else 0
    }

    actual fun getLightColorScheme(): ColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(ctx)
        else lightColorScheme()
    actual fun getDarkColorScheme(): ColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(ctx)
        else darkColorScheme()

    actual fun canShare(): Boolean = true
    actual fun shareText(text: String, title: String?) {
        val share_intent = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"

            putExtra(Intent.EXTRA_TEXT, text)
            if (title != null) {
                putExtra(Intent.EXTRA_TITLE, title)
            }
        }, title)

        ctx.startActivity(share_intent)
    }

    actual fun canOpenUrl(): Boolean {
        val open_intent = Intent(Intent.ACTION_VIEW)
        return open_intent.resolveActivity(ctx.packageManager) != null
    }
    actual fun openUrl(url: String) {
        val open_intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        checkNotNull(open_intent.resolveActivity(ctx.packageManager))
        ctx.startActivity(open_intent)
    }

    actual fun sendToast(text: String, long: Boolean) {
        ctx.sendToast(text, long)
    }

    actual fun vibrate(duration: Double) {
        val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibrator?.vibrate(VibrationEffect.createOneShot((duration * 1000.0).toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    actual fun openFileInput(name: String): FileInputStream = ctx.openFileInput(name)
    actual fun openFileOutput(name: String, append: Boolean): FileOutputStream = ctx.openFileOutput(name, if (append) MODE_APPEND else MODE_PRIVATE)

    actual fun openResourceFile(path: String): InputStream = ctx.resources.assets.open(path)
    actual fun listResourceFiles(path: String): List<String>? = ctx.resources.assets.list(path)?.toList()

    actual fun loadFontFromFile(path: String): Font = Font(path, ctx.resources.assets)

    actual fun canSendNotifications(): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    @SuppressLint("MissingPermission")
    actual fun sendNotification(title: String, body: String) {
        if (canSendNotifications()) {
            val notification = Notification.Builder(context, getDefaultNotificationChannel(ctx))
                .setContentTitle(title)
                .setContentText(body)
                .build()

            NotificationManagerCompat.from(ctx).notify(
                System.currentTimeMillis().toInt(),
                notification
            )
        }
    }
    @SuppressLint("MissingPermission")
    actual fun sendNotification(throwable: Throwable) {
        if (canSendNotifications()) {
            NotificationManagerCompat.from(ctx).notify(
                System.currentTimeMillis().toInt(),
                throwable.createNotification(ctx, getErrorNotificationChannel(ctx))
            )
        }
    }

    actual fun isConnectionMetered(): Boolean = ctx.isConnectionMetered()

    @Composable
    actual fun getScreenHeight(): Dp {
        return LocalConfiguration.current.screenHeightDp.dp// + getStatusBarHeight()
    }

    @Composable
    actual fun getScreenWidth(): Dp {
        return LocalConfiguration.current.screenWidthDp.dp
    }

    @Composable
    actual fun CopyShareButtons(name: String?, getText: () -> String) {
        val clipboard = LocalClipboardManager.current
        IconButton({
            clipboard.setText(AnnotatedString(getText()))

            if (name != null) {
                sendToast(getString("notif_copied_x_to_clipboard").replace("\$x", name))
            }
            else {
                sendToast(getString("notif_copied_to_clipboard"))
            }
        }) {
            Icon(Icons.Filled.ContentCopy, null, Modifier.size(20.dp))
        }

        IconButton({
            val share_intent = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, getText())
                type = "text/plain"
            }, null)
            ctx.startActivity(share_intent)
        }) {
            Icon(Icons.Filled.Share, null, Modifier.size(20.dp))
        }
    }

    companion object {
        lateinit var main_activity: Class<out Activity>
    }
}

private fun Context.findWindow(): Window? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}

private fun getDefaultNotificationChannel(context: Context): String {
    val channel = NotificationChannel(
        DEFAULT_NOTIFICATION_CHANNEL_ID,
        getStringTODO("Default channel"),
        NotificationManager.IMPORTANCE_DEFAULT
    )

    NotificationManagerCompat.from(context).createNotificationChannel(channel)
    return DEFAULT_NOTIFICATION_CHANNEL_ID
}

private fun getErrorNotificationChannel(context: Context): String {
    val channel = NotificationChannel(
        ERROR_NOTIFICATION_CHANNEL_ID,
        getString("download_service_error_name"),
        NotificationManager.IMPORTANCE_HIGH
    )

    NotificationManagerCompat.from(context).createNotificationChannel(channel)
    return ERROR_NOTIFICATION_CHANNEL_ID
}

@Suppress("UsePropertyAccessSyntax")
fun Throwable.createNotification(context: Context, notification_channel: String): Notification {
    return Notification.Builder(context, notification_channel)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(this::class.simpleName)
        .setContentText(message)
        .setStyle(Notification.BigTextStyle().bigText("$message\nStack trace:\n${stackTraceToString()}"))
        .addAction(
            Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_share),
                "Share",
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent.createChooser(Intent().also { share ->
                        share.action = Intent.ACTION_SEND
                        share.putExtra(Intent.EXTRA_TITLE, this::class.simpleName)
                        share.putExtra(Intent.EXTRA_TITLE, this::class.simpleName)
                        share.putExtra(Intent.EXTRA_TEXT, stackTraceToString())
                        share.type = "text/plain"
                    }, null),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
            ).build())
        .build()
}

fun Context.sendToast(text: String, long: Boolean = false) {
    try {
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
    catch (_: NullPointerException) {
        Looper.prepare()
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

fun Context.isAppInForeground(): Boolean {
    val activity_manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val running_processes: List<ActivityManager.RunningAppProcessInfo> = activity_manager.runningAppProcesses ?: return false
    for (process in running_processes) {
        if (
            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            && process.processName.equals(packageName)
        ) {
            return true
        }
    }
    return false
}

fun Context.isConnectionMetered(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)

    if (capabilities != null) {
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    return false
}

fun <T> Settings.get(context: Context): T {
    return Settings.get(this, ProjectPreferences.getInstance(context))
}
