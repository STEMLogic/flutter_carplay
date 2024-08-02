package com.oguzhnatly.flutter_carplay.models.map

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.here.sdk.core.Anchor2D
import com.here.sdk.core.GeoBox
import com.here.sdk.core.GeoCoordinates
import com.here.sdk.core.GeoCoordinatesUpdate
import com.here.sdk.core.GeoOrientationUpdate
import com.here.sdk.core.Metadata
import com.here.sdk.core.Point2D
import com.here.sdk.core.Rectangle2D
import com.here.sdk.core.Size2D
import com.here.sdk.mapview.MapCameraAnimationFactory
import com.here.sdk.mapview.MapError
import com.here.sdk.mapview.MapFeatures
import com.here.sdk.mapview.MapMeasure
import com.here.sdk.mapview.MapScheme
import com.here.sdk.mapview.MapSurface
import com.here.sdk.mapview.MapViewBase
import com.here.sdk.mapview.MapViewLifecycleListener
import com.here.sdk.routing.Waypoint
import com.here.time.Duration
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.Debounce
import com.oguzhnatly.flutter_carplay.FlutterCarplayPlugin
import com.oguzhnatly.flutter_carplay.FlutterCarplayTemplateManager
import com.oguzhnatly.flutter_carplay.Logger
import com.oguzhnatly.flutter_carplay.MapMarkerType
import com.oguzhnatly.flutter_carplay.Throttle
import com.oguzhnatly.flutter_carplay.models.map.here_map.CGSize
import com.oguzhnatly.flutter_carplay.models.map.here_map.ConstantsEnum
import com.oguzhnatly.flutter_carplay.models.map.here_map.MapController
import com.oguzhnatly.flutter_carplay.models.map.here_map.MapCoordinates
import com.oguzhnatly.flutter_carplay.models.map.here_map.locationUpdatedHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.recenterMapViewHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.toggleSatelliteViewHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.updateMapCoordinatesHandler
import com.oguzhnatly.flutter_carplay.pathToByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.max
import kotlin.math.min

/** A custom Android Auto map view controller. */
class FCPMapViewController : SurfaceCallback {
    /// The private map surface instance.
    private val mapSurface: MapSurface = MapSurface()

    /// The map view associated with the map view controller.
    var mapView: MapSurface? = null

    /// The banner view associated with the map view controller.
    val bannerView = FCPBannerView()
    //
    //    /// The toast view associated with the map view controller.
    //    @IBOutlet
    //    var toastView: FCPToastView!
    //    {
    //        didSet {
    //            guard let view = toastView else { return }
    //            view.backgroundColor = UIColor.black.withAlphaComponent(0.8)
    //            view.layer.cornerRadius = 10
    //            view.alpha = 0.0
    //        }
    //    }

    /// The overlay view associated with the map view controller.
    val overlayView = FCPOverlayView()

    /// The trip preview associated with the map view controller.
    val tripPreview = FCPTripPreview()

    /// The stable area associated with the map view controller.
    private var stableArea = Rect(0, 0, 0, 0)

    /// The visible area associated with the map view controller.
    var visibleArea = Rect(0, 0, 0, 0)

    /// The app associated with the map view controller.
    var mapController: MapController? = MapController

    /// The size of the marker pin.
    val markerPinSize: Double
        get() = 40 * (mapView?.pixelScale ?: 1.0)

    /// Recenter map position to adjust the map camera.
    private var recenterMapPosition = "initialMarker"

    /// Map coordinates to render marker on the map.
    var mapCoordinates = MapCoordinates()

    /// Whether the satellite view is enabled.
    private var isSatelliteViewEnabled = false

    /// Whether the dashboard scene is active.
    var isDashboardSceneActive
        get() = FlutterCarplayTemplateManager.isDashboardSceneActive
        set(value) {
            FlutterCarplayTemplateManager.isDashboardSceneActive = value
        }

    /// The map template associated with the map view controller.
    private val fcpMapTemplate: FCPMapTemplate?
        get() = FlutterCarplayPlugin.fcpRootTemplate as? FCPMapTemplate

    /// Whether the dashboard scene is active.
    var isPanningInterfaceVisible
        get() = fcpMapTemplate?.isPanningInterfaceVisible ?: false
        set(value) {
            fcpMapTemplate?.togglePanningInterface(value)
        }

