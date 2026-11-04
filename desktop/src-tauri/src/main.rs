// Esconde o console no Windows em builds de release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod openvpn;
mod platform;
mod vpngate;

use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};

#[derive(Serialize, Deserialize, Clone)]
struct ProfileMeta {
    id: String,
    name: String,
}

fn default_true() -> bool {
    true
}

fn default_theme() -> String {
    "system".into()
}

#[derive(Serialize, Deserialize, Clone)]
struct Settings {
    #[serde(default)]
    auto_connect: bool,
    #[serde(default)]
    trusted_ssids: BTreeSet<String>,
    /// Redes cadastradas com a chave desligada.
    #[serde(default)]
    disabled_ssids: BTreeSet<String>,
    #[serde(default = "default_true")]
    disconnect_on_leave: bool,
    /// A VPN atual foi ligada pela regra automática (só então desligamos ao sair da rede).
    #[serde(default)]
    auto_started: bool,
    #[serde(default)]
    profiles: Vec<ProfileMeta>,
    #[serde(default)]
    selected: Option<String>,
    #[serde(default = "default_theme")]
    theme: String,
    #[serde(default)]
    onboarding_done: bool,
    /// Servidor do VPN Gate escolhido (guardado inteiro, porque a lista pública muda).
    #[serde(default)]
    selected_vg: Option<vpngate::VgServer>,
}

impl Default for Settings {
    fn default() -> Self {
        serde_json::from_str("{}").unwrap()
    }
}

impl Settings {
    fn active_ssids(&self) -> BTreeSet<String> {
        self.trusted_ssids.difference(&self.disabled_ssids).cloned().collect()
    }
}

#[derive(Serialize, Clone)]
struct ProfileView {
    id: String,
    name: String,
    endpoint: Option<String>,
}

#[derive(Serialize, Clone)]
struct Status {
    /// "down", "connecting" ou "up"
    state: String,
    profiles: Vec<ProfileView>,
    selected: Option<String>,
    active: Option<String>,
    ssid: Option<String>,
    settings: Settings,
    last_error: Option<String>,
    connected_since: Option<u64>,
    rx_bytes: Option<u64>,
    tx_bytes: Option<u64>,
    autostart: bool,
    version: String,
    vg_servers: Vec<vpngate::VgServer>,
    vg_loading: bool,
    vg_error: Option<String>,
    /// "wireguard" ou "openvpn": tipo do servidor selecionado.
    kind: String,
}

struct App {
    dir: PathBuf,
    settings: Mutex<Settings>,
    busy: Mutex<bool>,
    last_error: Mutex<Option<String>>,
    /// Rede em que o usuário desligou a VPN na mão: não religamos até ele mudar de rede.
    paused_on: Mutex<Option<String>>,
    active: Mutex<Option<String>>,
    connected_since: Mutex<Option<u64>>,
    vg_list: Mutex<Vec<vpngate::VgServer>>,
    vg_loading: Mutex<bool>,
    vg_error: Mutex<Option<String>>,
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

impl App {
    /// Arquivo do túnel ativo: o wg-quick usa o nome do arquivo como nome da interface.
    fn conf_path(&self) -> PathBuf {
        self.dir.join(format!("{}.conf", platform::TUNNEL))
    }

    fn profile_path(&self, id: &str) -> PathBuf {
        self.dir.join("profiles").join(format!("{id}.conf"))
    }

    fn save_settings(&self, s: &Settings) {
        if let Ok(json) = serde_json::to_string_pretty(s) {
            let _ = std::fs::write(self.dir.join("settings.json"), json);
        }
    }

    fn update<F: FnOnce(&mut Settings)>(&self, f: F) {
        let mut s = self.settings.lock().unwrap();
        f(&mut s);
        self.save_settings(&s);
    }

    fn ovpn(&self) -> openvpn::Paths {
        openvpn::Paths::new(&self.dir)
    }

    fn using_vg(&self) -> bool {
        let s = self.settings.lock().unwrap();
        s.selected.as_deref().is_some_and(|id| id.starts_with("vg:")) && s.selected_vg.is_some()
    }

