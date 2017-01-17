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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.system.OsConstants;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.motorola.mod.ModDevice;
import com.motorola.mod.ModManager;
import com.motorola.samples.mdkutility.raw.RawPersonalityService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A class to represent the main activity.
 */
public class MainActivity extends Activity implements View.OnClickListener {
    public static final String MOD_UID = "mod_uid";

    private static final int REQUEST_SELECT_FIRMWARE = 120;
    private static final int REQUEST_RAW_PERMISSION = 121;

    /**
     * Instance of MDK Personality Card interface
     */
    private FirmwarePersonality fwPersonality;
    private List<Uri> pendingFirmware = null;
    private boolean performUpdate = false;

    /**
     * Instance of MDK Personality Card interface
     */
    private RawPersonalityService rawService;

    /**
     * Handler for events from mod device
     */
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Personality.MSG_MOD_DEVICE:
                    /** Mod attach/detach */
                    ModDevice device = fwPersonality.getModDevice();
                    onModDevice(device);
                    break;
                case Personality.MSG_UPDATE_START:
                    /** Mod firmware update started */
                    Toast.makeText(MainActivity.this,
                            getString(R.string.firmware_update_started), Toast.LENGTH_SHORT).show();
                    break;
                case Personality.MSG_UPDATE_DONE:
                    /** Mod firmware update finished */
                    int result = msg.arg1;

                    if (result == 1) {
                        /** User cancelled update */
                        break;
                    }

                    if (result == 11) {
                        /** Update in queue */
                        break;
                    }

