package com.kdb.pim.ui.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.View;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.jsibbold.zoomage.ZoomageView;
import com.kdb.pim.R;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * A full-screen activity to view images in messages, with pinch-to-zoom
 */
public class ImageActivity extends AppCompatActivity {
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.zoomageView) ZoomageView zoomageView;
    @BindView(R.id.appbar)
    AppBarLayout appBarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        ButterKnife.bind(this);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener((v) -> onBackPressed());
        appBarLayout.setPadding(0, getStatusBarHeight(), 0, 0);

        Bundle bundle = getIntent().getExtras();
        String url = bundle.getString("url");
        String name = bundle.getString("name");

        getSupportActionBar().setTitle(name);

        Glide.with(this)
                .load(url)
                .into(zoomageView);
    }

    /**
     * Utility method to get the notification/status bar height of the device
     * @return The status bar height, in px
     */
    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}