    /// "up", "connecting" ou "down", somando WireGuard e OpenVPN.
    fn vpn_state(&self) -> &'static str {
        if platform::is_up(&self.conf_path()) {
            return "up";
        }
        openvpn::state(&self.ovpn())
    }

    fn selected_id(&self) -> Option<String> {
        if self.using_vg() {
            return self.settings.lock().unwrap().selected.clone();
        }
        let s = self.settings.lock().unwrap();
        s.selected.clone().filter(|id| s.profiles.iter().any(|p| &p.id == id)).or_else(|| s.profiles.first().map(|p| p.id.clone()))
    }

    fn status(&self, handle: &AppHandle) -> Status {
        let vpn = self.vpn_state();
        let up = vpn == "up";
        let state = if *self.busy.lock().unwrap() { "connecting" } else { vpn };
        if !up {
            *self.connected_since.lock().unwrap() = None;
        } else if self.connected_since.lock().unwrap().is_none() {
            *self.connected_since.lock().unwrap() = Some(now_ms());
        }
        let settings = self.settings.lock().unwrap().clone();
        let profiles = settings
            .profiles
            .iter()
            .map(|p| ProfileView {
                id: p.id.clone(),
                name: p.name.clone(),
                endpoint: std::fs::read_to_string(self.profile_path(&p.id)).ok().and_then(|t| endpoint(&t)),
            })
            .collect();
        let (rx, tx) = if up && !self.using_vg() { platform::transfer() } else { (None, None) };
        let mut last_error = self.last_error.lock().unwrap().clone();
        if last_error.is_none() && self.using_vg() && state == "down" {
            last_error = openvpn::last_error(&self.ovpn());
        }
        let mut settings = settings;
        if let Some(v) = settings.selected_vg.as_mut() {
            v.config.clear();
        }
        Status {
            state: state.into(),
            profiles,
            selected: self.selected_id(),
            active: if up { self.active.lock().unwrap().clone() } else { None },
            ssid: platform::current_ssid(),
            settings,
            last_error,
            connected_since: *self.connected_since.lock().unwrap(),
            rx_bytes: rx,
            tx_bytes: tx,
            autostart: handle.autolaunch().is_enabled().unwrap_or(false),
            version: handle.package_info().version.to_string(),
            vg_servers: self.vg_list.lock().unwrap().iter().map(|v| v.summary()).collect(),
            vg_loading: *self.vg_loading.lock().unwrap(),
            vg_error: self.vg_error.lock().unwrap().clone(),
            kind: if self.using_vg() { "openvpn" } else { "wireguard" }.into(),
        }
    }

    /// Liga (com o servidor selecionado) ou desliga o túnel.
    fn set_tunnel(&self, up: bool) -> Result<(), String> {
        {
            let mut busy = self.busy.lock().unwrap();
            if *busy {
                return Err("Operação em andamento".into());
            }
            *busy = true;
        }
        let result = (|| {
            let conf = self.conf_path();
            let ovpn = self.ovpn();
            let wg_up = platform::is_up(&conf);
            let ovpn_up = openvpn::state(&ovpn) != "down";
            if up && self.using_vg() {
                let vg = self.settings.lock().unwrap().selected_vg.clone().ok_or("Nenhum servidor selecionado")?;
                if wg_up {
                    platform::down(&conf)?;
                }
                if ovpn_up {
                    openvpn::down(&ovpn)?;
                }
                write_private(&ovpn.config, &vg.config).map_err(|e| e.to_string())?;
                openvpn::up(&ovpn)?;
                *self.active.lock().unwrap() = Some(vg.id());
                *self.connected_since.lock().unwrap() = Some(now_ms());
            } else if up {
                if ovpn_up {
                    openvpn::down(&ovpn)?;
                }
                let id = self.selected_id().ok_or("Nenhum servidor configurado")?;
                let text = std::fs::read_to_string(self.profile_path(&id)).map_err(|e| e.to_string())?;
                // Trocando de servidor com a VPN ligada: derruba antes
                if wg_up {
                    platform::down(&conf)?;
                }
                write_private(&conf, &text).map_err(|e| e.to_string())?;
                platform::up(&conf)?;
                *self.active.lock().unwrap() = Some(id);
                *self.connected_since.lock().unwrap() = Some(now_ms());
            } else {
                if wg_up {
                    platform::down(&conf)?;
                }
                if ovpn_up {
                    openvpn::down(&ovpn)?;
                }
                *self.active.lock().unwrap() = None;
                *self.connected_since.lock().unwrap() = None;
            }
            Ok(())
        })();
        *self.busy.lock().unwrap() = false;
        *self.last_error.lock().unwrap() = result.as_ref().err().cloned();
        result
    }
}

