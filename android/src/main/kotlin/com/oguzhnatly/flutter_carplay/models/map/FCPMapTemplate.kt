package com.oguzhnatly.flutter_carplay.models.map

import android.content.res.Configuration.TOUCHSCREEN_NOTOUCH
import androidx.car.app.AppManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.TravelEstimate
import com.oguzhnatly.flutter_carplay.AndroidAutoService
import com.oguzhnatly.flutter_carplay.Bool
import com.oguzhnatly.flutter_carplay.CPMapTemplate
import com.oguzhnatly.flutter_carplay.CPNavigationSession
import com.oguzhnatly.flutter_carplay.FCPChannelTypes
import com.oguzhnatly.flutter_carplay.FCPRootTemplate
import com.oguzhnatly.flutter_carplay.FCPStreamHandlerPlugin
import com.oguzhnatly.flutter_carplay.models.button.FCPBarButton

/**
 * A custom Android Auto map template with additional customization options.
 *
 * @param obj A dictionary containing the configuration parameters for the map template.
 */
class FCPMapTemplate(obj: Map<String, Any>) : FCPRootTemplate() {
    /// The super template object representing the CarPlay map template.
    lateinit var _super: CPMapTemplate

    /// The view controller associated with the map template.
    var viewController: SurfaceCallback
        private set

    /// The title displayed on the map template.
    private var title: String?

    /// The map buttons to be displayed on the map template.
    private var mapButtons: List<FCPMapButton>

    /// The leading navigation bar buttons for the map template.
    private var leadingNavigationBarButtons: List<FCPBarButton>

    /// The trailing navigation bar buttons for the map template.
    private var trailingNavigationBarButtons: List<FCPBarButton>

    //    /// The dashboard buttons to be displayed on the CarPlay dashboard.
    //    private var dashboardButtons: List<FCPDashboardButton>

    /// A boolean value indicating whether the navigation bar is automatically hidden.
    private var automaticallyHidesNavigationBar: Bool

    /// A boolean value indicating whether buttons are hidden with the navigation bar.
    private var hidesButtonsWithNavigationBar: Bool

    /// A boolean value indicating whether the map is in panning mode.
    var isPanningInterfaceVisible: Bool = false

    /// Navigation session used to manage the upcomingManeuvers and  arrival estimation details.
    var navigationSession: CPNavigationSession? = null

    /// Flag to check if the car context is initialized.
    private var isCarContextInitialized: Bool = false

    /// Get the `FCPMapViewController` associated with the map template.
    val fcpMapViewController: FCPMapViewController?
        get() = viewController as? FCPMapViewController

    /// The routing information associated with the map template.
    var routingInfo: RoutingInfo? = null

    /// The current destination travel estimates associated with the map template.
    private var destinationTravelEstimates: TravelEstimate? = null

