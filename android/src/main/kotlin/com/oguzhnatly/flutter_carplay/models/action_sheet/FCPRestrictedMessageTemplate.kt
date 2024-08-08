package com.oguzhnatly.flutter_carplay.models.action_sheet

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.CPAlertAction
import com.oguzhnatly.flutter_carplay.CPAlertActionStyle
import com.oguzhnatly.flutter_carplay.CPTemplate
import com.oguzhnatly.flutter_carplay.FCPPresentTemplate
import com.oguzhnatly.flutter_carplay.models.alert.FCPAlertAction

/**
 * A wrapper class for FCPRestrictedMessageTemplate to show restricted use message
 */
class FCPRestrictedMessageTemplate(val message : String) : FCPPresentTemplate() {

    /** Returns the underlying CPTemplate instance configured with the specified properties. */
    override fun getTemplate(): CPTemplate {
        val actionSheetTemplate =
            MessageTemplate.Builder(message).setTitle(" ").setIcon(CarIcon.ALERT)
                .setHeaderAction(Action.BACK)
        return actionSheetTemplate.build()
    }
}
