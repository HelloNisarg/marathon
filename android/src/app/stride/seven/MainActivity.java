package app.stride.seven;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private static final String[] PERMISSIONS={"android.permission.health.READ_STEPS","android.permission.health.READ_EXERCISE","android.permission.health.READ_DISTANCE"};
    private static final int HEALTH_PERMISSION=40, SAVE_FILE=41, OPEN_FILE=42, WATER_PERMISSION=43;
    private WebView web;
    private boolean busy=false,resumed=false,pageReady=false;
    private String pendingResult=null,pendingFile=null;
    private String[] pendingWater=null;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xfff7f8f2);
        getWindow().setNavigationBarColor(0xfff7f8f2);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this); setContentView(web);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        // The bridge is exposed only to packaged assets. No Internet permission,
        // external navigation, remote scripts, frames or universal file access.
        web.addJavascriptInterface(new Bridge(),"AndroidStride");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                Uri uri=request.getUrl();String path=uri.getPath();
                if(!"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost())||path==null||path.contains(".."))return blocked();
                if(path.equals("/"))path="/index.html";
                String file=path.substring(1);
                if(!Arrays.asList("index.html","app.js","core.mjs","health.mjs","activity.mjs","strength.mjs","style.css","manifest.webmanifest","icon.svg","icon-192.png","icon-512.png").contains(file))return blocked();
                try{
                    String mime=file.endsWith(".html")?"text/html":file.endsWith(".js")||file.endsWith(".mjs")?"text/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":file.endsWith(".png")?"image/png":"application/json";
                    Map<String,String> headers=new HashMap<>();
                    headers.put("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; manifest-src 'self'");
                    headers.put("X-Content-Type-Options","nosniff");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open(file));
                }catch(IOException error){return blocked();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                return !"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost())||!"/index.html".equals(uri.getPath());
            }
            @Override public void onPageFinished(WebView view,String url){pageReady=true;deliverPending();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Continue",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
            }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams parameters){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                try{startActivityForResult(intent,OPEN_FILE);}catch(ActivityNotFoundException error){fileCallback.onReceiveValue(null);fileCallback=null;message("No file picker is available on this phone.");}
                return true;
            }
        });
        web.loadUrl(ORIGIN+"/index.html");
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    @Override public void onResume(){super.onResume();resumed=true;if(web!=null)web.onResume();deliverPending();if(web!=null&&pageReady)web.evaluateJavascript("window.dispatchEvent(new Event('stride-native-resume'))",null);}
    @Override public void onPause(){resumed=false;if(web!=null)web.onPause();super.onPause();}
    @Override public void onDestroy(){worker.shutdownNow();if(fileCallback!=null)fileCallback.onReceiveValue(null);if(web!=null){web.removeJavascriptInterface("AndroidStride");web.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("(function(){const d=document.querySelector('#modal');if(d&&d.open){d.close();return true;}if(location.hash&&location.hash!=='#today'){location.hash='today';return true;}return false;})()",value->{if(!"true".equals(value))finish();});}
    private void message(String text){if(isFinishing()||isDestroyed())return;Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private boolean hasPermissions(){for(String p:PERMISSIONS)if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
    private void connect(){
        if(busy||!resumed)return;
        if(!hasPermissions()){
            new AlertDialog.Builder(this).setTitle("Connect Samsung Health")
                .setMessage("Stride will read the last seven days of steps, runs, walks and distance shared by Samsung Health. Data stays in this app on your phone. You will review it before importing.\n\nFirst enable Samsung Health → Settings → Health Connect sharing for these three data types.")
                .setPositiveButton("Choose permissions",(d,w)->requestPermissions(PERMISSIONS,HEALTH_PERMISSION))
                .setNeutralButton("Data policy",(d,w)->startActivity(new Intent(this,PrivacyActivity.class))).setNegativeButton("Cancel",null).show();
        }else sync();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==HEALTH_PERMISSION){if(hasPermissions())sync();else message("Permission was not granted for all three data types. No data was imported. You can review Stride in Health Connect app permissions.");}
        if(code==WATER_PERMISSION&&pendingWater!=null){String[] q=pendingWater;pendingWater=null;if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)waterConfig(true,q);else message("Notifications remain off. Your reminder times were saved, but phone alerts were not enabled.");}
    }
    private void waterConfig(boolean enabled,String[] values){try{WaterReminder.update(this,enabled,values[0],values[1],Integer.parseInt(values[2]),Integer.parseInt(values[3]));message(enabled?"Phone water reminders enabled. Delivery times may vary with battery settings.":"Phone water reminders turned off.");if(pageReady)web.evaluateJavascript("window.dispatchEvent(new Event('stride-water-status'))",null);}catch(Exception error){message("Could not schedule water reminders. Check the times and try again.");}}
    private void sync(){
        if(busy||!hasPermissions())return;busy=true;message("Reading Samsung Health… Keep Stride open.");
        worker.execute(()->{
            try{
                String json=new HealthReader(this).read().toString();
                runOnUiThread(()->{busy=false;pendingResult=json;deliverPending();});
            }catch(Exception error){runOnUiThread(()->{busy=false;message("Could not read Samsung Health. Open Samsung Health to sync, check Health Connect permissions, then try again. Your Stride logs were not changed.");});}
        });
    }
    private void deliverPending(){
        if(resumed&&pageReady&&pendingResult!=null&&!isFinishing()&&!isDestroyed()){
            String value=pendingResult;pendingResult=null;
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('stride-health-result',{detail:JSON.parse("+JSONObject.quote(value)+")}));",null);
        }
    }
    public final class Bridge {
        @JavascriptInterface public String liveHistory(){return getSharedPreferences(TrackingService.PREF,0).getString("history","[]");}
        @JavascriptInterface public int liveStepCount(){return getSharedPreferences(TrackingService.PREF,0).getInt("steps."+TrackingService.today(),0);}
        @JavascriptInterface public void mergeLiveHistory(String raw){if(raw==null||raw.length()>1000000)return;runOnUiThread(()->{try{org.json.JSONArray incoming=new org.json.JSONArray(raw),current=new org.json.JSONArray(getSharedPreferences(TrackingService.PREF,0).getString("history","[]"));java.util.LinkedHashMap<String,org.json.JSONObject> map=new java.util.LinkedHashMap<>();for(int i=0;i<incoming.length();i++){org.json.JSONObject v=incoming.getJSONObject(i);if(v.has("id"))map.put(v.getString("id"),v);}for(int i=0;i<current.length();i++){org.json.JSONObject v=current.getJSONObject(i);if(v.has("id"))map.put(v.getString("id"),v);}org.json.JSONArray merged=new org.json.JSONArray();for(org.json.JSONObject v:map.values())merged.put(v);getSharedPreferences(TrackingService.PREF,0).edit().putString("history",merged.toString()).apply();}catch(Exception error){message("Could not merge live history from this backup.");}});}
        @JavascriptInterface public boolean waterReminderEnabled(){return WaterReminder.data(MainActivity.this).getBoolean("enabled",false);}
        @JavascriptInterface public void updateWaterLog(String date,int ml){if(date!=null&&date.matches("20\\d{2}-\\d{2}-\\d{2}")&&ml>=0&&ml<=20000)runOnUiThread(()->{WaterReminder.log(MainActivity.this,date,ml);WaterReminder.schedule(MainActivity.this);});}
        @JavascriptInterface public void configureWater(boolean on,String start,String end,int interval,int goal){runOnUiThread(()->{String[] values={start,end,String.valueOf(interval),String.valueOf(goal)};if(on&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){pendingWater=values;requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},WATER_PERMISSION);}else waterConfig(on,values);});}
        @JavascriptInterface public void openLiveTracking(String name,int stepGoal){runOnUiThread(()->{getSharedPreferences(TrackingService.PREF,0).edit().putString("name",name==null?"Your profile":name.substring(0,Math.min(40,name.length()))).putInt("stepGoal",Math.max(1000,Math.min(30000,stepGoal))).apply();startActivity(new Intent(MainActivity.this,TrackingActivity.class));});}
        @JavascriptInterface public void syncSamsungHealth(){runOnUiThread(()->connect());}
        @JavascriptInterface public void saveFile(String name,String content,String mime){
            if(content==null||content.length()>5_000_000||!Arrays.asList("application/json","text/calendar").contains(mime))return;
            runOnUiThread(()->{
                if(pendingFile!=null){message("Finish the current file save first.");return;}
                pendingFile=content;
                String safeName=name!=null&&name.matches("stride-[a-zA-Z0-9.-]+")?name:"stride-backup.json";
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,safeName);
                try{startActivityForResult(intent,SAVE_FILE);}catch(ActivityNotFoundException error){pendingFile=null;message("No file save app is available.");}
            });
        }
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==OPEN_FILE&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}
        if(code==SAVE_FILE){
            String content=pendingFile;pendingFile=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&content!=null){
                try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
                    if(stream==null)throw new IOException();stream.write(content.getBytes(StandardCharsets.UTF_8));message("File saved. Keep personal backups private.");
                }catch(IOException error){message("File was not saved. Please try exporting again.");}
            }
        }
    }
}
