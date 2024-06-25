package com.oguzhnatly.flutter_carplay.managers.audio

import android.media.MediaPlayer
import android.net.Uri
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.FlutterCarplayPlugin
import com.oguzhnatly.flutter_carplay.Logger

/**
 * A singleton class responsible for managing sound effects in the Flutter CarPlay plugin.
 */
class FCPSoundEffects private constructor() {
    companion object {
        /**
         * Shared instance of the sound effects manager.
         */
        val shared: FCPSoundEffects by lazy { FCPSoundEffects() }
    }

    /**
     * The media player used for playing sound effects.
     */
    private var mediaPlayer: MediaPlayer? = null

    /**
     * The URI of the audio file used for sound effects.
     */
    private var audioUri: Uri? = null

    /**
     * The duration of the currently loaded audio file in milliseconds.
     */
    val duration: Int
        get() = mediaPlayer?.duration ?: 0

    /**
     * Prepares the sound effects with the specified audio file and volume.
     *
     * @param sound The name of the audio file.
     * @param volume The volume level for the sound effects.
     */
    fun prepare(sound: String, volume: Float) {
        val flutterPluginBinding = FlutterCarplayPlugin.flutterPluginBinding ?: return

        try {
            val path =
                flutterPluginBinding.flutterAssets.getAssetFilePathBySubpath(sound)
            val assetFileDescriptor =
                AndroidAutoService.session?.carContext?.assets?.openFd(path ?: sound) ?: return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                setVolume(volume, volume)
                prepare()
            }
        } catch (e: Exception) {
            Logger.log(e.message ?: e.toString(), tag = "UIImage")
            return
        }
    }

    /**
     * Plays the prepared sound effects.
     */
    fun play() {
        mediaPlayer?.start()
    }

    /**
     * Pauses the currently playing sound effects.
     */
    fun pause() {
        mediaPlayer?.pause()
    }
}
