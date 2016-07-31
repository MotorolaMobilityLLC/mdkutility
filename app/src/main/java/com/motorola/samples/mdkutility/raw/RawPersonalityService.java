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

package com.motorola.samples.mdkutility.raw;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.motorola.mod.ModDevice;
import com.motorola.samples.mdkutility.Constants;
import com.motorola.samples.mdkutility.MainActivity;
import com.motorola.samples.mdkutility.Personality;
import com.motorola.samples.mdkutility.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to represent blinky mod status.
 */
public class RawPersonalityService extends Service {
    private static final int NOTIFICATION = 1001;

    public static final int BLINKY_STATUS = 100;
    public static final int EXIT_APP = 101;

    public static final String BLINKY = "blinky";
    public static final String BLINKY_ON = "on";
    public static final String BLINKY_OFF = "off";
    public static final String CANCEL_NOTI = "cancel_notification";

    private boolean cancelNoti = false;

    private RawPersonality rawPersonality;
    private NotificationManager notificationManager;

    public class LocalBinder extends Binder {
        public RawPersonalityService getService() {
            return RawPersonalityService.this;
        }
    }

    List<Handler> listeners = new ArrayList<>();

    public void registerListener(Handler listener) {
        listeners.add(listener);
    }

    protected void notifyListeners(int what) {
        for (Handler handler : listeners) {
            handler.sendEmptyMessage(what);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (rawPersonality == null) {
            initPersonality();
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (rawPersonality == null) {
            initPersonality();
        }

        if (intent != null) {
            cancelNoti = intent.getBooleanExtra(CANCEL_NOTI, false);
            String blinky = intent.getStringExtra(BLINKY);
            if (blinky != null && blinky.isEmpty() == false) {
                boolean blinking = blinky.equalsIgnoreCase(BLINKY_ON);

                if (rawPersonality != null
                        && rawPersonality.getModDevice() != null
                        && rawPersonality.getModDevice().getUniqueId() != null) {
                    SharedPreferences preference = getSharedPreferences(
                            rawPersonality.getModDevice().getUniqueId().toString(), MODE_PRIVATE);
                    preference.edit().putBoolean(BLINKY, blinking).apply();
                }
            }
        }

        if (cancelNoti) {
            /**
             * If Exit Service action on notification item is pressed, notify activity to exit,
             * and do not toggle LED.
             */
            notificationManager.cancelAll();
            notifyListeners(EXIT_APP);
        } else {
            /** Check RAW I/O and toggle LED if needed */
            checkRawInterface();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationManager.cancelAll();

        releasePersonality();
    }

    /**
     * Push the blinky status notification to status bar
     */
    private void showNotification(boolean blinking) {
        /** Prepare text string for notification item */
        CharSequence text = getText(R.string.led_off);
        if (blinking) {
            text = getText(R.string.led_blinky);
        }

        /** Prepare notification item */
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder notification = new Notification.Builder(this)
                .setTicker(text)
                .setContentTitle(getText(R.string.raw_service_label))
                .setContentText(text)
                .setDefaults(0)
                .setOngoing(true)
                // TODO: Set PRIORITY_MAX will put the notification on top.
                // Consider your app's behavior and select a appropriate priority.
                .setPriority(Notification.PRIORITY_MAX)
                .setSmallIcon(R.drawable.ic_mod_led)
                .setColor(getColor(R.color.mod_background))
                .setWhen(System.currentTimeMillis())
                .setLocalOnly(true)
                .setContentIntent(contentIntent);

        /** Add the pendingIntent for notification action items */
        Intent led = new Intent(this, RawPersonalityService.class);
        if (blinking) {
            led.putExtra(RawPersonalityService.BLINKY, RawPersonalityService.BLINKY_OFF);
            PendingIntent pChangeIntent = PendingIntent.getService(this,
                    (int) System.currentTimeMillis(), led, 0);

            notification.addAction(0,
                    getString(R.string.change_led_off), pChangeIntent);
        } else {
            led.putExtra(RawPersonalityService.BLINKY, RawPersonalityService.BLINKY_ON);
            PendingIntent pChangeIntent = PendingIntent.getService(this,
                    (int) System.currentTimeMillis(), led, 0);

            notification.addAction(0,
                    getString(R.string.change_led_blinking), pChangeIntent);
        }

        /** Add the pendingIntent for notification action items */
        Intent service = new Intent(this, RawPersonalityService.class);
        service.putExtra(RawPersonalityService.CANCEL_NOTI, true);
        PendingIntent pChangeIntent = PendingIntent.getService(this,
                (int) System.currentTimeMillis(), service, 0);
        notification.addAction(R.drawable.ic_close_black_18dp,
                getString(R.string.exit_service), pChangeIntent);

        /** Push notification item to status bar */
        notificationManager.notify(NOTIFICATION, notification.build());
    }

    /** Initial Personality interface */
    private void initPersonality() {
        if (null == rawPersonality) {
            /** For this example we expect to use MDK Blinky mod */
            rawPersonality = new RawPersonality(this, Constants.VID_MDK, Constants.PID_BLINKY);

            /** Register handler to get event and data update */
            rawPersonality.registerListener(handler);
        }
    }

    /** Clean up */
    private void releasePersonality() {
        if (null != rawPersonality) {
            rawPersonality.onDestroy();
            rawPersonality = null;
        }
    }

    /** Handle mod events */
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Personality.MSG_RAW_IO_READY:
                    /** The RAW I/O of attached mod device is created. */
                    onRawInterfaceReady();
                    break;
                case Personality.MSG_RAW_DATA:
                    // TODO: Does not expect any data from example blinky mod.
                    // Handle the data here if you are developing a consumer mod
                    // and grant data from the mod.
                    break;
                case Personality.MSG_RAW_IO_EXCEPTION:
                    /** Got RAW I/O exception. */
                    break;
                case Personality.MSG_MOD_DEVICE:
                    /** Got mod attach/detach event */
                    ModDevice device = rawPersonality.getModDevice();
                    if (device == null) {
                        notificationManager.cancelAll();
                    }
                    break;
                default:
                    Log.e(Constants.TAG, "RawPersonalityService - Un-handle events: " + msg.what);
                    break;
            }
        }
    };

