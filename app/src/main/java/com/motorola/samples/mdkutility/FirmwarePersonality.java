/**
 * Copyright (c) 2016 Motorola Mobility, LLC.
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.motorola.samples.mdkutility;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import com.motorola.mod.ModManager;

import java.util.List;

/**
 * A class to represent ModManager firmware update interface.
 */
public class FirmwarePersonality extends Personality {
    private BroadcastReceiver modEventReceiver;
    private BroadcastReceiver requestFwReceiver;
    private List<Uri> pendingUri;

    /**
     * Constructor
     */
    public FirmwarePersonality(Context context) {
        super(context);

        /** Register for firmware update related intents */
        modEventReceiver = new MyBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ModManager.ACTION_MOD_FIRMWARE_UPDATE_DONE);
        filter.addAction(ModManager.ACTION_MOD_FIRMWARE_UPDATE_START);
        filter.addAction(ModManager.ACTION_MOD_ENUMERATION_DONE);
        filter.addAction(ModManager.ACTION_REQUEST_CONSENT_FOR_UNSECURE_FIRMWARE_UPDATE);
        filter.addAction(ModManager.ACTION_MOD_ERROR);
        context.registerReceiver(modEventReceiver, filter, ModManager.PERMISSION_MOD_INTERNAL, null);


        /** Register for firmware request intent with higher priority to overlap ModManager */
        requestFwReceiver = new MyBroadcastReceiver();
        filter = new IntentFilter(ModManager.ACTION_MOD_REQUEST_FIRMWARE);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        /**
         * Request the broadcaster who send these intents must hold permission PERMISSION_MOD_INTERNAL,
         * to avoid the intent from fake senders. For future details, refer to:
         * https://developer.android.com/reference/android/content/Context.html#registerReceiver
         */
        context.registerReceiver(requestFwReceiver, filter, ModManager.PERMISSION_MOD_INTERNAL, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        context.unregisterReceiver(requestFwReceiver);
        context.unregisterReceiver(modEventReceiver);
    }

    /** Provide firmware files and call ModManager function to flash the firmware */
    public static final int FIRMWARE_UPDATE_SUCCESS = 0;
    public static final int FIRMWARE_UPDATE_FAILED = -1;
    public static final int FIRMWARE_UPDATE_ILLEGAL_EXCEPTION = -2;
    public static final int FIRMWARE_UPDATE_SECURITY_EXCEPTION = -3;

    public int performUpdate(Context context, List<Uri> pendingFirmware) {
        int result = FIRMWARE_UPDATE_FAILED;
        if (null == modDevice) {
            Log.e(Constants.TAG, "No Mod to flash.");
            return result;
        }

        if (null == pendingFirmware
                || pendingFirmware.size() == 0) {
            Log.e(Constants.TAG, "No firmware file to flash.");
            return result;
        }

        pendingUri = pendingFirmware;
        /** Generate the URI permissions for ModService to access */
        for (Uri u : pendingUri) {
            context.grantUriPermission("com.motorola.modservice",
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        try {
            /** Provide the firmware file uris to ModManager for update */
            result = modManager.requestUpdateFirmware(modDevice, pendingUri);
        } catch (IllegalArgumentException e) {
            result = FIRMWARE_UPDATE_ILLEGAL_EXCEPTION;
            Log.e(Constants.TAG, "modManager.requestUpdateFirmware illegal failed.");
            e.printStackTrace();
        } catch (SecurityException e) {
            result = FIRMWARE_UPDATE_SECURITY_EXCEPTION;
            Log.e(Constants.TAG, "modManager.requestUpdateFirmware security failed.");
            e.printStackTrace();
        } catch (Exception e) {
            result = FIRMWARE_UPDATE_FAILED;
            Log.e(Constants.TAG, "modManager.requestUpdateFirmware failed.");
            e.printStackTrace();
        }

        return result;
    }

    /** Handle mod device event intents */
    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_FIRMWARE_UPDATE_DONE.equals(action)) {
                /** The firmware update of the mod completed */

                /** Revoke the URI permissions. */
                if (pendingUri != null && pendingUri.size() != 0) {
                    for (Uri u : pendingUri) {
                        context.revokeUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    pendingUri = null;
                }

                updateModList();
                int result = intent.getIntExtra(ModManager.EXTRA_RESULT_CODE, -1);
                notifyListeners(MSG_UPDATE_DONE, result);
            } else if (ModManager.ACTION_MOD_ENUMERATION_DONE.equals(action)) {
                /** Phone has finished enumerating all the functionality of mod */
                onModAttach(true);
            } else if (ModManager.ACTION_MOD_FIRMWARE_UPDATE_START.equals(action)) {
                /** The device starts firmware update on an attached mod */
                notifyListeners(MSG_UPDATE_START);
            } else if (ModManager.ACTION_MOD_REQUEST_FIRMWARE.equals(action)) {
                /** The mod is being attached to the device but but is unable to boot due to
                 * missing or invalid firmware, and request userspace to give the firmware. */

                /** Update Mod device info firstly */
                updateModList();

                // TODO: This intent is broadcast when a mod missing or invalid firmware.
                // If you are developing the consumer mod, call abortBroadcast() here and
                // provide the according consumer firmware for the mod.
                /* Code Example:
                if (consumerMod) {
                    abortBroadcast();

                    if (modManager != null && modDevice != null) {
                        modManager.requestUpdateFirmware(modDevice, consumerFirmwareUris);
                    }
                } else {
                    // Notify UI that ModManager is requesting the firmware for this mod
                }
                */

                notifyListeners(MSG_REQUEST_FIRMWARE);
            } else if (ModManager.ACTION_MOD_ERROR.equals(action)) {
                /** An error happened to the mod */
                int error = intent.getIntExtra(ModManager.EXTRA_MOD_ERROR, -1);
                Log.e(Constants.TAG, "EXTRA_MOD_ERROR: " + error);
            }
        }
    }
}

