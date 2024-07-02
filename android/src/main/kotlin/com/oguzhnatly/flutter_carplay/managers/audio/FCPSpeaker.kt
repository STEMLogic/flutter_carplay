package com.oguzhnatly.flutter_carplay.managers.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import java.util.Locale

/** A singleton class responsible for handling text-to-speech functionality in the Flutter CarPlay plugin. */
object FCPSpeaker : TextToSpeech.OnInitListener {
    private var audioManager: AudioManager? = null

    /// An optional error description that provides information about any encountered errors during text-to-speech.
    var errorDescription: String? = null

    /// The TextToSpeech instance responsible for synthesizing and speaking text.
    private var tts: TextToSpeech? = null

    /// A closure that will be called when speech synthesis is completed or canceled.
    private var willEnd: (() -> Unit)? = null

    /// The locale identifier.
    private var locale: Locale = Locale.US

    /** Initializes the text-to-speech manager and sets itself as the delegate for the TextToSpeech.*/
    fun initializeTTS() {
        AndroidAutoService.session?.carContext?.let {
            tts = TextToSpeech(it, this)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                // Called when speech is done
                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        willEnd?.invoke()
                    }
                }

                // Called when speech is having an error
                override fun onError(utteranceId: String?) {
                    errorDescription = "Error in onError with utteranceId: $utteranceId"
                    Handler(Looper.getMainLooper()).post {
                        willEnd?.invoke()
                    }
                }

                override fun onStart(utteranceId: String?) {}
            })
        }
        audioManager =
            AndroidAutoService.session?.carContext?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        requestAudioFocus()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = locale
        } else {
            errorDescription = "Initialization of TextToSpeech failed."
        }
    }

    private fun requestAudioFocus() {
        audioManager?.requestAudioFocus(
            null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    /** Determines if the given language is supported.
     * @param locale The locale identifier.
     * @return True if supported, false if not.
     */
    fun isLanguageAvailable(locale: Locale): Boolean {
        return isLanguageAvailable(locale.toString())
    }

    /** Determines if the given language is supported.
     * @param identifier The locale identifier.
     * @return True if supported, false if not.
     */
    fun isLanguageAvailable(identifier: String): Boolean {
        val availableLocales = tts?.availableLanguages
        return availableLocales?.any { it.toString() == identifier } ?: false
    }

    /** Sets the language.
     * @param locale The locale identifier.
     * @return True if supported, false if not.
     */
    fun setLanguage(locale: Locale): Boolean {
        return if (isLanguageAvailable(locale)) {
            this.locale = locale
            tts?.language = locale
            true
        } else {
            println("Google's TextToSpeech does not support this language: $locale. Keeping ${this.locale}.")
            false
        }
    }

    /** Synthesizes and speaks the provided text using the specified language.
     *
     * @param text The text to be spoken.
     * @param didEnd: A closure to be executed when speech synthesis is completed or canceled.
     */
    fun speak(text: String, didEnd: () -> Unit = {}) {
        willEnd = didEnd
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    /// Stops the ongoing speech synthesis immediately.
    fun stop() {
        tts?.stop()
    }
}
