package com.oguzhnatly.flutter_carplay

import androidx.car.app.SurfaceCallback
import androidx.car.app.constraints.ConstraintManager
import com.here.sdk.core.GeoCoordinates
import com.oguzhnatly.flutter_carplay.managers.audio.FCPSoundEffects
import com.oguzhnatly.flutter_carplay.managers.audio.FCPSpeaker
import com.oguzhnatly.flutter_carplay.models.action_sheet.FCPActionSheetTemplate
import com.oguzhnatly.flutter_carplay.models.alert.FCPAlertTemplate
import com.oguzhnatly.flutter_carplay.models.button.FCPBarButton
import com.oguzhnatly.flutter_carplay.models.button.FCPTextButton
import com.oguzhnatly.flutter_carplay.models.grid.FCPGridTemplate
import com.oguzhnatly.flutter_carplay.models.information.FCPInformationItem
import com.oguzhnatly.flutter_carplay.models.information.FCPInformationTemplate
import com.oguzhnatly.flutter_carplay.models.list.FCPListItem
import com.oguzhnatly.flutter_carplay.models.list.FCPListSection
import com.oguzhnatly.flutter_carplay.models.list.FCPListTemplate
import com.oguzhnatly.flutter_carplay.models.map.FCPMapButton
import com.oguzhnatly.flutter_carplay.models.map.FCPMapTemplate
import com.oguzhnatly.flutter_carplay.models.map.FCPTrip
import com.oguzhnatly.flutter_carplay.models.map.dismissPanningInterface
import com.oguzhnatly.flutter_carplay.models.map.here_map.MapCoordinates
import com.oguzhnatly.flutter_carplay.models.map.here_map.primaryManeuverActionTextHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.recenterMapViewHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.secondaryManeuverActionTextHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.toggleOfflineModeHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.toggleSatelliteViewHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.toggleVoiceInstructionsHandler
import com.oguzhnatly.flutter_carplay.models.map.here_map.updateMapCoordinatesHandler
import com.oguzhnatly.flutter_carplay.models.map.hideBanner
import com.oguzhnatly.flutter_carplay.models.map.hideOverlay
import com.oguzhnatly.flutter_carplay.models.map.hideTripPreviews
import com.oguzhnatly.flutter_carplay.models.map.showBanner
import com.oguzhnatly.flutter_carplay.models.map.showOverlay
import com.oguzhnatly.flutter_carplay.models.map.showPanningInterface
import com.oguzhnatly.flutter_carplay.models.map.showTripPreviews
import com.oguzhnatly.flutter_carplay.models.map.startNavigation
import com.oguzhnatly.flutter_carplay.models.map.stopNavigation
import com.oguzhnatly.flutter_carplay.models.map.zoomInMapView
import com.oguzhnatly.flutter_carplay.models.map.zoomOutMapView
import com.oguzhnatly.flutter_carplay.models.search.FCPSearchTemplate
import com.oguzhnatly.flutter_carplay.models.voice_control.FCPVoiceControlTemplate
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.Locale

/**
 * FlutterCarplayPlugin A Kotlin Flutter plugin for Android Auto integration.
 *
 * This plugin provides a bridge between Flutter and Android Auto, allowing developers to create
 * Android Auto-enabled Flutter apps.
 */
class FlutterCarplayPlugin : FlutterPlugin, MethodCallHandler {
    /**
     * The MethodChannel that will handle the communication between Flutter and native Android
     *
     * This local reference serves to register the plugin with the Flutter Engine and unregister it
     * when the Flutter Engine is detached from the Activity
     */
    private lateinit var channel: MethodChannel

    companion object {

        /// The root template to be displayed on CarPlay.
        var fcpRootTemplate: FCPRootTemplate? = null

        /// The root view controller for CarPlay.
        var rootViewController: SurfaceCallback? = null

        /// The root template for CarPlay.
        var rootTemplate: CPTemplate? = null


        /// The present template object for CarPlay modals.
        var fcpPresentTemplate: FCPPresentTemplate? = null

        // The Template history for CarPlay.
        var fcpTemplateHistory: List<FCPTemplate> = emptyList()
            private set
            get() = (AndroidAutoService.session?.screenManager?.screenStack?.toList() as? List<FCPScreen>)?.map { it.fcpTemplate }
                ?: emptyList()

        /// The Flutter plugin registrar.
        var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    }

    /// The stream handler for CarPlay communication.
    private var streamHandler: FCPStreamHandlerPlugin? = null

