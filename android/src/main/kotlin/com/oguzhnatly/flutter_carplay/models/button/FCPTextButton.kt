package com.oguzhnatly.flutter_carplay.models.button

import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ParkedOnlyOnClickListener
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.CPTextButton
import com.oguzhnatly.flutter_carplay.CPTextButtonStyle
import com.oguzhnatly.flutter_carplay.FCPChannelTypes
import com.oguzhnatly.flutter_carplay.FCPStreamHandlerPlugin
import com.oguzhnatly.flutter_carplay.Throttle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A wrapper class for CPTextButton with additional functionality.
 *
 * @param obj A dictionary containing information about the text button.
 */
class FCPTextButton(obj: Map<String, Any>) {

    /// The underlying CPTextButton instance.
    private lateinit var _super: CPTextButton

    /// The unique identifier for the text button.
    var elementId: String
        private set

    /// The title associated with the text button.
    private var title: String

    /// Whether the text button is primary or not.
    ///
    /// Available only on Android Auto.
    var isPrimary: Bool

    /// The style of the text button.
    private var style: CPTextButtonStyle

    /// The throttle object that ensures that the map button is only executed once per interval.
    private val throttle = Throttle(CoroutineScope(Dispatchers.Main))

    init {
        val elementIdValue = obj["_elementId"] as? String
        val titleValue = obj["title"] as? String
        assert(elementIdValue != null || titleValue != null) {
            "Missing required keys: _elementId, title"
        }

        elementId = elementIdValue!!
        title = titleValue!!
        isPrimary = obj["isPrimary"] as? Bool ?: false

        val styleString = obj["style"] as? String ?: "normal"

        style = when (styleString) {
            "confirm" -> CPTextButtonStyle.confirm
            "cancel" -> CPTextButtonStyle.cancel
            else -> CPTextButtonStyle.normal
        }
    }

    /** Returns the underlying CPTextButton instance configured with the specified properties. */
    fun getTemplate(): CPTextButton {
        val onClick = {
            throttle.throttle(1000) {
                FCPStreamHandlerPlugin.sendEvent(
                    type = FCPChannelTypes.onTextButtonPressed.name,
                    data = mapOf("elementId" to elementId)
                )
            }
        }
        val textButton = Action.Builder().setTitle(title)
            .setOnClickListener(ParkedOnlyOnClickListener.create(onClick))
        when (style) {
            CPTextButtonStyle.confirm -> textButton.setBackgroundColor(CarColor.BLUE)
            CPTextButtonStyle.cancel -> textButton.setBackgroundColor(CarColor.RED)
            else -> {}
        }

        _super = textButton.build()
        return _super
    }
}
