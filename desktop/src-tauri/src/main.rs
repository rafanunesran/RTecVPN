// Esconde o console no Windows em builds de release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod platform;

use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::Duration;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};

#[derive(Default, Serialize, Deserialize, Clone)]
struct Settings {
    auto_connect: bool,
    trusted_ssids: BTreeSet<String>,
    /// A VPN atual foi ligada pela regra automática (só então desligamos ao sair da rede).
    #[serde(default)]
    auto_started: bool,
}

#[derive(Serialize, Clone)]
struct Status {
    /// "down", "connecting" ou "up"
    state: String,
    has_config: bool,
    server: Option<String>,
    ssid: Option<String>,
    settings: Settings,
    last_error: Option<String>,
}

struct App {
    dir: PathBuf,
    settings: Mutex<Settings>,
    busy: Mutex<bool>,
    last_error: Mutex<Option<String>>,
    /// Rede em que o usuário desligou a VPN na mão: não religamos até ele mudar de rede.
    paused_on: Mutex<Option<String>>,
}

impl App {
    fn conf_path(&self) -> PathBuf {
        // wg-quick usa o nome do arquivo como nome da interface
        self.dir.join(format!("{}.conf", platform::TUNNEL))
    }

    fn settings_path(&self) -> PathBuf {
        self.dir.join("settings.json")
    }

    fn save_settings(&self, s: &Settings) {
        if let Ok(json) = serde_json::to_string_pretty(s) {
            let _ = std::fs::write(self.settings_path(), json);
        }
    }

    fn status(&self) -> Status {
        let conf = self.conf_path();
        let state = if *self.busy.lock().unwrap() {
            "connecting"
        } else if platform::is_up(&conf) {
            "up"
        } else {
            "down"
        };
        Status {
            state: state.into(),
            has_config: conf.exists(),
            server: std::fs::read_to_string(&conf).ok().and_then(|t| endpoint_host(&t)),
            ssid: platform::current_ssid(),
            settings: self.settings.lock().unwrap().clone(),
            last_error: self.last_error.lock().unwrap().clone(),
        }
    }

    fn set_tunnel(&self, up: bool) -> Result<(), String> {
        {
            let mut busy = self.busy.lock().unwrap();
            if *busy {
                return Err("Operação em andamento".into());
            }
            *busy = true;
        }
        let conf = self.conf_path();
        let result = if up { platform::up(&conf) } else { platform::down(&conf) };
        *self.busy.lock().unwrap() = false;
        *self.last_error.lock().unwrap() = result.as_ref().err().cloned();
        result
    }
}

fn endpoint_host(conf: &str) -> Option<String> {
    conf.lines()
        .filter_map(|l| l.split_once('='))
        .find(|(k, _)| k.trim().eq_ignore_ascii_case("endpoint"))
        .map(|(_, v)| {
            let v = v.trim();
            v.rsplit_once(':').map(|(h, _)| h).unwrap_or(v).trim_matches(['[', ']']).to_string()
        })
}

fn validate_config(text: &str) -> Result<(), String> {
    let has = |section: &str| text.lines().any(|l| l.trim().eq_ignore_ascii_case(section));
    let has_key = |key: &str| {
        text.lines()
            .filter_map(|l| l.split_once('='))
            .any(|(k, _)| k.trim().eq_ignore_ascii_case(key))
    };
    if !has("[Interface]") || !has("[Peer]") {
        return Err("A configuração precisa ter as seções [Interface] e [Peer]".into());
    }
    for key in ["PrivateKey", "Address", "PublicKey", "Endpoint"] {
        if !has_key(key) {
            return Err(format!("Campo {key} ausente na configuração"));
        }
    }
    Ok(())
}

// ------------------------------------------------------------------ comandos para a UI

#[tauri::command]
fn get_status(app: State<'_, App>) -> Status {
    app.status()
}

#[tauri::command]
fn import_config(app: State<'_, App>, text: String) -> Result<Status, String> {
    validate_config(&text)?;
    write_private(&app.conf_path(), &text).map_err(|e| e.to_string())?;
    Ok(app.status())
}

#[tauri::command]
async fn connect(handle: AppHandle) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        app.set_tunnel(true)?;
        let mut s = app.settings.lock().unwrap();
        s.auto_started = false; // ligado manualmente
        app.save_settings(&s);
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    let status = handle.state::<App>().status();
    let _ = handle.emit("status", &status);
    Ok(status)
}

