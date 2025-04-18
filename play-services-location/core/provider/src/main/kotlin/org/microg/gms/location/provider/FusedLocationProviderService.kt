/*
 * SPDX-FileCopyrightText: 2024 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.location.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.core.location.LocationRequestCompat
import com.android.location.provider.ProviderPropertiesUnbundled
import com.android.location.provider.ProviderRequestUnbundled
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.max

class FusedLocationProviderService : IntentLocationProviderService() {
    override fun extractLocation(intent: Intent): Location? = LocationResult.extractResult(intent)?.lastLocation

    @SuppressLint("MissingPermission")
    override fun requestIntentUpdated(currentRequest: ProviderRequestUnbundled?, pendingIntent: PendingIntent) {
        val intervalMillis = max(currentRequest?.interval ?: Long.MAX_VALUE, minIntervalMillis)
        val request = LocationRequest.Builder(intervalMillis)
        if (SDK_INT >= 31 && currentRequest != null) {
            request.setPriority(when {
                currentRequest.interval == LocationRequestCompat.PASSIVE_INTERVAL -> Priority.PRIORITY_PASSIVE
                currentRequest.isLowPower -> Priority.PRIORITY_LOW_POWER
                currentRequest.quality == LocationRequestCompat.QUALITY_LOW_POWER -> Priority.PRIORITY_LOW_POWER
                currentRequest.quality == LocationRequestCompat.QUALITY_HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
                else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            })
            request.setMaxUpdateDelayMillis(currentRequest.maxUpdateDelayMillis)
            request.setWorkSource(currentRequest.workSource)
        } else {
            request.setPriority(when {
                currentRequest?.interval == LocationRequestCompat.PASSIVE_INTERVAL -> Priority.PRIORITY_PASSIVE
                else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            })
        }
        if (SDK_INT >= 29 && currentRequest != null) {
            request.setBypass(currentRequest.isLocationSettingsIgnored)
        }
        val identity = Binder.clearCallingIdentity()
        try {
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request.build(), pendingIntent)
        } catch (e: SecurityException) {
            Log.d(TAG, "Failed requesting location updated", e)
        }
        Binder.restoreCallingIdentity(identity)
    }

    override fun stopIntentUpdated(pendingIntent: PendingIntent) {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(pendingIntent)
    }

    override val minIntervalMillis: Long
        get() = MIN_INTERVAL_MILLIS
    override val minReportMillis: Long
        get() = MIN_REPORT_MILLIS
    override val properties: ProviderPropertiesUnbundled
        get() = PROPERTIES
    override val providerName: String
        get() = "fused"

    companion object {
        private const val MIN_INTERVAL_MILLIS = 1000L
        private const val MIN_REPORT_MILLIS = 1000L
        private val PROPERTIES = ProviderPropertiesUnbundled.create(false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE)
    }
}