    /**
     * Attaches the plugin to the Flutter engine.
     *
     * @param flutterPluginBinding the Flutter plugin binding
     */
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Logger.log("onAttachedToEngine")
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, makeFCPChannelId(""))
        channel.setMethodCallHandler(this)
        FlutterCarplayPlugin.flutterPluginBinding = flutterPluginBinding
        streamHandler = FCPStreamHandlerPlugin(flutterPluginBinding)
    }

    /**
     * Handles a Flutter method call and provides a result callback.
     *
     * This method is responsible for processing Flutter method calls and producing a result through
     * the provided `FlutterResult` callback. It is typically used as part of a Flutter plugin
     * implementation.
     *
     * @param call The `FlutterMethodCall` representing the invoked method.
     * @param result The callback to provide the result of the method call to Flutter.
     */
    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method != FCPChannelTypes.showOverlay.name) {
            Logger.log("FlutterMethodCall received : ${call.method}")
        }

        when (call.method) {
            FCPChannelTypes.setRootTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                val runtimeType = args?.get("runtimeType") as? String
                if (args == null || runtimeType == null) {
                    result.success(false)
                    return
                }
                val rootTemplate = createRootTemplate(args = args, runtimeType = runtimeType)
                if (rootTemplate == null) {
                    result.success(false)
                    return
                }

                setRootTemplate(fcpRootTemplate = rootTemplate, result = result)
            }

            FCPChannelTypes.forceUpdateRootTemplate.name -> {
                AndroidAutoService.session?.forceUpdateRootTemplate(result)
            }

            FCPChannelTypes.popTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                val count = args?.get("count") as? Int
                if (args == null || count == null) {
                    result.success(false)
                    return
                }

                List(count) { AndroidAutoService.session?.pop() }

                result.success(true)
            }

            FCPChannelTypes.setVoiceControl.name -> {
                if (AndroidAutoService.session?.isStackOverflow ?: true) {
                    result.success(false)
                    return
                }
                val args = call.arguments as? Map<String, Any>
                val rootTemplateArgs = args?.get("rootTemplate") as? Map<String, Any>
                if (args == null || rootTemplateArgs == null) {
                    result.success(false)
                    return
                }

                val showVoiceTemplate = {
                    val voiceControlTemplate = FCPVoiceControlTemplate(rootTemplateArgs)
                    AndroidAutoService.session?.presentTemplate(template = voiceControlTemplate)
                    FCPStreamHandlerPlugin.sendEvent(
                        type = FCPChannelTypes.onPresentStateChanged.name,
                        data = mapOf("completed" to true)
                    )
                    result.success(true)
                    fcpPresentTemplate = voiceControlTemplate

                }

                if (fcpPresentTemplate != null) {
                    fcpPresentTemplate = null
                    AndroidAutoService.session?.closePresent()
                    showVoiceTemplate()
                } else {
                    showVoiceTemplate()
                }
            }

            FCPChannelTypes.activateVoiceControlState.name -> {
                if (fcpPresentTemplate == null) {
                    result.error(
                        "ERROR",
                        "To activate a voice control state, a voice control template must be presented to CarPlay Screen at first.",
                        null
                    )
                    return
                }
                val args = call.arguments as? String ?: run {
                    result.success(false)
                    return
                }

                if (fcpPresentTemplate is FCPVoiceControlTemplate) {
                    (fcpPresentTemplate as FCPVoiceControlTemplate).activateVoiceControlState(args)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            FCPChannelTypes.getActiveVoiceControlStateIdentifier.name -> {
                if (fcpPresentTemplate == null) {
                    result.error(
                        "ERROR",
                        "To get the active voice control state identifier, a voice control template must be presented to CarPlay Screen at first.",
                        null
                    )
                    return
                }

                if (fcpPresentTemplate is FCPVoiceControlTemplate) {
                    val identifier =
                        (fcpPresentTemplate as FCPVoiceControlTemplate).getActiveVoiceControlStateIdentifier()
                    result.success(identifier)
                } else {
                    result.success(null)
                }
            }

            FCPChannelTypes.startVoiceControl.name -> {
                if (fcpPresentTemplate == null) {
                    result.error(
                        "ERROR",
                        "To start the voice control, a voice control template must be presented to CarPlay Screen at first.",
                        null
                    )
                    return
                }
                if (fcpPresentTemplate is FCPVoiceControlTemplate) {
                    (fcpPresentTemplate as FCPVoiceControlTemplate).start()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            FCPChannelTypes.stopVoiceControl.name -> {
                if (fcpPresentTemplate == null) {
                    result.error(
                        "ERROR",
                        "To stop the voice control, a voice control template must be presented to CarPlay Screen at first.",
                        null
                    )
                    return
                }
                if (fcpPresentTemplate is FCPVoiceControlTemplate) {
                    (fcpPresentTemplate as FCPVoiceControlTemplate).stop()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            FCPChannelTypes.speak.name -> {
                val args = call.arguments as? Map<String, Any>
                val text = args?.get("text") as? String
                val language = args?.get("language") as? String
                val elementId = args?.get("_elementId") as? String
                val onCompleted = args?.get("onCompleted") as? Boolean

                if (text == null || language == null || elementId == null || onCompleted == null) {
                    result.success(false)
                    return
                }

                FCPSpeaker.setLanguage(Locale(language))
                FCPSpeaker.speak(text) {
                    if (onCompleted) {
                        FCPStreamHandlerPlugin.sendEvent(
                            FCPChannelTypes.onSpeechCompleted.name, mapOf("elementId" to elementId)
                        )
                    }
                }
                result.success(true)
            }

            FCPChannelTypes.playAudio.name -> {
                val args = call.arguments as? Map<String, Any>
                val soundPath = args?.get("soundPath") as? String
                val volume = args?.get("volume") as? Double

                if (soundPath == null || volume == null) {
                    result.success(false)
                    return
                }

                FCPSoundEffects.shared.prepare(soundPath, volume.toFloat())
                FCPSoundEffects.shared.play()
                result.success(true)
            }

            FCPChannelTypes.closePresent.name -> {
                fcpPresentTemplate = null
                AndroidAutoService.session?.closePresent(result)
            }

            FCPChannelTypes.pushTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                if (args == null) {
                    result.success(false)
                    return
                }
                pushTemplate(args = args, result = result)
            }

            FCPChannelTypes.popToRootTemplate.name -> {
                fcpPresentTemplate = null
                AndroidAutoService.session?.popToRootTemplate(result)
            }

            FCPChannelTypes.updateInformationTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                updateInformationTemplate(elementId = elementId, args = args)
                result.success(true)
            }

            FCPChannelTypes.updateMapTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                updateMapTemplate(elementId = elementId, args = args)
                result.success(true)
            }

            FCPChannelTypes.updateListTemplate.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                val sections = args?.get("sections") as? List<Map<String, Any>>
                if (args == null || elementId == null || (sections?.isEmpty() != false)) {
                    result.success(false)
                    return
                }

                updateListTemplate(elementId = elementId, sections = sections, args = args)
                result.success(true)
            }

            FCPChannelTypes.updateListItem.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }
                updateListItem(elementId = elementId, args = args)
                result.success(true)
            }

            FCPChannelTypes.onFCPListItemSelectedComplete.name -> {}

            FCPChannelTypes.onSearchTextUpdatedComplete.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                val searchResults = (args["searchResults"] as? List<Map<String, Any>>)?.map {
                    FCPListItem(it)
                }

                FlutterCarplayPlugin.findSearchTemplate(elementId = elementId) {
                    if (searchResults != null) {
                        it.searchPerformed(searchResults)
                    }
                }
                result.success(true)
            }

            FCPChannelTypes.setAlert.name -> {
                if (AndroidAutoService.session?.isStackOverflow ?: true) {
                    result.success(false)
                    return
                }
                val args = call.arguments as? Map<String, Any>
                val rootTemplateArgs = args?.get("rootTemplate") as? Map<String, Any>
                if (args == null || rootTemplateArgs == null) {
                    result.success(false)
                    return
                }

                val showAlertTemplate = {
                    val alertTemplate = FCPAlertTemplate(rootTemplateArgs)
                    AndroidAutoService.session?.presentTemplate(template = alertTemplate)
                    FCPStreamHandlerPlugin.sendEvent(
                        type = FCPChannelTypes.onPresentStateChanged.name,
                        data = mapOf("completed" to true)
                    )
                    result.success(true)
                    fcpPresentTemplate = alertTemplate
                }

                if (fcpPresentTemplate != null) {
                    fcpPresentTemplate = null
                    AndroidAutoService.session?.closePresent()
                    showAlertTemplate()
                } else {
                    showAlertTemplate()
                }
            }

            FCPChannelTypes.setActionSheet.name -> {
                if (AndroidAutoService.session?.isStackOverflow ?: true) {
                    result.success(false)
                    return
                }
                val args = call.arguments as? Map<String, Any>
                val rootTemplateArgs = args?.get("rootTemplate") as? Map<String, Any>
                if (args == null || rootTemplateArgs == null) {
                    result.success(false)
                    return
                }

                val showActionSheet = {
                    val actionSheetTemplate = FCPActionSheetTemplate(rootTemplateArgs)
                    AndroidAutoService.session?.presentTemplate(
                        template = actionSheetTemplate, result = result
                    )
                    fcpPresentTemplate = actionSheetTemplate

                }

                if (fcpPresentTemplate != null) {
                    fcpPresentTemplate = null
                    AndroidAutoService.session?.closePresent()
                    showActionSheet()
                } else {
                    showActionSheet()
                }
            }

            FCPChannelTypes.getConfig.name -> {
                val carContext = AndroidAutoService.session?.carContext
                if (carContext == null) {
                    result.success(null)
                    return
                }

                val listItemLimit =
                    carContext.getCarService(ConstraintManager::class.java).getContentLimit(
                        ConstraintManager.CONTENT_LIMIT_TYPE_LIST
                    )

                val config = mapOf(
                    "maximumItemCount" to listItemLimit,
                    "maximumSectionCount" to listItemLimit,
                )
                result.success(config)
            }

            FCPChannelTypes.showTripPreviews.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                val trips = args?.get("trips") as? List<Map<String, Any>>

                if (args == null || elementId == null || trips == null) {
                    result.success(false)
                    return
                }

                val selectedTrip = args["selectedTrip"] as? Map<String, Any>

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    val fcpTrips = trips.map { FCPTrip(it) }
                    val fcpSelectedTrip = selectedTrip?.let { FCPTrip(it) }

                    mapTemplate.showTripPreviews(
                        trips = fcpTrips,
                        selectedTrip = fcpSelectedTrip,
                    )
                }

                result.success(template != null)
            }

            FCPChannelTypes.hideTripPreviews.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.hideTripPreviews()
                }
                result.success(template != null)
            }

            FCPChannelTypes.showBanner.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                val message = args?.get("message") as? String
                val color = args?.get("color") as? Long

                if (args == null || elementId == null || message == null || color == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.showBanner(message = message, color = color)
                }
                result.success(template != null)
            }

            FCPChannelTypes.hideBanner.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.hideBanner()
                }
                result.success(template != null)
            }

            FCPChannelTypes.showToast.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                val message = args?.get("message") as? String
                val duration = args?.get("duration") as? Double

                if (args == null || elementId == null || message == null || duration == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
//                    mapTemplate.fcpMapViewController?.showToast(
//                        message = message, duration = duration
//                    )
                }
                result.success(template != null)
            }

            FCPChannelTypes.showOverlay.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                val primaryTitle = args["primaryTitle"] as? String
                val secondaryTitle = args["secondaryTitle"] as? String
                val subtitle = args["subtitle"] as? String

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.showOverlay(
                        primaryTitle = primaryTitle,
                        secondaryTitle = secondaryTitle,
                        subtitle = subtitle
                    )
                }

                result.success(template != null)
            }

            FCPChannelTypes.hideOverlay.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }


                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.hideOverlay()
                }
                result.success(template != null)
            }

            FCPChannelTypes.showPanningInterface.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.showPanningInterface()
                }
                result.success(template != null)
            }

            FCPChannelTypes.dismissPanningInterface.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.dismissPanningInterface()
                }
                result.success(template != null)
            }

            FCPChannelTypes.zoomInMapView.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.zoomInMapView()
                }

                result.success(template != null)
            }

            FCPChannelTypes.zoomOutMapView.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.fcpMapViewController?.zoomOutMapView()
                }

                result.success(template != null)
            }

            FCPChannelTypes.startNavigation.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String
                val trip = args?.get("trip") as? Map<String, Any>

                if (args == null || elementId == null || trip == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    val fcpTrip = FCPTrip(trip)
                    mapTemplate.startNavigation(fcpTrip)
                }
                result.success(template != null)
            }

            FCPChannelTypes.stopNavigation.name -> {
                val args = call.arguments as? Map<String, Any>
                val elementId = args?.get("_elementId") as? String

                if (args == null || elementId == null) {
                    result.success(false)
                    return
                }

                // Find the map template based on the provided element ID
                val template = FlutterCarplayPlugin.findMapTemplate(elementId) { mapTemplate ->
                    mapTemplate.stopNavigation()
                }
                result.success(template != null)
            }

            FCPChannelTypes.onManeuverActionTextRequestComplete.name -> {
                val args = call.arguments as? Map<String, Any>
                val text = args?.get("actionText") as? String
                val isPrimary = args?.get("isPrimary") as? Bool

                if (args == null || text == null || isPrimary == null) {
                    result.success(false)
                    return
                }

                if (isPrimary) primaryManeuverActionTextHandler?.invoke(text)
                else secondaryManeuverActionTextHandler?.invoke(text)

                result.success(true)
            }

            FCPChannelTypes.toggleOfflineMode.name -> {
                val args = call.arguments as? Map<String, Any>
                val isOffline = args?.get("isOffline") as? Bool

                if (args == null || isOffline == null) {
                    result.success(false)
                    return
                }

                toggleOfflineModeHandler?.invoke(isOffline)

                result.success(true)
            }

            FCPChannelTypes.toggleVoiceInstructions.name -> {
                val args = call.arguments as? Map<String, Any>
                val isMuted = args?.get("isMuted") as? Bool

                if (args == null || isMuted == null) {
                    result.success(false)
                    return
                }

                toggleVoiceInstructionsHandler?.invoke(isMuted)

                result.success(true)
            }

            FCPChannelTypes.toggleSatelliteView.name -> {
                val args = call.arguments as? Map<String, Any>
                val showSatelliteView = args?.get("showSatelliteView") as? Bool

                if (args == null || showSatelliteView == null) {
                    result.success(false)
                    return
                }

                toggleSatelliteViewHandler?.invoke(showSatelliteView)

                result.success(true)
            }

            FCPChannelTypes.recenterMapView.name -> {
                val args = call.arguments as? Map<String, Any>
                val recenterMapPosition = args?.get("recenterMapPosition") as? String

                if (args == null || recenterMapPosition == null) {
                    result.success(false)
                    return
                }

                recenterMapViewHandler?.invoke(recenterMapPosition)

                result.success(true)
            }

            FCPChannelTypes.updateMapCoordinates.name -> {
                val args = call.arguments as? Map<String, Any>

                if (args == null) {
                    result.success(false)
                    return
                }

                var stationAddressCoordinates: GeoCoordinates? = null
                var incidentAddressCoordinates: GeoCoordinates? = null
                var destinationAddressCoordinates: GeoCoordinates? = null

                val stationAddressLatitude = args["stationAddressLatitude"] as? Double
                val stationAddressLongitude = args["stationAddressLongitude"] as? Double
                if (stationAddressLatitude != null && stationAddressLongitude != null) {
                    stationAddressCoordinates = GeoCoordinates(
                        stationAddressLatitude, stationAddressLongitude
                    )
                }

                val incidentAddressLatitude = args["incidentAddressLatitude"] as? Double
                val incidentAddressLongitude = args["incidentAddressLongitude"] as? Double
                if (incidentAddressLatitude != null && incidentAddressLongitude != null) {
                    incidentAddressCoordinates = GeoCoordinates(
                        incidentAddressLatitude, incidentAddressLongitude
                    )
                }

                val destinationAddressLatitude = args["destinationAddressLatitude"] as? Double
                val destinationAddressLongitude = args["destinationAddressLongitude"] as? Double
                if (destinationAddressLatitude != null && destinationAddressLongitude != null) {
                    destinationAddressCoordinates = GeoCoordinates(
                        destinationAddressLatitude, destinationAddressLongitude
                    )
                }

                val mapCoordinates = MapCoordinates(
                    stationAddressCoordinates = stationAddressCoordinates,
                    incidentAddressCoordinates = incidentAddressCoordinates,
                    destinationAddressCoordinates = destinationAddressCoordinates
                )

                updateMapCoordinatesHandler?.invoke(mapCoordinates)

                result.success(false)
            }

            else -> result.notImplemented()
        }
    }

    /**
     * Detaches the plugin from the Flutter engine.
     *
     * @param binding the Flutter plugin binding
     */
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