#[tauri::command]
async fn disconnect(handle: AppHandle) -> Result<Status, String> {
    let h = handle.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let app = h.state::<App>();
        app.set_tunnel(false)?;
        *app.paused_on.lock().unwrap() = platform::current_ssid();
        let mut s = app.settings.lock().unwrap();
        s.auto_started = false;
        app.save_settings(&s);
        Ok::<_, String>(())
    })
    .await
    .map_err(|e| e.to_string())??;
    let status = handle.state::<App>().status();
    let _ = handle.emit("status", &status);
    Ok(status)
}

#[tauri::command]
fn save_settings(app: State<'_, App>, auto_connect: bool, trusted_ssids: Vec<String>) -> Status {
    {
        let mut s = app.settings.lock().unwrap();
        s.auto_connect = auto_connect;
        s.trusted_ssids = trusted_ssids.into_iter().map(|x| x.trim().to_string()).filter(|x| !x.is_empty()).collect();
        app.save_settings(&s);
    }
    app.status()
}

fn write_private(path: &PathBuf, text: &str) -> std::io::Result<()> {
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

// ------------------------------------------------------------------ regra automática de Wi-Fi

/// Decide o que fazer dado o Wi-Fi atual. Retorna Some(true) para ligar, Some(false) para desligar.
fn auto_decision(s: &Settings, ssid: Option<&str>, is_up: bool) -> Option<bool> {
    if !s.auto_connect {
        return None;
    }
    let on_trusted = ssid.is_some_and(|x| s.trusted_ssids.contains(x));
    if on_trusted && !is_up {
        Some(true)
    } else if !on_trusted && is_up && s.auto_started {
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
        let up = platform::is_up(&app.conf_path());
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
        let has_config = app.conf_path().exists();
        // Não insiste se o usuário cancelou o pedido de senha nesta mesma rede
        let blocked = failed_for.is_some() && failed_for == ssid;
        if has_config && !blocked && !*app.busy.lock().unwrap() {
            if let Some(turn_on) = auto_decision(&settings, ssid.as_deref(), up) {
                match app.set_tunnel(turn_on) {
                    Ok(()) => {
                        let mut s = app.settings.lock().unwrap();
                        s.auto_started = turn_on;
                        app.save_settings(&s);
                    }
                    Err(_) => failed_for = ssid.clone(),
                }
            }
        }
        last_ssid = ssid;
        let _ = handle.emit("status", app.status());
        std::thread::sleep(Duration::from_secs(5));
    }
}

fn main() {
    tauri::Builder::default()
        .setup(|app| {
            let dir = app.path().app_config_dir()?;
            std::fs::create_dir_all(&dir)?;
            let settings: Settings = std::fs::read_to_string(dir.join("settings.json"))
                .ok()
                .and_then(|t| serde_json::from_str(&t).ok())
                .unwrap_or_default();
            app.manage(App {
                dir,
                settings: Mutex::new(settings),
                busy: Mutex::new(false),
                last_error: Mutex::new(None),
                paused_on: Mutex::new(None),
            });

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
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .invoke_handler(tauri::generate_handler![get_status, import_config, connect, disconnect, save_settings])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar o RTecVPN");
}

#[cfg(test)]
mod tests {
    use super::*;

    fn settings(auto: bool, ssids: &[&str], auto_started: bool) -> Settings {
        Settings { auto_connect: auto, trusted_ssids: ssids.iter().map(|s| s.to_string()).collect(), auto_started }
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
    fn nao_desliga_vpn_ligada_manualmente() {
        assert_eq!(auto_decision(&settings(true, &["Cafe"], false), None, true), None);
    }

    #[test]
    fn regra_desativada_nao_faz_nada() {
        assert_eq!(auto_decision(&settings(false, &["Cafe"], false), Some("Cafe"), false), None);
    }

    #[test]
    fn le_endpoint_e_valida_config() {
        let conf = "[Interface]\nPrivateKey = a\nAddress = 10.8.0.2/32\n\n[Peer]\nPublicKey = b\nEndpoint = 1.2.3.4:51820\n";
        assert_eq!(endpoint_host(conf).as_deref(), Some("1.2.3.4"));
        assert!(validate_config(conf).is_ok());
        assert!(validate_config("[Interface]\n").is_err());
        let p = std::env::temp_dir().join("rtecvpn-test.conf");
        std::fs::write(&p, conf).unwrap();
        assert_eq!(platform::tunnel_address(&p).as_deref(), Some("10.8.0.2"));
    }
}
