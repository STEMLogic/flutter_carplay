package com.oguzhnatly.flutter_carplay

import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import java.io.ByteArrayOutputStream

/**
 * Applies a color tint to the UIImage and returns a new UIImage with the tint applied.
 *
 * @param color The color to apply as a tint.
 * @param colorDark The dark color to apply as a tint (optional).
 * @return A new UIImage with the color tint applied.
 */
fun UIImage.withColor(color: Long, colorDark: Long? = null): UIImage {
    return CarIcon.Builder(this)
        .setTint(CarColor.createCustom(color.toInt(), (colorDark ?: color).toInt()))
        .build()
}

/// Creates a dynamic image that supports displaying a different image asset when dark mode is active.
private fun dynamicImageWith(
    light: UIImage,
    light2x: UIImage? = null,
    light3x: UIImage? = null,
    dark: UIImage,
    dark2x: UIImage? = null,
    dark3x: UIImage? = null,
): UIImage {
    // Currently dark theme is default for Android Auto. So we use dark images.
    // Dynamic images for reference for future.
    /*val image: UIImage = if (AndroidAutoService.session?.carContext?.isDarkMode == true) {
        dark3x ?: (dark2x ?: dark)
    } else {
        light3x ?: (light2x ?: light)
    }
    return image*/
    return dark3x ?: (dark2x ?: dark)
}

/**
 * Get dynamic theme image
 *
 * @param lightImage The image name for light mode
 * @param darkImage The image name for dark mode
 * @return The UIImage
 */
fun dynamicImage(lightImage: String? = null, darkImage: String? = null): UIImage? {
    when {
        lightImage?.isNotEmpty() == true && darkImage?.isNotEmpty() == true -> {
            return dynamicImageWith(
                light = UIImageObject.fromFlutterAsset(lightImage) ?: CarIcon.ERROR,
                light2x = UIImageObject.fromFlutterAsset(lightImage.replaceLast(".png", "@2x.png")),
                light3x = UIImageObject.fromFlutterAsset(lightImage.replaceLast(".png", "@3x.png")),
                dark = UIImageObject.fromFlutterAsset(darkImage) ?: CarIcon.ERROR,
                dark2x = UIImageObject.fromFlutterAsset(darkImage.replaceLast(".png", "@2x.png")),
                dark3x = UIImageObject.fromFlutterAsset(darkImage.replaceLast(".png", "@3x.png"))
            )
        }

        lightImage?.isNotEmpty() == true -> {
            return dynamicImageWith(
                light = UIImageObject.fromFlutterAsset(lightImage) ?: CarIcon.ERROR,
                light2x = UIImageObject.fromFlutterAsset(lightImage.replaceLast(".png", "@2x.png")),
                light3x = UIImageObject.fromFlutterAsset(lightImage.replaceLast(".png", "@3x.png")),
                dark = UIImageObject.fromFlutterAsset("") ?: CarIcon.ERROR,
            )
        }

        darkImage?.isNotEmpty() == true -> {
            return dynamicImageWith(
                light = UIImageObject.fromFlutterAsset("") ?: CarIcon.ERROR,
                dark = UIImageObject.fromFlutterAsset(darkImage) ?: CarIcon.ERROR,
                dark2x = UIImageObject.fromFlutterAsset(darkImage.replaceLast(".png", "@2x.png")),
                dark3x = UIImageObject.fromFlutterAsset(darkImage.replaceLast(".png", "@3x.png"))
            )
        }

        else -> {
            return null
        }
    }
}

/** Converts a snake case string to camel case. */
fun String.snakeToLowerCamelCase(): String {
    return split("_").joinToString("") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }.replaceFirstChar { it.lowercase() }
}

/** Converts a path to a byte array. */
fun String.pathToByteArray(): ByteArray {
    val flutterPluginBinding = FlutterCarplayPlugin.flutterPluginBinding ?: return byteArrayOf()

    try {
        val path =
            flutterPluginBinding.flutterAssets.getAssetFilePathBySubpath(this)
        if (path?.isNotEmpty() == true) {
            val bufLen = 4 * 0x400 // 4KB
            val buf = ByteArray(bufLen)
            var readLen: Int

            ByteArrayOutputStream().use { outputStream ->
                flutterPluginBinding.applicationContext.assets.open(path).use { inputStream ->
                    while (inputStream.read(buf, 0, bufLen).also { readLen = it } != -1)
                        outputStream.write(buf, 0, readLen)
                }

                return outputStream.toByteArray()
            }
        }
    } catch (e: Exception) {
        Logger.log(e.message ?: e.toString(), tag = "UIImage catch")
        return byteArrayOf()
    }
    return byteArrayOf()
}

fun String.replaceLast(searchString: String, replacementString: String): String {
    val lastIndex = lastIndexOf(searchString)
    if (lastIndex == -1) {
        return this
    }
    val prefix = substring(0, lastIndex)
    val suffix = substring(lastIndex + searchString.length)
    return "$prefix$replacementString$suffix"
}
