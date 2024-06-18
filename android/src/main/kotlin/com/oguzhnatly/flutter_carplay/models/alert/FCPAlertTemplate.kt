package com.oguzhnatly.flutter_carplay.models.alert

import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import com.oguzhnatly.flutter_carplay.CPAlertAction
import com.oguzhnatly.flutter_carplay.CPAlertActionStyle
import com.oguzhnatly.flutter_carplay.CPAlertTemplate
import com.oguzhnatly.flutter_carplay.FCPPresentTemplate
import com.oguzhnatly.flutter_carplay.models.alert.FCPAlertAction

/**
 * A wrapper class for CPActionSheetTemplate with additional functionality.
 *
 * @param obj A map containing information about the action sheet template.
 */
class FCPAlertTemplate(obj: Map<String, Any>) : FCPPresentTemplate() {

    /// The underlying CPActionSheetTemplate instance.
    private lateinit var _super: CPAlertTemplate

    /// The title of the action sheet template (optional).
    private var titleVariants: List<String>?

    /// An array of CPAlertAction instances associated with the action sheet template.
    private var actions: List<CPAlertAction>

    /// An array of FCPAlertAction instances associated with the action sheet template.
    private var objcActions: List<FCPAlertAction>

    init {
        val elementIdValue = obj["_elementId"] as? String
        assert(elementIdValue != null) { "Missing required key: _elementId" }
        elementId = elementIdValue!!
        titleVariants = obj["titleVariants"] as? List<String>
        objcActions = (obj["actions"] as? List<Map<String, Any>>)?.map {
            FCPAlertAction(it)
        } ?: emptyList()
        actions = objcActions.map { it.getTemplate() }
    }

    /** Returns the underlying CPActionSheetTemplate instance configured with the specified properties. */
    override fun getTemplate(): CPAlertTemplate {
        val stringBuilder = StringBuilder()
        titleVariants?.forEach {
            stringBuilder.append(it)
            stringBuilder.append("\n")
        }
        val alertTemplate =
            MessageTemplate.Builder(stringBuilder.toString())
        objcActions.forEach {
            when (it.style) {
                CPAlertActionStyle.destructive, CPAlertActionStyle.cancel -> {
                    alertTemplate.addAction(it.getTemplate())
                }

                else -> {
                    val actionStripBuilder = ActionStrip.Builder()
                    actionStripBuilder.addAction(it.getTemplate())
                    alertTemplate.setActionStrip(actionStripBuilder.build())
                }
            }
        }
        _super = alertTemplate.build()
        return _super
    }

}
