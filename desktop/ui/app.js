const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const $ = (id) => document.getElementById(id);
const el = (tag, props = {}, ...kids) => {
  const e = Object.assign(document.createElement(tag), props);
  e.append(...kids);
  return e;
};

const ICON_SERVER = '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/></svg>';
const ICON_WIFI = '<svg viewBox="0 0 24 24"><path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0"/><circle cx="12" cy="19" r="1"/></svg>';
const ONBOARDING = [
  { title: "Privacidade com WireGuard", text: "Criptografia moderna e rápida protegendo tudo o que você faz em redes públicas.",
    icon: '<svg viewBox="0 0 24 24"><path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z"/></svg>' },
  { title: "Servidores em vários países", text: "Sem cadastro: escolha entre centenas de servidores gratuitos do VPN Gate, ou use o seu próprio.", icon: ICON_SERVER },
  { title: "Auto-conexão no Wi‑Fi", text: "Escolha as redes onde a VPN liga sozinha, e ela desliga quando você sai delas.", icon: ICON_WIFI },
];

let status = null;
let serverTab = "free";
let country = null;
const flagOf = (code) => code && code.length === 2 ? String.fromCodePoint(...[...code.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65)) : "🌐";

function vgCard(v, s, { best = false, label } = {}) {
  const selected = !best && `vg:${v.ip}` === s.selected;
  const card = el("div", { className: `card server${selected ? " selected" : ""}${best ? " best" : ""}` });
  if (best) {
    const ico = el("div", { className: "ico" }); ico.innerHTML = '<svg viewBox="0 0 24 24"><path d="M13 2 3 14h9l-1 8 10-12h-9z"/></svg>';
    card.append(ico, el("div", { className: "info" }, el("b", { textContent: label }), el("span", { textContent: "Maior pontuação de velocidade e estabilidade" })));
  } else {
    const pills = el("div", { className: "pills" });
    if (v.ping > 0) pills.append(el("span", { className: `pill ${v.ping < 80 ? "ok" : "warn"}`, textContent: `${v.ping} ms` }));
    pills.append(el("span", { className: "pill info", textContent: `${Math.round(v.speed / 1e6)} Mbps` }));
    if (`vg:${v.ip}` === s.active) pills.append(el("span", { className: "pill ok", textContent: "Em uso" }));
    card.append(el("div", { className: "flag", textContent: flagOf(v.code) }),
      el("div", { className: "info" }, el("b", { textContent: v.country }), el("span", { textContent: `${v.host} · ${v.transport}` }), pills));
    if (selected) card.append(el("div", { className: "check", textContent: "✓" }));
  }
  card.onclick = () => call("select_vg", { ip: v.ip });
  return card;
}

function renderFree(s) {
  const list = s.vg_servers;
  const byCountry = {};
  list.forEach((v) => (byCountry[v.code] ||= []).push(v));
  const countries = Object.entries(byCountry).sort((a, b) => b[1].length - a[1].length);
  $("vgCount").textContent = s.vg_loading ? "Atualizando lista…" : list.length ? `${list.length} servidores em ${countries.length} países` : "Lista do VPN Gate";
  const chip = (label, code) => {
    const b = el("button", { textContent: label, className: country === code ? "on" : "" });
    b.onclick = () => { country = code; renderFree(status); };
    return b;
  };
  $("countryChips").replaceChildren(chip("Todos", null), ...countries.map(([code, vs]) => chip(`${flagOf(code)} ${vs[0].country}`, code)));
  $("vgError").replaceChildren(...(s.vg_error && !list.length ? [el("div", { className: "notice", textContent: s.vg_error })] : []));
  const shown = list.filter((v) => !country || v.code === country);
  const cards = shown.slice(0, 150).map((v) => vgCard(v, s));
  if (shown.length) cards.unshift(vgCard(shown[0], s, { best: true, label: country ? `Melhor servidor em ${shown[0].country}` : "Melhor servidor" }));
  $("vgList").replaceChildren(...cards);
}
let obPage = 0;

// ------------------------------------------------------------ formatação
const fmtBytes = (b) => b == null ? "—" : b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : b < 1073741824 ? `${(b / 1048576).toFixed(1)} MB` : `${(b / 1073741824).toFixed(2)} GB`;
const pad = (n) => String(n).padStart(2, "0");
const fmtDur = (ms) => { const s = Math.floor(ms / 1000); return `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`; };

