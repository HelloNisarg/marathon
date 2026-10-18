package app.stride.seven;

import android.content.Context;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.units.Length;
import android.os.OutcomeReceiver;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground, read-only, Samsung-origin-only Health Connect import. */
final class HealthReader {
    private final HealthConnectManager manager;
    private final DataOrigin samsung = new DataOrigin.Builder().setPackageName("com.sec.android.app.shealth").build();
    HealthReader(Context context) { manager=context.getSystemService(HealthConnectManager.class); }
    private TimeInstantRangeFilter range(Instant start,Instant end) {
        return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();
    }
    private <T> T aggregate(AggregationType<T> type,Instant start,Instant end) throws Exception {
        CompletableFuture<T> future=new CompletableFuture<>();
        AggregateRecordsRequest<T> request=new AggregateRecordsRequest.Builder<T>(range(start,end))
            .addDataOriginsFilter(samsung).addAggregationType(type).build();
        manager.aggregate(request,Runnable::run,new OutcomeReceiver<AggregateRecordsResponse<T>,HealthConnectException>() {
            public void onResult(AggregateRecordsResponse<T> result){future.complete(result.get(type));}
            public void onError(HealthConnectException error){future.completeExceptionally(error);}
        });
        return future.get(25,TimeUnit.SECONDS);
    }
    private List<ExerciseSessionRecord> sessions(Instant start,Instant end) throws Exception {
        List<ExerciseSessionRecord> all=new ArrayList<>(); long token=-1;
        do {
            ReadRecordsRequestUsingFilters.Builder<ExerciseSessionRecord> builder=
                new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                    .addDataOrigins(samsung).setTimeRangeFilter(range(start,end)).setPageSize(500);
            if(token!=-1)builder.setPageToken(token);
            CompletableFuture<ReadRecordsResponse<ExerciseSessionRecord>> future=new CompletableFuture<>();
            manager.readRecords(builder.build(),Runnable::run,new OutcomeReceiver<ReadRecordsResponse<ExerciseSessionRecord>,HealthConnectException>() {
                public void onResult(ReadRecordsResponse<ExerciseSessionRecord> result){future.complete(result);}
                public void onError(HealthConnectException error){future.completeExceptionally(error);}
            });
            ReadRecordsResponse<ExerciseSessionRecord> response=future.get(25,TimeUnit.SECONDS);
            all.addAll(response.getRecords()); token=response.getNextPageToken();
            if(all.size()>2000)throw new IllegalStateException("Too many sessions to import safely.");
        } while(token!=-1);
        return all;
    }
    JSONObject read() throws Exception {
        if(manager==null)throw new IllegalStateException("Health Connect is unavailable on this phone.");
        ZoneId zone=ZoneId.systemDefault(); LocalDate today=LocalDate.now(zone),first=today.minusDays(6);
        Instant now=Instant.now(); Map<String,JSONObject> days=new LinkedHashMap<>();
        for(int n=0;n<7;n++) {
            LocalDate date=first.plusDays(n);
            Instant start=date.atStartOfDay(zone).toInstant(),end=date.plusDays(1).atStartOfDay(zone).toInstant();
            if(end.isAfter(now))end=now;
            Long steps=start.isBefore(end)?aggregate(StepsRecord.STEPS_COUNT_TOTAL,start,end):null;
            days.put(date.toString(),new JSONObject().put("date",date.toString())
                .put("steps",steps==null?JSONObject.NULL:steps).put("sessions",new JSONArray()));
        }
        Set<String> seen=new HashSet<>();
        for(ExerciseSessionRecord record:sessions(first.atStartOfDay(zone).toInstant(),now)) {
            int type=record.getExerciseType();
            boolean run=type==ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING||type==ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING_TREADMILL;
            if(!run&&type!=ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING)continue;
            if(record.getEndTime().isAfter(now)||!record.getEndTime().isAfter(record.getStartTime()))continue;
            String date=record.getStartTime().atZone(zone).toLocalDate().toString();
            if(!days.containsKey(date))continue;
            String id=record.getMetadata().getId();
            if(id==null||id.isEmpty()||!seen.add(id))continue;
            Length length=aggregate(DistanceRecord.DISTANCE_TOTAL,record.getStartTime(),record.getEndTime());
            JSONObject activity=new JSONObject().put("id",id).put("type",run?"run":"walk")
                .put("start",record.getStartTime().toString()).put("end",record.getEndTime().toString())
                .put("minutes",Duration.between(record.getStartTime(),record.getEndTime()).toMillis()/60000.0)
                .put("km",length==null?JSONObject.NULL:length.getInMeters()/1000.0);
            days.get(date).getJSONArray("sessions").put(activity);
        }
        return new JSONObject().put("format","stride-health-connect-v1").put("source","com.sec.android.app.shealth")
            .put("syncedAt",now.toString()).put("days",new JSONArray(days.values()));
    }
}
