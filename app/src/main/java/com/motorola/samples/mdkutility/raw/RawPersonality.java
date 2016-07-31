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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import com.motorola.mod.ModDevice;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModManager;
import com.motorola.mod.ModProtocol;
import com.motorola.samples.mdkutility.Constants;
import com.motorola.samples.mdkutility.Personality;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A class to represent the Moto Mod RAW protocol interface.
 */
public class RawPersonality extends Personality {
    private static final int SEND_RAW_CMD = 1;
    private Handler handler;

    /**
     * Result indicator for Os.poll()
     */
    private static final int POLL_TYPE_READ_DATA = 1;
    private static final int POLL_TYPE_EXIT = 2;

    /** Output stream end indicator */
    private static final int EXIT_BYTE = 0xFF;

    /** File descriptor for RAW I/O */
    private ParcelFileDescriptor parcelFD;

    /**
     * File descriptor pipes for RAW I/O. For further details,
     * refer to http://man7.org/linux/man-pages/man2/pipe.2.html
     */
    private FileDescriptor[] syncPipes;

    /** Work thread for read / write data via RAW I/O */
    private Thread receiveThread = null;
    private HandlerThread sendingThread = null;
    private FileOutputStream outputStream;

    /** The expected mod device PID / VID */
    private int targetPID = Constants.INVALID_ID;
    private int targetVID = Constants.INVALID_ID;

    /** Constructor */
    public RawPersonality(Context context, int vid, int pid) {
        super(context);
        targetPID = pid;
        targetVID = vid;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        /** Don't forget close the I/O and work threads */
        closeRawDeviceifAvailable();
    }