// ------------------------------------------------------------ render
function applyTheme(theme) {
  document.documentElement.dataset.theme = theme || "system";
  document.querySelectorAll("#themeChips button").forEach((b) => b.classList.toggle("on", b.dataset.theme === theme));
}

function renderOnboarding() {
  const step = ONBOARDING[obPage];
  $("obIcon").innerHTML = step.icon;
  $("obTitle").textContent = step.title;
  $("obText").textContent = step.text;
  $("obDots").replaceChildren(...ONBOARDING.map((_, i) => el("i", { className: i === obPage ? "on" : "" })));
  $("obNext").textContent = obPage === ONBOARDING.length - 1 ? "Começar" : "Próximo";
  $("obSkip").hidden = obPage === ONBOARDING.length - 1;
}

function serverCard(p, s, { menu = true, onClick } = {}) {
  const selected = p.id === s.selected;
  const card = el("div", { className: `card server${selected ? " selected" : ""}` });
  const ico = el("div", { className: "ico" }); ico.innerHTML = ICON_SERVER;
  const info = el("div", { className: "info" }, el("b", { textContent: p.name }), el("span", { textContent: p.endpoint || "—" }));
  if (p.id === s.active) info.append(el("em", { textContent: "Em uso agora" }));
  card.append(ico, info);
  if (selected) card.append(el("div", { className: "check", textContent: "✓" }));
  if (menu) {
    const btn = el("button", { className: "menu-btn", textContent: "⋮", ariaLabel: "Mais opções" });
    btn.onclick = (e) => { e.stopPropagation(); openMenu(card, p); };
    card.append(btn);
  }
  card.onclick = onClick || (() => call("select_profile", { id: p.id }));
  return card;
}

function openMenu(card, p) {
  closeMenus();
  const m = el("div", { className: "menu" });
  const rename = el("button", { textContent: "Renomear" });
  rename.onclick = () => {
    const input = el("input", { value: p.name });
    const b = card.querySelector(".info b");
    b.replaceWith(input);
    input.onclick = (e) => e.stopPropagation();
    input.focus();
    input.select();
    const done = () => input.value.trim() && input.value !== p.name ? call("rename_profile", { id: p.id, name: input.value }) : render(status);
    input.onkeydown = (e) => { if (e.key === "Enter") input.blur(); if (e.key === "Escape") { input.value = p.name; input.blur(); } };
    input.onblur = done;
  };
  const del = el("button", { className: "danger", textContent: "Excluir" });
  del.onclick = async (e) => {
    if (del.dataset.confirm !== "1") {
      e.stopPropagation();
      del.dataset.confirm = "1";
      del.textContent = "Confirmar exclusão";
      return;
    }
    if (p.id === status.active) await call("disconnect");
    call("delete_profile", { id: p.id });
  };
  m.append(rename, del);
  card.style.position = "relative";
  m.style.top = "48px";
  card.append(m);
  setTimeout(() => document.addEventListener("click", closeMenus, { once: true }));
}
const closeMenus = () => document.querySelectorAll(".menu").forEach((m) => m.remove());

