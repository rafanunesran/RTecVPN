//! Comandos específicos de cada sistema para ligar/desligar o WireGuard e ler o Wi-Fi atual.
//!
//! - Linux: `wg-quick` (pacote wireguard-tools) com elevação via `pkexec`.
//! - macOS: `wg-quick` do Homebrew com o prompt de administrador do `osascript`.
//! - Windows: serviço de túnel do WireGuard para Windows (`wireguard.exe /installtunnelservice`) via UAC.
//!   O instalador oficial vai dentro do RTecVPN e é instalado em silêncio na primeira conexão.

use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::OnceLock;

pub const TUNNEL: &str = "rtecvpn";

static RESOURCES: OnceLock<PathBuf> = OnceLock::new();

/// Pasta de recursos do app (instaladores/binários que vão junto com o RTecVPN).
pub fn set_resource_dir(dir: PathBuf) {
    let _ = RESOURCES.set(dir);
}

/// Caminho de um arquivo que vem dentro do app, se existir.
#[allow(dead_code)]
pub fn bundled(rel: &str) -> Option<PathBuf> {
    let p = RESOURCES.get()?.join(rel);
    p.exists().then_some(p)
}

fn run(cmd: &mut Command) -> Result<Output, String> {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    cmd.output().map_err(|e| e.to_string())
}

fn check(out: Output) -> Result<(), String> {
    if out.status.success() {
        Ok(())
    } else {
        let msg = String::from_utf8_lossy(&out.stderr).trim().to_string();
        Err(if msg.is_empty() { format!("comando falhou ({})", out.status) } else { msg })
    }
}

// ---------------------------------------------------------------- Linux
#[cfg(target_os = "linux")]
pub fn up(conf: &Path) -> Result<(), String> {
    check(run(Command::new("pkexec").arg("wg-quick").arg("up").arg(conf))?)
}

#[cfg(target_os = "linux")]
pub fn down(conf: &Path) -> Result<(), String> {
    check(run(Command::new("pkexec").arg("wg-quick").arg("down").arg(conf))?)
}

#[cfg(target_os = "linux")]
pub fn is_up(_conf: &Path) -> bool {
    Path::new("/sys/class/net").join(TUNNEL).exists()
}

#[cfg(target_os = "linux")]
pub fn current_ssid() -> Option<String> {
    if let Ok(out) = run(Command::new("nmcli").args(["-t", "-f", "ACTIVE,SSID", "dev", "wifi"])) {
        let text = String::from_utf8_lossy(&out.stdout);
        for line in text.lines() {
            if let Some(ssid) = line.strip_prefix("yes:").or_else(|| line.strip_prefix("sim:")) {
                let ssid = ssid.replace("\\:", ":");
                if !ssid.is_empty() {
                    return Some(ssid);
                }
            }
        }
    }
    let out = run(Command::new("iwgetid").arg("-r")).ok()?;
    clean(&String::from_utf8_lossy(&out.stdout))
}

// ---------------------------------------------------------------- macOS
#[cfg(target_os = "macos")]
fn wg_quick() -> &'static str {
    ["/opt/homebrew/bin/wg-quick", "/usr/local/bin/wg-quick"]
        .into_iter()
        .find(|p| Path::new(p).exists())
        .unwrap_or("wg-quick")
}

#[cfg(target_os = "macos")]
fn admin_shell(script: &str) -> Result<(), String> {
    // PATH do Homebrew para o wg-quick achar o bash 4+ e o wireguard-go
    let full = format!("export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH; {script}");
    let escaped = full.replace('\\', "\\\\").replace('"', "\\\"");
    let apple = format!("do shell script \"{escaped}\" with administrator privileges");
    check(run(Command::new("osascript").arg("-e").arg(apple))?)
}

#[cfg(target_os = "macos")]
fn quote(p: &Path) -> String {
    format!("'{}'", p.display().to_string().replace('\'', "'\\''"))
}

#[cfg(target_os = "macos")]
pub fn up(conf: &Path) -> Result<(), String> {
    admin_shell(&format!("{} up {}", wg_quick(), quote(conf)))
}

#[cfg(target_os = "macos")]
pub fn down(conf: &Path) -> Result<(), String> {
    admin_shell(&format!("{} down {}", wg_quick(), quote(conf)))
}

#[cfg(target_os = "macos")]
pub fn is_up(conf: &Path) -> bool {
    // /var/run/wireguard só é legível pelo root, então procuramos o IP do túnel numa interface utun
    let Some(addr) = tunnel_address(conf) else { return false };
    run(&mut Command::new("ifconfig"))
        .map(|o| String::from_utf8_lossy(&o.stdout).contains(&format!("inet {addr} ")))
        .unwrap_or(false)
}

/// Primeiro IPv4 da linha `Address =` da config (sem o /prefixo).
#[allow(dead_code)]
pub fn tunnel_address(conf: &Path) -> Option<String> {
    let text = std::fs::read_to_string(conf).ok()?;
    text.lines()
        .filter_map(|l| l.split_once('='))
        .find(|(k, _)| k.trim().eq_ignore_ascii_case("address"))
        .and_then(|(_, v)| v.split(',').map(str::trim).find(|a| a.contains('.')).map(|a| a.split('/').next().unwrap_or(a).to_string()))
}

