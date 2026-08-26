package com.example.msag1reader;

import android.app.Activity;
import android.graphics.Typeface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfcAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView componentText;
    private TextView serialText;
    private TextView componentCodeText;
    private TextView typeCodeText;
    private TextView tagText;
    private TextView noteText;
    private TextView rawText;
    private Button rawButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            statusText.setText("This phone does not have NFC hardware.");
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            statusText.setText("NFC is turned off.");
            Button enableButton = new Button(this);
            enableButton.setText("Open NFC settings");
            ((LinearLayout) statusText.getParent()).addView(enableButton);
            enableButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            Bundle options = new Bundle();
            nfcAdapter.enableReaderMode(
                    this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    options
            );
            if (componentText.getText().length() == 0) {
                statusText.setText("Ready to scan\nHold the top/back of the phone against an MSA RFID tag.");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        NfcV nfcV = NfcV.get(tag);
        if (nfcV == null) {
            mainHandler.post(() -> statusText.setText("Tag detected, but it is not an ISO 15693 / NFC-V tag."));
            return;
        }

        try {
            Map<Integer, byte[]> blocks = readBlocks(nfcV);
            MsaDecoder.Result decoded = MsaDecoder.decode(tag.getId(), blocks);
            String raw = buildRawDump(blocks);
            mainHandler.post(() -> showResult(decoded, raw));
        } catch (Exception ex) {
            mainHandler.post(() -> {
                statusText.setText("Tag detected, but memory could not be read. Keep the phone in place and try again.");
                Toast.makeText(this, ex.getMessage() == null ? "NFC read error" : ex.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private Map<Integer, byte[]> readBlocks(NfcV nfcV) throws IOException {
        Map<Integer, byte[]> blocks = new LinkedHashMap<>();
        nfcV.connect();
        try {
            nfcV.setTimeout(1500);
            // ICODE SLIX used in the observed MSA tags has 28 x 4-byte user blocks.
            // Read each block independently so a marginal scan can still return useful data.
            for (int block = 0; block <= 0x1B; block++) {
                try {
                    byte[] command = new byte[] {
                            0x02,       // high data rate, non-addressed
                            0x20,       // ISO 15693 Read Single Block
                            (byte) block
                    };
                    byte[] response = nfcV.transceive(command);
                    if (response != null && response.length >= 5 && (response[0] & 0x01) == 0) {
                        byte[] data = new byte[4];
                        System.arraycopy(response, 1, data, 0, 4);
                        blocks.put(block, data);
                    }
                } catch (IOException ignored) {
                    // Continue; movement or a protected/out-of-range block should not discard the scan.
                }
            }
        } finally {
            try { nfcV.close(); } catch (IOException ignored) {}
        }
        return blocks;
    }

    private void showResult(MsaDecoder.Result result, String raw) {
        statusText.setText("Scan complete — ready for another tag");
        componentText.setText(result.componentName);
        serialText.setText(result.rfidSerial);
        componentCodeText.setText(emptyAsUnknown(result.componentCode));
        typeCodeText.setText(emptyAsUnknown(result.typeCode));
        tagText.setText(result.tagTechnology);
        noteText.setText(result.confidenceNote + "\n\nProduction number, manufacture date, and other factory fields have not yet been reliably decoded, so the app does not guess at them.");
        rawText.setText(raw);
        rawButton.setVisibility(View.VISIBLE);
        rawText.setVisibility(View.GONE);
        rawButton.setText("Show raw tag memory");
    }

    private static String emptyAsUnknown(String s) {
        return s == null || s.isEmpty() ? "Unknown" : s;
    }

    private String buildRawDump(Map<Integer, byte[]> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, byte[]> e : blocks.entrySet()) {
            String hex = MsaDecoder.hex(e.getValue());
            String ascii = MsaDecoder.ascii(e.getValue());
            sb.append(String.format(Locale.US, "[%02X] %s", e.getKey(), spaced(hex)));
            if (!ascii.isEmpty()) sb.append("   ").append(ascii);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String spaced(String hex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            if (i > 0) out.append(' ');
            out.append(hex, i, Math.min(i + 2, hex.length()));
        }
        return out.toString();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("MSA G1 Tag Reader");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("Ready to scan");
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(12), dp(30), dp(12), dp(30));
        root.addView(statusText, fullWidth());

        componentText = addValue(root, "Component");
        serialText = addValue(root, "RFID Serial Number");
        componentCodeText = addValue(root, "MSA Component Code");
        typeCodeText = addValue(root, "MSA Type Code");
        tagText = addValue(root, "RFID Technology");

        noteText = new TextView(this);
        noteText.setTextSize(14);
        noteText.setPadding(0, dp(20), 0, dp(8));
        root.addView(noteText);

        rawButton = new Button(this);
        rawButton.setText("Show raw tag memory");
        rawButton.setVisibility(View.GONE);
        root.addView(rawButton);

        rawText = new TextView(this);
        rawText.setTextSize(13);
        rawText.setTypeface(Typeface.MONOSPACE);
        rawText.setTextIsSelectable(true);
        rawText.setPadding(0, dp(12), 0, dp(20));
        rawText.setVisibility(View.GONE);
        root.addView(rawText);

        rawButton.setOnClickListener(v -> {
            boolean showing = rawText.getVisibility() == View.VISIBLE;
            rawText.setVisibility(showing ? View.GONE : View.VISIBLE);
            rawButton.setText(showing ? "Show raw tag memory" : "Hide raw tag memory");
        });

        setContentView(scroll);
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView labelView = new TextView(this);
        labelView.setText(label.toUpperCase(Locale.US));
        labelView.setTextSize(12);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setPadding(0, dp(14), 0, dp(2));
        root.addView(labelView);

        TextView value = new TextView(this);
        value.setTextSize(20);
        value.setTextIsSelectable(true);
        root.addView(value);
        return value;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