function render(s) {
  status = s;
  applyTheme(s.settings.theme);
  const onboarding = !s.settings.onboarding_done;
  $("onboarding").hidden = !onboarding;
  $("app").hidden = onboarding;
  if (onboarding) return renderOnboarding();

  document.body.className = s.state;
  $("stateLabel").textContent = { up: "Conectado", connecting: "Conectando…", down: "Desconectado" }[s.state];
  $("toggle").disabled = !s.selected || s.state === "connecting";
  $("toggle").setAttribute("aria-label", s.state === "up" ? "Desconectar" : "Conectar");
  renderTimer();

  const current = s.profiles.find((p) => p.id === s.selected);
  const vg = s.kind === "openvpn" ? s.settings.selected_vg : null;
  if (vg) {
    $("homeServer").replaceChildren(vgCard(vg, s));
    $("homeServer").firstChild.onclick = () => goTab("servers");
  } else if (current) {
    $("homeServer").replaceChildren(serverCard(current, s, { menu: false, onClick: () => goTab("servers") }));
  } else {
    const add = el("button", { className: "primary", textContent: "+ Adicionar servidor" });
    add.onclick = openSheet;
    $("homeServer").replaceChildren(add);
  }
  $("rx").textContent = s.state === "up" ? fmtBytes(s.rx_bytes) : "—";
  $("tx").textContent = s.state === "up" ? fmtBytes(s.tx_bytes) : "—";
  $("error").hidden = !s.last_error;
  $("error").textContent = s.last_error || "";

  // Servidores
  document.querySelectorAll("#serverTabs button").forEach((b) => b.classList.toggle("on", b.dataset.st === serverTab));
  $("freePane").hidden = serverTab !== "free";
  $("serverList").hidden = serverTab !== "mine";
  renderFree(s);
  if (s.profiles.length) {
    $("serverList").replaceChildren(...s.profiles.map((p) => serverCard(p, s)));
  } else {
    const add = el("button", { className: "primary", textContent: "Adicionar servidor" });
    add.onclick = openSheet;
    $("serverList").replaceChildren(
      el("p", { className: "muted center", textContent: "Nenhum servidor ainda. Rode o add-client.sh no seu servidor e importe o arquivo gerado." }), add);
  }
  $("fab").hidden = !s.profiles.length || serverTab !== "mine";

  // Wi-Fi
  const st = s.settings;
  $("autoSwitch").checked = st.auto_connect;
  $("leaveSwitch").checked = st.disconnect_on_leave;
  const known = st.trusted_ssids.includes(s.ssid);
  const cur = el("div", { className: "server" });
  const ico = el("div", { className: "ico" }); ico.innerHTML = ICON_WIFI;
  cur.append(ico, el("div", { className: "info" },
    el("b", { textContent: s.ssid || "Sem Wi‑Fi" }),
    el("span", { textContent: !s.ssid ? "Conecte-se a um Wi‑Fi para adicioná-lo" : known ? "Já está na lista" : "Não está na lista" })));
  $("currentCard").replaceChildren(cur);
  $("currentCard").style.borderColor = s.ssid ? "rgba(34,211,238,.5)" : "";
  if (s.ssid && !known) {
    const add = el("button", { className: "primary", textContent: "+ Adicionar rede atual" });
    add.style.marginTop = "12px";
    add.onclick = () => saveSettings({ trusted_ssids: [...st.trusted_ssids, s.ssid] });
    $("currentCard").append(add);
  }
  $("ssidList").replaceChildren(...(st.trusted_ssids.length ? st.trusted_ssids.map((name) => {
    const sw = el("input", { type: "checkbox", className: "switch", checked: !st.disabled_ssids.includes(name) });
    sw.onchange = () => saveSettings({ disabled_ssids: sw.checked ? st.disabled_ssids.filter((x) => x !== name) : [...st.disabled_ssids, name] });
    const rm = el("button", { className: "menu-btn", textContent: "✕", ariaLabel: `Remover ${name}` });
    rm.onclick = () => saveSettings({ trusted_ssids: st.trusted_ssids.filter((x) => x !== name), disabled_ssids: st.disabled_ssids.filter((x) => x !== name) });
    return el("div", { className: "row-item" }, el("div", {}, el("b", { textContent: name })), sw, rm);
  }) : [el("p", { className: "muted", textContent: "Nenhuma rede ainda." })]));

  // Atualização
  $("updateBanner").hidden = !s.update_version;
  $("updateTitle").textContent = s.update_version ? `Nova versão ${s.update_version}` : "";
  $("updateSub").textContent = s.update_error || (s.update_installing ? "Baixando e instalando…" : "Atualiza e reinicia o app");
  $("updateBtn").disabled = s.update_installing;
  $("updateStatus").textContent = s.update_installing ? "Baixando e instalando…" : s.update_error || (s.update_version ? `Versão ${s.update_version} disponível: toque para atualizar` : "Atualiza direto pelo app");

  // Ajustes
  $("autostartSwitch").checked = s.autostart;
  $("version").textContent = `Versão ${s.version}`;

  // Detalhes
  const rows = [
    ["Protocolo", vg ? `OpenVPN ${vg.transport}` : "WireGuard"],
    ["Servidor", vg ? `${flagOf(vg.code)} ${vg.country} (${vg.host})` : current?.name || "—"],
    ["Endpoint", vg ? vg.ip : current?.endpoint || "—"],
    ["Estado", $("stateLabel").textContent], ["Tráfego", s.rx_bytes != null && s.state === "up" ? `↓ ${fmtBytes(s.rx_bytes)}  ↑ ${fmtBytes(s.tx_bytes)}` : "—"],
  ];
  $("detailList").replaceChildren(...rows.map(([k, v]) => el("div", { className: "detail" }, el("span", { textContent: k }), el("b", { textContent: v }))));
}

