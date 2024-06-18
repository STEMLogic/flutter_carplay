package com.oguzhnatly.flutter_carplay

import android.graphics.BitmapFactory
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

object UIImageObject {
    /**
     * Fetches a UIImage from a Flutter asset using the asset name.
     *
     * @param name The name of the Flutter asset.
     * @return A UIImage fetched from the Flutter asset or a system image if not found.
     */
    fun fromFlutterAsset(name: String): UIImage {
        val flutterPluginBinding = FlutterCarplayPlugin.flutterPluginBinding ?: return CarIcon.ERROR

        try {
            val path =
                flutterPluginBinding.flutterAssets.getAssetFilePathBySubpath(name)
            if (path?.isNotEmpty() == true) {
                flutterPluginBinding.applicationContext.assets.open(path).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    return CarIcon.Builder(IconCompat.createWithBitmap(bitmap))
                        .build()
                }
            }
        } catch (e: Exception) {
            Logger.log(e.message ?: e.toString(), tag = "UIImage")
            return CarIcon.ERROR
        }
        Logger.log("image \"$name\" not found", tag = "UIImage")
        return CarIcon.ERROR

        // Check if the asset is a GIF
//    if name.hasSuffix(".gif"), let gifData = try? Data(contentsOf: URL(fileURLWithPath: path)) {
//        return UIImage.gifImageWithData(gifData) ?? UIImage()
//    }

//    return image

    }
}

/**
 * Applies a color tint to the UIImage and returns a new UIImage with the tint applied.
 *
 * @param color The color to apply as a tint.
 * @param colorDark The dark color to apply as a tint (optional).
 * @return A new UIImage with the color tint applied.
 */
fun UIImage.withColor(color: Long, colorDark: Long? = null): UIImage {
    return CarIcon.Builder(this).setTint(
        CarColor.createCustom(color.toInt(), (colorDark ?: color).toInt())
    ).build()
}