/**
 * Creates an FCPRootTemplate based on the provided arguments and runtime type.
 *
 * @param args A dictionary containing the root template arguments.
 * @param runtimeType A string representing the runtime type of the root template.
 * @return An instance of FCPRootTemplate if successful, otherwise null.
 */
private fun FlutterCarplayPlugin.Companion.createRootTemplate(
    args: Map<String, Any>,
    runtimeType: String,
): FCPRootTemplate? {
    // Ensure that the rootTemplateArgs key exists in the args map
    val rootTemplateArgs = args["rootTemplate"] as? Map<String, Any> ?: return null

    // Create an FCPRootTemplate based on the provided runtime type
    return when (runtimeType) {
        //        FCPTabBarTemplate::class.java.simpleName -> FCPTabBarTemplate(rootTemplateArgs)
        FCPGridTemplate::class.java.simpleName -> FCPGridTemplate(rootTemplateArgs)
        FCPInformationTemplate::class.java.simpleName -> FCPInformationTemplate(rootTemplateArgs)
        //        FCPPointOfInterestTemplate::class.java.simpleName ->
        // FCPPointOfInterestTemplate(rootTemplateArgs)
        FCPMapTemplate::class.java.simpleName -> FCPMapTemplate(rootTemplateArgs)
        FCPListTemplate::class.java.simpleName -> {
            FCPListTemplate(obj = rootTemplateArgs, templateType = FCPListTemplateTypes.DEFAULT)
        }

        else -> null
    }
}

