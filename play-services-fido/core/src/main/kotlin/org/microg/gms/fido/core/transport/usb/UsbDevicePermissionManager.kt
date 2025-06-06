/*
 * SPDX-FileCopyrightText: 2022 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.fido.core.transport.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import kotlinx.coroutines.CompletableDeferred

private val Context.usbPermissionCallbackAction
    get() = "$packageName.USB_PERMISSION_CALLBACK"

private object UsbDevicePermissionReceiver : BroadcastReceiver() {
    private var registered = false
    private val pendingRequests = hashMapOf<UsbDevice, MutableList<CompletableDeferred<Boolean>>>()

    fun register(context: Context) = synchronized(this) {
        if (!registered) {
            ContextCompat.registerReceiver(context, this, IntentFilter(context.usbPermissionCallbackAction), RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    fun addDeferred(device: UsbDevice, deferred: CompletableDeferred<Boolean>) = synchronized(this) {
        if (pendingRequests.containsKey(device)) {
            pendingRequests[device]!!.add(deferred)
            false
        } else {
            pendingRequests[device] = arrayListOf(deferred)
            true
        }
    }

    fun unregister(context: Context) = synchronized(this) {
        if (registered) {
            context.unregisterReceiver(this)
            registered = false
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        if (intent.action == context.usbPermissionCallbackAction) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null) {
                synchronized(this) {
                    if (pendingRequests.containsKey(device)) {
                        val hasPermission = context.usbManager?.hasPermission(device) == true
                        for (deferred in pendingRequests[device].orEmpty()) {
                            deferred.complete(hasPermission)
                        }
                        pendingRequests.remove(device)
                        if (pendingRequests.isEmpty()) {
                            unregister(context)
                        }
                    }
                }
            }
        }
    }
}

class UsbDevicePermissionManager(private val context: Context) {

    suspend fun awaitPermission(device: UsbDevice): Boolean {
        if (context.usbManager?.hasPermission(device) == true) return true
        val res = CompletableDeferred<Boolean>()
        if (UsbDevicePermissionReceiver.addDeferred(device, res)) {
            UsbDevicePermissionReceiver.register(context)
            val intent = PendingIntentCompat.getBroadcast(context, 0, Intent(context.usbPermissionCallbackAction).apply { `package` = context.packageName }, 0, true)
            context.usbManager?.requestPermission(device, intent)
        }
        return res.await()
    }
}
