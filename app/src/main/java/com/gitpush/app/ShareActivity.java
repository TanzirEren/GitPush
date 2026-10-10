package com.gitpush.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;

/** Receives files shared from other apps, stages them and opens the repo picker. */
public class ShareActivity extends BaseActivity {
    @SuppressWarnings("deprecation")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())) {
            ArrayList<Uri> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        }
        if (uris.isEmpty()) Ui.toast(this, "Nothing to import");
        else st.receiveShared(uris);
        startActivity(new Intent(this, st.active == null ? LoginActivity.class : MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
