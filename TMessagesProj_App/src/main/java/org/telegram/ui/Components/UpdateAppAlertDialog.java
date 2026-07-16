package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;

public class UpdateAppAlertDialog extends BottomSheet {

    public UpdateAppAlertDialog(Context context, BetaUpdate update, int account) {
        super(context, false);
        setCanceledOnTouchOutside(false);
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        Drawable background = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        background.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        FrameLayout container = new FrameLayout(context);
        container.setBackground(background);
        containerView = container;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, AndroidUtilities.dp(120));
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        TextView title = new TextView(context);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setText(LocaleController.getString(R.string.AppUpdate));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 23, 20, 23, 0));

        TextView version = new TextView(context);
        version.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        version.setGravity(Gravity.CENTER);
        if (update.fileSize > 0) {
            version.setText(LocaleController.formatString(
                    R.string.AppUpdateVersionAndSize,
                    update.version,
                    AndroidUtilities.formatFileSize(update.fileSize)));
        } else {
            version.setText(LocaleController.formatString(R.string.AppBetaUpdateVersion, update.version, update.versionCode));
        }
        content.addView(version, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 23, 2, 23, 4));

        TextView changelog = new TextView(context);
        changelog.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        changelog.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        changelog.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        changelog.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        CharSequence changelogText = TextUtils.isEmpty(update.changelog)
                ? AndroidUtilities.replaceTags(LocaleController.getString(R.string.AppUpdateChangelogEmpty))
                : Emoji.replaceEmoji(update.changelog, changelog.getPaint().getFontMetricsInt(), false);
        changelog.setText(changelogText);
        NotificationCenter.listenEmojiLoading(changelog);
        content.addView(changelog, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 23, 16, 23, 24));

        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        container.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM, 0, 0, 0, 116));

        ButtonWithCounterView actionButton = new ButtonWithCounterView(context, null);
        File downloadedFile = ApplicationLoader.applicationLoaderInstance.getDownloadedUpdateFile();
        if (downloadedFile != null) {
            actionButton.setText(LocaleController.getString(R.string.AppUpdateNow), false);
            actionButton.setOnClickListener(view -> {
                Activity activity = AndroidUtilities.findActivity(getContext());
                File file = ApplicationLoader.applicationLoaderInstance.getDownloadedUpdateFile();
                if (activity != null && file != null) {
                    AndroidUtilities.openForView(file, "Konkegram.apk", "application/vnd.android.package-archive", activity, null, false);
                }
                dismiss();
            });
        } else {
            actionButton.setText(LocaleController.getString(R.string.AppUpdateDownloadNow), false);
            actionButton.setOnClickListener(view -> {
                ApplicationLoader.applicationLoaderInstance.downloadUpdate();
                dismiss();
            });
        }
        container.addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 20, 0, 20, 60));

        ButtonWithCounterView laterButton = new ButtonWithCounterView(context, false, null);
        laterButton.setText(LocaleController.getString(R.string.AppUpdateRemindMeLater), false);
        laterButton.setOnClickListener(view -> {
            ApplicationLoader.applicationLoaderInstance.remindUpdateLater();
            dismiss();
        });
        container.addView(laterButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 20, 0, 20, 8));
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }
}
