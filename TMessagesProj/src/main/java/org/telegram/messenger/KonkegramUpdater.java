package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class KonkegramUpdater {

    private static final long CHECK_INTERVAL = 12L * 60L * 60L * 1000L;
    private static final long FAILED_CHECK_INTERVAL = 30L * 60L * 1000L;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private final String channel;
    private final String manifestUrl;

    private String version;
    private int versionCode;
    private String changelog;
    private String fileUrl;
    private String sha256;
    private long fileSize;
    private String downloadedPath;
    private boolean downloadedVerified;
    private boolean resumeRequested;
    private long lastSuccessfulCheck;
    private long lastAttempt;

    private boolean checking;
    private boolean lastCheckSuccessful;
    private boolean downloading;
    private float downloadProgress;
    private UpdateDownloadTask downloadTask;

    public KonkegramUpdater(String channel, String manifestUrl) {
        this.channel = channel;
        this.manifestUrl = manifestUrl;
        load();
    }

    private SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("konkegram_updater_" + channel, Activity.MODE_PRIVATE);
    }

    private void load() {
        SharedPreferences preferences = getPreferences();
        version = preferences.getString("version", null);
        versionCode = preferences.getInt("version_code", 0);
        changelog = preferences.getString("changelog", null);
        fileUrl = preferences.getString("file_url", null);
        sha256 = preferences.getString("sha256", null);
        fileSize = preferences.getLong("file_size", 0);
        downloadedPath = preferences.getString("downloaded_path", null);
        downloadedVerified = preferences.getBoolean("downloaded_verified", false);
        resumeRequested = preferences.getBoolean("resume_requested", false);
        lastSuccessfulCheck = preferences.getLong("last_successful_check", 0);
        lastAttempt = preferences.getLong("last_attempt", 0);

        if (versionCode <= getCurrentVersionCode()) {
            clearUpdate(true);
        } else if (!TextUtils.isEmpty(downloadedPath) && !new File(downloadedPath).exists()) {
            downloadedPath = null;
            downloadedVerified = false;
            save();
        }
        if (resumeRequested && versionCode > getCurrentVersionCode() && getPartialFile().exists()) {
            AndroidUtilities.runOnUIThread(this::downloadUpdate, 1_000);
        }
    }

    private void save() {
        SharedPreferences.Editor editor = getPreferences().edit();
        putOrRemove(editor, "version", version);
        putOrRemove(editor, "changelog", changelog);
        putOrRemove(editor, "file_url", fileUrl);
        putOrRemove(editor, "sha256", sha256);
        putOrRemove(editor, "downloaded_path", downloadedPath);
        if (versionCode == 0) {
            editor.remove("version_code");
        } else {
            editor.putInt("version_code", versionCode);
        }
        if (fileSize == 0) {
            editor.remove("file_size");
        } else {
            editor.putLong("file_size", fileSize);
        }
        editor.putBoolean("downloaded_verified", downloadedVerified);
        editor.putBoolean("resume_requested", resumeRequested);
        editor.putLong("last_successful_check", lastSuccessfulCheck);
        editor.putLong("last_attempt", lastAttempt);
        editor.apply();
    }

    private static void putOrRemove(SharedPreferences.Editor editor, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }

    public void checkForUpdate(boolean force, Runnable whenDone) {
        if (checking) {
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (!force) {
            if (lastSuccessfulCheck != 0 && now - lastSuccessfulCheck < CHECK_INTERVAL) {
                lastCheckSuccessful = true;
                if (whenDone != null) {
                    whenDone.run();
                }
                return;
            }
            if (lastAttempt != 0 && now - lastAttempt < FAILED_CHECK_INTERVAL) {
                if (whenDone != null) {
                    whenDone.run();
                }
                return;
            }
        }

        checking = true;
        lastCheckSuccessful = false;
        lastAttempt = now;
        save();

        new UpdateCheckTask(whenDone).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void applyManifest(JSONObject manifest) throws Exception {
        if (manifest.optInt("schema", 0) != 1) {
            throw new IllegalArgumentException("Unsupported update manifest schema");
        }

        String newVersion = manifest.getString("version_name");
        JSONObject assets = manifest.getJSONObject("assets");
        String assetKey = selectAssetKey();
        JSONObject asset = assets.optJSONObject(assetKey);
        if (asset == null) {
            asset = assets.getJSONObject("universal");
        }
        int newVersionCode = asset.getInt("version_code");
        String newFileUrl = asset.getString("url");
        String newSha256 = asset.getString("sha256").toLowerCase(Locale.US);
        long newFileSize = asset.getLong("size");
        String newChangelog = manifest.optString("changelog", null);

        if (TextUtils.isEmpty(newVersion)
                || TextUtils.isEmpty(newFileUrl)
                || !newSha256.matches("[0-9a-f]{64}")
                || newFileSize <= 0) {
            throw new IllegalArgumentException("Invalid update manifest");
        }

        if (newVersionCode <= getCurrentVersionCode()) {
            clearUpdate(true);
            return;
        }

        boolean sameUpdate = versionCode == newVersionCode
                && TextUtils.equals(sha256, newSha256)
                && TextUtils.equals(fileUrl, newFileUrl);
        if (!sameUpdate) {
            deleteUpdateFiles();
            downloadedPath = null;
            downloadedVerified = false;
            resumeRequested = false;
        }

        version = newVersion;
        versionCode = newVersionCode;
        changelog = newChangelog;
        fileUrl = newFileUrl;
        sha256 = newSha256;
        fileSize = newFileSize;
    }

    private String selectAssetKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("arm64-v8a".equals(abi)) {
                    return "arm64-v8a";
                }
                if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) {
                    return "armeabi-v7a";
                }
            }
        }
        return "universal";
    }

    public BetaUpdate getUpdate() {
        if (TextUtils.isEmpty(version) || versionCode == 0) {
            return null;
        }
        return new BetaUpdate(version, versionCode, changelog, fileSize);
    }

    public boolean wasLastCheckSuccessful() {
        return lastCheckSuccessful;
    }

    public void downloadUpdate() {
        if (downloading || TextUtils.isEmpty(fileUrl) || TextUtils.isEmpty(sha256)) {
            return;
        }

        File completedFile = getCompletedFile();
        if (completedFile.exists()
                && downloadedVerified
                && TextUtils.equals(downloadedPath, completedFile.getAbsolutePath())
                && (fileSize <= 0 || completedFile.length() == fileSize)) {
            downloadedPath = completedFile.getAbsolutePath();
            resumeRequested = false;
            save();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            return;
        }

        File partialFile = getPartialFile();
        downloading = true;
        resumeRequested = true;
        downloadProgress = fileSize > 0 ? Math.min(1.0f, (float) partialFile.length() / fileSize) : 0;
        save();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);

        downloadTask = new UpdateDownloadTask(partialFile, fileUrl, fileSize);
        downloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void cancelDownload() {
        if (!downloading) {
            return;
        }
        if (downloadTask != null) {
            downloadTask.cancel(true);
        }
        downloadTask = null;
        downloading = false;
        resumeRequested = false;
        save();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    public void remindLater() {
        clearUpdate(true);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    public boolean isDownloading() {
        return downloading;
    }

    public float getDownloadProgress() {
        return downloadProgress;
    }

    public File getDownloadedFile() {
        if (TextUtils.isEmpty(downloadedPath)) {
            return null;
        }
        File file = new File(downloadedPath);
        if (!file.exists() || fileSize > 0 && file.length() != fileSize) {
            downloadedPath = null;
            downloadedVerified = false;
            if (file.exists()) {
                file.delete();
            }
            save();
            return null;
        }
        if (!downloadedVerified) {
            downloadedVerified = verifyApk(file);
            if (!downloadedVerified) {
                downloadedPath = null;
                file.delete();
                save();
                return null;
            }
            save();
        }
        return file;
    }

    private File getUpdateDirectory() {
        File directory = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache/updates");
        if (!directory.exists() && !directory.mkdirs()) {
            FileLog.e("Unable to create update directory " + directory);
        }
        return directory;
    }

    private File getPartialFile() {
        return new File(getUpdateDirectory(), "konkegram-update.apk.part");
    }

    private File getCompletedFile() {
        return new File(getUpdateDirectory(), "konkegram-update.apk");
    }

    private void onDownloadFinished(File partialFile) {
        downloadTask = null;
        downloading = false;

        if (partialFile != null) {
            File completedFile = getCompletedFile();
            if (completedFile.exists()) {
                completedFile.delete();
            }
            if (partialFile.renameTo(completedFile)) {
                downloadedPath = completedFile.getAbsolutePath();
                downloadedVerified = true;
                resumeRequested = false;
                downloadProgress = 1.0f;
                save();
            } else {
                FileLog.e("Unable to finalize downloaded update");
                resumeRequested = partialFile.exists();
                save();
            }
        } else {
            resumeRequested = getPartialFile().exists();
            save();
        }

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    private boolean verifyApk(File file) {
        try {
            if (!file.exists() || fileSize > 0 && file.length() != fileSize) {
                return false;
            }
            if (!sha256.equals(calculateSha256(file))) {
                FileLog.e("Konkegram update SHA-256 mismatch");
                return false;
            }

            PackageManager packageManager = ApplicationLoader.applicationContext.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo archive = packageManager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            PackageInfo installed = packageManager.getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), flags);
            if (archive == null
                    || !ApplicationLoader.applicationContext.getPackageName().equals(archive.packageName)
                    || getVersionCode(archive) != versionCode
                    || getVersionCode(archive) <= getVersionCode(installed)) {
                FileLog.e("Konkegram update package or version mismatch");
                return false;
            }

            Set<String> archiveSigners = getSignerDigests(archive);
            Set<String> installedSigners = getSignerDigests(installed);
            if (archiveSigners.isEmpty() || !archiveSigners.equals(installedSigners)) {
                FileLog.e("Konkegram update signer mismatch");
                return false;
            }
            return true;
        } catch (Exception e) {
            FileLog.e("Failed to verify Konkegram update", e);
            return false;
        }
    }

    private static Set<String> getSignerDigests(PackageInfo packageInfo) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo == null) {
                return new HashSet<>();
            }
            signatures = packageInfo.signingInfo.hasMultipleSigners()
                    ? packageInfo.signingInfo.getApkContentsSigners()
                    : packageInfo.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = packageInfo.signatures;
        }

        Set<String> result = new HashSet<>();
        if (signatures != null) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                result.add(toHex(digest.digest(signature.toByteArray())));
            }
        }
        return result;
    }

    private static String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte b : value) {
            result.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return result.toString();
    }

    private static long getVersionCode(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    private int getCurrentVersionCode() {
        try {
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return (int) getVersionCode(packageInfo);
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    private void clearUpdate(boolean deleteFiles) {
        if (deleteFiles) {
            deleteUpdateFiles();
        }
        version = null;
        versionCode = 0;
        changelog = null;
        fileUrl = null;
        sha256 = null;
        fileSize = 0;
        downloadedPath = null;
        downloadedVerified = false;
        resumeRequested = false;
        save();
    }

    private void deleteUpdateFiles() {
        File partial = getPartialFile();
        File completed = getCompletedFile();
        if (partial.exists()) {
            partial.delete();
        }
        if (completed.exists()) {
            completed.delete();
        }
    }

    private class UpdateCheckTask extends AsyncTask<Void, Void, String> {
        private final Runnable whenDone;

        private UpdateCheckTask(Runnable whenDone) {
            this.whenDone = whenDone;
        }

        @Override
        protected String doInBackground(Void... ignored) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Konkegram-Android-Updater");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Unexpected manifest HTTP status " + status);
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                return response.toString();
            } catch (Exception e) {
                FileLog.e("Failed to download Konkegram update manifest", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String response) {
            checking = false;
            int previousVersionCode = versionCode;
            try {
                if (TextUtils.isEmpty(response)) {
                    throw new IllegalStateException("Empty update manifest");
                }
                applyManifest(new JSONObject(response));
                lastSuccessfulCheck = System.currentTimeMillis();
                lastCheckSuccessful = true;
                save();
            } catch (Exception e) {
                lastCheckSuccessful = false;
                FileLog.e("Failed to check Konkegram update", e);
            }

            if (previousVersionCode != versionCode) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            }
            if (whenDone != null) {
                whenDone.run();
            }
        }
    }

    private class UpdateDownloadTask extends AsyncTask<Void, Float, File> {
        private final File target;
        private final String url;
        private final long expectedSize;

        private UpdateDownloadTask(File target, String url, long expectedSize) {
            this.target = target;
            this.url = url;
            this.expectedSize = expectedSize;
        }

        @Override
        protected File doInBackground(Void... ignored) {
            for (int attempt = 0; attempt < MAX_DOWNLOAD_ATTEMPTS && !isCancelled(); attempt++) {
                HttpURLConnection connection = null;
                try {
                    long existingSize = target.exists() ? target.length() : 0;
                    if (expectedSize > 0 && existingSize > expectedSize) {
                        target.delete();
                        existingSize = 0;
                    }

                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(15_000);
                    connection.setReadTimeout(30_000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Konkegram-Android-Updater");
                    if (existingSize > 0) {
                        connection.setRequestProperty("Range", "bytes=" + existingSize + "-");
                    }

                    int status = connection.getResponseCode();
                    boolean append = existingSize > 0 && status == HttpURLConnection.HTTP_PARTIAL;
                    if (status == 416 && expectedSize > 0 && existingSize == expectedSize) {
                        if (verifyApk(target)) {
                            return target;
                        }
                        target.delete();
                        throw new IllegalStateException("Completed partial update verification failed");
                    }
                    if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                        throw new IllegalStateException("Unexpected download HTTP status " + status);
                    }
                    if (!append) {
                        existingSize = 0;
                    }

                    try (InputStream input = new BufferedInputStream(connection.getInputStream(), 32 * 1024);
                         FileOutputStream output = new FileOutputStream(target, append)) {
                        byte[] buffer = new byte[32 * 1024];
                        long downloaded = existingSize;
                        int read;
                        while (!isCancelled() && (read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                            downloaded += read;
                            if (expectedSize > 0) {
                                publishProgress(Math.min(1.0f, (float) downloaded / expectedSize));
                            }
                        }
                        output.getFD().sync();
                    }

                    if (!isCancelled() && (expectedSize <= 0 || target.length() == expectedSize)) {
                        if (verifyApk(target)) {
                            return target;
                        }
                        target.delete();
                        throw new IllegalStateException("Downloaded update verification failed");
                    }
                    if (!isCancelled()) {
                        throw new IllegalStateException("Incomplete update download");
                    }
                } catch (Exception e) {
                    FileLog.e("Konkegram update download attempt " + (attempt + 1) + " failed", e);
                    if (attempt + 1 < MAX_DOWNLOAD_ATTEMPTS && !isCancelled()) {
                        try {
                            Thread.sleep((attempt + 1L) * 1_000L);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            if (values.length > 0) {
                downloadProgress = values[0];
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
            }
        }

        @Override
        protected void onPostExecute(File file) {
            onDownloadFinished(file);
        }

        @Override
        protected void onCancelled() {
            downloadTask = null;
            downloading = false;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }
    }
}
