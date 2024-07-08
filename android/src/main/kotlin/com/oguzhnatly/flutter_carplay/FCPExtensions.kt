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
