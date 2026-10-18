package app.stride.seven;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.*;
import android.location.*;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Explicitly started, on-device tracking. No networking or automatic reboot starts. */
public class TrackingService extends Service implements SensorEventListener, LocationListener {
    static boolean alive=false;
    static final String PREF="stride-tracking";
    SharedPreferences p; SensorManager sensors; LocationManager locations;
    boolean counting=false,active=false,paused=false; double meters=0; long elapsed=0,segment=0;
    Location previous; String id="",kind="Run",date=""; double goal=7000;
    final Handler timer=new Handler();
    static String today(){return LocalDate.now().toString();}
    @Override public void onCreate(){super.onCreate();alive=true;p=getSharedPreferences(PREF,0);sensors=getSystemService(SensorManager.class);locations=getSystemService(LocationManager.class);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("tracking","Live tracking",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("goals","Goal completed",NotificationManager.IMPORTANCE_HIGH));
        // Recover an interrupted workout as paused. Never count downtime or bridge a GPS gap.
        active=p.getBoolean("active",false);paused=active;meters=p.getFloat("meters",0);elapsed=p.getLong("elapsed",0);id=p.getString("id","");kind=p.getString("kind","Run");date=p.getString("date",today());goal=p.getFloat("goal",7000);
        timer.post(tick);
    }
    final Runnable tick=new Runnable(){public void run(){if(alive){persist();if(counting||active)notifyStatus();timer.postDelayed(this,3000);}}};
    long duration(){return elapsed+(active&&!paused?Math.max(0,SystemClock.elapsedRealtime()-segment):0);}
    PendingIntent open(){return PendingIntent.getActivity(this,1,new Intent(this,TrackingActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    Notification status(){String text=(counting?steps()+" steps today":"Step counter off")+(active?String.format(Locale.US," · %s %.2f km%s",kind,meters/1000,paused?" (paused)":""):"");return new Notification.Builder(this,"tracking").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Stride tracking").setContentText(text).setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).build();}
    void foreground(){int type=(counting?ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH:0)|(active?ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION:0);if(type!=0)startForeground(101,status(),type);}
    void notifyStatus(){getSystemService(NotificationManager.class).notify(101,status());}
    int steps(){return p.getInt("steps."+today(),0);}
    void alert(String key,String title,String text){if(p.getBoolean(key,false))return;
        if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled())return;
        nm.notify(key.hashCode(),new Notification.Builder(this,"goals").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle(title).setContentText(text).setContentIntent(open()).setAutoCancel(true).build());p.edit().putBoolean(key,true).apply();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null){stopSelf();return START_NOT_STICKY;}
        String action=i.getAction();
        try{
            if("steps-on".equals(action)){
                Sensor counter=sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if(counter==null){error("This phone does not expose a step-counter sensor.");stopIfIdle();return START_NOT_STICKY;}
                if(!counting){counting=true;p.edit().remove("sensor.last").apply();foreground();if(!sensors.registerListener(this,counter,SensorManager.SENSOR_DELAY_NORMAL)){counting=false;error("Could not enable step counting.");}}
            }else if("steps-off".equals(action)){counting=false;sensors.unregisterListener(this);p.edit().remove("sensor.last").apply();}
            else if("start".equals(action)&&!active){active=true;paused=false;meters=0;elapsed=0;segment=SystemClock.elapsedRealtime();id=UUID.randomUUID().toString();kind=i.getStringExtra("kind");if(!"Walk".equals(kind))kind="Run";date=today();goal=Math.max(100,Math.min(100000,i.getDoubleExtra("goal",7000)));foreground();gps();}
            else if("pause".equals(action)&&active&&!paused){elapsed=duration();paused=true;locations.removeUpdates(this);previous=null;}
            else if("resume".equals(action)&&active&&paused){paused=false;segment=SystemClock.elapsedRealtime();foreground();gps();}
            else if("finish".equals(action)&&active){elapsed=duration();paused=true;locations.removeUpdates(this);previous=null;saveSession();active=false;}
            if(counting||active)foreground();persist();checkGoals();stopIfIdle();
        }catch(SecurityException|IllegalArgumentException e){error("Tracking permission or GPS is unavailable. Check Android permissions and restart tracking.");if(active&&!paused)elapsed=duration();paused=active;counting=false;sensors.unregisterListener(this);locations.removeUpdates(this);persist();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        return START_NOT_STICKY;
    }
    void gps(){previous=null;p.edit().putString("gps","Waiting for an accurate outdoor GPS fix…").apply();locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,2,this);}
    void error(String text){p.edit().putString("error",text).apply();}
    void persist(){p.edit().putBoolean("counting",counting).putBoolean("active",active).putBoolean("paused",paused).putFloat("meters",(float)meters).putLong("elapsed",duration()).putString("id",id).putString("kind",kind).putString("date",date).putFloat("goal",(float)goal).apply();}
    void stopIfIdle(){if(!counting&&!active){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}
    void checkGoals(){int target=p.getInt("stepGoal",10000);if(TrackingMath.reached(steps(),target))alert("notified.steps."+today(),"Daily step target complete!",String.format(Locale.US,"%,d / %,d steps. Well done!",steps(),target));if(active&&TrackingMath.reached(meters,goal))alert("notified.session."+id,kind+" target complete!",String.format(Locale.US,"You reached %.2f km. Tap to view or finish your session.",goal/1000));}
    @Override public void onSensorChanged(SensorEvent e){if(!counting)return;long now=(long)e.values[0],last=p.getLong("sensor.last",-1);String day=Instant.ofEpochMilli(System.currentTimeMillis()-(SystemClock.elapsedRealtimeNanos()-e.timestamp)/1000000).atZone(ZoneId.systemDefault()).toLocalDate().toString();long delta=TrackingMath.stepDelta(last,now,day.equals(p.getString("sensor.day",day)));
        // A cumulative batch spanning midnight cannot be split reliably; start a fresh baseline.
        if(!day.equals(p.getString("sensor.day",day)))delta=0;
        p.edit().putLong("sensor.last",now).putString("sensor.day",day).putInt("steps."+day,(int)Math.min(Integer.MAX_VALUE,(long)p.getInt("steps."+day,0)+delta)).apply();checkGoals();notifyStatus();}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onLocationChanged(Location l){if(!active||paused)return;long age=(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000;
        if(!l.hasAccuracy()||l.getAccuracy()>25||age<0||age>15000){p.edit().putString("gps","Weak GPS — distance paused until accuracy improves.").apply();previous=null;return;}
        p.edit().putString("gps","GPS accuracy ±"+Math.round(l.getAccuracy())+" m").apply();
        if(previous!=null){double seconds=(l.getElapsedRealtimeNanos()-previous.getElapsedRealtimeNanos())/1e9;float d=previous.distanceTo(l);if(TrackingMath.validLeg(d,seconds))meters+=d;else if(seconds>0&&seconds<=30&&d<3)return;}
        previous=l;persist();checkGoals();notifyStatus();
    }
    @Override public void onProviderDisabled(String provider){previous=null;p.edit().putString("gps","GPS is disabled. Enable Location to continue measuring distance.").apply();}
    void saveSession(){try{JSONArray history=new JSONArray(p.getString("history","[]"));JSONObject s=new JSONObject();s.put("id",id).put("date",date).put("type",kind).put("meters",meters).put("elapsedMs",elapsed).put("targetMeters",goal);history.put(s);p.edit().putString("history",history.toString()).apply();}catch(JSONException e){error("Could not save this session. Keep the app open and export your records.");}}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){if(active&&!paused){elapsed=duration();paused=true;}alive=false;timer.removeCallbacks(tick);sensors.unregisterListener(this);locations.removeUpdates(this);counting=false;persist();super.onDestroy();}
}