    /// Should stop voice assistant.
    var shouldStopVoiceAssistant = true

    /// The voice instructions toggle button.
    var isVoiceInstructionsMuted = false

    /// Whether navigation is in progress.
    var isNavigationInProgress = false

    /// Should show banner.
    var shouldShowBanner = false

    /// Should show overlay.
    var shouldShowOverlay = false

    /// Should show trip preview.
    var shouldShowTripPreview = false

    /// Overlay view width.
    var overlayViewWidth = 0.0

    /// Trip preview width.
    var tripPreviewWidth = 0.0

    /// Banner view height.
    var bannerViewHeight = 0.0

    /// To perform actions only once when map is loaded.
    private var mapLoadedOnce = false

    /// A debounce object for optimizing surface events.
    private var surfaceDebounce = Throttle(CoroutineScope(Dispatchers.Main))

    /// A debounce object for optimizing camera updates.
    private var cameraUpdateDebounce = Debounce(CoroutineScope(Dispatchers.Main))

    /// Default coordinates for the map.
//    val defaultCoordinates = GeoCoordinates(21.1812352, 72.8629248)
    private val defaultCoordinates = GeoCoordinates(-25.02970994781628, 134.28333173662492)

    /// Whether the surface is ready.
    private fun isSurfaceReady(surfaceContainer: SurfaceContainer): Bool {
        return surfaceContainer.surface != null && surfaceContainer.dpi != 0 && surfaceContainer.height != 0 && surfaceContainer.width != 0
    }

    /**
     * Sets the surface of the map view with the provided surface container.
     *
     * @param surfaceContainer the surface container containing the surface, width, and height
     */
    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        if (!isSurfaceReady(surfaceContainer)) return