fn kv<'a>(conf: &'a str, key: &str) -> Option<&'a str> {
    conf.lines()
        .filter_map(|l| l.split_once('='))
        .find(|(k, _)| k.trim().eq_ignore_ascii_case(key))
        .map(|(_, v)| v.trim())
}

fn endpoint(conf: &str) -> Option<String> {
    kv(conf, "endpoint").map(str::to_string)
}

fn endpoint_host(conf: &str) -> Option<String> {
    kv(conf, "endpoint").map(|v| v.rsplit_once(':').map(|(h, _)| h).unwrap_or(v).trim_matches(['[', ']']).to_string())
}

fn validate_config(text: &str) -> Result<(), String> {
    let has = |section: &str| text.lines().any(|l| l.trim().eq_ignore_ascii_case(section));
    if !has("[Interface]") || !has("[Peer]") {
        return Err("A configuração precisa ter as seções [Interface] e [Peer]".into());
    }
    for key in ["PrivateKey", "Address", "PublicKey", "Endpoint"] {
        if kv(text, key).is_none() {
            return Err(format!("Campo {key} ausente na configuração"));
        }
    }
    Ok(())
}

fn write_private(path: &PathBuf, text: &str) -> std::io::Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    #[cfg(unix)]
    {
        use std::io::Write;
        use std::os::unix::fs::OpenOptionsExt;
        let mut f = std::fs::OpenOptions::new().write(true).create(true).truncate(true).mode(0o600).open(path)?;
        f.write_all(text.as_bytes())
    }
    #[cfg(not(unix))]
    {
        std::fs::write(path, text)
    }
}

fn new_id() -> String {
    format!("{:x}", now_ms() ^ (std::process::id() as u64) << 20)
}

// ------------------------------------------------------------------ comandos para a UI

fn emit(handle: &AppHandle) -> Status {
    let status = handle.state::<App>().status(handle);
    let _ = handle.emit("status", &status);
    status
}

#[tauri::command]
fn get_status(handle: AppHandle) -> Status {
    handle.state::<App>().status(&handle)
}

#[tauri::command]
fn add_profile(handle: AppHandle, app: State<'_, App>, name: Option<String>, text: String) -> Result<Status, String> {
    validate_config(&text)?;
    let id = new_id();
    write_private(&app.profile_path(&id), &text).map_err(|e| e.to_string())?;
    let name = name.filter(|n| !n.trim().is_empty()).or_else(|| endpoint_host(&text)).unwrap_or_else(|| "Servidor".into());
    app.update(|s| {
        s.profiles.push(ProfileMeta { id: id.clone(), name });
        s.selected = Some(id.clone());
    });
    Ok(emit(&handle))
}

#[tauri::command]
fn rename_profile(handle: AppHandle, app: State<'_, App>, id: String, name: String) -> Status {
    app.update(|s| {
        if let Some(p) = s.profiles.iter_mut().find(|p| p.id == id) {
            if !name.trim().is_empty() {
                p.name = name.trim().to_string();
            }
        }
    });
    emit(&handle)
}

