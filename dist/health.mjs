// Health Connect data stays on-device. Re-imports replace a day's snapshot;
// they never add steps or duplicate sessions from an earlier sync.
const isDate = value => /^20\d{2}-\d{2}-\d{2}$/.test(value) &&
  new Date(value+'T12:00:00Z').toISOString().slice(0,10) === value;
const finite = (value,max) => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= max;
export function cleanHealthDay(value) {
  if (!value || !Array.isArray(value.sessions) || value.sessions.length > 500) throw Error('Invalid activity data.');
  if (value.steps !== null && (!finite(value.steps,100000) || !Number.isInteger(value.steps))) throw Error('Invalid steps.');
  const ids = new Set();
  const sessions = value.sessions.map(s => {
    if (!s || typeof s.id !== 'string' || !s.id || s.id.length>160 || !['run','walk'].includes(s.type) ||
        !finite(s.minutes,1440) || (s.km!==null && !finite(s.km,1000)) ||
        !Number.isFinite(Date.parse(s.start)) || !Number.isFinite(Date.parse(s.end)) ||
        Date.parse(s.end)<=Date.parse(s.start)) throw Error('Invalid exercise session.');
    return {id:s.id,type:s.type,start:new Date(s.start).toISOString(),end:new Date(s.end).toISOString(),km:s.km,minutes:s.minutes};
  }).filter(s => {if(ids.has(s.id))return false;ids.add(s.id);return true;});
  return {steps:value.steps,sessions,syncedAt:typeof value.syncedAt==='string'&&Number.isFinite(Date.parse(value.syncedAt))?new Date(value.syncedAt).toISOString():null};
}
export function parseHealthSync(input,today) {
  if(input?.format!=='stride-health-connect-v1'||input.source!=='com.sec.android.app.shealth'||
      !Array.isArray(input.days)||!input.days.length||input.days.length>7) throw Error('Choose a Stride Samsung Health export.');
  const dates=new Set();
  return input.days.map(d=>{
    if(!isDate(d.date)||d.date>today||dates.has(d.date))throw Error('Invalid or future sync date.');
    dates.add(d.date);
    return {date:d.date,...cleanHealthDay({...d,syncedAt:input.syncedAt})};
  });
}
export function healthRuns(day){return day.health?.sessions.filter(s=>s.type==='run')||[];}
export function runDistance(day){const runs=healthRuns(day);return runs.length?runs.reduce((n,s)=>n+(s.km||0),0):(day.km||0);}
export function runCount(day,plannedRun){const runs=healthRuns(day);return runs.length||(day.workout&&plannedRun?1:0);}
