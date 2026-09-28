package dev.davirazuk.okplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.davirazuk.okplayer.BuildConfig
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.Pipeline
import dev.davirazuk.okplayer.audio.TrackInfo
import dev.davirazuk.okplayer.audio.formatRate
import dev.davirazuk.okplayer.ui.components.GroupHeader
import dev.davirazuk.okplayer.ui.components.Win7Check
import dev.davirazuk.okplayer.ui.theme.Palette

@Composable
fun OptionsScreen(
    hiResOutput: Boolean,
    noSkipping: Boolean,
    builtInDecoder: Boolean,
    onlineLyrics: Boolean,
    output: OutputStatus,
    track: TrackInfo?,
    pipeline: Pipeline,
    onHiRes: (Boolean) -> Unit,
    onNoSkipping: (Boolean) -> Unit,
    onBuiltInDecoder: (Boolean) -> Unit,
    onOnlineLyrics: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        GroupHeader("Playback")
        Option(
            hiResOutput, onHiRes, "Hi-res output to USB DACs",
            "With a USB DAC connected, sends 24-bit files at full resolution and decodes FLAC and ALAC with FFmpeg. " +
                "Phone speakers and Bluetooth always get standard 16-bit.",
        )
        Option(
            noSkipping, onNoSkipping, "No skipping",
            "Songs can't be skipped or fast-forwarded until they end, from the app, the lock screen or headset buttons.",
        )

        GroupHeader("Decoding")
        Option(
            builtInDecoder, onBuiltInDecoder, "Always use the built-in decoder",
            "Decodes FLAC, ALAC, MP3, AAC, Opus, Vorbis and more with FFmpeg instead of the phone's own decoders. " +
                "okplayer already switches automatically when the phone's decoder fails on a file.",
        )

        GroupHeader("Lyrics")
        Option(
            onlineLyrics, onOnlineLyrics, "Find lyrics online",
            "Looks songs up on lrclib.net, a free lyrics database. Only the artist, title, album and length are sent.",
        )

        GroupHeader("Output")
        Detail(
            when (output) {
                OutputStatus.Idle -> "Nothing is playing."
                is OutputStatus.Internal -> "Phone audio. Android mixes all apps together, so it may resample."
                is OutputStatus.Usb -> buildString {
                    append(output.deviceName)
                    append(if (output.bitPerfect) ": bit-perfect." else ": ${output.reason ?: "resampled by Android"}.")
                    if (output.supportedRates.isNotEmpty()) {
                        append("\nBit-perfect rates: ")
                        append(output.supportedRates.joinToString(", ") { formatRate(it) })
                    }
                }
            },
        )
        if (track != null) {
            Detail(
                listOfNotNull(
                    track.codec,
                    track.sampleRate?.let(::formatRate),
                    track.bitDepth?.let { "$it-bit" },
                    track.channels?.let { if (it == 2) "stereo" else if (it == 1) "mono" else "$it channels" },
                    track.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
                ).joinToString(", ").let { "This song: $it" },
            )
        }
        pipeline.decoder?.let { decoder ->
            val shown = if (decoder.startsWith("ffmpeg")) "FFmpeg (built-in)" else "Phone ($decoder)"
            val pcm = listOfNotNull(pipeline.pcmLabel, pipeline.pcmRate?.let(::formatRate)).joinToString(" at ")
            Detail("Decoder: $shown" + if (pcm.isNotEmpty()) "\nDecoded to: $pcm" else "")
        }

        GroupHeader("About")
        Detail("okplayer ${BuildConfig.VERSION_NAME}. Free software under the GPL-3.0.\ngithub.com/davirazuk/OkPlayer")
    }
}

@Composable
private fun Option(checked: Boolean, onChange: (Boolean) -> Unit, title: String, body: String) {
    Win7Check(checked, onChange, Modifier.padding(horizontal = 12.dp)) {
        Column {
            Text(title, fontSize = 13.5.sp, color = Palette.Ink)
            Text(body, fontSize = 12.sp, color = Palette.Sub, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, fontSize = 12.5.sp, color = Palette.Ink, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
}