        surfaceDebounce.throttle(1000L) {
            mapLoadedOnce = false
            mapView = null

            AndroidAutoService.session?.carContext.let {
                mapSurface.setSurface(
                    it,
                    surfaceContainer.surface,
                    surfaceContainer.width,
                    surfaceContainer.height,
                )
            }

            // Wait for the map surface to be valid
            while (mapSurface.isValid == false) {
                if (mapSurface.isValid == true) break
            }

            // Set the map surface to the map view only if the map surface is valid
            if (mapSurface.isValid == true) mapView = mapSurface

            toggleSatelliteViewHandler = { isSatelliteViewEnabled: Bool ->
                this.isSatelliteViewEnabled = isSatelliteViewEnabled

                var mapScheme = MapScheme.NORMAL_DAY

                AndroidAutoService.session?.carContext?.let {
                    mapScheme = if (it.isDarkMode) {
                        if (isSatelliteViewEnabled) MapScheme.HYBRID_NIGHT else MapScheme.NORMAL_NIGHT
                    } else {
                        if (isSatelliteViewEnabled) MapScheme.HYBRID_DAY else MapScheme.NORMAL_DAY
                    }
                }

                mapView?.mapScene?.loadScene(mapScheme, ::onLoadScene)
            }

            // Load the map scene using a map scheme to render the map with.
            toggleSatelliteViewHandler?.invoke(isSatelliteViewEnabled)

            // Need to call hideSubViews in order to reset the isHidden variable
            // and show the views again.
            hideSubviews()
            showSubviews()
        }
    }

    /**
     * Destroys the surface of the map view when the surface is destroyed.
     *
     * @param surfaceContainer the surface container that is being destroyed
     */
    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mapController?.clearWaypointMapMarkers()
        mapController?.clearMapPolygons()
        mapSurface.destroySurface()
        mapView = null
        (FlutterCarplayPlugin.fcpRootTemplate as? FCPMapTemplate)?.resetCarContext()
        hideSubviews()
    }

    /**
     * Updates the stable area of the view controller and updates the camera principal point.
     *
     * @param stableArea the new stable area of the view controller
     */
    override fun onStableAreaChanged(stableArea: Rect) {
        if (stableArea.isEmpty) return

        this.stableArea = stableArea
        Logger.log("Stable Area Updated: $stableArea")

        cameraUpdateDebounce.debounce(500L) {
            updateCameraPrincipalPoint()
        }
    }

    /**
     * Updates the visible area of the map view controller and updates the camera principal point.
     *
     * @param visibleArea the new visible area of the map view controller
     */
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (visibleArea.isEmpty) return

        this.visibleArea = visibleArea
        Logger.log("Visible Area Updated: $visibleArea")

        bannerView.getView()
        overlayView.getView()
        tripPreview.getView()

        cameraUpdateDebounce.debounce(500L) {
            updateCameraPrincipalPoint()
        }
    }

    /**
     * Will be called on scroll event. Needs car api version 2 to work. See
     * [SurfaceCallback.onScroll] definition for more details.
     */
    override fun onScroll(distanceX: Float, distanceY: Float) {
        isPanningInterfaceVisible = true
        mapView?.gestures?.scrollHandler?.onScroll(distanceX, distanceY)
    }

    /**
     * Will be called on scale event. Needs car api version 2 to work. See [SurfaceCallback.onScale]
     * definition for more details.
     */
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        isPanningInterfaceVisible = true
        mapView?.gestures?.scaleHandler?.onScale(focusX, focusY, scaleFactor)
    }

    /**
     * Will be called on scale event. Needs car api version 2 to work. See [SurfaceCallback.onFling]
     * definition for more details.
     */
    override fun onFling(velocityX: Float, velocityY: Float) {
        isPanningInterfaceVisible = true

        /**
         * Fling event appears to have inverted axis compared to scroll event on desktop head unit.
         * This should not be the case according to
         * [androidx.car.app.navigation.model.NavigationTemplate]. To compensate inverted axis ,
         * factor of -1 was introduced. This might differ depending on which head unit model is
         * used.
         */
        mapView?.gestures?.flingHandler?.onFling(-1 * velocityX, -1 * velocityY)
    }


    /** Called when the car configuration changes. */
    fun onCarConfigurationChanged() {
        toggleSatelliteViewHandler?.invoke(isSatelliteViewEnabled)
    }

    /**
     * Completion handler when loading a map scene.
     *
     * @param mapError The map error, if any.
     */
    private fun onLoadScene(mapError: MapError?) {
        if (mapError != null) {
            Logger.log("Error: Map scene not loaded, $mapError")
            return
        }

        if (mapView == null) return

        // Disable traffic view support
        mapView!!.mapScene.disableFeatures(
            listOf(MapFeatures.TRAFFIC_FLOW, MapFeatures.TRAFFIC_INCIDENTS)
        )

        // Update the map coordinates
        updateMapCoordinatesHandler =
            updateMapCoordinatesHandler@{ mapCoordinates: MapCoordinates ->
                this.mapCoordinates = mapCoordinates

                if (mapView == null) return@updateMapCoordinatesHandler

                when {
                    mapController?.lastKnownLocation != null -> {
                        val location = mapController!!.lastKnownLocation!!
                        renderInitialMarker(
                            coordinates = location.coordinates,
                            accuracy = location.horizontalAccuracyInMeters ?: 0.0
                        )
                    }

                    mapCoordinates.stationAddressCoordinates != null -> {
                        renderInitialMarker(
                            coordinates = mapCoordinates.stationAddressCoordinates!!, accuracy = 0.0
                        )
                    }

                    else -> {
                        mapController?.removeMarker(MapMarkerType.INITIAL)
                        mapController?.removePolygon(MapMarkerType.INITIAL)
                    }
                }

                mapCoordinates.incidentAddressCoordinates?.let { renderIncidentAddressMarker(it) }
                    ?: mapController?.removeMarker(MapMarkerType.INCIDENT_ADDRESS)

                mapCoordinates.destinationAddressCoordinates?.let {
                    renderDestinationAddressMarker(it)
                } ?: mapController?.removeMarker(MapMarkerType.DESTINATION_ADDRESS)
            }

        // Recenter map position
        recenterMapViewHandler = recenterMapViewHandler@{ recenterMapPosition: String ->
            this.recenterMapPosition = recenterMapPosition

            if (isPanningInterfaceVisible || mapView == null) return@recenterMapViewHandler

            if (isNavigationInProgress) {
                mapController?.navigationHelper?.startCameraTracking()
            } else {
                val initialMarkerCoordinates =
                    mapController?.getMarkerCoordinates(MapMarkerType.INITIAL)
                val incidentAddressCoordinates =
                    mapController?.getMarkerCoordinates(MapMarkerType.INCIDENT_ADDRESS)
                val destinationAddressCoordinates = mapController?.getMarkerCoordinates(
                    MapMarkerType.DESTINATION_ADDRESS
                )

                when (recenterMapPosition) {
                    "initialMarker" -> {
                        if (initialMarkerCoordinates != null) {
                            flyToCoordinates(initialMarkerCoordinates)
                        }
                    }

                    "addressMarker" -> {
                        if (incidentAddressCoordinates != null && destinationAddressCoordinates != null) {
                            lookAtArea(
                                geoCoordinates = listOf(
                                    incidentAddressCoordinates,
                                    destinationAddressCoordinates,
                                )
                            )
                        } else if (incidentAddressCoordinates != null) {
                            flyToCoordinates(incidentAddressCoordinates)
                        }
                    }

                    "bothMarkers" -> {
                        if (initialMarkerCoordinates != null && incidentAddressCoordinates != null) {
                            val geoCoordinates = mutableListOf(
                                initialMarkerCoordinates,
                                incidentAddressCoordinates,
                            )
                            if (destinationAddressCoordinates != null) {
                                geoCoordinates.add(destinationAddressCoordinates)
                            }

                            lookAtArea(geoCoordinates)
                        }
                    }

                    else -> {}
                }
            }
        }

        // Update the initial location marker
        locationUpdatedHandler = {
            updateMapCoordinatesHandler?.invoke(mapCoordinates)

            recenterMapViewHandler?.invoke(recenterMapPosition)
        }

        if (!mapLoadedOnce) {
            updateMapCoordinatesHandler?.invoke(mapCoordinates)
            mapView?.setWatermarkLocation(
                Anchor2D(0.0, 1.0),
                Point2D(-mapView!!.watermarkSize.width / 2, -mapView!!.watermarkSize.height / 2)
            )
            flyToCoordinates(defaultCoordinates)

            // Refresh the views
            bannerView.getView()
            overlayView.getView()
            tripPreview.getView()

            updateCameraPrincipalPoint()

            if (isNavigationInProgress) {
                mapController?.navigationHelper?.startRendering()
            }else {
                mapController?.navigationHelper?.stopRendering()
                stopNavigation()
            }

            mapLoadedOnce = true
        }
    }

    /**
     * Look at the area containing all the markers.
     *
     * @param geoCoordinates The coordinates of the markers.
     */
    private fun lookAtArea(geoCoordinates: List<GeoCoordinates>) {
        if (mapView == null) return

        GeoBox.containing(geoCoordinates)?.let { geoBox ->
            //        val scale = FlutterCarplayTemplateManager.carWindow ?. screen . scale ?? 1.0
            //        val topSafeArea = view . safeAreaInsets . top * scale
            //                val leftSafeArea = view . safeAreaInsets . left * scale
            //                val rightSafeArea = view . safeAreaInsets . right * scale
            //                val width = view . frame . width * scale
            //                val height = view . frame . height * scale
            //                val bannerHeight = bannerView . isHidden ? 0.0 :
            // bannerView.bounds.height * scale

            val topSafeArea = visibleArea.top
            val leftSafeArea = visibleArea.left
            val width = mapView?.viewportSize?.width ?: visibleArea.width().toDouble()
            val height = mapView?.viewportSize?.height ?: visibleArea.height().toDouble()
            val rightSafeArea = width - visibleArea.right
            val bannerHeight = if (bannerView.isHidden) 0.0 else bannerView.height
            val tripPreviewWidth = if (tripPreview.isHidden) 0.0 else tripPreview.width

            val rectangle2D = if (isDashboardSceneActive) Rectangle2D(
                Point2D(markerPinSize, markerPinSize),
                Size2D(width - markerPinSize * 2, height - markerPinSize * 2)
            )
            else Rectangle2D(
                Point2D(
                    leftSafeArea + tripPreviewWidth + markerPinSize,
                    topSafeArea + bannerHeight + markerPinSize
                ), Size2D(
                    width - leftSafeArea - rightSafeArea - tripPreviewWidth - markerPinSize * 2,
                    height - topSafeArea - bannerHeight - markerPinSize * 2
                )
            )

            mapView!!.camera.lookAt(
                geoBox,
                GeoOrientationUpdate(0.0, 0.0),
                rectangle2D,
            )
        }
    }

    /** Update the camera principal point */
    fun updateCameraPrincipalPoint() {
        //            val scale = FlutterCarplayTemplateManager.carWindow?.screen.scale ?? 1.0
        //            val topSafeArea = view.safeAreaInsets.top * scale
        //            val bottomSafeArea = view.safeAreaInsets.bottom * scale
        //            val leftSafeArea = view.safeAreaInsets.left * scale
        //            val rightSafeArea = isPanningInterfaceVisible ? 0.0 :
        // view.safeAreaInsets.right * scale
        //            val width = view.frame.width * scale
        //            val height = view.frame.height * scale

        if (mapView == null) return

        val topSafeArea = visibleArea.top
        val bottomSafeArea = visibleArea.bottom
        val leftSafeArea = visibleArea.left
        val width = mapView?.viewportSize?.width ?: visibleArea.width().toDouble()
        val height = mapView?.viewportSize?.height ?: visibleArea.height().toDouble()
        val rightSafeArea = if (isPanningInterfaceVisible) 0 else width.toInt() - visibleArea.right

        if (isDashboardSceneActive) {
            val cameraPrincipalPoint = Point2D(width / 2.0, height / 2.0)
            mapView!!.camera.principalPoint = cameraPrincipalPoint

            val anchor2D = Anchor2D(0.5, 0.65)
            mapController?.navigationHelper?.setVisualNavigatorCameraPoint(anchor2D)

            recenterMapViewHandler?.invoke(recenterMapPosition)

            mapView!!.setWatermarkLocation(
                Anchor2D(
                    leftSafeArea / width, bottomSafeArea / height
                ), Point2D(-mapView!!.watermarkSize.width / 2, -mapView!!.watermarkSize.height / 2)
            )
        } else {
            val bannerHeight = if (bannerView.isHidden) 0.0 else bannerView.height
            val overlayViewWidth = if (overlayView.isHidden) 0.0 else overlayView.width
            val tripPreviewWidth = if (tripPreview.isHidden) 0.0 else tripPreview.width

            val cameraPrincipalPoint = Point2D(
                leftSafeArea + overlayViewWidth + tripPreviewWidth + (width - leftSafeArea - rightSafeArea - overlayViewWidth - tripPreviewWidth) / 2.0,
                topSafeArea + bannerHeight + (height - topSafeArea - bannerHeight) / 2.0
            )
            mapView!!.camera.principalPoint = cameraPrincipalPoint

            val anchor2D = Anchor2D(
                cameraPrincipalPoint.x / width,
                if (isPanningInterfaceVisible) cameraPrincipalPoint.y / height else 0.75
            )
            mapController?.navigationHelper?.setVisualNavigatorCameraPoint(anchor2D)

            if (isPanningInterfaceVisible) {
                mapController?.navigationHelper?.stopCameraTracking()
            } else {
                recenterMapViewHandler?.invoke(recenterMapPosition)
            }

            mapView!!.setWatermarkLocation(
                Anchor2D(
                    (leftSafeArea + tripPreviewWidth) / width,
                    bottomSafeArea / height,
                ), Point2D(
                    mapView!!.watermarkSize.width / 2,
                    mapView!!.watermarkSize.height / 2,
                )
            )
        }
    }
}

