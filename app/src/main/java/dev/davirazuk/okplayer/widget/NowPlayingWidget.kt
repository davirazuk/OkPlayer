package dev.davirazuk.okplayer.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaButtonReceiver
import dev.davirazuk.okplayer.MainActivity
import dev.davirazuk.okplayer.R

/** What the widget last showed, written by the playback service. */
data class WidgetSnapshot(val title: String, val artist: String, val artUri: String?, val playing: Boolean)

object WidgetState {
    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun save(context: Context, s: WidgetSnapshot) {
        prefs(context).edit()
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putString("art", s.artUri)
            .putBoolean("playing", s.playing)
            .apply()
    }

    fun read(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getString("art", null), p.getBoolean("playing", false))
    }
}

/** WMP 12's mini player on the home screen: black glass, the cover, and the play orb. */
class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetState.read(context)
        val art = state.artUri?.let { loadArt(context, Uri.parse(it)) }
        provideContent { Content(context, state, art) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, s: WidgetSnapshot, art: Bitmap?) {
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Row(
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(50.dp).clickable(openApp),
            )
            Column(GlanceModifier.defaultWeight().padding(start = 10.dp, end = 6.dp).clickable(openApp)) {
                Text(
                    s.title.ifEmpty { "okplayer" },
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    s.artist.ifEmpty { "Tap to open" },
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color(0xFFAEB6BF)), fontSize = 12.sp),
                )
            }
            MediaKey(context, R.drawable.ic_widget_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Box(
                GlanceModifier
                    .size(46.dp)
                    .background(ImageProvider(R.drawable.widget_orb))
                    .clickable(mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                    contentDescription = if (s.playing) "Pause" else "Play",
                    modifier = GlanceModifier.size(22.dp),
                )
            }
            MediaKey(context, R.drawable.ic_widget_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    @androidx.compose.runtime.Composable
    private fun MediaKey(context: Context, icon: Int, label: String, key: Int) {
        Box(
            GlanceModifier.size(34.dp).clickable(mediaButton(context, key)),
            contentAlignment = Alignment.Center,
        ) {
            Image(provider = ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(20.dp))
        }
    }

    /** A media button press delivered to the session, the same way a headset button is. */
    private fun mediaButton(context: Context, key: Int): Action = actionSendBroadcast(
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(context, MediaButtonReceiver::class.java)
            // A distinct URI per button keeps the system from merging the pending intents.
            .setData(Uri.parse("okplayer://widget/$key"))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key)),
    )

    private fun loadArt(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
