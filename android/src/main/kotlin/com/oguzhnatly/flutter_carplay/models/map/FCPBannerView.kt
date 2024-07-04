package com.oguzhnatly.flutter_carplay.models.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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
import java.io.ByteArrayOutputStream

class FCPBannerView {
    private val carContext: CarContext
        get() = AndroidAutoService.session?.carContext!!
    private var mapImageOverlay: MapImageOverlay? = null
    private var textView: String? = null
    private var backgroundColor: Int? = null
    var viewHeight: Double = 0.0
    private val fcpMapViewController: FCPMapViewController?
        get() = FlutterCarplayPlugin.rootViewController as? FCPMapViewController
    private val mapView: MapSurface?
        get() = fcpMapViewController?.mapView
    private val visibleArea: Rect?
        get() = fcpMapViewController?.visibleArea
    var isHidden: Boolean = true
        set(value) {
            if (field == value) return
            mapImageOverlay?.let {
                if (value) {
                    mapView?.mapScene?.removeMapImageOverlay(it)
                } else {
                    mapView?.mapScene?.removeMapImageOverlay(it)
                    mapView?.mapScene?.addMapImageOverlay(it)
                }
            }
            field = value
        }

    fun getView() {
        mapView ?: return
        visibleArea ?: return
        val linearLayout = LinearLayout(carContext)
        linearLayout.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                width = mapView!!.viewportSize.width.toInt()
                println(height)
                text = textView
                textSize = 24F
                setTextColor(Color.WHITE)
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
        val mapImage =
            MapImage(
                imageData,
                ImageFormat.PNG
            )
        if (mapImageOverlay == null) {
            mapImageOverlay = MapImageOverlay(
                Point2D(visibleArea!!.left.toDouble(), visibleArea!!.top.toDouble()),
                mapImage, Anchor2D(0.0, 0.0)
            )
        } else {
            mapImageOverlay?.image = mapImage
            mapImageOverlay?.anchor = Anchor2D(0.0, 0.0)
            mapImageOverlay?.viewCoordinates =
                Point2D(visibleArea!!.left.toDouble(), visibleArea!!.top.toDouble())
        }
        viewHeight = linearLayout.height.toDouble()
    }

    fun setMessage(text: String) {
        textView = text
        getView()
    }

    fun setBackgroundColor(color: Long) {
        backgroundColor = color.toInt()
        getView()
    }
}