#[tauri::command]
fn delete_profile(handle: AppHandle, app: State<'_, App>, id: String) -> Status {
    let _ = std::fs::remove_file(app.profile_path(&id));
    app.update(|s| {
        s.profiles.retain(|p| p.id != id);
        if s.selected.as_deref() == Some(&id) {
            s.selected = s.profiles.first().map(|p| p.id.clone());
        }
    });
    emit(&handle)
}

#[tauri::command]
async fn select_profile(handle: AppHandle, id: String) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        app.update(|s| s.selected = Some(id.clone()));
        // Conectado em outro servidor: troca na hora
        let active = app.active.lock().unwrap().clone();
        if app.vpn_state() == "up" && active.as_deref() != Some(&id) {
            app.set_tunnel(true)?;
        }
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(emit(&handle))
}

#[tauri::command]
async fn select_vg(handle: AppHandle, ip: String) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        let srv = app.vg_list.lock().unwrap().iter().find(|v| v.ip == ip).cloned().ok_or("Servidor não está mais na lista")?;
        let changed = app.selected_id().as_deref() != Some(&srv.id());
        app.update(|s| {
            s.selected = Some(srv.id());
            s.selected_vg = Some(srv.clone());
        });
        if changed && app.vpn_state() == "up" {
            app.set_tunnel(true)?;
        }
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(emit(&handle))
}

#[tauri::command]
async fn refresh_vpngate(handle: AppHandle) -> Status {
    let h = handle.clone();
    let _ = tauri::async_runtime::spawn_blocking(move || refresh_vg(&h)).await;
    emit(&handle)
}

fn refresh_vg(handle: &AppHandle) {
    let app = handle.state::<App>();
    {
        let mut loading = app.vg_loading.lock().unwrap();
        if *loading {
            return;
        }
        *loading = true;
    }
    emit(handle);
    match vpngate::fetch() {
        Ok((list, raw)) => {
            let _ = std::fs::write(app.dir.join("vpngate.csv"), raw);
            *app.vg_list.lock().unwrap() = list;
            *app.vg_error.lock().unwrap() = None;
        }
        Err(e) => *app.vg_error.lock().unwrap() = Some(e),
    }
    *app.vg_loading.lock().unwrap() = false;
    pick_default_vg(&app);
    emit(handle);
}

/// Sem nenhum servidor escolhido: já deixa o melhor gratuito pronto para conectar.
fn pick_default_vg(app: &App) {
    let best = app.vg_list.lock().unwrap().first().cloned();
    let needs = {
        let s = app.settings.lock().unwrap();
        s.profiles.is_empty() && s.selected_vg.is_none()
    };
    if let (true, Some(best)) = (needs, best) {
        app.update(|s| {
            s.selected = Some(best.id());
            s.selected_vg = Some(best);
        });
    }
}

#[tauri::command]
async fn connect(handle: AppHandle) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        app.set_tunnel(true)?;
        *app.paused_on.lock().unwrap() = None;
        app.update(|s| s.auto_started = false); // ligado manualmente
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(emit(&handle))
}

#[tauri::command]
async fn disconnect(handle: AppHandle) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        app.set_tunnel(false)?;
        *app.paused_on.lock().unwrap() = platform::current_ssid();
        app.update(|s| s.auto_started = false);
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    Ok(emit(&handle))
}

#[derive(Deserialize)]
struct SettingsPatch {
    auto_connect: Option<bool>,
    trusted_ssids: Option<Vec<String>>,
    disabled_ssids: Option<Vec<String>>,
    disconnect_on_leave: Option<bool>,
    theme: Option<String>,
    onboarding_done: Option<bool>,
}

#[tauri::command]
fn save_settings(handle: AppHandle, app: State<'_, App>, patch: SettingsPatch) -> Status {
    let clean = |v: Vec<String>| v.into_iter().map(|x| x.trim().to_string()).filter(|x| !x.is_empty()).collect();
    app.update(|s| {
        if let Some(v) = patch.auto_connect { s.auto_connect = v; }
        if let Some(v) = patch.trusted_ssids { s.trusted_ssids = clean(v); }
        if let Some(v) = patch.disabled_ssids { s.disabled_ssids = clean(v); }
        if let Some(v) = patch.disconnect_on_leave { s.disconnect_on_leave = v; }
        if let Some(v) = patch.theme { s.theme = v; }
        if let Some(v) = patch.onboarding_done { s.onboarding_done = v; }
    });
    emit(&handle)
}

