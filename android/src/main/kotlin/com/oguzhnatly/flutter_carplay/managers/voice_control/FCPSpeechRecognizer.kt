package com.oguzhnatly.flutter_carplay.managers.voice_control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.FlutterCarplayPlugin
import com.oguzhnatly.flutter_carplay.Logger
import com.oguzhnatly.flutter_carplay.sendSpeechRecognitionTranscriptChangeEvent

/** A structure handling speech recognition functionality in the Flutter CarPlay plugin. */
class FCPSpeechRecognizer {

    /** A nested class responsible for managing the speech transcript. */
    private class FCPSpeechTranscript {
        var transcript: String = ""
            private set

        fun set(newValue: String) {
            FlutterCarplayPlugin.sendSpeechRecognitionTranscriptChangeEvent(transcript = newValue)
            transcript = newValue
        }

        fun get(): String {
            return transcript
        }
    }

    /** A nested class assisting in speech recognition tasks. */
    inner class FCPSpeechAssist {
        var speechRecognizer: SpeechRecognizer? = null
        var recognizerIntent: Intent? = null

        /** Initializes the text-to-speech manager and sets itself as the delegate for the TextToSpeech.*/
        fun initializeRecognizer() {
            AndroidAutoService.session?.carContext?.let {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(it)

                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            }
        }

        /// Resets the speech recognition components.
        fun reset() {
            speechRecognizer?.destroy()
            speechRecognizer = null
            recognizerIntent = null
        }

        /** Sets the locale for the speech recognizer.
         *
         * @param locale The locale identifier.
         */
        fun setLocale(locale: String?) {
            recognizerIntent?.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale ?: "en")
            Logger.log(
                "[FlutterCarPlay]: Voice Control for locale " + (locale
                    ?: "en-US") + " is initialized."
            )
        }
    }

    /// An instance of FCPSpeechTranscript managing the speech transcript.
    private val transcript = FCPSpeechTranscript()

    /// An instance of FCPSpeechAssist assisting in speech recognition tasks.
    val assistant = FCPSpeechAssist()

    /** Initiates the speech recognition process.
     *
     * @param locale The locale identifier.
     */
    fun record(locale: String?) {
        Logger.log("[FlutterCarPlay]: Requesting access for Voice Control.")

        assistant.setLocale(locale)

        canAccess { authorized ->
            if (!authorized) {
                Log.d("[FlutterCarPlay]", "Access denied.")
                return@canAccess
            }
            Logger.log("[FlutterCarPlay]: Access granted.")

            assistant.speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Logger.log("[FlutterCarPlay]: Preparing audio engine.")
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                }

                override fun onError(error: Int) {
                    Logger.log("[FlutterCarPlay]: Error while transcribing audio: $error")
                    assistant.speechRecognizer?.startListening(assistant.recognizerIntent)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    println("onResults----${matches.toString()}")
                    if (!matches.isNullOrEmpty()) {
                        relay(matches[0])
                    }
                    assistant.speechRecognizer?.startListening(assistant.recognizerIntent)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    println("onPartialResults----${matches.toString()}")
                    if (!matches.isNullOrEmpty()) {
                        relay(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                }
            })

            assistant.speechRecognizer?.startListening(assistant.recognizerIntent)
        }
    }

    /// Stops the ongoing speech recognition.
    fun stopRecording() {
        Logger.log("[FlutterCarPlay]: Voice Control Record has been stopped.")
        assistant.reset()
    }

    /** Checks if the app has access to speech recognition and audio recording.
     *
     * @param handler A closure indicating whether access is granted.
     */
    private fun canAccess(handler: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                AndroidAutoService.session!!.carContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            handler(true)
        } else {
            handler(false)
        }
    }

    /** Relays the speech recognition result to the transcript.
     *
     * @param message The recognized speech message.
     */
    private fun relay(message: String) {
        transcript.set(newValue = message)
    }
}
