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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import com.motorola.mod.IModManager;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to represent the Moto Mod interface.
 */
public class Personality {
    protected BroadcastReceiver modReceiver;
    protected Context context;

    /**
     * ModManager interface
     */
    protected ModManager modManager;

    /**
     * ModDevice interface
     */
    protected ModDevice modDevice;

    /**
     * Listeners to notify mod event and data
     */
    List<Handler> listeners = new ArrayList<>();

    /** Constructor */
    public Personality(Context context) {
        this.context = context;

        /** Bind with Moto Mod service */
        Intent service = new Intent(ModManager.ACTION_BIND_MANAGER);
        service.setComponent(ModManager.MOD_SERVICE_NAME);
        context.bindService(service, mConnection, Context.BIND_AUTO_CREATE);

        /** Register Mod intents receiver */
        modReceiver = new MyBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        /**
         * Request the broadcaster who send these intents must hold permission PERMISSION_MOD_INTERNAL,
         * to avoid the intent from fake senders. For future details, refer to:
         * https://developer.android.com/reference/android/content/Context.html#registerReceiver
         */
        context.registerReceiver(modReceiver, filter, ModManager.PERMISSION_MOD_INTERNAL, null);
    }

    /** Clean up */
    public void onDestroy() {
        listeners.clear();
        context.unregisterReceiver(modReceiver);
        context.unbindService(mConnection);
    }

    // Personality common interface - Begin
    public final static int MSG_MOD_DEVICE = 1;
    public final static int MSG_MOD_BATTERY = 2;
    public final static int MSG_UPDATE_DONE = 3;
    public final static int MSG_UPDATE_START = 4;
    public final static int MSG_RAW_IO_EXCEPTION = 5;
    public final static int MSG_RAW_REQUEST_PERMISSION = 6;
    public final static int MSG_RAW_IO_READY = 7;
    public final static int MSG_RAW_DATA = 8;
    public final static int MSG_REQUEST_FIRMWARE = 9;

    public void registerListener(Handler listener) {
        listeners.add(listener);
    }

    public ModDevice getModDevice() {
        return modDevice;
    }

    public ModManager getModManager() {
        return modManager;
    }
    // Personality common interface - End

    protected void notifyListeners(int what) {
        for (Handler handler : listeners) {
            handler.sendEmptyMessage(what);
        }
    }

    protected void notifyListeners(Message msg) {
        for (Handler handler : listeners) {
            handler.sendMessage(msg);
        }
    }

    protected void notifyListeners(int what, int arg) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.arg1 = arg;

        for (Handler handler : listeners) {
            handler.sendMessage(msg);
        }
    }

    /** Bind with Moto Mod service */
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            IModManager mMgrSrvc = IModManager.Stub.asInterface(binder);
            modManager = new ModManager(context, mMgrSrvc);
            onModAttach(true);
        }

        public void onServiceDisconnected(ComponentName className) {
            modDevice = null;
            modManager = null;
            onModAttach(false);
        }
    };

    /** Query mod device when attach/detach event */
    protected void onModAttach(boolean attach) {
        new Thread(new Runnable() {
            public void run() {
                updateModList();
            }
        }).start();
    }

    /** Query and update mod device info */
    protected void updateModList() {
        if (modManager == null) {
            onModDevice(null);
            return;
        }

        try {
            /** Get currently mod device list from ModManager */
            List<ModDevice> l = modManager.getModList(false);
            if (l == null || l.size() == 0) {
                onModDevice(null);
                return;
            }

            // TODO: simply get first mod device from list for this example.
            // You may need consider to check expecting mod base on PID/VID or so on.
            for (ModDevice d : l) {
                if (d != null) {
                    onModDevice(d);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Notify listeners the mod device info */
    public void onModDevice(ModDevice d) {
        modDevice = d;
        notifyListeners(MSG_MOD_DEVICE);
    }

    /** Handle mod device attach/detach event */
    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_ATTACH.equals(action)) {
                /** Mod device attached */
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onModAttach(true);
                    }
                }, 1000);
            } else if (ModManager.ACTION_MOD_DETACH.equals(action)) {
                /** Mod device detached */
                onModAttach(false);
            }
        }
    }
}