package com.ikp.dev.app.core.update;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.ikp.dev.app.BuildConfig;
import com.ikp.dev.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Custom In-App Auto-Updater.
 * Queries the backend for new versions, prompts the user, downloads,
 * and launches the package installer via FileProvider.
 */
public final class AppUpdateManager {

    private static final String TAG = "AppUpdateManager";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    public static class VersionInfo {
        @SerializedName("latestVersionCode")
        public int latestVersionCode;

        @SerializedName("latestVersionName")
        public String latestVersionName;

        @SerializedName("minSupportedVersionCode")
        public int minSupportedVersionCode;

        @SerializedName("apkUrl")
        public String apkUrl;

        @SerializedName("releaseNotes")
        public String releaseNotes;

        @SerializedName("forceUpdate")
        public boolean forceUpdate;
    }

    private AppUpdateManager() {}

    /**
     * Checks for app updates.
     * @param activity Hosting activity
     * @param userInitiated If true, shows a toast if already on the latest version.
     */
    public static void checkForUpdate(Activity activity, boolean userInitiated) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String baseUrl = BuildConfig.BASE_URL;
                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }
                String updateUrl = baseUrl + "api/v1/app/version";
                Log.d(TAG, "Checking for update at: " + updateUrl);

                Request request = new Request.Builder()
                        .url(updateUrl)
                        .header("Accept", "application/json")
                        .build();

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject root = new Gson().fromJson(json, JsonObject.class);
                        JsonObject data = root.has("data") ? root.getAsJsonObject("data") : root;
                        VersionInfo info = new Gson().fromJson(data, VersionInfo.class);

                        if (info != null && info.latestVersionCode > BuildConfig.VERSION_CODE) {
                            MAIN_HANDLER.post(() -> showUpdateDialog(activity, info));
                            return;
                        }
                    }
                }

                if (userInitiated) {
                    MAIN_HANDLER.post(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Toast.makeText(activity, R.string.update_already_latest, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Update check failed: " + e.getMessage());
                if (userInitiated) {
                    MAIN_HANDLER.post(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Toast.makeText(activity, R.string.update_already_latest, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private static void showUpdateDialog(Activity activity, VersionInfo info) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String msg = String.format(activity.getString(R.string.update_available_msg),
                info.latestVersionName != null ? info.latestVersionName : String.valueOf(info.latestVersionCode));

        if (info.releaseNotes != null && !info.releaseNotes.isEmpty()) {
            msg += "\n\n" + info.releaseNotes;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(msg)
                .setCancelable(!info.forceUpdate)
                .setPositiveButton(R.string.update_btn_now, (dialog, which) -> {
                    startDownloadAndInstall(activity, info.apkUrl, info.latestVersionName);
                });

        if (!info.forceUpdate) {
            builder.setNegativeButton(R.string.update_btn_later, null);
        }

        builder.show();
    }

    public static void startDownloadAndInstall(Activity activity, String apkUrl, String versionName) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Check install permission first on Android 8+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.update_permission_title)
                        .setMessage(R.string.update_permission_msg)
                        .setPositiveButton(R.string.update_permission_btn, (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton(R.string.update_btn_later, null)
                        .show();
                return;
            }
        }

        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(R.string.update_downloading));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Request request = new Request.Builder().url(apkUrl).build();
                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new Exception("HTTP " + response.code());
                    }

                    long contentLength = response.body().contentLength();
                    File dir = activity.getExternalFilesDir("updates");
                    if (dir == null) {
                        dir = activity.getCacheDir();
                    }
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File apkFile = new File(dir, "The-Islam-update.apk");

                    try (InputStream is = response.body().byteStream();
                         OutputStream os = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[8192];
                        long totalRead = 0;
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                            totalRead += read;
                            if (contentLength > 0) {
                                int progress = (int) ((totalRead * 100) / contentLength);
                                MAIN_HANDLER.post(() -> progressDialog.setProgress(progress));
                            }
                        }
                        os.flush();
                    }

                    MAIN_HANDLER.post(() -> {
                        progressDialog.dismiss();
                        installApk(activity, apkFile);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                MAIN_HANDLER.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public static void installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install failed", e);
            Toast.makeText(context, "Could not open installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
