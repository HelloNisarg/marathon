import test from 'node:test';
import assert from 'node:assert/strict';
import {activityHistory,cleanLiveSessions,mergeLive,runningSummary} from './dist/activity.mjs';
import {normalize,DEFAULTS,emptyDay} from './dist/core.mjs';
import {GUIDE,requiredSets,cleanStrength} from './dist/strength.mjs';
const date='2026-10-17',start='2026-10-17T07:00:00Z';
const live={id:'d47890e0-7c69-4c78-ab41-e7203993400c',date,type:'Run',meters:3000,elapsedMs:1800000,start,end:'2026-10-17T07:30:00Z'};
const health={id:'health-77',type:'run',start,end:'2026-10-17T07:30:00Z',minutes:30,km:3};
test('combined activity history suppresses confident cross-source duplicates and does not sum manual distance',()=>{
 const state={version:1,settings:{...DEFAULTS},days:{[date]:{...emptyDay(),health:{steps:8000,sessions:[health]},km:3,minutes:30,workout:true}},liveSessions:[live]};
 const rows=activityHistory(state),summary=runningSummary(rows);
 assert.equal(rows.length,2);assert.equal(rows[0].source,'Samsung Health');assert.equal(rows[1].possiblyDuplicate,true);
 assert.deepEqual(summary,{runs:1,km:3});
 state.liveSessions=[{...live,id:'994a5e10-d749-4bae-a930-191f2f512565',start:'2026-10-17T17:00:00Z'}];
 assert.equal(runningSummary(activityHistory(state)).runs,2,'a second real run on the same day remains separate');
});
test('native live records survive a single planner backup and repeated resume merges',()=>{
 const original={version:1,settings:{...DEFAULTS},days:{[date]:{...emptyDay(),water:750,strength:{counts:[2,1,0,0,0,0]}}},liveSessions:[live]};
 const restored=normalize(JSON.parse(JSON.stringify(original)));
 assert.deepEqual(restored.liveSessions,[{id:live.id,date,type:'Run',meters:3000,elapsedMs:1800000,start,end:live.end}]);
 assert.deepEqual(restored.days[date].strength.counts,[2,1,0,0,0,0]);
 assert.equal(mergeLive(restored.liveSessions,[live,live]).length,1);
 assert.equal(cleanLiveSessions([{...live,meters:-2}, {...live,id:'bad'}]).length,0);
});
test('guided exercises provide concrete instructions and bounded saved sets',()=>{
 for(const name of ['Bodyweight squat','Wall or knee push-up','Glute bridge','Standing calf raise','Dead bug','Prone W raise','Supported split squat','Bodyweight hip hinge','Bird dog','Side plank from knees']){
  assert.equal(GUIDE[name].steps.length,3);assert.ok(GUIDE[name].check&&GUIDE[name].easier);
 }
 assert.equal(requiredSets(1,0),2);assert.equal(requiredSets(5,0),3);
 assert.deepEqual(cleanStrength({counts:[9,-2,2]}).counts,[3,0,2,0,0,0]);
});
test('a checked strength day is never counted as a manual run',()=>{
 const state={version:1,settings:{...DEFAULTS},days:{'2026-11-03':{...emptyDay(),workout:true}},liveSessions:[]};
 assert.equal(activityHistory(state).length,0);
});