/**
 * Sets the root template for CarPlay based on the provided FCPRootTemplate.
 *
 * @param fcpRootTemplate The FCPRootTemplate to be set as the root template.
 * @param args Additional arguments for setting the root template.
 * @param result A FlutterResult callback to communicate the success or failure of the operation.
 */
private fun FlutterCarplayPlugin.Companion.setRootTemplate(
    fcpRootTemplate: FCPRootTemplate,
    result: Result,
) {
    val cpRootTemplate: CPTemplate

    // Check the type of the root template and extract the corresponding FCPRootTemplate
    when (fcpRootTemplate) {
        //        is FCPTabBarTemplate -> {
        //            // Ensure that the number of templates in the tab bar template is within the
        // CarPlay limit
        //            if (rootTemplate.getTemplates().count > 5) {
        //                result.success(false)
        //                return
        //            }
        //            cpRootTemplate = fcpRootTemplate.getTemplate()
        //        }
        //
        is FCPGridTemplate -> {
            cpRootTemplate = fcpRootTemplate.getTemplate()
        }

        is FCPInformationTemplate -> {
            cpRootTemplate = fcpRootTemplate.getTemplate()
        }
        //
        //        is FCPPointOfInterestTemplate -> {
        //            cpRootTemplate = fcpRootTemplate.getTemplate()
        //        }
        //
        is FCPMapTemplate -> {
            // For FCPMapTemplate, set the rootViewController and update the CarPlay window's
            // rootViewController
            cpRootTemplate = fcpRootTemplate.getTemplate()

            rootViewController = fcpRootTemplate.viewController

            //            if (FlutterCarplayTemplateManager.isDashboardSceneActive) {
            //                FlutterCarplayTemplateManager.dashboardWindow?.rootViewController =
            //                    viewController
            //            } else {
            //                FlutterCarplayTemplateManager.carWindow?.rootViewController =
            //                    viewController
            //            }
        }
        //
        is FCPListTemplate -> {
            cpRootTemplate = fcpRootTemplate.getTemplate()
        }

        else -> {
            // If the root template type is not recognized, return false
            result.success(false)
            return
        }
    }

    // If an FCPRootTemplate is successfully extracted, set it as the root template
    rootTemplate = cpRootTemplate
    this.fcpRootTemplate = fcpRootTemplate
    AndroidAutoService.session?.forceUpdateRootTemplate()

    onCarplayConnectionChange(status = FlutterCarplayTemplateManager.fcpConnectionStatus.name)
    result.success(true)
}

