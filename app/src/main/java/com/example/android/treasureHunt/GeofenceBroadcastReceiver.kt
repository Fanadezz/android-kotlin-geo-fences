/*
 * Copyright (C) 2019 Google Inc.
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
 */

package com.example.android.treasureHunt

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.android.treasureHunt.HuntMainActivity.Companion.ACTION_GEOFENCE_EVENT
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import timber.log.Timber

/*
 * Triggered by the Geofence.  Since we only have one active Geofence at once, we pull the request
 * ID from the first Geofence, and locate it within the registered landmark data in our
 * GeofencingConstants within GeofenceUtils, which is a linear string search. If we had  very large
 * numbers of Geofence possibilities, it might make sense to use a different data structure.  We
 * then pass the Geofence index into the notification, which allows us to have a custom "found"
 * message associated with each Geofence.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

//ACTION_GEOFENCE_EVENT is constant specified in the HuntMainActivity
        if (intent.action == ACTION_GEOFENCE_EVENT) {

            val geofencingEvent = GeofencingEvent.fromIntent(intent)

            if (geofencingEvent.hasError()) {

                val errorMessage = errorMessage(context, geofencingEvent.errorCode)
                Timber.i(errorMessage)
                return
            }

            if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {

                Timber.i(context.getString(R.string.geofence_entered))

                val fenceId = when {
                    //if geofence triggering array is not empty
                    geofencingEvent.triggeringGeofences.isNotEmpty() -> geofencingEvent.triggeringGeofences[0].requestId
                    else -> {
                        Timber.i("No Geofence Trigger Found! Abort mission!")
                        return
                    }
                }

                //check that geofence is consistent with the constants listed in GeofenceUtils

                val foundIndex = GeofencingConstants.LANDMARK_DATA.indexOfFirst {

                    it.id == fenceId
                }

                //predicate returns -1 if not element foundIndex
                if (foundIndex == -1) {

                    //print log and return
                    Timber.i("Unknown Geofence: Abort Mission")
                }

                val notificationManager = ContextCompat.getSystemService(
                    context,
                    NotificationManager::class.java
                ) as NotificationManager

                notificationManager.sendGeofenceEnteredNotification(context,foundIndex)
            }

        }
    }
}

private const val TAG = "GeofenceReceiver"
