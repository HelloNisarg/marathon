package app.stride.seven;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import java.time.*;
import java.util.*;

/** A single inexact local alarm, rescheduled after delivery. No network or paid service. */
public class WaterReminder extends BroadcastReceiver {
    static final String PREF="stride-water-reminder";
    static final String ACTION="app.stride.seven.WATER_REMINDER";
    static android.content.SharedPreferences data(Context c){return c.getSharedPreferences(PREF,0);}
    static PendingIntent token(Context c){return PendingIntent.getBroadcast(c,130,new Intent(c,WaterReminder.class).setAction(ACTION),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    static void update(Context c,boolean on,String first,String last,int interval,int goal){
        if(!first.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!last.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||first.compareTo(last)>0||interval<30||interval>240||goal<500||goal>5000)throw new IllegalArgumentException();
        data(c).edit().putBoolean("enabled",on).putString("start",first).putString("end",last).putInt("interval",interval).putInt("goal",goal).apply();schedule(c);
    }
    static void log(Context c,String date,int ml){data(c).edit().putInt("logged."+date,Math.max(0,Math.min(20000,ml))).apply();}
    static void schedule(Context c){AlarmManager alarm=c.getSystemService(AlarmManager.class);alarm.cancel(token(c));android.content.SharedPreferences p=data(c);if(!p.getBoolean("enabled",false))return;
        LocalTime from=LocalTime.parse(p.getString("start","09:00")),to=LocalTime.parse(p.getString("end","19:00"));int interval=p.getInt("interval",120);ZonedDateTime now=ZonedDateTime.now();
        for(int day=0;day<3;day++){LocalDate date=now.toLocalDate().plusDays(day);if(p.getInt("logged."+date,0)>=p.getInt("goal",2000))continue;
            for(int minute=from.toSecondOfDay()/60;minute<=to.toSecondOfDay()/60;minute+=interval){
                ZonedDateTime at=ZonedDateTime.of(date,LocalTime.of(minute/60,minute%60),ZoneId.systemDefault());if(at.isAfter(now.plusSeconds(5))){alarm.set(AlarmManager.RTC_WAKEUP,at.toInstant().toEpochMilli(),token(c));return;}
            }
        }
    }
    @Override public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())||Intent.ACTION_TIME_CHANGED.equals(i.getAction())){schedule(c);return;}
        if(!ACTION.equals(i.getAction()))return;android.content.SharedPreferences p=data(c);if(p.getBoolean("enabled",false)&&p.getInt("logged."+LocalDate.now(),0)<p.getInt("goal",2000)&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED){
            NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("hydration","Water reminders",NotificationManager.IMPORTANCE_DEFAULT));
            if(nm.areNotificationsEnabled()){
                PendingIntent open=PendingIntent.getActivity(c,131,new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                nm.notify(203,new Notification.Builder(c,"hydration").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Time for a water break").setContentText("Have a comfortable drink, then log it in Stride.").setContentIntent(open).setAutoCancel(true).build());
            }
        }
        schedule(c);
    }
}