/**
 * Pushes a new Android Auto template onto the navigation stack.
 *
 * @param args Arguments containing information about the template to be pushed.
 * @param result The FlutterResult to return the completion status of the operation.
 */
private fun FlutterCarplayPlugin.Companion.pushTemplate(
    args: Map<String, Any>,
    result: Result,
) {
    // Extract necessary information from the provided arguments

    val runtimeType = args["runtimeType"] as? String?
    val templateArgs = args["template"] as? Map<String, Any>?
    if (runtimeType == null || templateArgs == null) {
        result.success(false)
        return
    }

    // Create the appropriate FCPTemplate based on the runtime type
    val pushTemplate = when (runtimeType) {
        //        FCPTabBarTemplate::class.java.simpleName ->
        //             FCPTabBarTemplate(obj = templateArgs)
        //
        FCPGridTemplate::class.java.simpleName -> FCPGridTemplate(obj = templateArgs)
        //
        FCPInformationTemplate::class.java.simpleName -> FCPInformationTemplate(obj = templateArgs)
        //
        //        FCPPointOfInterestTemplate::class.java.simpleName ->
        //             FCPPointOfInterestTemplate(obj = templateArgs)
        //
        FCPMapTemplate::class.java.simpleName -> FCPMapTemplate(obj = templateArgs)
        //
        FCPSearchTemplate::class.java.simpleName -> FCPSearchTemplate(obj = templateArgs)

        FCPListTemplate::class.java.simpleName -> {
            FCPListTemplate(obj = templateArgs, templateType = FCPListTemplateTypes.DEFAULT)
        }

        else -> null
    }
    if (pushTemplate == null) {
        result.success(false)
        return
    }

    // Push the template onto the navigation stack
    AndroidAutoService.session?.push(pushTemplate, result)
}

