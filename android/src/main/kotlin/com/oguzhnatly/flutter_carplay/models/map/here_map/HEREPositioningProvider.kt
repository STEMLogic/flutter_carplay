/*
 * Copyright (C) 2019-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.oguzhnatly.flutter_carplay.models.map.here_map

import android.util.Log
import com.here.sdk.core.Location
import com.here.sdk.core.LocationListener
import com.here.sdk.core.errors.InstantiationErrorException
import com.here.sdk.location.LocationAccuracy
import com.here.sdk.location.LocationEngine
import com.here.sdk.location.LocationEngineStatus
import com.here.sdk.location.LocationFeature
import com.here.sdk.location.LocationStatusListener
import com.oguzhnatly.flutter_carplay.Bool
import kotlin.math.abs

// A reference implementation using HERE Positioning to get notified on location updates
// from various location sources available from a device and HERE services.
class HEREPositioningProvider {
    private var locationEngine: LocationEngine? = null
    private var updateListener: LocationListener? = null
    private var locationEngineStatus: LocationEngineStatus? = null
    private var lastLocation: Location? = null
    private val isLocationEngineStarted: Bool
        get() = locationEngineStatus == LocationEngineStatus.ENGINE_STARTED || locationEngineStatus == LocationEngineStatus.ALREADY_STARTED


    private val locationStatusListener: LocationStatusListener =
        object : LocationStatusListener {
            override fun onStatusChanged(locationEngineStatus: LocationEngineStatus) {
                this@HEREPositioningProvider.locationEngineStatus = locationEngineStatus
                Log.d(LOG_TAG, "Location engine status: " + locationEngineStatus.name)
            }

            override fun onFeaturesNotAvailable(features: List<LocationFeature>) {
                for (feature in features) {
                    Log.d(LOG_TAG, "Location feature not available: " + feature.name)
                }
            }
        }

    init {
//        val consentEngine: ConsentEngine

        try {
//            consentEngine = ConsentEngine()
            locationEngine = LocationEngine()
        } catch (e: InstantiationErrorException) {
            throw RuntimeException("Initialization failed: " + e.message)
        }

        locationEngineResetHandler = {
            locationEngine?.removeLocationListener(updateListener!!)
            locationEngine?.removeLocationListener(onLocationUpdated)
            locationEngine?.removeLocationStatusListener(locationStatusListener)
            locationEngine?.stop()
            locationEngine = LocationEngine()
            locationEngine?.addLocationListener(updateListener!!)
            locationEngine?.addLocationListener(onLocationUpdated)
            locationEngine?.addLocationStatusListener(locationStatusListener)
            locationEngineStatus = locationEngine?.start(LocationAccuracy.NAVIGATION)
        }

        // Ask user to optionally opt in to HERE's data collection / improvement program.
//        if (consentEngine.userConsentState == Consent.UserReply.NOT_HANDLED) {
//            consentEngine.requestUserConsent()
//        }
    }

    val lastKnownLocation: Location?
        get() = locationEngine?.lastKnownLocation

    // Does nothing when engine is already running.
    fun startLocating(updateListener: LocationListener?, accuracy: LocationAccuracy?) {
        if (isLocationEngineStarted) return

        this.updateListener = updateListener

        // Set listeners to get location updates.
        locationEngine?.addLocationListener(updateListener!!)
        locationEngine?.addLocationListener(onLocationUpdated)
        locationEngine?.addLocationStatusListener(locationStatusListener)

        locationEngineStatus = locationEngine?.start(accuracy!!)
    }

    // Does nothing when engine is already stopped.
    fun stopLocating() {
        if (!isLocationEngineStarted) return

        // Remove listeners and stop location engine.
        locationEngine?.removeLocationListener(updateListener!!)
        locationEngine?.removeLocationListener(onLocationUpdated)
        locationEngine?.removeLocationStatusListener(locationStatusListener)
        locationEngine?.stop()
    }

    /**
     * Conforms to the LocationDelegate protocol.
     *
     * @param location The new location.
     */
    private val onLocationUpdated = LocationListener { location: Location ->
        val lastCoordinates = lastLocation?.coordinates
        val currentCoordinates = location.coordinates

        val lastLatitude = lastCoordinates?.latitude ?: 0.0
        val currentLatitude = currentCoordinates.latitude
        val lastLongitude = lastCoordinates?.longitude ?: 0.0
        val currentLongitude = currentCoordinates.longitude

        val latitudeDifference = abs(lastLatitude - currentLatitude)
        val longitudeDifference = abs(lastLongitude - currentLongitude)

        // Skip update if the location didn't change significantly.
        if (latitudeDifference < 0.0001 && longitudeDifference < 0.0001) {
            return@LocationListener
        }

        lastLocation = location

        locationUpdatedHandler?.invoke(location)
    }

    companion object {
        private val LOG_TAG: String = HEREPositioningProvider::class.java.name
    }
}