#[cfg(target_os = "macos")]
pub fn current_ssid() -> Option<String> {
    for iface in ["en0", "en1"] {
        if let Ok(out) = run(Command::new("ipconfig").args(["getsummary", iface])) {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                if let Some(v) = line.trim().strip_prefix("SSID : ") {
                    if let Some(s) = clean(v) {
                        return Some(s);
                    }
                }
            }
        }
        if let Ok(out) = run(Command::new("networksetup").args(["-getairportnetwork", iface])) {
            let text = String::from_utf8_lossy(&out.stdout);
            if let Some((_, v)) = text.split_once(": ") {
                if let Some(s) = clean(v) {
                    return Some(s);
                }
            }
        }
    }
    None
}

// ---------------------------------------------------------------- Windows
#[cfg(windows)]
fn wireguard_exe() -> String {
    let base = std::env::var("ProgramFiles").unwrap_or_else(|_| "C:\\Program Files".into());
    format!("{base}\\WireGuard\\wireguard.exe")
}

/// Executa um programa como administrador (pedido do UAC) e espera terminar.
/// Cada argumento vai entre aspas, a não ser que comece com `=` (ex.: propriedades do msiexec).
#[cfg(windows)]
pub fn run_elevated(exe: &str, args: &[&str]) -> Result<(), String> {
    let list = args
        .iter()
        .map(|a| match a.strip_prefix('=') {
            Some(raw) => format!("'{}'", raw.replace('\'', "''")),
            None => format!("'\"{}\"'", a.replace('\'', "''")),
        })
        .collect::<Vec<_>>()
        .join(",");
    let exe = exe.replace('\'', "''");
    let ps = format!(
        "$p = Start-Process -FilePath '{exe}' -ArgumentList {list} -Verb RunAs -Wait -PassThru -WindowStyle Hidden; exit $p.ExitCode"
    );
    check(run(Command::new("powershell").args(["-NoProfile", "-NonInteractive", "-Command", &ps]))?)
}

/// Instala em silêncio um .msi que vem dentro do RTecVPN (pasta installers/).
#[cfg(windows)]
pub fn install_bundled_msi(name: &str, props: &[&str]) -> Result<(), String> {
    let msi = bundled(&format!("installers/{name}")).ok_or_else(|| format!("instalador {name} não encontrado no app"))?;
    let msi = msi.display().to_string();
    let mut args = vec!["/i", msi.as_str(), "=/qn", "=/norestart"];
    args.extend_from_slice(props);
    run_elevated("msiexec.exe", &args)
}

#[cfg(windows)]
fn elevated(args: &[&str]) -> Result<(), String> {
    let exe = wireguard_exe();
    if !Path::new(&exe).exists() {
        install_bundled_msi("wireguard.msi", &["=DO_NOT_LAUNCH=1"])
            .map_err(|e| format!("Não foi possível instalar o WireGuard: {e}"))?;
    }
    run_elevated(&exe, args)
}

#[cfg(windows)]
pub fn up(conf: &Path) -> Result<(), String> {
    elevated(&["/installtunnelservice", &conf.display().to_string()])
}

#[cfg(windows)]
pub fn down(_conf: &Path) -> Result<(), String> {
    elevated(&["/uninstalltunnelservice", TUNNEL])
}

#[cfg(windows)]
pub fn is_up(_conf: &Path) -> bool {
    run(Command::new("sc").args(["query", &format!("WireGuardTunnel${TUNNEL}")]))
        .map(|o| String::from_utf8_lossy(&o.stdout).contains("RUNNING"))
        .unwrap_or(false)
}

#[cfg(windows)]
pub fn current_ssid() -> Option<String> {
    let out = run(Command::new("netsh").args(["wlan", "show", "interfaces"])).ok()?;
    let text = String::from_utf8_lossy(&out.stdout);
    for line in text.lines() {
        let line = line.trim();
        // "SSID   : nome" (ignora a linha BSSID)
        if line.starts_with("SSID") && !line.starts_with("BSSID") {
            if let Some((_, v)) = line.split_once(':') {
                if let Some(s) = clean(v) {
                    return Some(s);
                }
            }
        }
    }
    None
}

/// Bytes recebidos/enviados pelo túnel (só no Linux, onde o contador é legível sem root).
#[cfg(target_os = "linux")]
pub fn transfer() -> (Option<u64>, Option<u64>) {
    let read = |f: &str| {
        std::fs::read_to_string(Path::new("/sys/class/net").join(TUNNEL).join("statistics").join(f))
            .ok()
            .and_then(|s| s.trim().parse().ok())
    };
    (read("rx_bytes"), read("tx_bytes"))
}

#[cfg(not(target_os = "linux"))]
pub fn transfer() -> (Option<u64>, Option<u64>) {
    (None, None)
}

fn clean(raw: &str) -> Option<String> {
    let s = raw.trim();
    if s.is_empty() || s.starts_with('<') || s.contains("not associated") {
        None
    } else {
        Some(s.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::clean;

    #[test]
    fn clean_ignores_empty_and_redacted() {
        assert_eq!(clean("  Casa  "), Some("Casa".into()));
        assert_eq!(clean(""), None);
        assert_eq!(clean("<redacted>"), None);
        assert_eq!(clean("You are not associated with an AirPort network."), None);
    }
}
