package com.oguzhnatly.flutter_carplay.models.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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


/** A custom trip preview for the map view controller. */
class FCPTripPreview {
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

    var width: Double = 0.0
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
        val frameLayout = FrameLayout(carContext)
        val linearLayout = LinearLayout(carContext)
        val shape = GradientDrawable()
        shape.setCornerRadius(10f)
        linearLayout.apply {
            clipToPadding = false
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            setPadding(4, 4, 4, 4)
            val typedValue = TypedValue()
            val theme: Resources.Theme = carContext.theme
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            val textColor = typedValue.data

            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                maxWidth = ((mapView?.viewportSize?.width ?: 0.0) * 0.3).toInt()
                text = primaryTitle
                textSize = 20F
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
                setPadding(10, 10, 10, 0)
            })
            addView(TextView(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                maxWidth = ((mapView?.viewportSize?.width ?: 0.0) * 0.3).toInt()
                text = secondaryTitle
                textSize = 20F
                setTextColor(textColor)
                setPadding(10, 0, 10, 10)
            })
        }

        val specWidth = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val specHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        linearLayout.measure(specWidth, specHeight)
        linearLayout.layout(0, 0, linearLayout.measuredWidth, linearLayout.measuredHeight)

        frameLayout.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(View(carContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ((mapView?.viewportSize?.width ?: 0.0) * 0.3).toInt(),
                    (mapView?.viewportSize?.height
                        ?: visibleArea!!.height()).toInt() - visibleArea!!.top - (fcpMapViewController?.bannerViewHeight?.toInt()
                        ?: 0) - 24
                )

                val typedValue = TypedValue()
                val theme: Resources.Theme = carContext.theme
                theme.resolveAttribute(
                    android.R.attr.panelColorBackground,
                    typedValue,
                    true
                )
                background =
                    getDrawableWithRadius(
                        typedValue.data,
                        floatArrayOf(10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f)
                    )
            })

            addView(linearLayout)
        }

        frameLayout.measure(specWidth, specHeight)
        frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)

        if(linearLayout.measuredWidth <= 0 || linearLayout.measuredHeight <= 0) return
        val bitmap = Bitmap.createBitmap(
            frameLayout.measuredWidth,
            frameLayout.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        frameLayout.draw(canvas)
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
}
