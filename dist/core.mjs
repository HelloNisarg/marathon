import {cleanHealthDay} from './health.mjs';
import {cleanLiveSessions} from './activity.mjs';
import {cleanStrength} from './strength.mjs';
export const START='2026-11-01', TRAINING='2026-11-02', END='2027-02-01';
export const DEFAULTS={name:'Nisarg',waterGoal:2000,stepGoal:10000,runTime:'07:00',strengthTime:'18:30',waterStart:'09:00',waterEnd:'19:00',waterInterval:120,waterReminders:true,activityReminders:true,browserReminders:false};
export const localDate=(d=new Date())=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
export const dateObj=s=>new Date(s+'T12:00:00');
export const addDays=(s,n)=>{const d=dateObj(s);d.setDate(d.getDate()+n);return localDate(d)};
export const daysBetween=(a,b)=>(Date.parse(b+'T12:00:00Z')-Date.parse(a+'T12:00:00Z'))/86400000;
export const weekOf=s=>s<TRAINING?0:s>END?14:s===END?13:Math.floor(daysBetween(TRAINING,s)/7)+1;
export const fmt=(s,options={month:'short',day:'numeric'})=>dateObj(s).toLocaleDateString('en-IN',options);
export const interval=(r,w,n)=>({text:`${r<1?Math.round(r*60)+' sec':r+' min'} jog / ${w} min walk × ${n}`,minutes:(r+w)*n,kind:'interval'});
const easy=n=>({text:`${n} min easy run / walk`,minutes:n,kind:'easy'});
export const RUNS=[
 [interval(1,2,8),interval(1,2,8),interval(1,2,8)],
 [interval(1.5,2,7),interval(1.5,2,7),interval(1.5,2,7)],
 [interval(2,2,7),interval(2,2,7),interval(2,2,7)],
 [interval(2,2,7),interval(2,2,7),interval(2,2,7)],
 [interval(3,2,6),interval(3,2,6),interval(3,2,6)],
 [interval(5,2,4),interval(5,2,4),interval(5,2,5)],
 [interval(8,2,3),interval(8,2,3),interval(10,2,3)],
 [interval(8,2,3),interval(8,2,3),interval(8,2,3)],
 [interval(12,2,2),interval(15,2,2),easy(40)],
 [easy(25),easy(30),easy(45)],
 [easy(30),easy(30),easy(50)],
 [easy(30),easy(30),{text:'Up to 55 min or 6.5 km, whichever comes first',minutes:55,kind:'easy'}],
 [easy(20),easy(15),null]
];
export const EXERCISES={A:[['Bodyweight squat','8–12 reps','Hips back, feet planted.'],['Wall or knee push-up','6–12 reps','Lower slowly with your body aligned.'],['Glute bridge','10–15 reps','Squeeze glutes without arching your back.'],['Standing calf raise','12–20 reps','Rise and lower slowly; use a wall for balance.'],['Dead bug','6–10 / side','Move opposite arm and leg; keep your back steady.'],['Prone W raise','8–12 reps','Lie face down and gently lift bent arms into a W.']],B:[['Supported split squat','6–10 / leg','Use a wall for balance and a comfortable depth.'],['Wall or knee push-up','6–12 reps','Choose a variation you can control.'],['Bodyweight hip hinge','10–15 reps','Soften knees and push hips back.'],['Bird dog','6–10 / side','Extend opposite arm and leg without twisting.'],['Side plank from knees','15–30 sec / side','Keep hips lifted and body aligned.'],['Prone W raise','8–12 reps','Keep your neck relaxed and use a small lift.']]};
export function session(date){
 const week=weekOf(date),dow=dateObj(date).getDay();
 if(date===END)return {type:'run',title:'Your 7 km day',detail:'Easy effort. Walking breaks welcome.',minutes:60,goal:true,week:13};
 if(date<TRAINING)return {type:'prep',title:'Start with a little movement',detail:'A comfortable walk. Set your baseline and find your rhythm.',minutes:20,week:0};
 if(date>END)return {type:'recovery',title:'Keep your rhythm',detail:'Choose gentle movement and plan your next chapter.',minutes:0,week:14};
 if(dow===0 || (week===13 && (dow===5||dow===6)))return {type:'recovery',title:'Make room for recovery',detail:'Rest, or take a short gentle walk. No step quota.',minutes:0,week};
 if(dow===4)return {type:'mobility',title:'A little room to move',detail:'Comfortable walking + 10 minutes of gentle mobility.',minutes:10,week};
 if(dow===2||dow===5){const variant=dow===2?'A':'B';return {type:'strength',title:`Home strength ${variant}`,detail:week===13?'One light session. Keep plenty in reserve.':'Full body · no equipment · steady, controlled reps',minutes:week<3?20:30,variant,week};}
 const run=RUNS[week-1][{1:0,3:1,6:2}[dow]];
 return {type:'run',title:dow===6?'Your longer, easy outing':'Find your easy pace',detail:run.text,minutes:run.minutes+10,week};
}
export function setsFor(week,index){return week<=2||week===13?'1–2':week<=4||index>=3?'2':'3';}
export function emptyDay(){return {water:0,waterEntries:[],steps:0,sleep:0,protein:false,workout:false,km:0,minutes:0,weight:null,waist:null,note:'',mood:'',custom:[],exercises:{},strength:null};}
export function targets(state,date){const saved=state.days[date]?.targets;return saved||{water:state.settings.waterGoal,steps:session(date).type==='recovery'?0:state.settings.stepGoal};}
export function goals(state,date){const d=state.days[date]||emptyDay(),t=targets(state,date);const list=[{id:'workout',label:session(date).type==='recovery'?'Give yourself a recovery day':'Complete today’s movement',done:!!d.workout},{id:'water',label:'Hydration goal',done:d.water>=t.water},{id:'protein',label:'Protein with my meals',done:!!d.protein},{id:'sleep',label:'At least 7 hours of sleep',done:d.sleep>=7}];if(t.steps>0)list.push({id:'steps',label:'Daily steps',done:d.steps>=t.steps});for(const c of d.custom||[])list.push({id:c.id,label:c.label,done:!!c.done,custom:true});return list;}
export function dayScore(state,date){const g=goals(state,date);return {done:g.filter(x=>x.done).length,total:g.length};}
export function waterTimes(s){if(!s.waterReminders)return [];const minutes=v=>Number(v.slice(0,2))*60+Number(v.slice(3));const start=minutes(s.waterStart),end=minutes(s.waterEnd),out=[];for(let m=start;m<=end;m+=s.waterInterval)out.push(`${String(Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}`);return out;}
const icsEscape=s=>String(s).replace(/\\/g,'\\\\').replace(/\n/g,'\\n').replace(/,/g,'\\,').replace(/;/g,'\\;');
export function calendar(state,today=localDate(),url=''){
 const settings=state.settings,start=today>START?today:START;
 const lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Stride//7K Journey//EN','CALSCALE:GREGORIAN','METHOD:PUBLISH','X-WR-CALNAME:Stride reminders'];
 let count=0;
 const event=(id,date,time,title,description,duration,rrule)=>{count++;lines.push('BEGIN:VEVENT',`UID:${id}@stride-seven`, 'DTSTAMP:20261017T000000Z',`DTSTART:${date.replaceAll('-','')}T${time.replace(':','')}00`,`DURATION:PT${duration}M`,`SUMMARY:${icsEscape(title)}`,`DESCRIPTION:${icsEscape(description)}`,'TRANSP:TRANSPARENT');if(url)lines.push(`URL:${url}`);if(rrule)lines.push(`RRULE:${rrule}`);lines.push('BEGIN:VALARM','TRIGGER:PT0M','ACTION:DISPLAY',`DESCRIPTION:${icsEscape(title)}`,'END:VALARM','END:VEVENT')};
 if(start<=END){
  if(settings.activityReminders){for(let date=start;date<=END;date=addDays(date,1)){const s=session(date);if(['run','strength','mobility'].includes(s.type)){const time=s.type==='strength'?settings.strengthTime:settings.runTime;event(`activity-${date}`,date,time,`Stride: ${s.goal?'7 km goal':s.type==='run'?'Easy run / walk':s.type==='strength'?s.title:'Walk & mobility'}`,`${s.detail}\n${s.type==='run'?'Walk 5 minutes before and after. Keep a conversational pace. ':''}Repeat an easier week if needed. Open Stride to log your day.`,Math.ceil(s.minutes));}}}
  for(const time of waterTimes(settings))event(`water-${time.replace(':','')}`,start,time,'Stride: water break','Take a comfortable drink and log it in Stride. Adjust to thirst, heat and activity.',5,`FREQ=DAILY;UNTIL=20270201T235959`);
 }
 lines.push('END:VCALENDAR');
 // Fold at 75 UTF-8 octets, as required by iCalendar.
 const folded=lines.flatMap(line=>{const result=[];let part='';for(const char of line){if(new TextEncoder().encode(part+char).length>75){result.push(part);part=' '+char}else part+=char;}result.push(part);return result});
 return {text:folded.join('\r\n')+'\r\n',count};
}
export function normalize(input){
 if(!input||input.version!==1||typeof input.days!=='object'||Array.isArray(input.days)||!input.days)throw Error('Choose a Stride backup JSON file.');
 const number=(v,min,max,fallback)=>Number.isFinite(Number(v))&&Number(v)>=min&&Number(v)<=max?Number(v):fallback;
 const settings={...DEFAULTS},s=input.settings||{};
 settings.name=typeof s.name==='string'?s.name.slice(0,40):DEFAULTS.name;
 settings.waterGoal=number(s.waterGoal,500,5000,2000);settings.stepGoal=number(s.stepGoal,1000,30000,10000);
 for(const k of ['runTime','strengthTime','waterStart','waterEnd'])if(/^([01]\d|2[0-3]):[0-5]\d$/.test(s[k]))settings[k]=s[k];
 settings.waterInterval=[60,90,120,180,240].includes(s.waterInterval)?s.waterInterval:120;
 for(const k of ['waterReminders','activityReminders','browserReminders'])if(typeof s[k]==='boolean')settings[k]=s[k];
 if(settings.waterStart>settings.waterEnd){settings.waterStart='09:00';settings.waterEnd='19:00';}
 const days={};
 for(const [key,value] of Object.entries(input.days).slice(0,5000)){
  if(!/^20\d{2}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(key)||!value||typeof value!=='object'||localDate(dateObj(key))!==key)continue;
  const d=emptyDay();for(const [k,max] of [['water',20000],['steps',100000],['sleep',24],['km',1000],['minutes',1440]])d[k]=number(value[k],0,max,0);
  d.weight=value.weight==null?null:number(value.weight,20,400,null);d.waist=value.waist==null?null:number(value.waist,30,250,null);
  for(const k of ['protein','workout'])d[k]=!!value[k];
  d.note=typeof value.note==='string'?value.note.slice(0,1000):'';d.mood=['great','okay','tired'].includes(value.mood)?value.mood:'';
  d.waterEntries=Array.isArray(value.waterEntries)?value.waterEntries.filter(x=>Number.isFinite(x)&&x>0&&x<=2000).slice(-100):[];
  d.custom=Array.isArray(value.custom)?value.custom.slice(0,12).map((x,i)=>({id:`custom-${i}`,label:String(x?.label||'My goal').slice(0,80),done:!!x?.done})):[];
  if(value.exercises&&typeof value.exercises==='object')for(let i=0;i<6;i++)d.exercises[i]=!!value.exercises[i];
  if(value.targets)d.targets={water:number(value.targets.water,500,5000,settings.waterGoal),steps:number(value.targets.steps,0,30000,settings.stepGoal)};
  if(value.health){try{d.health=cleanHealthDay(value.health)}catch{}}
  d.strength=cleanStrength(value.strength);
  days[key]=d;
 }
 return {version:1,settings,days,liveSessions:cleanLiveSessions(input.liveSessions)};
}
