import test from 'node:test';
import assert from 'node:assert/strict';
import {cleanHealthDay,parseHealthSync,runDistance,runCount} from './dist/health.mjs';
import {normalize,DEFAULTS,emptyDay} from './dist/core.mjs';
const activity={id:'samsung-run-1',type:'run',start:'2026-10-17T01:30:00Z',end:'2026-10-17T02:00:00Z',minutes:30,km:3.2};
const snapshot={date:'2026-10-17',steps:10000,sessions:[activity]};
const payload={format:'stride-health-connect-v1',source:'com.sec.android.app.shealth',syncedAt:'2026-10-17T04:00:00Z',days:[snapshot]};
test('Samsung snapshots reject bad values, wrong sources and future dates',()=>{
 assert.equal(parseHealthSync(payload,'2026-10-17')[0].steps,10000);
 for(const changed of [
  {...payload,source:'another.app'}, {...payload,days:[{...snapshot,steps:-1}]},
  {...payload,days:[{...snapshot,steps:'10000'}]}, {...payload,days:[snapshot,snapshot]},
  {...payload,days:[{...snapshot,date:'2026-02-30'}]},
  {...payload,days:[{...snapshot,sessions:[{...activity,km:-1}]}]},
  {...payload,days:[{...snapshot,sessions:[{...activity,type:'bike'}]}]},
  {...payload,days:[{...snapshot,sessions:[{...activity,end:activity.start}]}]}
 ])assert.throws(()=>parseHealthSync(changed,'2026-10-17'));
 assert.throws(()=>parseHealthSync(payload,'2026-10-16'));
 assert.equal(cleanHealthDay({...snapshot,steps:null}).steps,null);
});
test('duplicate IDs collapse, walks are not runs, and synced runs are not added to manual logs',()=>{
 const health=cleanHealthDay({...snapshot,sessions:[activity,activity,{...activity,id:'walk-1',type:'walk',km:1.2}]});
 assert.equal(health.sessions.length,2);
 const d={...emptyDay(),km:3.2,workout:true,health};
 assert.equal(runDistance(d),3.2);
 assert.equal(runCount(d,true),1);
 assert.equal(runDistance({...d,health:{...health,sessions:[]}}),3.2);
 assert.equal(runDistance({...d,health:{...health,sessions:[{...activity,km:null}]}}),0,'unknown distance is not invented');
 assert.equal(runCount({...d,workout:false,health:{...health,sessions:[{...activity,type:'walk'}]}},false),0);
});
test('health data survives backup round trips and malformed optional health data cannot destroy the rest',()=>{
 const d={...emptyDay(),water:750,protein:true,steps:10000,health:cleanHealthDay(snapshot)};
 const state={version:1,settings:{...DEFAULTS},days:{'2026-10-17':d}};
 const restored=normalize(JSON.parse(JSON.stringify(state)));
 assert.deepEqual(restored.days['2026-10-17'].health,d.health);
 assert.equal(restored.days['2026-10-17'].water,750);
 state.days['2026-10-17'].health.steps=-50;
 assert.equal(normalize(state).days['2026-10-17'].water,750);
 assert.equal(normalize(state).days['2026-10-17'].health,undefined);
});