    /** Check RAW I/O status */
    public void checkRawInterface() {
        if (rawPersonality != null) {
            rawPersonality.checkRawInterface();
        }
        notifyListeners(BLINKY_STATUS);
    }

    /** Restore blinky status to attached mod device */
    public void onRawInterfaceReady() {
        if (rawPersonality != null) {
            boolean blinking = false;
            /** Get attached mod device UUID and check according LED record */
            if (rawPersonality.getModDevice() != null
                    && rawPersonality.getModDevice().getUniqueId() != null) {
                SharedPreferences preference = getSharedPreferences(
                        rawPersonality.getModDevice().getUniqueId().toString(), MODE_PRIVATE);
                blinking = preference.getBoolean(BLINKY, false);
            }

            /** Write RAW command to mod device to toggle LED */
            if (blinking) {
                rawPersonality.executeRaw(Constants.RAW_CMD_LED_ON);
                Toast.makeText(this, getString(R.string.led_blinky),
                        Toast.LENGTH_SHORT).show();
            } else {
                rawPersonality.executeRaw(Constants.RAW_CMD_LED_OFF);
                Toast.makeText(this, getString(R.string.led_off),
                        Toast.LENGTH_SHORT).show();
            }

            /** Update notification item to currently status */
            showNotification(blinking);
        }

        /** Notify UI LED status */
        notifyListeners(BLINKY_STATUS);
    }

    /** Check currently LED status */
    public boolean isBlinking() {
        boolean blinking = false;
        if (rawPersonality != null
                && rawPersonality.getModDevice() != null
                && rawPersonality.getModDevice().getUniqueId() != null) {
            SharedPreferences preference = getSharedPreferences(
                    rawPersonality.getModDevice().getUniqueId().toString(), MODE_PRIVATE);
            blinking = preference.getBoolean(BLINKY, false);
        }

        return blinking;
    }

    /** Check RAW I/O status */
    public boolean isRawInterfaceReady() {
        if (rawPersonality != null
                && rawPersonality.getModDevice() != null
                && rawPersonality.isRawInterfaceReady()) {
            return true;
        }

        return false;
    }
}
