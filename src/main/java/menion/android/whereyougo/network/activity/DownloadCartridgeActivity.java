/*
 * Copyright 2014 biylda <biylda@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package menion.android.whereyougo.network.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

import menion.android.whereyougo.R;
import menion.android.whereyougo.gui.activity.MainActivity;
import menion.android.whereyougo.gui.activity.XmlSettingsActivity;
import menion.android.whereyougo.gui.extension.activity.CustomActivity;
import menion.android.whereyougo.gui.extension.dialog.CustomDialog;
import menion.android.whereyougo.network.DownloadCartridgeTask;
import menion.android.whereyougo.preferences.Preferences;
import menion.android.whereyougo.utils.FileSystem;
import menion.android.whereyougo.utils.Images;
import menion.android.whereyougo.utils.ManagerNotify;
import menion.android.whereyougo.utils.UtilsFormat;

public class DownloadCartridgeActivity extends CustomActivity {
    private static final String TAG = "DownloadCartridgeActivity";
    private DownloadCartridgeTask downloadTask;
    private String cguid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Uri uri = getIntent().getData();
            cguid = uri.getQueryParameter("CGUID");
        } catch (Exception e) {
        }
        if (cguid == null) {
            finish();
            return;
        }

        setContentView(R.layout.layout_details);

        TextView tvName = (TextView) findViewById(R.id.layoutDetailsTextViewName);
        tvName.setText(R.string.download_cartridge);

        TextView tvDescription = (TextView) findViewById(R.id.layoutDetailsTextViewDescription);
        TextView tvState = (TextView) findViewById(R.id.layoutDetailsTextViewState);

        File cartridgeFile = FileSystem.findFile(cguid);
        if (cartridgeFile != null) {
            tvDescription.setText(
                    String.format("CGUID:\n%s\n%s",
                            cguid,
                            cartridgeFile.getName().replace(cguid + "_", "")
                    ));
            tvState.setText(
                    String.format("%s\n%s",
                            getString(R.string.download_successful),
                            UtilsFormat.formatDatetime(cartridgeFile.lastModified())
                    ));
        } else {
            tvDescription.setText(String.format("CGUID:\n%s", cguid));
        }

        ImageView ivImage = (ImageView) findViewById(R.id.mediaImageView);
        ivImage.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
        try {
            Bitmap icon = Images.getImageB(R.drawable.icon_gc_wherigo);
            ivImage.setImageBitmap(icon);
        } catch (Exception e) {
        }

        CustomDialog.setBottom(this, getString(R.string.download), new CustomDialog.OnClickListener() {
            @Override
            public boolean onClick(CustomDialog dialog, View v, int btn) {
                if (downloadTask != null && downloadTask.getStatus() != Status.FINISHED) {
                    downloadTask.cancel(true);
                    downloadTask = null;
                } else {
                    String username = Preferences.GC_USERNAME;
                    String password = Preferences.GC_PASSWORD;
                    downloadTask = new DownloadTask(DownloadCartridgeActivity.this, username, password);
                    downloadTask.execute(cguid);
                }
                return true;

            }
        }, null, null, getString(R.string.start), new CustomDialog.OnClickListener() {
            @Override
            public boolean onClick(CustomDialog dialog, View v, int btn) {
                Intent intent = new Intent(DownloadCartridgeActivity.this, MainActivity.class);
                intent.putExtra("cguid", cguid);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                DownloadCartridgeActivity.this.finish();
                return true;
            }
        });
        Button buttonDownload = (Button) findViewById(R.id.button_positive);
        Button buttonStart = (Button) findViewById(R.id.button_negative);
        buttonStart.setEnabled(cartridgeFile != null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (downloadTask != null && downloadTask.getStatus() != Status.FINISHED) {
            downloadTask.cancel(true);
            downloadTask = null;
        }
    }

    class DownloadTask extends DownloadCartridgeTask {
        final ProgressDialog progressDialog;

        public DownloadTask(final Context context, String username, String password) {
            super(context, username, password);
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(1);
            progressDialog.setIndeterminate(true);
            progressDialog.setCanceledOnTouchOutside(false);

            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {

                @Override
                public void onCancel(DialogInterface arg0) {
                    if (downloadTask != null && downloadTask.getStatus() != Status.FINISHED) {
                        downloadTask.cancel(false);
                        downloadTask = null;
                        Log.i("down", "cancel");
                        ManagerNotify.toastShortMessage(context, getString(R.string.cancelled));
                    }
                }
            });
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            super.onPostExecute(result);
            if (result) {
                progressDialog.dismiss();
                MainActivity.refreshCartridges();
                DownloadCartridgeActivity.this.finish();
                DownloadCartridgeActivity.this.startActivity(DownloadCartridgeActivity.this.getIntent());
            } else {
                progressDialog.setIndeterminate(false);
            }
            downloadTask = null;
        }

        @Override
        protected void onProgressUpdate(Progress... values) {
            super.onProgressUpdate(values);
            Progress progress = values[0];
            String suffix = "";
            if (progress.getState() == State.SUCCESS) {
                suffix = String.format(": %s", getString(R.string.ok));
            } else if (progress.getState() == State.FAIL) {
                if (progress.getMessage() == null){
                    suffix = String.format(": %s", getString(R.string.error));
                } else {
                    suffix = String.format(": %s(%s)", getString(R.string.error), progress.getMessage());
                }
            }
            switch (progress.getTask()) {
                case INIT:
                case PING:
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage(Html.fromHtml(getString(R.string.download_state_connect) + suffix));
                    break;
                case LOGIN:
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage(Html.fromHtml(getString(R.string.download_state_login) + suffix));
                    if (progress.getState() == State.FAIL) {
                        Intent loginPreferenceIntent = new Intent(DownloadCartridgeActivity.this, XmlSettingsActivity.class);
                        loginPreferenceIntent.putExtra(getString(R.string.pref_KEY_X_LOGIN_PREFERENCES), true);
                        startActivity(loginPreferenceIntent);
                    }
                    break;
                case LOGOUT:
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage(Html.fromHtml(getString(R.string.download_state_logout) + suffix));
                    break;
                case DOWNLOAD:
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage(Html.fromHtml(getString(R.string.download_state_download) + suffix));
                    break;
                case DOWNLOAD_SINGLE:
                    progressDialog.setIndeterminate(false);
                    progressDialog.setMax((int) progress.getTotal());
                    progressDialog.setProgress((int) progress.getCompleted());
                    progressDialog.setMessage(Html.fromHtml(getString(R.string.download_state_download) + suffix));
                    break;
            }
        }

    }
}

