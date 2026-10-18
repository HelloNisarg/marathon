package app.stride.seven;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyActivity extends Activity {
    public static final String POLICY = "Stride · Samsung Health connection\n\n"
        + "Stride reads steps, running/walking exercise sessions and distance from Samsung Health through Android Health Connect when you tap Sync. Only the last seven days are requested.\n\n"
        + "It does not write or delete Samsung Health data. It does not request routes, GPS, heart rate or background health access.\n\n"
        + "Records stay in this app on your phone. This app has no Internet permission and no analytics. Your website records and app records are separate. Export or restore a backup to move your records. Share backup files only with people you choose.\n\n"
        + "Review the preview before importing. Steps are replaced, never added; repeated syncing does not duplicate sessions. Other daily habits and manual run logs remain intact. Synced runs take priority in the Progress totals.\n\n"
        + "You can revoke Stride's permissions in Android Settings → Health Connect → App permissions. Revoking permission stops future reads; existing imported records remain until you clear this app's storage or uninstall it. Export a backup first if you want to retain your records.\n\n"
        + "This personal-use release has not yet been verified against your Samsung phone. If permission is denied or data is unavailable, your saved logs remain unchanged. There is no subscription or paid service.";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText(POLICY); text.setTextSize(17); text.setPadding(28,40,28,40);
        ScrollView scroll = new ScrollView(this); scroll.addView(text); setContentView(scroll);
    }
}