#[tauri::command]
fn set_autostart(handle: AppHandle, enabled: bool) -> Result<Status, String> {
    let al = handle.autolaunch();
    if enabled { al.enable() } else { al.disable() }.map_err(|e| e.to_string())?;
    Ok(emit(&handle))
}

// ------------------------------------------------------------------ regra automática de Wi-Fi

/// Decide o que fazer dado o Wi-Fi atual. Retorna Some(true) para ligar, Some(false) para desligar.
fn auto_decision(s: &Settings, ssid: Option<&str>, is_up: bool) -> Option<bool> {
    if !s.auto_connect {
        return None;
    }
    let on_trusted = ssid.is_some_and(|x| s.active_ssids().contains(x));
    if on_trusted && !is_up {
        Some(true)
    } else if !on_trusted && is_up && s.auto_started && s.disconnect_on_leave {
        Some(false)
    } else {
        None
    }
}

fn watcher(handle: AppHandle) {
    let mut last_ssid: Option<String> = None;
    let mut failed_for: Option<String> = None;
    loop {
        let app = handle.state::<App>();
        let ssid = platform::current_ssid();
        let up = app.vpn_state() != "down";
        if ssid != last_ssid {
            failed_for = None; // nova rede: tenta de novo
        }
        {
            let mut paused = app.paused_on.lock().unwrap();
            if paused.is_some() && *paused != ssid {
                *paused = None;
            }
            if paused.is_some() {
                failed_for = paused.clone();
            }
        }
        let settings = app.settings.lock().unwrap().clone();
        // Não insiste se o usuário cancelou o pedido de senha nesta mesma rede
        let blocked = failed_for.is_some() && failed_for == ssid;
        if app.selected_id().is_some() && !blocked && !*app.busy.lock().unwrap() {
            if let Some(turn_on) = auto_decision(&settings, ssid.as_deref(), up) {
                match app.set_tunnel(turn_on) {
                    Ok(()) => app.update(|s| s.auto_started = turn_on),
                    Err(_) => failed_for = ssid.clone(),
                }
            }
        }
        last_ssid = ssid;
        emit(&handle);
        std::thread::sleep(Duration::from_secs(3));
    }
}

