// One display history across sources. Keep original records intact; suppress only
// highly confident cross-source duplicates when both have start timestamps.
export function cleanLiveSessions(input){if(!Array.isArray(input))return [];
 const seen=new Set(),out=[];
 for(const x of input.slice(-2000)){
  if(!x||typeof x.meters!=='number'||typeof x.elapsedMs!=='number'||typeof x.id!=='string'||!/^[a-zA-Z0-9-]{8,80}$/.test(x.id)||seen.has(x.id)||!['Run','Walk'].includes(x.type)||!/^20\d\d-\d\d-\d\d$/.test(x.date))continue;
  const meters=Number(x.meters),elapsed=Number(x.elapsedMs),start=typeof x.start==='string'&&Number.isFinite(Date.parse(x.start))?x.start:null,end=typeof x.end==='string'&&Number.isFinite(Date.parse(x.end))?x.end:null;
  if(!Number.isFinite(meters)||meters<0||meters>100000||!Number.isFinite(elapsed)||elapsed<0||elapsed>86400000||!Number.isFinite(Date.parse(x.date+'T12:00:00'))||new Date(x.date+'T12:00:00').toISOString().slice(0,10)!==x.date||x.date>`${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}`)continue;
  seen.add(x.id);out.push({id:x.id,date:x.date,type:x.type,meters,elapsedMs:elapsed,start,end});
 }
 return out;
}
export function mergeLive(existing,incoming){const byId=new Map(cleanLiveSessions(existing).map(x=>[x.id,x]));for(const x of cleanLiveSessions(incoming))byId.set(x.id,x);return [...byId.values()].slice(-2000);}
export function activityHistory(state){
 const health=[];for(const [date,d] of Object.entries(state.days||{})){
  for(const s of d.health?.sessions||[])health.push({id:'health:'+s.id,date,type:s.type,km:s.km,minutes:s.minutes,start:s.start,source:'Samsung Health'});
 }
 const live=cleanLiveSessions(state.liveSessions).map(x=>({id:'live:'+x.id,date:x.date,type:x.type.toLowerCase(),km:x.meters/1000,minutes:x.elapsedMs/60000,start:x.start,source:'Live tracking'}));
 const healthIds=new Set(health.map(x=>x.id));const uniqueLive=live.filter(l=>!health.some(h=>h.type===l.type&&h.date===l.date&&l.start&&h.start&&Math.abs(Date.parse(l.start)-Date.parse(h.start))<=10*60000&&h.km!==null&&Math.abs(l.km-h.km)<=Math.max(.2,h.km*.15)&&Math.abs(l.minutes-h.minutes)<=10));
 const covered=new Set([...health,...uniqueLive].map(x=>x.date+':'+x.type));const manual=Object.entries(state.days||{}).filter(([date,d])=>d.km>0||d.minutes>0||d.workout&&date>='2026-11-02'&&[1,3,6].includes(new Date(date+'T12:00:00').getDay())).map(([date,d])=>({id:'manual:'+date,date,type:'run',km:d.km,minutes:d.minutes,start:null,source:d.km||d.minutes?'Manual log':'Planner check-in',possiblyDuplicate:covered.has(date+':run')}));
 return [...health,...uniqueLive,...manual].sort((a,b)=>b.date.localeCompare(a.date)||String(b.start||'').localeCompare(String(a.start||'')));
}
export function runningSummary(history){const counted=history.filter(x=>x.type==='run'&&!x.possiblyDuplicate);return {runs:counted.length,km:counted.reduce((n,x)=>n+(Number.isFinite(x.km)?x.km:0),0)};}
