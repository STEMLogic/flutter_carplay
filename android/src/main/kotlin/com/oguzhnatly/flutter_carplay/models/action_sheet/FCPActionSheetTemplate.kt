package com.oguzhnatly.flutter_carplay.models.action_sheet

import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.CPActionSheetTemplate
import com.oguzhnatly.flutter_carplay.CPAlertAction
import com.oguzhnatly.flutter_carplay.CPAlertActionStyle
import com.oguzhnatly.flutter_carplay.CPTemplate
import com.oguzhnatly.flutter_carplay.FCPPresentTemplate
import com.oguzhnatly.flutter_carplay.models.alert.FCPAlertAction

/**
 * A wrapper class for CPActionSheetTemplate with additional functionality.
 *
 * @param obj A map containing information about the action sheet template.
 */
class FCPActionSheetTemplate(obj: Map<String, Any>) : FCPPresentTemplate() {
    /// The underlying CPActionSheetTemplate instance.
    private lateinit var _super: CPTemplate

    /// The title of the action sheet template (optional).
    private var title: String?

    /// The message of the action sheet template (optional).
    private var message: String?

    /// An array of CPAlertAction instances associated with the action sheet template.
    private var actions: List<CPAlertAction>

    /// An array of FCPAlertAction instances associated with the action sheet template.
    private var objcActions: List<FCPAlertAction>

    /// Indicates whether the action sheet template is a long message.
    private var isLongMessage: Bool = false

    init {
        val elementIdValue = obj["_elementId"] as? String
        assert(elementIdValue != null) { "Missing required key: _elementId" }
        elementId = elementIdValue!!
        title = obj["title"] as? String
        message = obj["message"] as? String
        objcActions = (obj["actions"] as? List<Map<String, Any>>)?.map {
            FCPAlertAction(it)
        } ?: emptyList()
        actions = objcActions.map { it.getTemplate() }
        isLongMessage = obj["isLongMessage"] as? Boolean ?: false
    }

    /** Returns the underlying CPTemplate instance configured with the specified properties. */
    override fun getTemplate(): CPTemplate {
        return if (isLongMessage)
            longMessageTemplate()
        else
            messageTemplate()
    }

    private fun longMessageTemplate(): CPTemplate {
        val actionSheetTemplate =
            LongMessageTemplate.Builder(message?:"").setTitle(title?:"")
        objcActions.forEach {
            when (it.style) {
                CPAlertActionStyle.destructive, CPAlertActionStyle.cancel -> {
                    actionSheetTemplate.addAction(it.getTemplate())
                }

                else -> {
                    val actionStripBuilder = ActionStrip.Builder()
                    actionStripBuilder.addAction(it.getTemplate())
                    actionSheetTemplate.setActionStrip(actionStripBuilder.build())
                }
            }
        }
        _super = actionSheetTemplate.build()
        return _super
    }

    private fun messageTemplate(): CPTemplate {
        val actionSheetTemplate =
            MessageTemplate.Builder(message?:"").setTitle(title?:"").setIcon(CarIcon.ALERT)
        objcActions.forEach {
            when (it.style) {
                CPAlertActionStyle.destructive, CPAlertActionStyle.cancel -> {
                    actionSheetTemplate.addAction(it.getTemplate())
                }

                else -> {
                    val actionStripBuilder = ActionStrip.Builder()
                    actionStripBuilder.addAction(it.getTemplate())
                    actionSheetTemplate.setActionStrip(actionStripBuilder.build())
                }
            }
        }
        _super = actionSheetTemplate.build()
        return _super
    }
}
