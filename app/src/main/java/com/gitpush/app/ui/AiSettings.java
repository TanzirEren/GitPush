package com.gitpush.app.ui;

import android.app.Activity;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.R;
import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.Store;

/** Dialog to configure the AI provider (key is stored encrypted on the device). */
public final class AiSettings {
    private AiSettings() {}

    private static String type;

    public static void show(final Activity a, final Runnable onSaved) {
        final Store store = GitPushApp.get().state.store;
        type = store.aiProvider();
        LinearLayout box = Ui.vbox(a);
        final TextView provider = Ui.outlineButton(a, "", null);
        String cur = "Custom (OpenAI-compatible)";
        for (String[] p : AiClient.PRESETS) if (p[2].equals(store.aiBase())) cur = p[0];
        provider.setText("Provider: " + cur + "  ▾");
        final EditText base = Ui.input(a, "Base URL", store.aiBase());
        final EditText model = Ui.input(a, "Model", store.aiModel());
        final EditText key = Ui.input(a, "API key", store.aiKey());
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setTransformationMethod(PasswordTransformationMethod.getInstance());
        base.setSingleLine(true); model.setSingleLine(true); key.setSingleLine(true);
        provider.setOnClickListener(v -> {
            String[] names = new String[AiClient.PRESETS.length];
            for (int i = 0; i < names.length; i++) names[i] = AiClient.PRESETS[i][0];
            Ui.pickList(a, "AI provider", names, i -> {
                String[] p = AiClient.PRESETS[i];
                type = p[1];
                provider.setText("Provider: " + p[0] + "  ▾");
                if (!p[2].isEmpty()) { base.setText(p[2]); model.setText(p[3]); }
            });
        });
        box.addView(provider, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(base, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(model, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(key, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(Ui.muted(a, "Bring your own key. It is stored encrypted on this device and sent only to the provider you choose. "
                + "File names and a few small files of the repo are sent when you generate content."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        new GlassDialog(a).setTitle("AI assistant").setIcon("✨").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    store.aiConfig(type, base.getText().toString().trim(), model.getText().toString().trim(), key.getText().toString().trim());
                    Ui.toast(a, "AI settings saved");
                    if (onSaved != null) onSaved.run();
                }).setNegativeButton("Cancel", null).show();
    }
}