/**
 * Notifies Flutter about changes in Android Auto connection status.
 *
 * @param status The Android Auto connection status.
 */
fun FlutterCarplayPlugin.Companion.onCarplayConnectionChange(status: String) {
    FCPStreamHandlerPlugin.sendEvent(
        FCPChannelTypes.onCarplayConnectionChange.name, mapOf("status" to status)
    )
}

/** Sends an event to Flutter with the updated speech recognition transcript.
 *
 * @param transcript The updated speech recognition transcript.
 */
fun FlutterCarplayPlugin.Companion.sendSpeechRecognitionTranscriptChangeEvent(transcript: String) {
    FCPStreamHandlerPlugin.sendEvent(
        type = FCPChannelTypes.onVoiceControlTranscriptChanged.name,
        data = mapOf("transcript" to transcript)
    )
}

/**
 * Updates a Android Auto information template identified by its element ID with new data.
 *
 * @param elementId The unique identifier of the information template to be updated.
 * @param args Additional arguments for updating the information template.
 */
private fun FlutterCarplayPlugin.Companion.updateInformationTemplate(
    elementId: String,
    args: Map<String, Any>,
) {
    // Find the information template based on the provided element ID
    findInformationTemplate(elementId) { informationTemplate ->

        // Extract and handle the data for updating the information template
        val items = args["informationItems"] as? List<Map<String, Any>>
        val actions = args["actions"] as? List<Map<String, Any>>

        // Map dictionary representations to FCPBarButton instances for navigation bar buttons
        //        val leadingNavigationBarButtons = (args["leadingNavigationBarButtons"] as?
        // List<Map<String,Any>>)?.map {
        //            FCPBarButton(it as Map<String, Any>)
        //        }
        //        val trailingNavigationBarButtons = (args["trailingNavigationBarButtons"] as?
        // List<Map<String,Any>>)?.map {
        //            FCPBarButton(it as Map<String, Any>)
        //        }

        // Update the information template with the extracted data
        informationTemplate.update(
            items = items?.map { FCPInformationItem(it) },
            actions = actions?.map { FCPTextButton(it) },
            //        leadingNavigationBarButtons = leadingNavigationBarButtons,
            //        trailingNavigationBarButtons = trailingNavigationBarButtons
        )
    }
}