/**
 * Displays a banner message at the top of the screen.
 *
 * @param message The message to display.
 * @param color The color of the banner.
 */
fun FCPMapViewController.showBanner(message: String, color: Long) {
    shouldShowBanner = true
    bannerView.setMessage(message)
    bannerView.setBackgroundColor(color)
    bannerView.isHidden = isDashboardSceneActive || isPanningInterfaceVisible

    if (!isDashboardSceneActive && bannerViewHeight != bannerView.height) {
        bannerViewHeight = bannerView.height
        updateCameraPrincipalPoint()
    }
}

/** Hides the banner message at the top of the screen. */
fun FCPMapViewController.hideBanner() {
    bannerView.isHidden = true
    shouldShowBanner = false
}

/**
 * Displays a toast message on the screen for a specified duration.
 *
 * @param message The message to display.
 * @param duration The duration of the toast.
 */
// fun FCPMapViewController.showToast (message: String, duration: Double = 2.0) {
//    if(isDashboardSceneActive) return
//
//    // Cancel any previous toast
//    NSObject.cancelPreviousPerformRequests(withTarget: self)
//
//    toastViewMaxWidth.constant = view.bounds.size.width * 0.65
//
//    // Set the message and show the toast
//    toastView.setMessage(message)
//
//    // Fade in the toast
//    toastView.alpha = 1.0
//
//    // Dismiss the toast after the specified duration
//    perform(# selector (dismissToast), with: null, afterDelay: duration)
// }

