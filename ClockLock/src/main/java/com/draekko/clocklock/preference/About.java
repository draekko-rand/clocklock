/*
 * Copyright (C) 2016 Benoit Touchette
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.draekko.clocklock.preference;

// ===========================================================
// ==[ CLASS ]================================================
// ===========================================================

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import com.draekko.clocklock.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidParameterException;
import java.util.Locale;

public class About extends Activity {

    private static final String TAG = "About";

    private static Activity staticActivity;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        staticActivity = this;
        String aboutText = "";
        InputStream inputStream = null;

        setTheme(R.style.transparent);
        setContentView(R.layout.about);

        try {
            String filename;
            String locale = getLocale(staticActivity);

            filename = "about-"+locale+".txt";

            try {
                inputStream = getResources().getAssets().open(filename);
            } catch (Exception e) {
                filename = "about.txt";
            }
            if (inputStream == null) {
                try {
                    inputStream = getResources().getAssets().open(filename);
                } catch (Exception e) {
                }
            }

            if (inputStream == null) {
                finish();
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte buf[] = new byte[2048];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            aboutText += outputStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String filename = "license.txt";

            try {
                inputStream = getResources().getAssets().open(filename);
            } catch (Exception e) {
                finish();
                return;
            }

            if (inputStream == null) {
                finish();
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte buf[] = new byte[2048];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            aboutText += outputStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (aboutText == null || aboutText.isEmpty()) {
            finish();
            return;
        }

        AlertDialog.Builder builder =
                new AlertDialog.Builder(staticActivity, R.style.Theme_AppAlertDialog);
        builder.setCancelable(false);
        builder.setTitle(getString(R.string.about));
        builder.setMessage(aboutText);
        builder.setPositiveButton(getString(R.string.close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                staticActivity.finish();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    public String getLocale(Context c_context) {
        if (c_context == null) {
            throw new InvalidParameterException("c_context == null");
        }

        String language = null;
        final Locale current = c_context.getResources().getConfiguration().locale;
        if (!current.getLanguage().toString().isEmpty()) {
            language = current.getLanguage().toString();
        }
        if (language == null) {
            return "en";
        }

        return language.toLowerCase(Locale.US);
    }
}
