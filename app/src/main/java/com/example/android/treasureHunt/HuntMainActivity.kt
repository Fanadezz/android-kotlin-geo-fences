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

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.example.android.treasureHunt.databinding.ActivityHuntMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

/**
 * The Treasure Hunt app is a single-player game based on geofences.
 *
 * This app demonstrates how to create and remove geofences using the GeofencingApi. Uses an
 * BroadcastReceiver to monitor geofence transitions and creates notification and finishes the game
 * when the user enters the final geofence (destination).
 *
 * This app requires a device's Location settings to be turned on. It also requires
 * the ACCESS_FINE_LOCATION permission and user consent. For geofences to work
 * in Android Q, app also needs the ACCESS_BACKGROUND_LOCATION permission and user consent.
 */

class HuntMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHuntMainBinding
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var viewModel: GeofenceViewModel

    private val runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build
            .VERSION_CODES.Q
    //create a pendingIntent to handle geofencing transitions

    private val geofencePendingIntent: PendingIntent by lazy {

        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }
    private val backgroundForegroundPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) {

        permissions ->

        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true) {
            checkDeviceLocationSettingsAndStartGeofence()


            Toast.makeText(this, "xx - granted", Toast.LENGTH_SHORT)
                    .show()

        } else {

            //Toast.makeText(this, "xx - denied",Toast.LENGTH_SHORT).show()
            Snackbar.make(binding.activityMapsMain, R.string.permission_denied_explanation, Snackbar
                    .LENGTH_INDEFINITE)
                    .setAction(R.string.settings) {
                        startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                    .show()
        }
    }


    // A PendingIntent for the Broadcast Receiver that handles geofence transitions.
    // TODO: Step 8 add in a pending intent


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hunt_main)

        Timber.plant(Timber.DebugTree())
        viewModel = ViewModelProvider(this, SavedStateViewModelFactory(this.application, this)).get(
                GeofenceViewModel::class.java)
        binding.viewmodel = viewModel
        binding.lifecycleOwner = this


        // request for permissions
        backgroundForegroundPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        //instantiate the geofencing client - the main entry point for interacting with
        // geofencing API

        geofencingClient = LocationServices.getGeofencingClient(this)
        // Create channel for notifications
        createChannel(this)
    }

    override fun onStart() {
        super.onStart()
        //checkPermissionsAndStartGeofencing()
    }

    /*
 *  When we get the result from asking the user to turn on device location, we call
 *  checkDeviceLocationSettingsAndStartGeofence again to make sure it's actually on, but
 *  we don't resolve the check to keep the user from seeing an endless loop.
 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // TODO: Step 7 add code to check that the user turned on their device location and ask
        //  again if they did not
    }

    /*
     *  When the user clicks on the notification, this method will be called, letting us know that
     *  the geofence has been triggered, and it's time to move to the next one in the treasure
     *  hunt.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val extras = intent?.extras
        if (extras != null) {
            if (extras.containsKey(GeofencingConstants.EXTRA_GEOFENCE_INDEX)) {
                viewModel.updateHint(extras.getInt(GeofencingConstants.EXTRA_GEOFENCE_INDEX))
              //  checkPermissionsAndStartGeofencing()
            }
        }
    }

    /*
     * In all cases, we need to have the location permission.  On Android 10+ (Q) we need to have
     * the background permission as well.
     */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // TODO: Step 5 add code to handle the result of the user's permission
    }

    /**
     * This will also destroy any saved state in the associated ViewModel, so we remove the
     * geofences here.
     */
    override fun onDestroy() {
        super.onDestroy()
        removeGeofences()
    }

   /* *//**
     * Starts the permission check and Geofence process only if the Geofence associated with the
     * current hint isn't yet active.
     *//*
    private fun checkPermissionsAndStartGeofencing() {
        if (viewModel.geofenceIsActive()) return
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }*/

    /*
     *  Uses the Location Client to check the current state of location settings, and gives the user
     *  the opportunity to turn on location services within our app.
     */
    private fun checkDeviceLocationSettingsAndStartGeofence(resolve: Boolean = true) {

        //create a location request object

        val locationRequest = LocationRequest.create()
                .apply {

                    priority = LocationRequest.PRIORITY_LOW_POWER
                }

        //create location response task

        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(this)


        val locationSettingsResponseTask = settingsClient.checkLocationSettings(builder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->

            if (exception is ResolvableApiException && resolve) {
                try {

                    exception.startResolutionForResult(this@HuntMainActivity,
                                                       REQUEST_TURN_DEVICE_LOCATION_ON)
                } catch (sendEx: IntentSender.SendIntentException) {

                    Timber.i("Error getting location settings resolution: %s", sendEx.message)

                }
            } else {

                Snackbar.make(binding.activityMapsMain, R.string.location_required_error,
                              Snackbar.LENGTH_INDEFINITE)
                        .setAction(android.R.string.ok) {

                            checkDeviceLocationSettingsAndStartGeofence()
                        }
                        .show()
            }
        }
                .addOnCompleteListener {

                    if (it.isSuccessful) {

                        addGeofenceForClue()
                    }
                }
    }

  /*  *//*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     *//*
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        // TODO: Step 3 replace this with code to check that the foreground and background
        //  permissions were approved
        return false
    }

    *//*
     *  Requests ACCESS_FINE_LOCATION and (on Android 10+ (Q) ACCESS_BACKGROUND_LOCATION.
     *//*
    @TargetApi(29)
    private fun requestForegroundAndBackgroundLocationPermissions() {
        // TODO: Step 4 add code to request foreground and background permissions
    }

    *//*
     * Adds a Geofence for the current clue if needed, and removes any existing Geofence. This
     * method should be called after the user has granted the location permission.  If there are
     * no more geofences, we remove the geofence and let the viewmodel know that the ending hint
     * is now "active."
     */
    @SuppressLint("MissingPermission")
    private fun addGeofenceForClue() {
        //check if you have any active geofences if you do, you shouldn't add another - 1 treasure
        // being sought at a time.
        if (viewModel.geofenceIsActive()) return

        val currentGeofenceIndex = viewModel.nextGeofenceIndex()
        if (currentGeofenceIndex >= GeofencingConstants.NUM_LANDMARKS) {

            removeGeofences()
            viewModel.geofenceActivated()
            return
        }

        val currentGeofenceData = GeofencingConstants.LANDMARK_DATA[currentGeofenceIndex]

        //build geofence
        val geofence = Geofence.Builder()
                .setRequestId(currentGeofenceData.id)
                .setCircularRegion(currentGeofenceData.latLong.latitude, currentGeofenceData
                        .latLong.longitude, GeofencingConstants.GEOFENCE_RADIUS_IN_METERS)
                .setExpirationDuration(GeofencingConstants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()

        //build geofence request
        val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

        //remove geofence disregarding its failure as it doesn't affect new goefence addition

        geofencingClient.removeGeofences(geofencePendingIntent)
                .run {

                    addOnCompleteListener{

                        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {

                            addOnSuccessListener {

                                Toast.makeText(this@HuntMainActivity, R.string.geofences_added,
                                               Toast.LENGTH_SHORT).show()
                                Timber.d("Add Geofence ${geofence.requestId}")
                                viewModel.geofenceActivated()
                            }.addOnFailureListener {
                                Toast.makeText(this@HuntMainActivity, R.string
                                        .geofences_not_added, Toast.LENGTH_SHORT ).show()
                                if (it.message != null) {
                                    Timber.i(" this is the error xxx - $it")
                                }
                            }
                        }
                    }
                }


    }

    /**
     * Removes geofences. This method should be called after the user has granted the location
     * permission.
     */
    private fun removeGeofences() {


    }

    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
                "HuntMainActivity.treasureHunt.action.ACTION_GEOFENCE_EVENT"
    }
}

private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
private const val TAG = "HuntMainActivity"
private const val LOCATION_PERMISSION_INDEX = 0
private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