/** Hides the toast message on the screen. */
// @objc private fun FCPMapViewController.dismissToast() {
//    UIView.animate(withDuration: 0.3) {
//        self.toastView.alpha = 0.0
//    }
// }

/**
 * Displays an overlay view on the screen.
 *
 * @param primaryTitle The primary title of the overlay view.
 * @param secondaryTitle The secondary title of the overlay view.
 * @param subtitle The subtitle of the overlay view.
 */
fun FCPMapViewController.showOverlay(
    primaryTitle: String?,
    secondaryTitle: String?,
    subtitle: String?,
) {
    shouldShowOverlay = true

    primaryTitle?.let { overlayView.setPrimaryTitle(it) }
    secondaryTitle?.let { overlayView.setSecondaryTitle(it) }
    subtitle?.let { overlayView.setSubtitle(it) }

    overlayView.isHidden = isDashboardSceneActive || isPanningInterfaceVisible

    if (!isDashboardSceneActive && overlayViewWidth != overlayView.width) {
        overlayViewWidth = overlayView.width
        updateCameraPrincipalPoint()
    }
}

/** Hides the overlay view on the screen. */
fun FCPMapViewController.hideOverlay() {
    overlayView.setPrimaryTitle("00:00:00")
    overlayView.setSecondaryTitle("--")
    overlayView.setSubtitle("--")
    overlayView.isHidden = true
    overlayViewWidth = 0.0
    shouldShowOverlay = false
}

