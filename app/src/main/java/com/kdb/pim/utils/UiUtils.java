package com.kdb.pim.utils;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

public class UiUtils {
    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     * @param progressBar The {@link ProgressBar} (loading bar) to show in the loading state
     * @param loadingFrame The {@link FrameLayout} greyed-out overlay to show in the loading state
     * @param window The {@link Window} to set as not touchable while in loading state
     */
    public static void setLoading(boolean loading, ProgressBar progressBar, FrameLayout loadingFrame, @Nullable  Window window) {
        if (loading) {
            loadingFrame.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            if (window != null) window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        } else {
            loadingFrame.setVisibility(View.GONE);
            if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            progressBar.setVisibility(View.GONE);
        }
    }
}
