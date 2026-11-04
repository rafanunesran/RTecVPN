const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const $ = (id) => document.getElementById(id);

let status = null;

function render(s) {
  status = s;
  document.body.className = s.state;
  $("stateLabel").textContent = { up: "Conectado", connecting: "Conectando…", down: "Desconectado" }[s.state];
  $("toggle").disabled = !s.has_config || s.state === "connecting";
  $("toggle").setAttribute("aria-label", s.state === "up" ? "Desconectar" : "Conectar");
  $("server").textContent = s.server || "Nenhum configurado";
  $("ssid").textContent = s.ssid || "—";
  $("autoLabel").textContent = s.settings.auto_connect ? "Ligada" : "Desligada";
  $("autoSwitch").checked = s.settings.auto_connect;
  $("error").hidden = !s.last_error;
  $("error").textContent = s.last_error || "";
  $("addCurrent").disabled = !s.ssid;
  $("addCurrent").textContent = s.ssid ? `Adicionar rede atual (${s.ssid})` : "Sem Wi-Fi detectado";

  const list = $("ssidList");
  list.innerHTML = "";
  if (!s.settings.trusted_ssids.length) {
    const li = document.createElement("li");
    li.className = "empty";
    li.textContent = "Nenhuma rede adicionada";
    list.append(li);
  }
  for (const name of s.settings.trusted_ssids) {
    const li = document.createElement("li");
    const span = document.createElement("span");
    span.textContent = name;
    const rm = document.createElement("button");
    rm.textContent = "Remover";
    rm.onclick = () => saveSettings(s.settings.auto_connect, s.settings.trusted_ssids.filter((x) => x !== name));
    li.append(span, rm);
    list.append(li);
  }
}

function showError(e) {
  $("error").hidden = false;
  $("error").textContent = String(e);
}

async function saveSettings(auto, ssids) {
  render(await invoke("save_settings", { autoConnect: auto, trustedSsids: ssids }));
}

document.querySelectorAll(".tabs button").forEach((b) =>
  b.addEventListener("click", () => {
    document.querySelectorAll(".tabs button, .tab").forEach((el) => el.classList.remove("active"));
    b.classList.add("active");
    $(b.dataset.tab).classList.add("active");
  })
);

$("toggle").addEventListener("click", async () => {
  const goingUp = status.state !== "up";
  render({ ...status, state: "connecting", last_error: null });
  try {
    render(await invoke(goingUp ? "connect" : "disconnect"));
  } catch (e) {
    render(await invoke("get_status"));
    showError(e);
  }
});

$("autoSwitch").addEventListener("change", (e) => {
  if (e.target.checked && !status.has_config) {
    e.target.checked = false;
    return showError("Configure um servidor primeiro.");
  }
  saveSettings(e.target.checked, status.settings.trusted_ssids);
});

$("addCurrent").addEventListener("click", () => {
  if (status.ssid) saveSettings(status.settings.auto_connect, [...status.settings.trusted_ssids, status.ssid]);
});

$("addForm").addEventListener("submit", (e) => {
  e.preventDefault();
  const name = $("ssidInput").value.trim();
  if (!name) return;
  $("ssidInput").value = "";
  saveSettings(status.settings.auto_connect, [...status.settings.trusted_ssids, name]);
});

$("fileInput").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  if (file) $("configText").value = await file.text();
});

$("saveConfig").addEventListener("click", async () => {
  try {
    render(await invoke("import_config", { text: $("configText").value }));
    $("configText").value = "";
    document.querySelector('.tabs button[data-tab="home"]').click();
  } catch (e) {
    showError(e);
    document.querySelector('.tabs button[data-tab="home"]').click();
  }
});

listen("status", (e) => render(e.payload));
invoke("get_status").then(render);
