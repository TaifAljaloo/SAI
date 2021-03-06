package com.aefyr.sai.installer.rooted;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.aefyr.sai.R;
import com.aefyr.sai.installer.SAIPackageInstaller;
import com.aefyr.sai.utils.Root;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootedSAIPackageInstaller extends SAIPackageInstaller {
    private static final String TAG = "RootedSAIPI";

    @SuppressLint("StaticFieldLeak")//This is application context, lul
    private static RootedSAIPackageInstaller sInstance;
    private Root mSu;

    public static RootedSAIPackageInstaller getInstance(Context c) {
        return sInstance != null ? sInstance : new RootedSAIPackageInstaller(c);
    }

    private RootedSAIPackageInstaller(Context c) {
        super(c);
        mSu = new Root();
        sInstance = this;
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void installApkFiles(List<File> apkFiles) {
        try {
            if (mSu.isTerminated() || !mSu.isAcquired()) {
                mSu = new Root();
                if (!mSu.isAcquired()) {
                    //I don't know if this can even happen, because InstallerViewModel calls PackageInstallerProvider.getInstaller, which checks root access and returns correct installer in response, before every installation
                    dispatchCurrentSessionUpdate(InstallationStatus.INSTALLATION_FAILED, getContext().getString(R.string.installer_error_root, getContext().getString(R.string.installer_error_root_no_root)));
                    installationCompleted();
                    return;
                }
            }

            int totalSize = 0;
            for (File apkFile : apkFiles)
                totalSize += apkFile.length();

            String result = ensureCommandSucceeded(mSu.exec(String.format("pm install-create -r -S %d", totalSize)));
            Pattern sessionIdPattern = Pattern.compile("(\\d+)");
            Matcher sessionIdMatcher = sessionIdPattern.matcher(result);
            sessionIdMatcher.find();
            int sessionId = Integer.parseInt(sessionIdMatcher.group(1));

            for (File apkFile : apkFiles)
                ensureCommandSucceeded(mSu.exec(String.format("cat \"%s\" | pm install-write -S %d %d \"%s\"", apkFile.getAbsolutePath(), apkFile.length(), sessionId, apkFile.getName())));

            result = ensureCommandSucceeded(mSu.exec(String.format("pm install-commit %d ", sessionId)));
            if (result.toLowerCase().contains("success"))
                dispatchCurrentSessionUpdate(InstallationStatus.INSTALLATION_SUCCEED, getPackageNameFromApk(apkFiles));
            else
                dispatchCurrentSessionUpdate(InstallationStatus.INSTALLATION_FAILED, getContext().getString(R.string.installer_error_root, result));

            installationCompleted();
        } catch (Exception e) {
            Log.w(TAG, e);
            dispatchCurrentSessionUpdate(InstallationStatus.INSTALLATION_FAILED, getContext().getString(R.string.installer_error_root, e.getMessage()));
            installationCompleted();
        }
    }

    private String ensureCommandSucceeded(String result) {
        if (result == null || result.length() == 0)
            throw new RuntimeException(mSu.readError());
        return result;
    }

    private String getPackageNameFromApk(List<File> apkFiles) {
        for (File apkFile : apkFiles) {
            PackageInfo packageInfo = getContext().getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (packageInfo != null)
                return packageInfo.packageName;
        }
        return "null";
    }
}
