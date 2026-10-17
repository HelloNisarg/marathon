import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {START,TRAINING,END,DEFAULTS,localDate,addDays,weekOf,session,emptyDay,targets,dayScore,waterTimes,calendar,normalize} from './dist/core.mjs';
const fresh=()=>({version:1,settings:{...DEFAULTS},days:{}});
test('93-day calendar, correct week boundaries and recovery taper',()=>{
 assert.equal(addDays(START,92),END);
 assert.equal(weekOf('2026-11-01'),0);assert.equal(weekOf(TRAINING),1);
 assert.equal(weekOf('2026-12-28'),9);assert.equal(weekOf('2027-01-25'),13);
 assert.equal(session('2026-11-03').variant,'A');assert.equal(session('2026-11-06').variant,'B');
 assert.equal(session('2027-01-29').type,'recovery');assert.equal(session('2027-01-30').type,'recovery');assert.equal(session(END).goal,true);
 let counts={};for(let d=START;d<=END;d=addDays(d,1)){let s=session(d);assert.ok(s.title&&s.detail);counts[s.type]=(counts[s.type]||0)+1;}
 assert.equal(counts.run,39);assert.equal(counts.strength,25);assert.equal(counts.mobility,13);
});
test('goal completion and rest days respect stored targets',()=>{
 let s=fresh();assert.equal(targets(s,'2026-11-08').steps,0);
 s.days[TRAINING]={...emptyDay(),water:2000,steps:10000,sleep:7,protein:true,workout:true,targets:{water:2000,steps:10000}};
 assert.deepEqual(dayScore(s,TRAINING),{done:5,total:5});s.settings.waterGoal=2500;
 assert.equal(targets(s,TRAINING).water,2000);assert.equal(targets(s,'2026-11-03').water,2500);
});
test('reminder calendar covers every workout, includes alarms and valid folds',()=>{
 const s=fresh(),c=calendar(s,'2026-10-17','https://example.test');
 assert.equal(c.count,83);assert.equal((c.text.match(/BEGIN:VALARM/g)||[]).length,83);
 assert.ok(c.text.includes('DTSTART:20261102T070000'));
 assert.ok(c.text.includes('DTSTART:20261103T183000'));
 assert.ok(c.text.includes('DTSTART:20270201T070000'));
 assert.ok(c.text.includes('RRULE:FREQ=DAILY;UNTIL=20270201T235959'));
 for(const line of c.text.split('\r\n'))assert.ok(Buffer.byteLength(line)<=75);
 assert.equal(calendar(s,'2027-02-02').count,0);
 s.settings.waterReminders=false;assert.equal(calendar(s,'2026-10-17').count,77);
 s.settings.activityReminders=false;assert.equal(calendar(s,'2026-10-17').count,0);
});
test('water times respect quiet hours and interval',()=>{
 assert.deepEqual(waterTimes(DEFAULTS),['09:00','11:00','13:00','15:00','17:00','19:00']);
 assert.deepEqual(waterTimes({...DEFAULTS,waterStart:'08:30',waterEnd:'12:00',waterInterval:90}),['08:30','10:00','11:30']);
});
test('backup normalization preserves logs and rejects broken formats',()=>{
 const s=fresh();s.days[START]={...emptyDay(),water:750,waterEntries:[250,500],weight:72.4,custom:[{id:'x',label:'Read 10 pages',done:true}]};
 const restored=normalize(JSON.parse(JSON.stringify(s)));assert.equal(restored.days[START].water,750);assert.equal(restored.days[START].weight,72.4);assert.equal(restored.days[START].custom[0].done,true);
 assert.throws(()=>normalize({}));assert.throws(()=>normalize({version:1,days:null}));
 assert.equal(normalize({version:1,days:{'2026-02-30':{}},settings:{waterGoal:-50}}).settings.waterGoal,2000);
 assert.equal(Object.keys(normalize({version:1,days:{'2026-02-30':{}}}).days).length,0);
});
test('manifest and HTML reference shipped assets',()=>{
 const m=JSON.parse(readFileSync('dist/manifest.webmanifest','utf8'));for(const i of m.icons)assert.ok(existsSync('dist/'+i.src));
 for(const name of ['app.js','core.mjs','style.css','icon.svg'])assert.ok(existsSync('dist/'+name));
});
test('app renders all views and click handlers save, undo and restore local logs',async()=>{
 // Minimal DOM adapter exercises render and interaction logic; not a visual/browser test.
 const elements=new Map(),events=new Map(),windowEvents=new Map(),memory=new Map();
 const element=()=>({innerHTML:'',textContent:'',hidden:false,style:{},dataset:{},open:false,classList:{add(){},remove(){},toggle(){}},addEventListener(){},setAttribute(){},showModal(){this.open=true},close(){this.open=false}});
 globalThis.document={querySelector(sel){if(!elements.has(sel))elements.set(sel,element());return elements.get(sel)},addEventListener(name,fn){events.set(name,fn)},title:''};
 globalThis.window={addEventListener(name,fn){windowEvents.set(name,fn)},scrollTo(){}};
 globalThis.location={hash:'#today',origin:'https://example.test'};
 globalThis.localStorage={getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,v)};
 const realInterval=globalThis.setInterval;globalThis.setInterval=()=>0;
 await import('./dist/app.js');
 const click=async dataset=>events.get('click')({target:{closest:()=>({dataset,disabled:false})}});
 assert.match(elements.get('#main').innerHTML,/Your daily wins/);
 await click({action:'water',amount:'250'});await click({action:'water',amount:'500'});
 let saved=JSON.parse(memory.get('stride-seven-v1'));assert.equal(saved.days[localDate()].water,750);
 await click({action:'undo-water'});saved=JSON.parse(memory.get('stride-seven-v1'));assert.equal(saved.days[localDate()].water,250);
 await click({action:'toggle',key:'protein'});saved=JSON.parse(memory.get('stride-seven-v1'));assert.equal(saved.days[localDate()].protein,true);
 for(const v of ['plan','progress','settings','today']){await click({action:'view',view:v});assert.ok(elements.get('#main').innerHTML.length>1000);assert.ok(!elements.get('#main').innerHTML.includes('undefined'));}
 await click({action:'date',date:addDays(localDate(),1)});const before=memory.get('stride-seven-v1');await click({action:'water',amount:'250'});assert.equal(memory.get('stride-seven-v1'),before,'future logging is blocked');
 await click({action:'session',date:'2026-11-03'});assert.match(elements.get('#modal').innerHTML,/Bodyweight squat/);
 globalThis.setInterval=realInterval;
});
