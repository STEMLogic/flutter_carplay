package com.oguzhnatly.flutter_carplay.models.voice_control

import com.oguzhnatly.flutter_carplay.UIImage
import com.oguzhnatly.flutter_carplay.UIImageObject

/** Initializes a new instance of FCPVoiceControlState with the specified parameters.
 *
 * @param obj A dictionary containing the properties of the voice control state.
 */
class FCPVoiceControlState(obj: Map<String, Any>) {

    /// The unique identifier for the voice control state.
    var elementId: String
        private set

    /// The identifier associated with the voice control state.
    var identifier: String

    /// An array of title variants for the voice control state.
    var titleVariants: List<String>

    /// The name of the image associated with the voice control state.
    var image: UIImage? = null

    init {
        val elementIdValue = obj["_elementId"] as? String
        val identifier = obj["identifier"] as? String
        val titleVariants = obj["titleVariants"] as? List<String>
        assert(elementIdValue != null || identifier != null || titleVariants?.isNotEmpty() == true) {
            "Missing required keys in dictionary for FCPVoiceControlState initialization."
        }

        elementId = elementIdValue!!
        this.identifier = identifier!!
        this.titleVariants = titleVariants!!

        (obj["image"] as? String)?.let {
            image = UIImageObject.fromFlutterAsset(it)
        }
    }
}