    init {
        val elementIdValue = obj["_elementId"] as? String
        assert(elementIdValue != null) {
            "Missing required keys in dictionary for FCPMapTemplate initialization."
        }
        elementId = elementIdValue!!
        title = obj["title"] as? String
        automaticallyHidesNavigationBar = obj["automaticallyHidesNavigationBar"] as? Bool ?: false
        hidesButtonsWithNavigationBar = obj["hidesButtonsWithNavigationBar"] as? Bool ?: false
        isPanningInterfaceVisible = obj["isPanningInterfaceVisible"] as? Bool ?: false

        mapButtons =
            (obj["mapButtons"] as? List<Map<String, Any>>)?.map { FCPMapButton(it) } ?: emptyList()

        //        dashboardButtons = (obj["dashboardButtons"] as? List<Map<String, Any>>)?.map {
        //            FCPDashboardButton(it)
        //        } ?: emptyList()

        leadingNavigationBarButtons =
            (obj["leadingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            } ?: emptyList()
        trailingNavigationBarButtons =
            (obj["trailingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            } ?: emptyList()

        // Initialize the view controller.
        viewController = FCPMapViewController()
    }

    /**
     * Overrides the `onStopNavigation` function and calls the `super.onStopNavigation()` method to
     * perform any necessary cleanup. Then, calls the `stopNavigation()` method to stop the
     * navigation.
     *
     * @return void
     */
    private fun initializeCarContext() {
        AndroidAutoService.session?.carContext?.let {
            it.getCarService(AppManager::class.java).setSurfaceCallback(viewController)

            navigationSession = it.getCarService(NavigationManager::class.java)
        }
        navigationSession?.setNavigationManagerCallback(object : NavigationManagerCallback {
            /**
             * Overrides the `onStopNavigation` function and calls the
             * `super.onStopNavigation()` method to perform any necessary cleanup. Then,
             * calls the `stopNavigation()` method to stop the navigation.
             *
             * @return void
             */
            override fun onStopNavigation() {
                stopNavigation()
                if (fcpMapViewController?.mapView != null) {
                    FCPStreamHandlerPlugin.sendEvent(
                        type = FCPChannelTypes.onNavigationCompletedFromCarplay.name,
                        data = mapOf()
                    )
                }
            }
        })
    }

    /** Resets the car context. */
    fun resetCarContext() {
        isCarContextInitialized = false
    }

    /** Gets the Android Auto map template object based on the configured parameters. */
    override fun getTemplate(): CPMapTemplate {
        if (AndroidAutoService.session?.carContext != null && !isCarContextInitialized) {
            initializeCarContext()
            isCarContextInitialized = true
        }

        val mapTemplate = NavigationTemplate.Builder().setBackgroundColor(CarColor.GREEN)

        if (!isPanningInterfaceVisible) {
            routingInfo?.let { mapTemplate.setNavigationInfo(it) }
            destinationTravelEstimates?.let {
                mapTemplate.setDestinationTravelEstimate(it)
            }
        }

        val mapActionStripButtons = trailingNavigationBarButtons.filter { it.showInMapActionStrip }

        if (mapButtons.isNotEmpty() || mapActionStripButtons.isNotEmpty()) {
            val actionStrip = ActionStrip.Builder()

            for (button in mapActionStripButtons) {
                actionStrip.addAction(button.getTemplate(isForPanning = button.isForPanning))
            }

            for (button in mapButtons) {
                if (!button.showInActionStrip) actionStrip.addAction(button.getTemplate())
            }
            mapTemplate.setMapActionStrip(actionStrip.build())
        }

        val actionStripButtons = mapButtons.filter { it.showInActionStrip }

        if (leadingNavigationBarButtons.isNotEmpty() || trailingNavigationBarButtons.isNotEmpty() || actionStripButtons.isNotEmpty()) {
            val actionStrip = ActionStrip.Builder()

            for (button in actionStripButtons) {
                actionStrip.addAction(button.getTemplate())
            }

            for (button in leadingNavigationBarButtons) {
                if (!button.showInMapActionStrip) actionStrip.addAction(button.getTemplate())
            }

            for (button in trailingNavigationBarButtons) {
                if (!button.showInMapActionStrip) actionStrip.addAction(button.getTemplate())
            }
            mapTemplate.setActionStrip(actionStrip.build())
        }

        mapTemplate.setPanModeListener { isPanningMode ->
            val configurations = AndroidAutoService.session?.carContext?.resources?.configuration

            if (configurations?.touchscreen == TOUCHSCREEN_NOTOUCH) {
                togglePanningInterface(isPanningMode)
            }
        }

        _super = mapTemplate.build()
        return _super
    }

    /**
     * Updates the properties of the map template.
     *
     * @param title The new title text.
     * @param automaticallyHidesNavigationBar A boolean value indicating whether the navigation bar
     * is automatically hidden.
     * @param hidesButtonsWithNavigationBar A boolean value indicating whether buttons are hidden
     * with the navigation bar.
     * @param mapButtons The new array of map buttons.
     * @param leadingNavigationBarButtons The new array of leading navigation bar buttons.
     * @param trailingNavigationBarButtons The new array of trailing navigation bar buttons.
     * @param destinationTravelEstimates The new destination travel estimates.
     * @param routingInfo The new routing information.
     * @param removeRoutingInfo A boolean value indicating whether the routing information should
     * be removed.
     */
    fun update(
        title: String? = null,
        automaticallyHidesNavigationBar: Bool? = null,
        hidesButtonsWithNavigationBar: Bool? = null,
        isPanningInterfaceVisible: Bool? = null,
        mapButtons: List<FCPMapButton>? = null,
        leadingNavigationBarButtons: List<FCPBarButton>? = null,
        trailingNavigationBarButtons: List<FCPBarButton>? = null,
        destinationTravelEstimates: TravelEstimate? = null,
        routingInfo: RoutingInfo? = null,
        removeRoutingInfo: Bool = false,
    ) {
        title?.let { this.title = it }
        automaticallyHidesNavigationBar?.let { this.automaticallyHidesNavigationBar = it }
        hidesButtonsWithNavigationBar?.let { this.hidesButtonsWithNavigationBar = it }
        isPanningInterfaceVisible?.let { this.isPanningInterfaceVisible = it }
        mapButtons?.let { this.mapButtons = it }
        leadingNavigationBarButtons?.let { this.leadingNavigationBarButtons = it }
        trailingNavigationBarButtons?.let { this.trailingNavigationBarButtons = it }

        if (removeRoutingInfo) {
            this.routingInfo = null
            this.destinationTravelEstimates = null
        } else {
            routingInfo?.let { this.routingInfo = it }
            destinationTravelEstimates?.let { this.destinationTravelEstimates = it }
        }

        onInvalidate()
    }
}

/**
 * Show trip previews
 *
 * @param trips The array of trips to show
 * @param selectedTrip The selected trip
 * @param textConfiguration The text configuration
 */
fun FCPMapTemplate.showTripPreviews(
    trips: List<FCPTrip>,
    selectedTrip: FCPTrip?,
) {
    selectedTrip?.let {
        fcpMapViewController?.showTripPreview(
            primaryTitle = it.destination.name,
            secondaryTitle = it.routeChoices.first().additionalInformationVariants.firstOrNull()
                ?: "--"
        )
    }
}

/** Hide trip previews. */
fun FCPMapTemplate.hideTripPreviews() {
    fcpMapViewController?.hideTripPreview()
}

/**
 * Starts the navigation
 *
 * @param trip The trip to start navigation
 */
fun FCPMapTemplate.startNavigation(trip: FCPTrip) {
    navigationSession?.navigationEnded()

    hideTripPreviews()

    fcpMapViewController?.startNavigation(trip)
    navigationSession?.navigationStarted()
    update(routingInfo = RoutingInfo.Builder().setLoading(true).build())
}

/** Stops the navigation. */
fun FCPMapTemplate.stopNavigation() {
    navigationSession?.navigationEnded()

    fcpMapViewController?.stopNavigation()
    update(removeRoutingInfo = true)
}

/** Pans the camera in the specified direction. */
fun FCPMapTemplate.showPanningInterface() {
    isPanningInterfaceVisible = true

    fcpMapViewController?.hideSubviews()
    fcpMapViewController?.mapController?.navigationHelper?.stopCameraTracking()
}

/** Dismisses the panning interface. */
fun FCPMapTemplate.dismissPanningInterface() {
    isPanningInterfaceVisible = false

    fcpMapViewController?.showSubviews()
    fcpMapViewController?.mapController?.navigationHelper?.startCameraTracking()
}

/** Toggles the panning interface. */
fun FCPMapTemplate.togglePanningInterface(isPanningMode: Bool = false) {
    if (isPanningInterfaceVisible == isPanningMode) return

    FCPStreamHandlerPlugin.sendEvent(
        FCPChannelTypes.onPanningInterfaceToggled.name,
        mapOf("elementId" to elementId)
    )
}