/**
 * Displays an overlay view on the screen.
 *
 * @param primaryTitle The primary title of the overlay view.
 * @param secondaryTitle The secondary title of the overlay view.
 */
fun FCPMapViewController.showTripPreview(
    primaryTitle: String,
    secondaryTitle: String,
) {
    shouldShowTripPreview = true

    tripPreview.setPrimaryTitle(primaryTitle)
    tripPreview.setSecondaryTitle(secondaryTitle)

    tripPreview.isHidden = isDashboardSceneActive || isPanningInterfaceVisible

    if (!isDashboardSceneActive && tripPreviewWidth != tripPreview.width) {
        tripPreviewWidth = tripPreview.width
        updateCameraPrincipalPoint()
    }
}

/** Hides the overlay view on the screen. */
fun FCPMapViewController.hideTripPreview() {
    tripPreview.setPrimaryTitle("--")
    tripPreview.setSecondaryTitle("--")
    tripPreview.isHidden = true
    tripPreviewWidth = 0.0
    shouldShowTripPreview = false
}

/** Hide all the subviews. */
fun FCPMapViewController.hideSubviews() {
    bannerView.isHidden = true
    overlayView.isHidden = true
    tripPreview.isHidden = true
    updateCameraPrincipalPoint()
}

/** Show the subviews. */
fun FCPMapViewController.showSubviews() {
    bannerView.isHidden = !shouldShowBanner || isPanningInterfaceVisible
    overlayView.isHidden = !shouldShowOverlay || isPanningInterfaceVisible
    tripPreview.isHidden = !shouldShowTripPreview || isPanningInterfaceVisible
    updateCameraPrincipalPoint()
}

/**
 * Adds an initial marker on the map.
 *
 * @param coordinates The coordinates of the marker
 * @param accuracy The accuracy of the marker
 */