    /** Close RAW I/O and work threads */
    private void closeRawDeviceifAvailable() {
        /** Exit write thread */
        if (null != sendingThread) {
            sendingThread.quitSafely();
        }

        /** Exit read thread */
        if (null != receiveThread) {
            signalReadThreadToExit();
        }

        /** Close the file descriptor pipes */
        if (null != syncPipes) {
            synchronized (syncPipes) {
                try {
                    Os.close(syncPipes[0]);
                    Os.close(syncPipes[1]);
                } catch (ErrnoException e) {
                    e.printStackTrace();
                }
                syncPipes = null;
            }
        }

        /** Close the file descriptor */
        if (parcelFD != null) try {
            parcelFD.close();
            parcelFD = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        sendingThread = null;
        receiveThread = null;
    }

    /** Write exit signal */
    private void signalReadThreadToExit() {
        FileOutputStream out = new FileOutputStream(syncPipes[1]);
        try {
            out.write(EXIT_BYTE);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**  Handle mod device attach/detach events */
    @Override
    public void onModDevice(ModDevice d) {
        super.onModDevice(d);

        if (modDevice != null) {
            openRawDeviceifAvailable();
        } else {
            closeRawDeviceifAvailable();
        }
    }

    /**  Put the RAW command into event queue to execute */
    public boolean executeRaw(byte[] cmd) {
        if (null != handler) {
            Message msg = Message.obtain(handler, SEND_RAW_CMD);
            msg.obj = cmd;
            handler.sendMessage(msg);

            return true;
        } else {
            return false;
        }
    }

    /** Check RAW I/O status, open I/O streams if not yet */
    public void checkRawInterface() {
        if (null != sendingThread && null != receiveThread) {
            onRawInterfaceReady();
        } else {
            openRawDeviceifAvailable();
        }
    }

    /** Check RAW I/O status */
    public boolean isRawInterfaceReady() {
        return sendingThread != null && receiveThread != null;
    }

    /**  Write the command byte array data via RAW I/O to Moto Mod device */
    private class SendHandler extends Handler {
        public SendHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SEND_RAW_CMD:
                    try {
                        /** Write data into RAW I/O, which mod device will get */
                        if (null != outputStream) {
                            outputStream.write((byte[]) msg.obj);
                        }
                    } catch (IOException e) {
                        Log.e(Constants.TAG, "IOException while writing to raw file" + e);
                        onIOException();
                    }
                    return;
            }
            super.handleMessage(msg);
        }
    }

    /** I/O exception */
    private void onIOException() {
        notifyListeners(MSG_RAW_IO_EXCEPTION);
    }

    /** Request grant RAW_PROTOCOL permission */
    private void onRequestRawPermission() {
        notifyListeners(MSG_RAW_REQUEST_PERMISSION);
    }

    /** RAW I/O is ready to use */
    private void onRawInterfaceReady() {
        notifyListeners(MSG_RAW_IO_READY);
    }

    /** Got data from RAW I/O from mod device */
    private void onRawData(byte[] buffer, int length) {
        Message msg = Message.obtain();
        msg.what = MSG_RAW_DATA;
        msg.arg1 = length;
        msg.obj = buffer;

        notifyListeners(msg);
    }

    /** Create RAW I/O for attached mod device */
    private boolean openRawDeviceifAvailable() {
        /** Check whether mod device is available */
        if (null == modManager || null == modDevice) {
            return false;
        }

        /**
         * Check whether expecting mod attached based on PID / VID.
         * For this example we ask for MDK Blinky.
         */
//        if (targetPID != Constants.INVALID_ID && targetVID != Constants.INVALID_ID) {
//            if (modDevice.getVendorId() != targetVID || modDevice.getProductId() != targetPID) {
//                return false;
//            }
//        }

        try {
            /** Query ModManager with RAW protocol */
            List<ModInterfaceDelegation> devices =
                    modManager.getModInterfaceDelegationsByProtocol(modDevice,
                            ModProtocol.Protocol.RAW);
            if (devices != null && !devices.isEmpty()) {
                // TODO: go through the whole devices list for multi connected devices.
                // Here simply operate the first device for this example.
                ModInterfaceDelegation device = devices.get(0);

                /**
                 * Be care to strict follow Android policy, you need visibly asking for
                 * grant permission.
                 */
                if (context.checkSelfPermission(ModManager.PERMISSION_USE_RAW_PROTOCOL)
                        != PackageManager.PERMISSION_GRANTED) {
                    onRequestRawPermission();
                } else {
                    /** The RAW_PROTOCOL permission already granted, open RAW I/O */
                    getRawPfd(device);
                    return true;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Get file description via ModManager for attached Moto Mod, to create RAW I/O */
    private void getRawPfd(ModInterfaceDelegation device) {
        try {
            /** Get file description of this mod device */
            parcelFD = modManager.openModInterface(device,
                    ParcelFileDescriptor.MODE_READ_WRITE);
            if (parcelFD != null) {
                try {
                    /**
                     * Get read / write file descriptor, For further details,
                     * refer to http://man7.org/linux/man-pages/man2/pipe.2.html
                     */
                    syncPipes = Os.pipe();
                } catch (ErrnoException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                /** Create work threads for read / write data */
                createSendingThread();
                createReceivingThread();

                if (null != sendingThread && null != receiveThread) {
                    /** Notify that RAW I/O is ready to use */
                    onRawInterfaceReady();
                }
            } else {
                Log.e(Constants.TAG, "getRawPfd PFD null ");
            }
        } catch (RemoteException e) {
            Log.e(Constants.TAG, "openRawDevice exception " + e);
        }
    }

    /**
     * Create the RAW data write stream based on the file description
     * for attached mod device.
     */
    private void createSendingThread() {
        if (sendingThread == null) {
            FileDescriptor fd = parcelFD.getFileDescriptor();
            outputStream = new FileOutputStream(fd);
            sendingThread = new HandlerThread("sendingThread");
            sendingThread.start();
            handler = new SendHandler(sendingThread.getLooper());
        }
    }

    /**
     * Create the RAW data read stream based on the file description
     * for attached mod device.
     */
    public static int MAX_BYTES = 1024;

    private void createReceivingThread() {
        if (receiveThread != null) return;
        receiveThread = new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[MAX_BYTES];
                FileDescriptor fd = parcelFD.getFileDescriptor();
                FileInputStream inputStream = new FileInputStream(fd);
                int ret = 0;
                synchronized (syncPipes) {
                    while (ret >= 0) {
                        try {
                            /** Poll on the exit pipe and the raw channel */
                            int polltype = blockRead();
                            if (polltype == POLL_TYPE_READ_DATA) {
                                ret = inputStream.read(buffer, 0, MAX_BYTES);
                                if (ret > 0) {
                                    /**  Got raw data */
                                    onRawData(buffer, ret);
                                }
                            } else if (polltype == POLL_TYPE_EXIT) {
                                break;
                            }
                        } catch (IOException e) {
                            Log.e(Constants.TAG, "IOException while reading from raw file" + e);
                            onIOException();
                        } catch (Exception e) {
                            Log.e(Constants.TAG, "Exception while reading from raw file" + e);
                            e.printStackTrace();
                        }
                    }
                    receiveThread = null;
                }
            }
        };

        receiveThread.start();
    }

    /** Read the RAW I/O input pipe, the data is written by attached mod device */
    private int blockRead() {
        /** Poll on the pipe to see whether signal to exit, or any data on raw fd to read */
        StructPollfd[] pollfds = new StructPollfd[2];

        /** readRawFd will watch whether data is available on the raw channel */
        StructPollfd readRawFd = new StructPollfd();
        pollfds[0] = readRawFd;
        readRawFd.fd = parcelFD.getFileDescriptor();
        readRawFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);

        /** syncFd will watch whether any exit signal */
        StructPollfd syncFd = new StructPollfd();
        pollfds[1] = syncFd;
        syncFd.fd = syncPipes[0];
        syncFd.events = (short) OsConstants.POLLIN;

        try {
            /** Waits for file descriptors pollfds to become ready to perform I/O */
            int ret = Os.poll(pollfds, -1);
            if (ret > 0) {
                if (syncFd.revents == OsConstants.POLLIN) {
                    /** POLLIN on the syncFd as signal to exit */
                    byte[] buffer = new byte[1];
                    new FileInputStream(syncPipes[0]).read(buffer, 0, 1);
                    return POLL_TYPE_EXIT;
                } else if ((readRawFd.revents & OsConstants.POLLHUP) != 0) {
                    /** RAW driver existing */
                    return POLL_TYPE_EXIT;
                } else if ((readRawFd.revents & OsConstants.POLLIN) != 0) {
                    /** Finally data ready to read */
                    return POLL_TYPE_READ_DATA;
                } else {
                    /** Unexcpected error */
                    Log.e(Constants.TAG, "unexpected events in blockRead rawEvents:"
                            + readRawFd.revents + " syncEvents:" + syncFd.revents);
                    return POLL_TYPE_EXIT;
                }
            } else {
                /** Error */
                Log.e(Constants.TAG, "Error in blockRead: " + ret);
            }
        } catch (ErrnoException e) {
            Log.e(Constants.TAG, "ErrnoException in blockRead: " + e);
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(Constants.TAG, "IOException in blockRead: " + e);
            e.printStackTrace();
        }
        return POLL_TYPE_EXIT;
    }
}