/// Migra a configuração única da v0.1 (rtecvpn.conf) para a lista de servidores.
fn migrate(dir: &std::path::Path, s: &mut Settings) {
    let old = dir.join(format!("{}.conf", platform::TUNNEL));
    if s.profiles.is_empty() {
        if let Ok(text) = std::fs::read_to_string(&old) {
            let id = new_id();
            if write_private(&dir.join("profiles").join(format!("{id}.conf")), &text).is_ok() {
                s.profiles.push(ProfileMeta { id: id.clone(), name: endpoint_host(&text).unwrap_or_else(|| "Meu servidor".into()) });
                s.selected = Some(id);
                s.onboarding_done = true;
            }
        }
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::init(MacosLauncher::LaunchAgent, Some(vec!["--minimized"])))
        .setup(|app| {
            let dir = app.path().app_config_dir()?;
            std::fs::create_dir_all(dir.join("profiles"))?;
            let mut settings: Settings = std::fs::read_to_string(dir.join("settings.json"))
                .ok()
                .and_then(|t| serde_json::from_str(&t).ok())
                .unwrap_or_default();
            migrate(&dir, &mut settings);
            let dir_vg = dir.clone();
            let state = App {
                dir,
                settings: Mutex::new(settings.clone()),
                busy: Mutex::new(false),
                last_error: Mutex::new(None),
                paused_on: Mutex::new(None),
                active: Mutex::new(None),
                connected_since: Mutex::new(None),
                vg_list: Mutex::new(
                    std::fs::read_to_string(dir_vg.join("vpngate.csv")).map(|t| vpngate::parse(&t)).unwrap_or_default(),
                ),
                vg_loading: Mutex::new(false),
                vg_error: Mutex::new(None),
            };
            pick_default_vg(&state);
            state.save_settings(&settings);
            if state.vpn_state() == "up" {
                *state.active.lock().unwrap() = state.selected_id();
            }
            app.manage(state);

            // Iniciado com o sistema: começa escondido na bandeja
            if std::env::args().any(|a| a == "--minimized") {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.hide();
                }
            }

            // Ícone na bandeja: o app continua monitorando o Wi-Fi com a janela fechada
            let show = MenuItem::with_id(app, "show", "Abrir RTecVPN", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Sair", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;
            TrayIconBuilder::new()
                .icon(app.default_window_icon().cloned().expect("ícone padrão"))
                .tooltip("RTecVPN")
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(w) = app.get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .build(app)?;

            let handle = app.handle().clone();
            std::thread::spawn(move || watcher(handle));
            let handle = app.handle().clone();
            std::thread::spawn(move || refresh_vg(&handle));
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .invoke_handler(tauri::generate_handler![
            get_status, add_profile, rename_profile, delete_profile, select_profile,
            connect, disconnect, save_settings, set_autostart, select_vg, refresh_vpngate
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o RTecVPN");
}

#[cfg(test)]
mod tests {
    use super::*;

    fn settings(auto: bool, ssids: &[&str], auto_started: bool) -> Settings {
        Settings {
            auto_connect: auto,
            trusted_ssids: ssids.iter().map(|s| s.to_string()).collect(),
            auto_started,
            ..Settings::default()
        }
    }

    #[test]
    fn liga_ao_entrar_em_rede_escolhida() {
        assert_eq!(auto_decision(&settings(true, &["Cafe"], false), Some("Cafe"), false), Some(true));
    }

    #[test]
    fn desliga_ao_sair_se_foi_automatico() {
        assert_eq!(auto_decision(&settings(true, &["Cafe"], true), Some("Casa"), true), Some(false));
        assert_eq!(auto_decision(&settings(true, &["Cafe"], true), None, true), Some(false));
    }

    #[test]
    fn respeita_opcao_de_nao_desligar_ao_sair() {
        let mut s = settings(true, &["Cafe"], true);
        s.disconnect_on_leave = false;
        assert_eq!(auto_decision(&s, None, true), None);
    }

    #[test]
    fn rede_desativada_nao_conecta() {
        let mut s = settings(true, &["Cafe"], false);
        s.disabled_ssids.insert("Cafe".into());
        assert_eq!(auto_decision(&s, Some("Cafe"), false), None);
    }

    #[test]
    fn nao_desliga_vpn_ligada_manualmente() {
        assert_eq!(auto_decision(&settings(true, &["Cafe"], false), None, true), None);
    }

    #[test]
    fn regra_desativada_nao_faz_nada() {
        assert_eq!(auto_decision(&settings(false, &["Cafe"], false), Some("Cafe"), false), None);
    }

    #[test]
    fn padroes_das_configuracoes() {
        let s = Settings::default();
        assert!(s.disconnect_on_leave);
        assert_eq!(s.theme, "system");
    }

    #[test]
    fn le_endpoint_e_valida_config() {
        let conf = "[Interface]\nPrivateKey = a\nAddress = 10.8.0.2/32\n\n[Peer]\nPublicKey = b\nEndpoint = 1.2.3.4:51820\n";
        assert_eq!(endpoint_host(conf).as_deref(), Some("1.2.3.4"));
        assert_eq!(endpoint(conf).as_deref(), Some("1.2.3.4:51820"));
        assert!(validate_config(conf).is_ok());
        assert!(validate_config("[Interface]\n").is_err());
        let p = std::env::temp_dir().join("rtecvpn-test.conf");
        std::fs::write(&p, conf).unwrap();
        assert_eq!(platform::tunnel_address(&p).as_deref(), Some("10.8.0.2"));
    }
}