                    if (result == FirmwarePersonality.FIRMWARE_UPDATE_SUCCESS) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.firmware_update_succeed), Toast.LENGTH_SHORT).show();
                    } else {
                        if (OsConstants.errnoName(result) != null) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.firmware_update_failed)
                                            + " - " + OsConstants.errnoName(result),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.firmware_update_failed)
                                            + " - " + result,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                case Personality.MSG_RAW_REQUEST_PERMISSION:
                    /** Request user to grant RAW protocol permission */
                    requestPermissions(new String[]{ModManager.PERMISSION_USE_RAW_PROTOCOL},
                            REQUEST_RAW_PERMISSION);
                    break;
                case Personality.MSG_REQUEST_FIRMWARE:
                    /** The mod request firmware due to missing or invalid firmware */
                        /** Exit activity when ModManager is trying to reload firmware */
                        finish();
                    break;
                case RawPersonalityService.BLINKY_STATUS:
                    /** The LED light status is changed */
                    Switch led = (Switch) findViewById(R.id.switch_led);
                    if (led != null) {
                        if ((rawService != null) &&
                                rawService.isRawInterfaceReady()) {
                            led.setChecked(rawService.isBlinking());
                            led.setEnabled(true);
                        } else {
                            led.setEnabled(false);
                            led.setChecked(false);
                        }
                    }
                    break;
                case RawPersonalityService.EXIT_APP:
                    /** Exit main activity UI */
                    finish();
                    break;
                default:
                    Log.i(Constants.TAG, "MainActivity - Un-handle events: " + msg.what);
                    break;
            }
        }
    };

    /**
     * Bind to the background service
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            rawService = ((RawPersonalityService.LocalBinder) service).getService();
            if (rawService != null) {
                rawService.registerListener(handler);
                rawService.checkRawInterface();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            rawService = null;
            Switch led = (Switch) findViewById(R.id.switch_led);
            if (led != null) {
                led.setEnabled(false);
                led.setChecked(false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        LinearLayout dipTitle = (LinearLayout)findViewById(R.id.layout_dip_description_title);
        dipTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LinearLayout dipDescription = (LinearLayout)findViewById(R.id.layout_dip_description);
                ImageView imgExpand = (ImageView)findViewById(R.id.imageview_description_img);

                if (dipDescription.getVisibility() == View.GONE) {
                    dipDescription.setVisibility(View.VISIBLE);
                    imgExpand.setImageResource(R.drawable.ic_expand_less);
                } else {
                    dipDescription.setVisibility(View.GONE);
                    imgExpand.setImageResource(R.drawable.ic_expand_more);
                }

                dipDescription.setPivotY(0);
                ObjectAnimator.ofFloat(dipDescription, "scaleY", 0f, 1f).setDuration(300).start();
            }
        });

        Switch switcher = (Switch) findViewById(R.id.switch_led);
        switcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Intent serviceIntent = new Intent(MainActivity.this, RawPersonalityService.class);
                if (isChecked) {
                    serviceIntent.putExtra(RawPersonalityService.BLINKY,
                            RawPersonalityService.BLINKY_ON);
                } else {
                    serviceIntent.putExtra(RawPersonalityService.BLINKY,
                            RawPersonalityService.BLINKY_OFF);
                }
                /** Call RawPersonalityService to toggle LED */
                startService(serviceIntent);
            }
        });

        TextView textView = (TextView) findViewById(R.id.mod_external_dev_portal);
        if (textView != null) {
            textView.setOnClickListener(this);
        }

        textView = (TextView) findViewById(R.id.mod_external_source_code);
        if (textView != null) {
            textView.setOnClickListener(this);
        }

        Button button = (Button) findViewById(R.id.firmware_update_select_file);
        if (button != null) {
            button.setOnClickListener(this);
        }

        button = (Button) findViewById(R.id.firmware_update_perform);
        if (button != null) {
            button.setOnClickListener(this);
        }

        /** Start background service to check LED light status */
        Intent serviceIntent = new Intent(MainActivity.this, RawPersonalityService.class);
        startService(serviceIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        releasePersonality();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        initPersonality();
    }

    private void initPersonality() {
        if (null == fwPersonality) {
            fwPersonality = new FirmwarePersonality(this);

            /** Register handler to get event and data update */
            fwPersonality.registerListener(handler);
        }

        if (null == rawService) {
            bindService(new Intent(this,
                    RawPersonalityService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void releasePersonality() {
        if (null != fwPersonality) {
            fwPersonality.onDestroy();
            fwPersonality = null;
        }

        if (null != rawService) {
            unbindService(mConnection);
            rawService = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_about) {
            /** Get the UUID from attached mod device */
            String uid = getString(R.string.na);
            if (fwPersonality != null
                    && fwPersonality.getModDevice() != null
                    && fwPersonality.getModDevice().getUniqueId() != null) {
                uid = fwPersonality.getModDevice().getUniqueId().toString();
            }
            startActivity(new Intent(this, AboutActivity.class).putExtra(MOD_UID, uid));
            return true;
        }

        if (id == R.id.action_policy) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.URL_PRIVACY_POLICY)));
        }

        return super.onOptionsItemSelected(item);
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + "/Download/");
        intent.setDataAndType(uri, "*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_SELECT_FIRMWARE);
    }

    /**
     * Button click event from UI
     */
    @Override
    public void onClick(View v) {
        if (v == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.mod_external_dev_portal:
                /** The Developer Portal link is clicked */
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.URL_DEV_PORTAL)));
                break;
            case R.id.mod_external_source_code:
                /** The accessing source code link is clicked */
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.URL_SOURCE_CODE)));
                break;
            case R.id.firmware_update_select_file:
                /** The Select Firmware File link is clicked */
                if (null == fwPersonality.getModDevice()) {
                    Toast.makeText(this, getString(R.string.mod_not_ready),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                /** Only allow firmware flashing when mod VID is VID_DEVELOPER */
                if (fwPersonality.getModDevice().getVendorId() != Constants.VID_DEVELOPER) {
                    showAlert(getString(R.string.mod_not_0x42));
                    return;
                }

                selectFile();
                break;
            case R.id.firmware_update_perform:
                /** The Perform Firmware Update button is clicked */
                if (null == fwPersonality.getModDevice()) {
                    Toast.makeText(this, getString(R.string.mod_not_ready),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                /** Only allow firmware flashing when mod VID is VID_DEVELOPER */
                if (fwPersonality.getModDevice().getVendorId() != Constants.VID_DEVELOPER) {
                    showAlert(getString(R.string.mod_not_0x42));
                    return;
                }

                if (null == pendingFirmware
                        || pendingFirmware.size() == 0) {
                    performUpdate = true;
                    Toast.makeText(this, getString(R.string.select_files),
                            Toast.LENGTH_SHORT).show();
                    selectFile();
                    return;
                }

                /** Send request to ModManager to flash firmware files */
                int result = fwPersonality.performUpdate(this, pendingFirmware);
                if (result != FirmwarePersonality.FIRMWARE_UPDATE_SUCCESS) {
                    showFirmwareUpdateFailure(result);
                }
                break;
            default:
                Log.e(Constants.TAG, "MainActivity onClick is not handled: " + v.getId());
                break;
        }
    }

    /**
     * ModManager:requestUpdateFirmware() returned failed
     */
    private void showFirmwareUpdateFailure(int result) {
        String reason = getString(R.string.firmware_update_failure);
        switch (result) {
            case FirmwarePersonality.FIRMWARE_UPDATE_ILLEGAL_EXCEPTION:
                reason = getString(R.string.firmware_update_failed)
                    + getString(R.string.firmware_illegal_argument_exception);
                break;
            case FirmwarePersonality.FIRMWARE_UPDATE_SECURITY_EXCEPTION:
                reason = getString(R.string.firmware_update_failed)
                    + getString(R.string.firmware_security_exception);
                break;
        }
        Toast.makeText(MainActivity.this, reason, Toast.LENGTH_SHORT).show();
    }

    /**
     * Pre Android 6.0 permission police, need check and ask granting permissions
     */
    public void checkRawPermission() {
        if (checkSelfPermission(ModManager.PERMISSION_USE_RAW_PROTOCOL)
                != PackageManager.PERMISSION_GRANTED) {
            handler.sendEmptyMessage(Personality.MSG_RAW_REQUEST_PERMISSION);
        }
    }

    /**
     * Handler the permission requesting result
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RAW_PERMISSION
                && grantResults != null && grantResults.length > 0) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent serviceIntent = new Intent(this, RawPersonalityService.class);
                startService(serviceIntent);
            } else {
                // TODO: User declined for RAW accessing permission.
                // You may need pop up a description dialog or other prompts to explain
                // the app cannot work without the permission granted.

                /** Disable LED control as no RAW permission, to write control command to it */
                Switch led = (Switch) findViewById(R.id.switch_led);
                if (led != null) {
                    led.setEnabled(false);
                    led.setChecked(false);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        /** Got selected files */
        if (requestCode == REQUEST_SELECT_FIRMWARE) {
            if (data != null) {
                Uri uri = data.getData();
                ClipData clip = data.getClipData();
                if (uri != null) {
                    pendingFirmware = new ArrayList<>();
                    pendingFirmware.add(uri);
                } else if (clip != null) {
                    pendingFirmware = new ArrayList<>();
                    int count = clip.getItemCount();
                    for (int i = 0; i < count; i++) {
                        pendingFirmware.add(clip.getItemAt(i).getUri());
                    }
                } else {
                    Log.e(Constants.TAG, "No file was selected.");
                    return;
                }

                if (null != pendingFirmware
                        && pendingFirmware.size() != 0) {

                    /** Get selected file names */
                    boolean start = true;
                    StringBuilder sb = new StringBuilder();
                    for (Uri u : pendingFirmware) {
                        String name = new File(u.getPath()).getName();
                        name = name.substring(name.indexOf(':') + 1);
                        if (!start) {
                            sb.append("\n");
                        }
                        sb.append(name);
                        start = false;
                    }

                    /** Update selected files on UI */
                    TextView tv = (TextView) findViewById(R.id.firmware_update_file);
                    tv.setText(sb.toString());

                    /** Start firmware update */
                    if (null != fwPersonality.getModDevice()
                            && null != pendingFirmware
                            && pendingFirmware.size() != 0
                            && performUpdate) {
                        performUpdate = false;
                        int result = fwPersonality.performUpdate(this, pendingFirmware);
                        if (result != FirmwarePersonality.FIRMWARE_UPDATE_SUCCESS) {
                            showFirmwareUpdateFailure(result);
                        }
                    }
                }
            }
        }
    }

    /**
     * Mod device attach/detach
     */
    public void onModDevice(ModDevice device) {
        /** Request RAW permission for Blinky Personality Card, to create RAW I/O */
        if (device != null) {
            if ((device.getVendorId() == Constants.VID_MDK
                    && device.getProductId() == Constants.PID_BLINKY)
                    || device.getVendorId() == Constants.VID_DEVELOPER) {
                checkRawPermission();
            }
        }

        /** Moto Mods Status */
        /**
         * Get mod device's Product String, which should correspond to
         * the product name or the vendor internal's name.
         */
        TextView tvName = (TextView) findViewById(R.id.mod_name);
        if (null != tvName) {
            tvName.setTextColor(getColor(R.color.mod_mismatch));
            if (null != device) {
                tvName.setText(device.getProductString());

                if ((device.getVendorId() == Constants.VID_MDK
                        && device.getProductId() == Constants.PID_BLINKY)
                        || device.getVendorId() == Constants.VID_DEVELOPER) {
                    tvName.setTextColor(getColor(R.color.mod_match));
                }
            } else {
                tvName.setText(getString(R.string.na));
            }
        }

        /**
         * Get mod device's Vendor ID. This is assigned by the Motorola
         * and unique for each vendor.
         */
        TextView tvVid = (TextView) findViewById(R.id.mod_status_vid);
        if (null != tvVid) {
            if (device == null
                    || device.getVendorId() == Constants.INVALID_ID) {
                tvVid.setText(getString(R.string.na));
            } else {
                tvVid.setText(String.format(getString(R.string.mod_pid_vid_format),
                        device.getVendorId()));
            }
        }

        /** Get mod device's Product ID. This is assigned by the vendor */
        TextView tvPid = (TextView) findViewById(R.id.mod_status_pid);
        if (null != tvPid) {
            if (device == null
                    || device.getProductId() == Constants.INVALID_ID) {
                tvPid.setText(getString(R.string.na));
            } else {
                tvPid.setText(String.format(getString(R.string.mod_pid_vid_format),
                        device.getProductId()));
            }
        }

        /** Get mod device's version of the firmware */
        TextView tvFirmware = (TextView) findViewById(R.id.mod_status_firmware);
        if (null != tvFirmware) {
            if (null != device && null != device.getFirmwareVersion()
                    && !device.getFirmwareVersion().isEmpty()) {
                tvFirmware.setText(device.getFirmwareVersion());
            } else {
                tvFirmware.setText(getString(R.string.na));
            }
        }

        /**
         * Get the default Android application associated with the currently attached mod,
         * as read from the mod hardware manifest.
         */
        TextView tvPackage = (TextView) findViewById(R.id.mod_status_package_name);
        if (null != tvPackage) {
            if (device == null
                    || fwPersonality.getModManager() == null) {
                tvPackage.setText(getString(R.string.na));
            } else {
                if (fwPersonality.getModManager() != null) {
                    String modPackage = fwPersonality.getModManager().getDefaultModPackage(device);
                    if (null == modPackage || modPackage.isEmpty()) {
                        modPackage = getString(R.string.name_default);
                    }
                    tvPackage.setText(modPackage);
                }
            }
        }

        /** Firmware Update */
        /** Show/hide the unable to flash reason */
        TextView tvUReason = (TextView) findViewById(R.id.no_update_reason);
        if (tvUReason != null) {
            if ((device == null) ||
                    (device.getVendorId() == Constants.VID_DEVELOPER)) {
                tvUReason.setVisibility(View.GONE);
            } else {
                tvUReason.setVisibility(View.VISIBLE);
            }
        }
        /** Update Firmware Files/Update button status */
        Button btFiles = (Button) findViewById(R.id.firmware_update_select_file);
        if (btFiles != null) {
            if ((device == null) ||
                    (device.getVendorId() != Constants.VID_DEVELOPER)) {
                btFiles.setEnabled(false);
            } else {
                btFiles.setEnabled(true);
            }
        }
        Button btFlash = (Button) findViewById(R.id.firmware_update_perform);
        if (btFlash != null) {
            if ((device == null) ||
                    (device.getVendorId() != Constants.VID_DEVELOPER)) {
                btFlash.setEnabled(false);
            } else {
                btFlash.setEnabled(true);
            }
        }

        /** LED Controls */
        /** Update LED swith button status */
        Switch led = (Switch) findViewById(R.id.switch_led);
        if (led != null) {
            if ((device == null) || (rawService == null)) {
                led.setEnabled(false);
                led.setChecked(false);
            } else {
                led.setChecked(rawService.isBlinking());
                led.setEnabled(true);
            }
        }
    }

    /**
     * Check current mod whether in developer mode
     */
    private boolean isMDKMod(ModDevice device) {
        if (device == null) {
            /** Mod is not available */
            return false;
        } else if (device.getVendorId() == Constants.VID_DEVELOPER) {
            // MDK in developer mode
            return true;
        } else {
            // Check MDK
            return device.getVendorId() == Constants.VID_MDK;
        }
    }

    /**
     * Check current mod whether in developer mode
     */
    private int getModMode(ModDevice device) {
        if (device == null) {
            return Constants.INVALID_ID;
        }

        if (device.getVendorId() == Constants.VID_DEVELOPER) {
            return Constants.MDK_MOD_DEVELOPER;
        } else if (isMDKMod(device)) {
            return Constants.MDK_MOD_EXAMPLE;
        } else {
            return Constants.INVALID_ID;
        }
    }

    /**
     * Get currently mod mode
     */
    private String getModModeName(ModDevice device) {
        if (device == null) {
            return getString(R.string.na);
        }

        if (device.getVendorId() == Constants.VID_DEVELOPER) {
            return getString(R.string.developer_mode);
        } else if (isMDKMod(device)) {
            return getString(R.string.example_mode);
        } else {
            return getString(R.string.no_mdk);
        }
    }


    private void showAlert(String content) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(content);
        alert.setPositiveButton(android.R.string.ok, null);
        alert.show();
    }
}