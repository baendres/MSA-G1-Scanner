package com.example.msag1reader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decoder for the MSA G1 RFID samples collected so far.
 *
 * Confirmed:
 *  - MSA's printed RFID Serial Number is the ISO-15693 UID with separators removed.
 *  - Blocks 05-06 are stable per sampled component family.
 *  - Block 0C is stable per sampled component class.
 *
 * The friendly names below are based on observed samples and can be expanded as
 * additional MSA components are scanned.
 */
public final class MsaDecoder {

    private MsaDecoder() {}

    public static final class Result {
        public String rfidSerial;
        public String componentName;
        public String componentCode;
        public String typeCode;
        public String tagTechnology = "NXP ICODE SLIX / ISO 15693 (NFC-V)";
        public String confidenceNote;
    }

    private static final Map<String, String> COMPONENT_CODES = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_CODES = new LinkedHashMap<>();

    static {
        COMPONENT_CODES.put("51019546", "G1 Facepiece");
        COMPONENT_CODES.put("52017200", "G1 PASS Device");
        COMPONENT_CODES.put("42018936", "G1 Pack Frame");

        TYPE_CODES.put("00000340", "G1 Facepiece");
        TYPE_CODES.put("00000540", "G1 PASS Device");
        TYPE_CODES.put("00000640", "G1 Pack Frame");
    }

    public static Result decode(byte[] androidUid, Map<Integer, byte[]> blocks) {
        Result result = new Result();
        result.rfidSerial = normalizeUid(androidUid);

        byte[] b5 = blocks.get(0x05);
        byte[] b6 = blocks.get(0x06);
        result.componentCode = ascii(b5) + ascii(b6);

        byte[] b0c = blocks.get(0x0C);
        result.typeCode = hex(b0c);

        String byComponent = COMPONENT_CODES.get(result.componentCode);
        String byType = TYPE_CODES.get(result.typeCode);

        if (byComponent != null && byType != null && byComponent.equals(byType)) {
            result.componentName = byComponent;
            result.confidenceNote = "Identified from both observed MSA component and type fields.";
        } else if (byComponent != null) {
            result.componentName = byComponent;
            result.confidenceNote = "Identified from observed MSA component code; type field did not match a known sample.";
        } else if (byType != null) {
            result.componentName = byType;
            result.confidenceNote = "Identified from observed MSA type code; component code is not yet known.";
        } else {
            result.componentName = "Unknown MSA G1 Component";
            result.confidenceNote = "The tag was read successfully, but this component code has not been mapped yet.";
        }
        return result;
    }

    /**
     * Android NFC-V implementations may expose an ISO-15693 UID in either display
     * order or RF byte order. MSA/NXP tags observed here use an E004 prefix, so
     * select the orientation that produces that prefix when possible.
     */
    public static String normalizeUid(byte[] uid) {
        if (uid == null) return "";
        String forward = hex(uid);
        byte[] reversed = new byte[uid.length];
        for (int i = 0; i < uid.length; i++) {
            reversed[i] = uid[uid.length - 1 - i];
        }
        String reverse = hex(reversed);
        if (forward.startsWith("E004")) return forward;
        if (reverse.startsWith("E004")) return reverse;
        return forward;
    }

    public static String ascii(byte[] data) {
        if (data == null) return "";
        String s = new String(data, StandardCharsets.US_ASCII);
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) clean.append(c);
        }
        return clean.toString();
    }

    public static String hex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }
}
