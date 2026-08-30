package com.example.msag1reader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MsaDecoder {
    private MsaDecoder() {}
    public static final class Result {
        public String rfidSerial;
        public String componentName;
        public String componentCode;
        public String typeCode;
        public String tagTechnology = "NXP ICODE SLIX / ISO 15693 (NFC-V)";
        public String confidenceNote;
        public boolean partialRead;
    }
    private static final Map<String,String> COMPONENT_CODES=new LinkedHashMap<>();
    private static final Map<String,String> TYPE_CODES=new LinkedHashMap<>();
    static {
        COMPONENT_CODES.put("51018546","G1 Facepiece - Small");
        COMPONENT_CODES.put("51019546","G1 Facepiece - Medium");
        COMPONENT_CODES.put("51010646","G1 Facepiece - Large");
        COMPONENT_CODES.put("52017200","G1 PASS Device");
        COMPONENT_CODES.put("42018936","G1 Pack Frame");
        TYPE_CODES.put("00000340","G1 Facepiece");
        TYPE_CODES.put("00000540","G1 PASS Device");
        TYPE_CODES.put("00000640","G1 Pack Frame");
    }
    public static Result decode(byte[] androidUid, Map<Integer,byte[]> blocks) {
        Result r=new Result();
        r.rfidSerial=normalizeUid(androidUid);
        byte[] b5=blocks.get(0x05), b6=blocks.get(0x06);
        if(b5!=null && b6!=null) r.componentCode=ascii(b5)+ascii(b6); else {r.componentCode=""; r.partialRead=true;}
        byte[] b0c=blocks.get(0x0C);
        if(b0c!=null) r.typeCode=hex(b0c); else {r.typeCode=""; r.partialRead=true;}
        String byComponent=COMPONENT_CODES.get(r.componentCode);
        String byType=TYPE_CODES.get(r.typeCode);
        if(byComponent!=null){
            r.componentName=byComponent;
            r.confidenceNote=byType!=null ? "Identified from the observed MSA component code and type code." : (r.partialRead ? "Identified from the MSA component code; part of the tag could not be read." : "Identified from the observed MSA component code.");
        } else if(byType!=null){
            r.componentName=byType;
            r.confidenceNote=r.partialRead ? "Identified from the MSA type code; the detailed component code could not be read." : "Identified from the observed MSA type code; this detailed component code has not been mapped yet.";
        } else {
            r.componentName="Unknown MSA G1 Component";
            r.confidenceNote=r.partialRead ? "The RFID tag was detected, but required identification blocks were not all read. Scan again while holding the phone steady." : "The tag was read successfully, but this component has not been mapped yet.";
        }
        return r;
    }
    public static String normalizeUid(byte[] uid){
        if(uid==null)return ""; String f=hex(uid); byte[] rev=new byte[uid.length];
        for(int i=0;i<uid.length;i++) rev[i]=uid[uid.length-1-i];
        String rr=hex(rev); if(f.startsWith("E004"))return f; if(rr.startsWith("E004"))return rr; return f;
    }
    public static String ascii(byte[] data){
        if(data==null)return ""; String s=new String(data,StandardCharsets.US_ASCII); StringBuilder c=new StringBuilder();
        for(int i=0;i<s.length();i++){char ch=s.charAt(i); if(ch>=32&&ch<=126)c.append(ch);} return c.toString();
    }
    public static String hex(byte[] data){
        if(data==null)return ""; StringBuilder sb=new StringBuilder(data.length*2);
        for(byte b:data)sb.append(String.format(Locale.US,"%02X",b&0xFF)); return sb.toString();
    }
}