function renderTimer() {
  if (!status) return;
  const since = status.state === "up" && status.connected_since;
  $("timer").textContent = since ? fmtDur(Date.now() - since) : status.selected ? "Toque para proteger sua conexão" : "Carregando servidores gratuitos…";
  $("session").textContent = since ? fmtDur(Date.now() - since).slice(0, 5) : "—";
}
setInterval(renderTimer, 1000);

// ------------------------------------------------------------ ações
function showError(e) {
  $("error").hidden = false;
  $("error").textContent = String(e);
}

async function call(cmd, args) {
  try {
    render(await invoke(cmd, args));
  } catch (e) {
    render(await invoke("get_status"));
    showError(e);
  }
}

const saveSettings = (patch) => call("save_settings", { patch });

function goTab(name) {
  document.querySelectorAll(".bottom button").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.id === name));
}

function openSheet() {
  $("configText").value = "";
  $("nameInput").value = "";
  $("sheetError").hidden = true;
  $("sheet").hidden = false;
}

document.querySelectorAll(".bottom button").forEach((b) => b.addEventListener("click", () => goTab(b.dataset.tab)));
document.querySelectorAll("[data-go]").forEach((b) => b.addEventListener("click", () => goTab(b.dataset.go)));
$("detailsLink").addEventListener("click", () => goTab("details"));

$("toggle").addEventListener("click", () => {
  const goingUp = status.state !== "up";
  render({ ...status, state: "connecting", last_error: null });
  call(goingUp ? "connect" : "disconnect");
});

$("obNext").addEventListener("click", () => {
  if (obPage < ONBOARDING.length - 1) { obPage++; renderOnboarding(); } else saveSettings({ onboarding_done: true });
});
$("obSkip").addEventListener("click", () => saveSettings({ onboarding_done: true }));

$("fab").addEventListener("click", openSheet);
$("updateBtn").addEventListener("click", () => call("install_update"));
$("checkUpdate").addEventListener("click", async () => {
  if (status.update_version) return call("install_update");
  $("updateStatus").textContent = "Verificando…";
  try {
    const s = await invoke("check_for_update");
    render(s);
    if (!s.update_version) $("updateStatus").textContent = "Você está na versão mais recente";
  } catch (e) {
    $("updateStatus").textContent = `Não foi possível verificar: ${e}`;
  }
});
document.querySelectorAll("#serverTabs button").forEach((b) => b.addEventListener("click", () => { serverTab = b.dataset.st; render(status); }));
$("vgRefresh").addEventListener("click", () => call("refresh_vpngate"));
$("sheetCancel").addEventListener("click", () => ($("sheet").hidden = true));
$("fileInput").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  $("configText").value = await file.text();
  if (!$("nameInput").value) $("nameInput").value = file.name.replace(/\.conf$/i, "");
  e.target.value = "";
});
$("sheetSave").addEventListener("click", async () => {
  try {
    render(await invoke("add_profile", { name: $("nameInput").value || null, text: $("configText").value }));
    $("sheet").hidden = true;
  } catch (e) {
    $("sheetError").hidden = false;
    $("sheetError").textContent = String(e);
  }
});

$("autoSwitch").addEventListener("change", (e) => {
  if (e.target.checked && !status.selected) {
    e.target.checked = false;
    return showError("Escolha um servidor primeiro.");
  }
  saveSettings({ auto_connect: e.target.checked });
});
$("leaveSwitch").addEventListener("change", (e) => saveSettings({ disconnect_on_leave: e.target.checked }));
$("autostartSwitch").addEventListener("change", (e) => call("set_autostart", { enabled: e.target.checked }));
$("addManualBtn").addEventListener("click", () => { $("addForm").hidden = !$("addForm").hidden; $("ssidInput").focus(); });
$("addForm").addEventListener("submit", (e) => {
  e.preventDefault();
  const name = $("ssidInput").value.trim();
  if (!name) return;
  $("ssidInput").value = "";
  $("addForm").hidden = true;
  saveSettings({ trusted_ssids: [...status.settings.trusted_ssids, name] });
});
document.querySelectorAll("#themeChips button").forEach((b) => b.addEventListener("click", () => saveSettings({ theme: b.dataset.theme })));

listen("status", (e) => {
  // Não redesenha a lista enquanto um menu está aberto
  if (!document.querySelector(".menu, .server input")) render(e.payload);
  else status = e.payload;
});
invoke("get_status").then(render);
