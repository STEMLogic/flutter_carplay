package com.oguzhnatly.flutter_carplay.models.voice_control

import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.CPVoiceControlTemplate
import com.oguzhnatly.flutter_carplay.FCPPresentTemplate
import com.oguzhnatly.flutter_carplay.managers.voice_control.FCPSpeechRecognizer

/**
 * A custom template for voice control on Android Auto.
 *
 * @param obj A dictionary containing the properties of the voice control template.
 */
class FCPVoiceControlTemplate(obj: Map<String, Any>) : FCPPresentTemplate() {
    /// The underlying CPVoiceControlTemplate instance.
    private lateinit var _super: CPVoiceControlTemplate

    /// The speech recognizer associated with the voice control template.
    private var speechRecognizer: FCPSpeechRecognizer? = null

    /// An array of voice control states in Objective-C representation.
    private var objcVoiceControlStates: List<FCPVoiceControlState>

    /// The locale associated with the voice control template.
    private var locale: String

    /// The identifier of the currently active voice control state.
    private var activeState: FCPVoiceControlState? = null

    /// Whether the recognizer is initialized.
    private var isRecognizerInitialized: Bool = false

    init {
        val elementIdValue = obj["_elementId"] as? String
        val localeValue = obj["locale"] as? String
        assert(elementIdValue != null || localeValue != null) {
            "Missing required keys in dictionary for FCPVoiceControlTemplate initialization."
        }

        elementId = elementIdValue!!
        locale = localeValue!!

        objcVoiceControlStates = (obj["voiceControlStates"] as? List<Map<String, Any>>)?.map {
            FCPVoiceControlState(it)
        } ?: emptyList()
        activeState = objcVoiceControlStates.first()
        speechRecognizer = FCPSpeechRecognizer()
    }

    /**
     * Returns a `CPVoiceControlTemplate` object representing the voice control template.
     *
     * @return A `CPVoiceControlTemplate` object.
     */
    override fun getTemplate(): CPVoiceControlTemplate {
        if (AndroidAutoService.session?.carContext != null && !isRecognizerInitialized) {
            speechRecognizer?.assistant?.initializeRecognizer()
            isRecognizerInitialized = true
        }

        val voiceControlTemplate =
            MessageTemplate.Builder(activeState?.titleVariants?.first() ?: "").setLoading(true)
                .setHeaderAction(Action.Builder(Action.BACK).setEnabled(false).build())
        _super = voiceControlTemplate.build()
        return _super
    }

    /**
     * Activates the voice control state with the specified identifier.
     *
     * @param identifier The identifier of the voice control state to activate.
     */
    fun activateVoiceControlState(identifier: String) {
        activeState = objcVoiceControlStates.find { it.identifier == identifier }
        onInvalidate()
    }

    /**
     * Retrieves the identifier of the currently active voice control state.
     *
     * @return The identifier of the active voice control state, or `nil` if none is active.
     */
    fun getActiveVoiceControlStateIdentifier(): String? {
        return activeState?.identifier
    }

    /** Starts the voice control template, initiating speech recognition. */
    fun start() {
        speechRecognizer?.record(locale)
    }

    /** Stops the voice control template, ending speech recognition. */
    fun stop() {
        speechRecognizer?.stopRecording()
    }
}
