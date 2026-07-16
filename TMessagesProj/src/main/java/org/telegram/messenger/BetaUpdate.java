package org.telegram.messenger;

import androidx.annotation.Nullable;

public class BetaUpdate {

    public final String version;
    public final int versionCode;

    @Nullable
    public final String changelog;

    public final long fileSize;

    public BetaUpdate(String version, int versionCode, String changelog) {
        this(version, versionCode, changelog, 0);
    }

    public BetaUpdate(String version, int versionCode, String changelog, long fileSize) {
        this.version = version;
        this.versionCode = versionCode;
        this.changelog = changelog;
        this.fileSize = fileSize;
    }

    public boolean higherThan(BetaUpdate update) {
        return update == null || SharedConfig.versionBiggerOrEqual(version, update.version) && versionCode > update.versionCode;
    }

}
