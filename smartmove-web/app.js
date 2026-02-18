// === Config ===
let API = "http://localhost:8080";

// === DOM helpers ===
const $ = (id) => document.getElementById(id);

function toast(type, title, msg){
  const wrap = $("toasts");
  const el = document.createElement("div");
  el.className = `toast ${type||""}`;
  el.innerHTML = `
    <div class="bar"></div>
    <div>
      <p class="t">${title}</p>
      <p class="m">${msg}</p>
    </div>
  `;
  wrap.appendChild(el);
  setTimeout(()=>{ el.style.opacity = "0"; el.style.transform="translateY(6px)"; }, 3200);
  setTimeout(()=>{ el.remove(); }, 3600);
}

function setApiStatus(ok){
  const dot = $("apiDot");
  dot.classList.remove("ok","bad");
  dot.classList.add(ok ? "ok" : "bad");
}

async function getJSON(path){
  const res = await fetch(`${API}${path}`);
  const data = await res.json().catch(()=> ({}));
  if(!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

async function postJSON(path, body){
  const res = await fetch(`${API}${path}`,{
    method:"POST",
    headers:{ "Content-Type":"application/json" },
    body: JSON.stringify(body)
  });
  const data = await res.json().catch(()=> ({}));
  if(!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

// === Tabs ===
function setTab(name){
  document.querySelectorAll(".tab").forEach(t=>{
    t.classList.toggle("active", t.dataset.tab === name);
  });
  ["register","actions","telemetry"].forEach(n=>{
    $(`pane-${n}`).style.display = (n===name) ? "block" : "none";
  });
}
$("tabs").addEventListener("click",(e)=>{
  const tab = e.target.closest(".tab");
  if(!tab) return;
  setTab(tab.dataset.tab);
});

// === Recent vehicles ===
const RECENT_KEY = "smartmove_recent_vehicle_ids";

function loadRecent(){
  try{
    const arr = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
    return Array.isArray(arr) ? arr : [];
  }catch{ return []; }
}
function saveRecent(arr){
  localStorage.setItem(RECENT_KEY, JSON.stringify(arr.slice(0,10)));
}
function addRecent(id){
  if(!id) return;
  const arr = loadRecent().filter(x=>x!==id);
  arr.unshift(id);
  saveRecent(arr);
  renderRecent();
}
function renderRecent(){
  const list = $("recentList");
  const arr = loadRecent();
  list.innerHTML = "";
  if(arr.length===0){
    const empty = document.createElement("div");
    empty.className="small muted";
    empty.textContent="No recent vehicles yet. Register one to begin.";
    list.appendChild(empty);
    return;
  }
  arr.forEach(id=>{
    const item = document.createElement("div");
    item.className="item";
    item.innerHTML = `
      <div class="id">${id}</div>
      <div style="display:flex;gap:8px">
        <button data-act="use">Use</button>
        <button data-act="view">View</button>
      </div>
    `;
    item.addEventListener("click", async (e)=>{
      const act = e.target?.dataset?.act;
      if(!act) return;
      if(act==="use"){
        $("vehicleId").value = id;
        $("telVehicleId").value = id;
        toast("good","Selected","Vehicle ID filled into forms.");
      }
      if(act==="view"){
        $("vehicleId").value = id;
        await fetchVehicle(id);
      }
    });
    list.appendChild(item);
  });
}
$("btnClear").addEventListener("click", ()=>{
  localStorage.removeItem(RECENT_KEY);
  renderRecent();
  toast("","Cleared","Recent list cleared.");
});

// === Live view state ===
let currentVehicleId = null;
let autoTimer = null;

function setStateBadge(state){
  const badge = $("stateBadge");
  badge.classList.remove("b-good","b-warn","b-bad");
  if(!state){
    $("stateText").textContent="—";
    return;
  }
  $("stateText").textContent = state;

  // styling hints
  if(state==="AVAILABLE") badge.classList.add("b-good");
  else if(state==="IN_USE" || state==="RESERVED") badge.classList.add("b-warn");
  else if(state==="EMERGENCY_LOCK") badge.classList.add("b-bad");
  else badge.classList.add("b-warn");
}

function setKV(v){
  $("kvId").textContent = v?.id || "—";
  $("kvType").textContent = v?.type || "—";
  $("kvCity").textContent = v?.city || "—";

  const tel = v?.telemetry;
  if(!tel){
    $("kvTel").textContent = "—";
  }else{
    const mini = `lat=${tel.latitude?.toFixed?.(4) ?? tel.latitude}, lon=${tel.longitude?.toFixed?.(4) ?? tel.longitude}, batt=${tel.batteryPercent}%, temp=${tel.temperatureC}°C`;
    $("kvTel").textContent = mini;
  }

  setStateBadge(v?.state);
}

async function fetchVehicle(id){
  if(!id) throw new Error("Missing vehicle id");
  currentVehicleId = id;
  try{
    const v = await getJSON(`/vehicle?id=${encodeURIComponent(id)}`);
    setKV(v);
    addRecent(id);
    toast("good","Vehicle Loaded", `State: ${v.state}`);
  }catch(e){
    setKV(null);
    setStateBadge(null);
    toast("bad","Fetch Failed", e.message);
    throw e;
  }
}

// === API health ===
async function health(){
  try{
    await getJSON("/health");
    setApiStatus(true);
    return true;
  }catch{
    setApiStatus(false);
    return false;
  }
}

// === Bind sliders ===
function bindRange(id, outId){
  const el = $(id);
  const out = $(outId);
  const sync = ()=> out.textContent = el.value;
  el.addEventListener("input", sync);
  sync();
}
bindRange("bat","batVal");
bindRange("temp","tempVal");

// === Buttons ===
$("btnRegister").addEventListener("click", async ()=>{
  const type = $("regType").value;
  const city = $("regCity").value;

  try{
    const ok = await health();
    if(!ok) throw new Error("API not reachable. Start smartmove-api on port 8080.");

    const res = await postJSON("/vehicles", { type, city });
    $("vehicleId").value = res.id;
    $("telVehicleId").value = res.id;
    addRecent(res.id);
    toast("good","Registered", `New vehicle id created.`);
    await fetchVehicle(res.id);
  }catch(e){
    toast("bad","Register Failed", e.message);
  }
});

$("btnFetch").addEventListener("click", async ()=>{
  const id = $("vehicleId").value.trim();
  if(!id) return toast("bad","Missing ID","Paste a vehicleId first.");
  try{
    await fetchVehicle(id);
  }catch{}
});

$("btnReserve").addEventListener("click", async ()=>{
  const vehicleId = $("vehicleId").value.trim();
  const city = $("actionCity").value;
  if(!vehicleId) return toast("bad","Missing ID","Paste a vehicleId first.");
  try{
    await postJSON("/reserve",{ vehicleId, city });
    toast("good","Reserved","Vehicle reserved.");
    await fetchVehicle(vehicleId);
  }catch(e){
    toast("bad","Reserve Failed", e.message);
  }
});

$("btnStart").addEventListener("click", async ()=>{
  const vehicleId = $("vehicleId").value.trim();
  const city = $("actionCity").value;
  if(!vehicleId) return toast("bad","Missing ID","Paste a vehicleId first.");
  try{
    await postJSON("/start",{ vehicleId, city });
    toast("good","Rental Started","Vehicle moved to IN_USE.");
    await fetchVehicle(vehicleId);
  }catch(e){
    toast("bad","Start Failed", e.message);
  }
});

$("btnEnd").addEventListener("click", async ()=>{
  const vehicleId = $("vehicleId").value.trim();
  if(!vehicleId) return toast("bad","Missing ID","Paste a vehicleId first.");
  try{
    await postJSON("/end",{ vehicleId });
    toast("good","Rental Ended","Vehicle ended and state updated.");
    await fetchVehicle(vehicleId);
  }catch(e){
    toast("bad","End Failed", e.message);
  }
});

$("btnTelemetry").addEventListener("click", async ()=>{
  const vehicleId = $("telVehicleId").value.trim();
  if(!vehicleId) return toast("bad","Missing ID","Paste a vehicleId first.");

  const latitude = parseFloat($("lat").value);
  const longitude = parseFloat($("lon").value);
  const batteryPercent = parseInt($("bat").value,10);
  const temperatureC = parseFloat($("temp").value);
  const helmetPresent = $("helmet").value === "true";
  const fault = $("fault").value === "true";
  const movementDetected = $("movementDetected").value === "true";


  try{
    await postJSON("/telemetry", {
      vehicleId,
      latitude,
      longitude,
      batteryPercent,
      temperatureC,
      helmetPresent,
      movementDetected,
      fault
    });
    toast("good","Telemetry Sent","Queued to backend telemetry worker.");
    // give the worker a moment then refresh
    setTimeout(()=> fetchVehicle(vehicleId).catch(()=>{}), 250);
  }catch(e){
    toast("bad","Telemetry Failed", e.message);
  }
});

// Auto-refresh
$("btnAuto").addEventListener("click", ()=>{
  const enabled = !!autoTimer;
  if(enabled){
    clearInterval(autoTimer);
    autoTimer = null;
    $("btnAuto").textContent = "Enable Auto-Refresh";
    $("refreshHint").textContent = "Auto-refresh: OFF";
    toast("","Auto-refresh","Disabled.");
  }else{
    autoTimer = setInterval(async ()=>{
      if(!currentVehicleId) return;
      try{ await fetchVehicle(currentVehicleId); }catch{}
    }, 1500);
    $("btnAuto").textContent = "Disable Auto-Refresh";
    $("refreshHint").textContent = "Auto-refresh: ON (1.5s)";
    toast("","Auto-refresh","Enabled (1.5s).");
  }
});

// Startup
(async function init(){
  $("apiUrl").textContent = API;
  renderRecent();
  const ok = await health();
  toast(ok ? "good":"bad", "API Status", ok ? "Connected." : "Not reachable. Start smartmove-api.");
})();
