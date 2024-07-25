package com.oguzhnatly.flutter_carplay.models.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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

/** A custom overlay view for the map view controller. */
class FCPOverlayView {
    private val carContext: CarContext
        get() = AndroidAutoService.session?.carContext!!
    private val fcpMapViewController: FCPMapViewController?
        get() = FlutterCarplayPlugin.rootViewController as? FCPMapViewController
    private val mapView: MapSurface?
        get() = fcpMapViewController?.mapView
    private val visibleArea: Rect?
        get() = fcpMapViewController?.visibleArea

    private var mapImageOverlay: MapImageOverlay? = null
    private var primaryTitle: String? = null
    private var secondaryTitle: String? = null
    private var subtitle: String? = null

    var width: Double = 0.0
    var isHidden: Boolean = true
        set(value) {
            if (field == value || mapView == null) return
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

    /** Get a drawable with rounded corners */
    private fun getDrawableWithRadius(color: Int? = null, floatArray: FloatArray): Drawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.setCornerRadii(floatArray)
        gradientDrawable.setColor(color ?: Color.TRANSPARENT)
        return gradientDrawable
    }

    /** Get the view for the overlay. */
    fun getView() {
        mapView ?: return
        visibleArea ?: return
        val linearLayout = LinearLayout(carContext)
        val shape = GradientDrawable()
        shape.setCornerRadius(10f)
        linearLayout.apply {
            clipToPadding = false
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(4, 4, 4, 4)
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = primaryTitle
                textSize = 20F
                maxLines = 1
                setTextColor(Color.WHITE)
                setPadding(10, 10, 10, 0)
                background = getDrawableWithRadius(
                    Color.parseColor("#004000"),
                    floatArrayOf(10f, 10f, 10f, 10f, 0f, 0f, 0f, 0f)
                )
            })
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                maxWidth = ((mapView?.viewportSize?.width ?: 0.0) * 0.65).toInt()
                text = secondaryTitle
                textSize = 20F
                setTextColor(Color.WHITE)
                background = getDrawableWithRadius(
                    Color.parseColor("#004000"),
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                )
                setPadding(10, 0, 10, 15)
            })
            addView(View(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.TRANSPARENT)
            })
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = subtitle
                maxWidth = ((mapView?.viewportSize?.width ?: 0.0) * 0.65).toInt()
                textSize = 20F
                setTextColor(Color.WHITE)
                background = getDrawableWithRadius(
                    Color.parseColor("#004000"),
                    floatArrayOf(0f, 0f, 0f, 0f, 10f, 10f, 10f, 10f)
                )
                setPadding(10, 15, 10, 15)
            })
        }

        val specWidth = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val specHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        linearLayout.measure(specWidth, specHeight)
        linearLayout.layout(0, 0, linearLayout.measuredWidth, linearLayout.measuredHeight)

        if(linearLayout.measuredWidth <= 0 || linearLayout.measuredHeight <= 0) return
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
        val point2D = Point2D(
            visibleArea!!.left.toDouble() + 12.0,
            (fcpMapViewController?.bannerViewHeight
                ?: 0.0).toDouble() + visibleArea!!.top.toDouble() + 12.0
        )
        val anchor2D = Anchor2D(0.0, 0.0)

        if (mapImageOverlay == null) {
            mapImageOverlay = MapImageOverlay(point2D, mapImage, anchor2D)
        } else {
            mapImageOverlay?.image = mapImage
            mapImageOverlay?.anchor = anchor2D
            mapImageOverlay?.viewCoordinates = point2D
        }
        width = linearLayout.width.toDouble()
    }

    /** Set the primary title of the overlay. */
    fun setPrimaryTitle(text: String) {
        primaryTitle = text
        getView()
    }

    /** Set the secondary title of the overlay. */
    fun setSecondaryTitle(text: String) {
        secondaryTitle = text
        getView()
    }

    /** Set the subtitle of the overlay. */
    fun setSubtitle(text: String) {
        subtitle = text
        getView()
    }
}
