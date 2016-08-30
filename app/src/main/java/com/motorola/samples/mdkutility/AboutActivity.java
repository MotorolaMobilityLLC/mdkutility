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

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toolbar;

import com.motorola.mod.ModManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class to represent About Activity view.
 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        String version = BuildConfig.VERSION_NAME;
        String uid = getString(R.string.na);
        if (getIntent() != null) {
            uid = getIntent().getStringExtra(MainActivity.MOD_UID);
        }

        int required = getResources().getInteger(R.integer.moto_mod_services_version) / 1000;
        String content = String.format(getString(R.string.about_content),
                version, android.os.Build.SERIAL,
                uid != null ? uid : getString(R.string.na),
                String.format(getString(R.string.version_main_minor),
                        required / 100, required % 100));

        TextView tvContent = (TextView) findViewById(R.id.about_content);
        tvContent.setText(content);

        /**
         * Verifies that the Moto Mod service is installed and enabled on this to determine
         * whether the device supports Moto Mods or not. Status can be one of the following
         * values: SUCCESS, SERVICE_MISSING, SERVICE_UPDATING, SERVICE_VERSION_UPDATE_REQUIRED,
         * SERVICE_DISABLED, SERVICE_INVALID.
         */
        int service = ModManager.isModServicesAvailable(this);
        String status;
        switch (service) {
            case ModManager.SUCCESS:
                status = "";
                break;
            case ModManager.SERVICE_MISSING:
                status = getString(R.string.SERVICE_MISSING);
                break;
            case ModManager.SERVICE_UPDATING:
                status = getString(R.string.SERVICE_UPDATING);
                break;
            case ModManager.SERVICE_VERSION_UPDATE_REQUIRED:
                status = getString(R.string.SERVICE_VERSION_UPDATE_REQUIRED);
                break;
            case ModManager.SERVICE_DISABLED:
                status = getString(R.string.SERVICE_DISABLED);
                break;
            case ModManager.SERVICE_INVALID:
                status = getString(R.string.SERVICE_INVALID);
                break;
            default:
                status = getString(R.string.na);
                break;
        }

        /**
         * Get the SDK version supported by the Phone's core platform and
         * the SDK version supported by the ModService that is installed on the phone.
         */
        content = String.format(getString(R.string.about_service),
                ModManager.getModPlatformSDKVersion() / 100,
                ModManager.getModPlatformSDKVersion() % 100,
                ModManager.getModSdkVersion(this) / 100,
                ModManager.getModSdkVersion(this) % 100,
                status);

        TextView tvService = (TextView) findViewById(R.id.about_service);
        tvService.setText(content);

        TextView tvLicense = (TextView) findViewById(R.id.license_notice);
        tvLicense.setText(Html.fromHtml(loadHtml()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Load html file content from raw resource file
     */
    private String loadHtml() {
        InputStream inputStream = getResources().openRawResource(R.raw.notice_html);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            int i = inputStream.read();
            while (i != -1) {
                byteArrayOutputStream.write(i);
                i = inputStream.read();
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toString();
    }
}
