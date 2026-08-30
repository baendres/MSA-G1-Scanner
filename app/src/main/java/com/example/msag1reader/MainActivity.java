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
    private static final int LAST_BLOCK=0x1B;
    private static final int READ_ATTEMPTS=3;
    private NfcAdapter nfcAdapter;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private TextView statusText,componentText,serialText,componentCodeText,typeCodeText,tagText,noteText,rawText;
    private Button rawButton;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); buildUi(); nfcAdapter=NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter==null){statusText.setText("This phone does not have NFC hardware."); return;}
        if(!nfcAdapter.isEnabled()){
            statusText.setText("NFC is turned off."); Button b=new Button(this); b.setText("Open NFC settings");
            ((LinearLayout)statusText.getParent()).addView(b); b.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        }
    }
    @Override protected void onResume(){
        super.onResume();
        if(nfcAdapter!=null&&nfcAdapter.isEnabled()){
            nfcAdapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK|NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,new Bundle());
            if(componentText.getText().length()==0) statusText.setText("Ready to scan\nHold the top/back of the phone against an MSA RFID tag.");
        }
    }
    @Override protected void onPause(){super.onPause(); if(nfcAdapter!=null)nfcAdapter.disableReaderMode(this);}
    @Override public void onTagDiscovered(Tag tag){
        NfcV n=NfcV.get(tag);
        if(n==null){mainHandler.post(()->statusText.setText("Tag detected, but it is not an ISO 15693 / NFC-V tag.")); return;}
        try{Map<Integer,byte[]> blocks=readBlocks(n); MsaDecoder.Result d=MsaDecoder.decode(tag.getId(),blocks); String raw=buildRawDump(blocks); mainHandler.post(()->showResult(d,raw));}
        catch(Exception ex){mainHandler.post(()->{statusText.setText("Tag detected, but memory could not be read. Keep the phone in place and try again."); Toast.makeText(this,ex.getMessage()==null?"NFC read error":ex.getMessage(),Toast.LENGTH_SHORT).show();});}
    }
    private Map<Integer,byte[]> readBlocks(NfcV n) throws IOException{
        Map<Integer,byte[]> blocks=new LinkedHashMap<>(); n.connect();
        try{
            int[] priority={0x05,0x06,0x0C,0x08,0x09,0x01,0x04};
            for(int b:priority){byte[] d=readBlockWithRetry(n,b); if(d!=null)blocks.put(b,d);}
            for(int b=0;b<=LAST_BLOCK;b++){if(blocks.containsKey(b))continue; byte[] d=readBlockWithRetry(n,b); if(d!=null)blocks.put(b,d);}
        } finally {try{n.close();}catch(IOException ignored){}}
        Map<Integer,byte[]> sorted=new LinkedHashMap<>(); for(int b=0;b<=LAST_BLOCK;b++)if(blocks.containsKey(b))sorted.put(b,blocks.get(b)); return sorted;
    }
    private byte[] readBlockWithRetry(NfcV n,int block){
        for(int attempt=0;attempt<READ_ATTEMPTS;attempt++){
            try{byte[] r=n.transceive(new byte[]{0x02,0x20,(byte)block}); if(r!=null&&r.length>=5&&(r[0]&0x01)==0){byte[] d=new byte[4]; System.arraycopy(r,1,d,0,4); return d;}}catch(IOException ignored){}
            try{Thread.sleep(30L);}catch(InterruptedException ignored){Thread.currentThread().interrupt(); break;}
        } return null;
    }
    private void showResult(MsaDecoder.Result r,String raw){
        statusText.setText(r.partialRead?"Partial scan — hold steady and scan again for complete data":"Scan complete — ready for another tag");
        componentText.setText(r.componentName); serialText.setText(r.rfidSerial); componentCodeText.setText(emptyAsUnavailable(r.componentCode)); typeCodeText.setText(emptyAsUnavailable(r.typeCode)); tagText.setText(r.tagTechnology);
        String extra=r.partialRead?"\n\nSome RFID memory blocks could not be read. The app still shows any identification it could confirm.":"";
        noteText.setText(r.confidenceNote+extra+"\n\nProduction number, manufacture date, and other factory fields have not yet been reliably decoded, so the app does not guess at them.");
        rawText.setText(raw); rawButton.setVisibility(View.VISIBLE); rawText.setVisibility(View.GONE); rawButton.setText("Show raw tag memory");
    }
    private static String emptyAsUnavailable(String s){return s==null||s.isEmpty()?"Unavailable - partial read":s;}
    private String buildRawDump(Map<Integer,byte[]> blocks){
        StringBuilder sb=new StringBuilder();
        for(int b=0;b<=LAST_BLOCK;b++){byte[] d=blocks.get(b); if(d==null){sb.append(String.format(Locale.US,"[%02X] -- -- -- --   <not read>",b)).append('\n'); continue;} String h=MsaDecoder.hex(d), a=MsaDecoder.ascii(d); sb.append(String.format(Locale.US,"[%02X] %s",b,spaced(h))); if(!a.isEmpty())sb.append("   ").append(a); sb.append('\n');}
        return sb.toString();
    }
    private static String spaced(String h){StringBuilder o=new StringBuilder(); for(int i=0;i<h.length();i+=2){if(i>0)o.append(' '); o.append(h,i,Math.min(i+2,h.length()));} return o.toString();}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22),dp(28),dp(22),dp(28)); scroll.addView(root);
        TextView title=new TextView(this); title.setText("MSA G1 Scanner"); title.setTextSize(28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(title);
        statusText=new TextView(this); statusText.setText("Ready to scan"); statusText.setTextSize(18); statusText.setGravity(Gravity.CENTER); statusText.setPadding(dp(12),dp(30),dp(12),dp(30)); root.addView(statusText,fullWidth());
        componentText=addValue(root,"Component"); serialText=addValue(root,"RFID Serial Number"); componentCodeText=addValue(root,"MSA Component Code"); typeCodeText=addValue(root,"MSA Type Code"); tagText=addValue(root,"RFID Technology");
        noteText=new TextView(this); noteText.setTextSize(14); noteText.setPadding(0,dp(20),0,dp(8)); root.addView(noteText);
        rawButton=new Button(this); rawButton.setText("Show raw tag memory"); rawButton.setVisibility(View.GONE); root.addView(rawButton);
        rawText=new TextView(this); rawText.setTextSize(13); rawText.setTypeface(Typeface.MONOSPACE); rawText.setTextIsSelectable(true); rawText.setPadding(0,dp(12),0,dp(20)); rawText.setVisibility(View.GONE); root.addView(rawText);
        rawButton.setOnClickListener(v->{boolean showing=rawText.getVisibility()==View.VISIBLE; rawText.setVisibility(showing?View.GONE:View.VISIBLE); rawButton.setText(showing?"Show raw tag memory":"Hide raw tag memory");}); setContentView(scroll);
    }
    private TextView addValue(LinearLayout root,String label){TextView l=new TextView(this); l.setText(label.toUpperCase(Locale.US)); l.setTextSize(12); l.setTypeface(Typeface.DEFAULT,Typeface.BOLD); l.setPadding(0,dp(14),0,dp(2)); root.addView(l); TextView v=new TextView(this); v.setTextSize(20); v.setTextIsSelectable(true); root.addView(v); return v;}
    private LinearLayout.LayoutParams fullWidth(){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
