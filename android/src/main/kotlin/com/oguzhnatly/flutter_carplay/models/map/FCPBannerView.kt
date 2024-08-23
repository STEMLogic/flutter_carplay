package com.oguzhnatly.flutter_carplay.models.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import com.here.sdk.core.Anchor2D
import com.here.sdk.core.Point2D
import com.here.sdk.mapview.ImageFormat
import com.here.sdk.mapview.MapImage
import com.here.sdk.mapview.MapImageOverlay
import com.here.sdk.mapview.MapSurface
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.FlutterCarplayPlugin
import com.oguzhnatly.flutter_carplay.getContrastTextColor
import java.io.ByteArrayOutputStream

/** A custom banner view with a message label for the map view controller. */
class FCPBannerView {
    private val carContext: CarContext
        get() = AndroidAutoService.session?.carContext!!
    private val fcpMapViewController: FCPMapViewController?
        get() = FlutterCarplayPlugin.rootViewController as? FCPMapViewController
    private val mapView: MapSurface?
        get() = fcpMapViewController?.mapView
    private val visibleArea: Rect?
        get() = fcpMapViewController?.visibleArea

    private var mapImageOverlay: MapImageOverlay? = null
    private var backgroundColor: Int? = null
    private var textView: String? = null

    var height: Double = 0.0
    var isHidden: Boolean = true
        set(value) {
            if (field == value || mapView == null) return
            mapImageOverlay?.let {
                if (value) {
                    mapView?.mapScene?.removeMapImageOverlay(it)
                } else {
                    mapView?.mapScene?.removeMapImageOverlay(it)
                    mapView?.mapScene?.addMapImageOverlay(it)
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            fcpMapViewController?.overlayView?.getView()
                            fcpMapViewController?.tripPreview?.getView()
                        },
                        100L
                    )
                }
            }
            field = value
        }

    /** Get the view for the banner. */
    fun getView() {
        mapView ?: return
        visibleArea ?: return
        val linearLayout = LinearLayout(carContext)
        linearLayout.apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(View(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    visibleArea!!.left,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                setBackgroundColor(Color.TRANSPARENT)
            })
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                width = mapView!!.viewportSize.width.toInt() - visibleArea!!.left
                text = textView
                textSize = 24F
                backgroundColor?.let { setTextColor(getContrastTextColor(it)) }
                    ?: setTextColor(Color.WHITE)
                setPadding(20, 8, 20, 8)
                gravity = android.view.Gravity.CENTER
            })
            gravity = android.view.Gravity.CENTER
        }

        val specWidth = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val specHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        linearLayout.measure(specWidth, specHeight)
        linearLayout.layout(0, 0, linearLayout.measuredWidth, linearLayout.measuredHeight)
        backgroundColor?.let { linearLayout.setBackgroundColor(it) }

        if (linearLayout.measuredWidth <= 0 || linearLayout.measuredHeight <= 0) return
        val bitmap = Bitmap.createBitmap(
            linearLayout.measuredWidth,
            linearLayout.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        linearLayout.draw(canvas)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageData = stream.toByteArray()
        bitmap.recycle()

        val mapImage = MapImage(imageData, ImageFormat.PNG)
        val point2D = Point2D(0.0, visibleArea!!.top.toDouble())
        val anchor2D = Anchor2D(0.0, 0.0)

        if (mapImageOverlay == null) {
            mapImageOverlay = MapImageOverlay(point2D, mapImage, anchor2D)
        } else {
            mapImageOverlay?.image = mapImage
            mapImageOverlay?.anchor = anchor2D
            mapImageOverlay?.viewCoordinates = point2D
        }
        height = linearLayout.height.toDouble()
    }

    /** Set the text for the banner. */
    fun setMessage(text: String) {
        textView = text
        getView()
    }

    /** Set the background color for the banner. */
    fun setBackgroundColor(color: Long, darkColor: Long) {
        backgroundColor = AndroidAutoService.session?.carContext?.let {
            if (it.isDarkMode) darkColor.toInt() else color.toInt()
        } ?: color.toInt()
        getView()
    }
}
