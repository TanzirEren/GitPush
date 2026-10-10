package com.gitpush.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Tiny avatar loader with an in-memory cache. */
public final class ImageLoader {
    private ImageLoader() {}
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(40);

    public static void circle(ImageView iv, String url, int sizeDp) {
        iv.setBackgroundResource(R.drawable.bg_circle);
        iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        iv.setClipToOutline(true);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int px = Ui.dp(iv.getContext(), sizeDp);
        iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        iv.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;
        iv.setTag(url);
        Bitmap b = CACHE.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        final GitPushApp app = GitPushApp.get();
        app.io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    final Bitmap bm = BitmapFactory.decodeStream(in);
                    if (bm != null) {
                        CACHE.put(url, bm);
                        app.main.post(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); });
                    }
                }
            } catch (Exception ignored) { }
        });
    }
}
