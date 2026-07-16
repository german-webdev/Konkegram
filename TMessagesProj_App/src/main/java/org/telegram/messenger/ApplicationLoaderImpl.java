package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

public class ApplicationLoaderImpl extends ApplicationLoader {
    private KonkegramUpdater updater;

    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public boolean isCustomUpdate() {
        return "release".equals(BuildConfig.BUILD_TYPE);
    }

    @Override
    public BetaUpdate getUpdate() {
        return isCustomUpdate() ? getUpdater().getUpdate() : null;
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        if (isCustomUpdate()) {
            getUpdater().checkForUpdate(force, whenDone);
        } else if (whenDone != null) {
            whenDone.run();
        }
    }

    @Override
    public boolean wasLastUpdateCheckSuccessful() {
        return isCustomUpdate() && getUpdater().wasLastCheckSuccessful();
    }

    @Override
    public void downloadUpdate() {
        if (isCustomUpdate()) {
            getUpdater().downloadUpdate();
        }
    }

    @Override
    public void cancelDownloadingUpdate() {
        if (isCustomUpdate()) {
            getUpdater().cancelDownload();
        }
    }

    @Override
    public void remindUpdateLater() {
        if (isCustomUpdate()) {
            getUpdater().remindLater();
        }
    }

    @Override
    public boolean isDownloadingUpdate() {
        return isCustomUpdate() && getUpdater().isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return isCustomUpdate() ? getUpdater().getDownloadProgress() : 0;
    }

    @Override
    public File getDownloadedUpdateFile() {
        return isCustomUpdate() ? getUpdater().getDownloadedFile() : null;
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return isCustomUpdate() ? new UpdateLayout(activity, sideMenuContainer) : null;
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        if (!isCustomUpdate()) {
            return false;
        }
        try {
            new UpdateAppAlertDialog(context, update, account).show();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private KonkegramUpdater getUpdater() {
        if (updater == null) {
            updater = new KonkegramUpdater(
                    "stable",
                    "https://github.com/german-webdev/Konkegram/releases/latest/download/update.json");
        }
        return updater;
    }
}