/**
 * Updates an Android Auto map template identified by its element ID with new data.
 *
 * @param elementId The unique identifier of the map template to be updated.
 * @param args Additional arguments for updating the map template.
 */
private fun FlutterCarplayPlugin.Companion.updateMapTemplate(
    elementId: String,
    args: Map<String, Any>,
) {
    // Find the map template based on the provided element ID
    findMapTemplate(elementId) { mapTemplate ->
        // Extract and handle the data for updating the map template
        val title = args["title"] as? String
        val hidesButtonsWithNavigationBar = args["hidesButtonsWithNavigationBar"] as? Bool
        val automaticallyHidesNavigationBar = args["automaticallyHidesNavigationBar"] as? Bool
        val isPanningInterfaceVisible = args["isPanningInterfaceVisible"] as? Bool

        // Map dictionary representations to FCPMapButton instances for map buttons
        val mapButtons = (args["mapButtons"] as? List<Map<String, Any>>)?.map { FCPMapButton(it) }

        // Map dictionary representations to FCPBarButton instances for navigation bar buttons
        val leadingNavigationBarButtons =
            (args["leadingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            }

        val trailingNavigationBarButtons =
            (args["trailingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            }

        mapTemplate.update(
            title = title,
            mapButtons = mapButtons,
            isPanningInterfaceVisible = isPanningInterfaceVisible,
            leadingNavigationBarButtons = leadingNavigationBarButtons,
            trailingNavigationBarButtons = trailingNavigationBarButtons,
            hidesButtonsWithNavigationBar = hidesButtonsWithNavigationBar,
            automaticallyHidesNavigationBar = automaticallyHidesNavigationBar,
        )
    }
}

/**
 * Updates an Android Auto list template identified by its element ID with new data.
 *
 * @param elementId The unique identifier of the list template to be updated.
 * @param sections An array of dictionaries representing the updated sections of the list template.
 * @param args Additional arguments for updating the list template.
 */
private fun FlutterCarplayPlugin.Companion.updateListTemplate(
    elementId: String,
    sections: List<Map<String, Any>>,
    args: Map<String, Any>,
) {
    // Find the list template based on the provided element ID
    findListTemplate(elementId) { listTemplate ->
        // Extract and handle the data for updating the list template
        val emptyViewTitleVariants = args["emptyViewTitleVariants"] as? List<String>
        val emptyViewSubtitleVariants = args["emptyViewSubtitleVariants"] as? List<String>

        // Map dictionary representations to FCPBarButton instances for navigation bar buttons
        val leadingNavigationBarButtons =
            (args["leadingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            }
        val trailingNavigationBarButtons =
            (args["trailingNavigationBarButtons"] as? List<Map<String, Any>>)?.map {
                FCPBarButton(it)
            }

        // Update the list template with the extracted data
        listTemplate.update(
            emptyViewTitleVariants = emptyViewTitleVariants,
            emptyViewSubtitleVariants = emptyViewSubtitleVariants,
            sections = sections.map { FCPListSection(it) },
            leadingNavigationBarButtons = leadingNavigationBarButtons,
            trailingNavigationBarButtons = trailingNavigationBarButtons
        )
    }
}

/**
 * Updates an Android Auto list item identified by its element ID with new data.
 *
 * @param elementId The unique identifier of the list item to be updated.
 * @param args Additional arguments for updating the list item.
 */
private fun FlutterCarplayPlugin.Companion.updateListItem(
    elementId: String,
    args: Map<String, Any>,
) {
    // Find the list item based on the provided element ID
    findListItem(elementId) { item ->

        // Update the list item with the extracted data
        item.update(
            text = args["text"] as? String,
            detailText = args["detailText"] as? String,
            image = args["image"] as? String,
            darkImage = args["darkImage"] as? String,
            playbackProgress = args["playbackProgress"] as? CGFloat,
            isPlaying = args["isPlaying"] as? Bool,
            isEnabled = args["isEnabled"] as? Bool,
            playingIndicatorLocation = args["playingIndicatorLocation"] as? String,
            accessoryType = args["accessoryType"] as? String,
            accessoryImage = args["accessoryImage"] as? String,
            accessoryDarkImage = args["accessoryDarkImage"] as? String
        )
    }
}

/**
 * Finds an Android Auto search template by element ID and performs an action when found.
 *
 *   @param elementId The element ID of the search template.
 *   @param actionWhenFound The action to perform when the search template is found.
 */
private fun FlutterCarplayPlugin.Companion.findSearchTemplate(
    elementId: String,
    actionWhenFound: (searchTemplate: FCPSearchTemplate) -> Unit,
) {
    // Filter the template history to include only FCPSearchTemplate instances.
    val filteredTemplates = fcpTemplateHistory.filterIsInstance<FCPSearchTemplate>()

    // Iterate through the templates to find the one with the matching element ID.
    for (template in filteredTemplates) {
        if (template.elementId == elementId) {
            // Perform the specified action when the template is found.
            actionWhenFound(template)
            return
        }
    }
    // If no templates are available or found, return early.
}

/**
 * Finds an Android Auto information template by element ID and performs an action when found.
 *
 * @param elementId The element ID of the information template.
 * @param actionWhenFound The action to perform when the information template is found.
 */
private fun FlutterCarplayPlugin.Companion.findInformationTemplate(
    elementId: String,
    actionWhenFound: (informationTemplate: FCPInformationTemplate) -> Unit,
) {
    // Get the array of FCPInformationTemplate instances.
    val templates = getFCPInformationTemplatesFromHistory()

    // Iterate through the templates to find the one with the matching element ID.
    for (template in templates) {
        if (template.elementId == elementId) {
            // Perform the specified action when the template is found.
            actionWhenFound(template)
            break
        }
    }
}

/**
 * Finds an Android Auto map template by element ID and performs an action when found.
 *
 * @param elementId The element ID of the map template.
 * @param actionWhenFound The action to perform when the map template is found.
 */
private fun FlutterCarplayPlugin.Companion.findMapTemplate(
    elementId: String,
    actionWhenFound: (mapTemplate: FCPMapTemplate) -> Unit,
): FCPMapTemplate? {
    // Get the array of FCPMapTemplate instances.
    val templates = getFCPMapTemplatesFromHistory()

    // Iterate through the templates to find the one with the matching element ID.
    for (template in templates) {
        if (template.elementId == elementId) {
            // Perform the specified action when the template is found.
            actionWhenFound(template)
            return template
        }
    }
    return null
}

/**
 * Finds an Android Auto list template by element ID and performs an action when found.
 *
 * @param elementId The element ID of the list template.
 * @param actionWhenFound The action to perform when the list template is found.
 */
private fun FlutterCarplayPlugin.Companion.findListTemplate(
    elementId: String,
    actionWhenFound: (listTemplate: FCPListTemplate) -> Unit,
) {
    // Get the array of FCPListTemplate instances.
    val templates = getFCPListTemplatesFromHistory()

    // Iterate through the templates to find the one with the matching element ID.
    for (template in templates) {
        if (template.elementId == elementId) {
            // Perform the specified action when the template is found.
            actionWhenFound(template)
            return
        }
    }
}

/**
 * Finds an Android Auto list item by element ID and performs an action when found.
 *
 * @param elementId The element ID of the list item.
 * @param actionWhenFound The action to perform when the list item is found.
 */
private fun FlutterCarplayPlugin.Companion.findListItem(
    elementId: String,
    actionWhenFound: (item: FCPListItem) -> Unit,
) {
    // Get the array of FCPListTemplate instances.
    val templates = getFCPListTemplatesFromHistory()

    // Iterate through the templates, sections, and items to find the one with the matching element
    // ID.
    for (template in templates) {
        for (section in template.getSections()) {
            for (item in section.getItems()) {
                if (item.elementId == elementId) {
                    // Perform the specified action when the item is found.
                    actionWhenFound(item)
                    return
                }
            }
        }
    }
}

/**
 * Finds an Android Auto information templates from history.
 *
 * @return An array of FCPInformationTemplate instances.
 */
private fun FlutterCarplayPlugin.Companion.getFCPInformationTemplatesFromHistory(): List<FCPInformationTemplate> {
    // Initialize an array to store FCPInformationTemplate instances.
    val templates: MutableList<FCPInformationTemplate> = mutableListOf()

    // Filter the template history to include only FCPInformationTemplate instances.
    for (template in fcpTemplateHistory) {
        if (template is FCPInformationTemplate) {
            templates.add(template)
        }
        //        else if (template is CPTabBarTemplate) {
        //            templates.addAll(template.getTemplates())
        //        }
    }

    return templates
}

/**
 * Finds an Android Auto map templates from history.
 *
 * @return An array of FCPMapTemplate instances.
 */
private fun FlutterCarplayPlugin.Companion.getFCPMapTemplatesFromHistory(): List<FCPMapTemplate> {
    // Initialize an array to store FCPMapTemplate instances.
    val templates: MutableList<FCPMapTemplate> = mutableListOf()
    // Filter the template history to include only FCPMapTemplate instances.
    for (template in fcpTemplateHistory) {
        if (template is FCPMapTemplate) {
            templates.add(template)
        }
    }
    return templates
}

/**
 * Finds an Android Auto list templates from history.
 *
 * @return An array of FCPListTemplate instances.
 */
private fun FlutterCarplayPlugin.Companion.getFCPListTemplatesFromHistory(): List<FCPListTemplate> {
    // Initialize an array to store FCPListTemplate instances.
    val templates: MutableList<FCPListTemplate> = mutableListOf()

    // Filter the template history to include only FCPListTemplate instances.
    for (template in fcpTemplateHistory) {
        if (template is FCPListTemplate) {
            templates.add(template)
        }
        //        else if (template is CPTabBarTemplate) {
        //            templates.addAll(template.getTemplates())
        //        }
    }

    return templates
}