fun FCPMapViewController.renderInitialMarker(coordinates: GeoCoordinates, accuracy: Double) {
    if(mapView == null) return

    if (isNavigationInProgress) {
        mapController?.removeMarker(MapMarkerType.INITIAL)
        mapController?.removePolygon(MapMarkerType.INITIAL)
        return
    }

    val metadata = Metadata()
    metadata.setString("marker", MapMarkerType.INITIAL.name)
    metadata.setString("polygon", MapMarkerType.INITIAL.name)

    val image = "assets/icons/carplay/position.png".pathToByteArray()
    val markerSize = 30 * (mapView?.pixelScale ?: 1.0)

    mapController?.addMapMarker(
        coordinates = coordinates,
        markerImage = image,
        markerSize = CGSize(width = markerSize, height = markerSize),
        metadata = metadata
    )
    mapController?.addMapPolygon(
        coordinate = coordinates, accuracy = accuracy, metadata = metadata
    )
}

/**
 * Adds an incident marker on the map.
 *
 * @param coordinates The coordinates of the marker
 */
fun FCPMapViewController.renderIncidentAddressMarker(coordinates: GeoCoordinates) {
    val metadata = Metadata()
    metadata.setString("marker", MapMarkerType.INCIDENT_ADDRESS.name)

    val image = "assets/icons/carplay/map_marker_big.png".pathToByteArray()

    mapController?.addMapMarker(
        coordinates = coordinates,
        markerImage = image,
        markerSize = CGSize(width = markerPinSize, height = markerPinSize),
        metadata = metadata,
    )
}

/**
 * Adds a destination marker on the map.
 *
 * @param coordinates The coordinates of the marker
 * @param accuracy The accuracy of the marker
 */
fun FCPMapViewController.renderDestinationAddressMarker(coordinates: GeoCoordinates) {
    val metadata = Metadata()
    metadata.setString("marker", MapMarkerType.DESTINATION_ADDRESS.name)

    val image = "assets/icons/carplay/map_marker_wp.png".pathToByteArray()

    mapController?.addMapMarker(
        coordinates = coordinates,
        markerImage = image,
        markerSize = CGSize(width = markerPinSize, height = markerPinSize),
        metadata = metadata,
    )
}

/**
 * Fly to coordinates with animation on the map.
 *
 * @param coordinates The coordinates to fly to
 * @param bowFactor The bow factor of the animation
 * @param duration The duration of the animation
 */
fun FCPMapViewController.flyToCoordinates(
    coordinates: GeoCoordinates,
    bowFactor: Double = 0.2,
    duration: Duration = Duration.ofSeconds(1),
) {
    Logger.log("principlePoint at fly to: ${mapView!!.camera.principalPoint}")

    if (isPanningInterfaceVisible) {
        val animation = MapCameraAnimationFactory.flyTo(
            GeoCoordinatesUpdate(coordinates),
            GeoOrientationUpdate(0.0, 0.0),
            0.0,
            Duration.ofMillis(500)
        )

        mapView!!.camera.startAnimation(animation)
    } else {
        val animation = MapCameraAnimationFactory.flyTo(
            GeoCoordinatesUpdate(coordinates), GeoOrientationUpdate(0.0, 0.0), MapMeasure(
                MapMeasure.Kind.DISTANCE, ConstantsEnum.DEFAULT_DISTANCE_IN_METERS
            ), bowFactor, duration
        )

        mapView!!.camera.startAnimation(animation)
    }
}

/**
 * Starts the navigation.
 *
 * @param destination The destination waypoint
 */
fun FCPMapViewController.startNavigation(trip: FCPTrip) {
    val wayPoint = Waypoint(
        GeoCoordinates(trip.destination.latitude, trip.destination.longitude)
    )
    mapController?.setDestinationWaypoint(wayPoint)

    mapController?.addRouteDeviceLocation()

    mapController?.removeMarker(MapMarkerType.INITIAL)
    mapController?.removePolygon(MapMarkerType.INITIAL)
}

/** Stops the navigation. */
fun FCPMapViewController.stopNavigation() {
    mapController?.clearMap()
    updateMapCoordinatesHandler?.invoke(mapCoordinates)
}

/** Zooms in the camera. */
fun FCPMapViewController.zoomInMapView() {
    if (mapView == null) return
    val zoomLevel = min(mapView!!.camera.state.zoomLevel + 1, 22.0)
    mapView!!.camera.zoomTo(zoomLevel)
}

/** Zooms out the camera. */
fun FCPMapViewController.zoomOutMapView() {
    if (mapView == null) return
    val zoomLevel = max(mapView!!.camera.state.zoomLevel - 1, 0.0)
    mapView!!.camera.zoomTo(zoomLevel)
}
