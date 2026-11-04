//! Conexão OpenVPN (servidores gratuitos do VPN Gate) usando o `openvpn` instalado no sistema.
//!
//! - Linux: `openvpn` (o .deb/.rpm já instala) com elevação via `pkexec`.
//! - macOS: `brew install openvpn`, com o prompt de administrador do `osascript`.
//! - Windows: OpenVPN Community (https://openvpn.net/community-downloads/), via UAC.
//!
//! O estado vem do log: o processo grava "Initialization Sequence Completed" quando o túnel sobe.

use std::path::{Path, PathBuf};
#[allow(unused_imports)]
use std::process::{Command, Output};

pub struct Paths {
    pub config: PathBuf,
    pub log: PathBuf,
    #[cfg_attr(windows, allow(dead_code))]
    pub pid: PathBuf,
}

impl Paths {
    pub fn new(dir: &Path) -> Self {
        Paths {
            config: dir.join("vpngate.ovpn"),
            log: dir.join("openvpn.log"),
            pid: dir.join("openvpn.pid"),
        }
    }
}

fn run(cmd: &mut Command) -> Result<Output, String> {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000);
    }
    cmd.output().map_err(|e| e.to_string())
}

#[allow(dead_code)]
fn check(out: Output) -> Result<(), String> {
    if out.status.success() {
        Ok(())
    } else {
        let msg = String::from_utf8_lossy(&out.stderr).trim().to_string();
        Err(if msg.is_empty() { format!("comando falhou ({})", out.status) } else { msg })
    }
}

#[allow(dead_code)]
fn sh_quote(p: &Path) -> String {
    format!("'{}'", p.display().to_string().replace('\'', "'\\''"))
}

/// "up", "connecting" ou "down".
pub fn state(p: &Paths) -> &'static str {
    if !process_alive(p) {
        return "down";
    }
    let log = std::fs::read_to_string(&p.log).unwrap_or_default();
    if log.contains("Initialization Sequence Completed") { "up" } else { "connecting" }
}

/// Últimas linhas úteis do log, para mostrar o motivo de uma falha.
pub fn last_error(p: &Paths) -> Option<String> {
    let log = std::fs::read_to_string(&p.log).ok()?;
    log.lines()
        .rev()
        .find(|l| l.contains("ERROR") || l.contains("error") || l.contains("failed") || l.contains("Exiting due"))
        .map(|l| l.chars().skip_while(|c| !c.is_alphabetic()).collect::<String>())
        .map(|l| l.trim().to_string())
}

// ---------------------------------------------------------------- Linux
#[cfg(target_os = "linux")]
pub fn up(p: &Paths) -> Result<(), String> {
    let _ = std::fs::remove_file(&p.log);
    check(run(Command::new("pkexec")
        .arg("openvpn").arg("--config").arg(&p.config)
        .arg("--daemon").arg("rtecvpn")
        .arg("--writepid").arg(&p.pid)
        .arg("--log").arg(&p.log))?)
}

#[cfg(target_os = "linux")]
pub fn down(p: &Paths) -> Result<(), String> {
    let pid = read_pid(p).ok_or("OpenVPN não está rodando")?;
    check(run(Command::new("pkexec").arg("kill").arg(pid.to_string()))?)?;
    let _ = std::fs::remove_file(&p.pid);
    Ok(())
}

#[cfg(target_os = "linux")]
fn process_alive(p: &Paths) -> bool {
    read_pid(p).is_some_and(|pid| Path::new(&format!("/proc/{pid}")).exists())
}

// ---------------------------------------------------------------- macOS
#[cfg(target_os = "macos")]
fn openvpn_bin() -> &'static str {
    ["/opt/homebrew/sbin/openvpn", "/usr/local/sbin/openvpn", "/opt/homebrew/bin/openvpn", "/usr/local/bin/openvpn"]
        .into_iter()
        .find(|p| Path::new(p).exists())
        .unwrap_or("openvpn")
}

#[cfg(target_os = "macos")]
fn admin_shell(script: &str) -> Result<(), String> {
    let escaped = script.replace('\\', "\\\\").replace('"', "\\\"");
    let apple = format!("do shell script \"{escaped}\" with administrator privileges");
    check(run(Command::new("osascript").arg("-e").arg(apple))?)
}

#[cfg(target_os = "macos")]
pub fn up(p: &Paths) -> Result<(), String> {
    let bin = openvpn_bin();
    if bin == "openvpn" {
        return Err("Instale o OpenVPN: brew install openvpn".into());
    }
    let _ = std::fs::remove_file(&p.log);
    admin_shell(&format!(
        "{bin} --config {} --daemon rtecvpn --writepid {} --log {}",
        sh_quote(&p.config), sh_quote(&p.pid), sh_quote(&p.log)
    ))
}

#[cfg(target_os = "macos")]
pub fn down(p: &Paths) -> Result<(), String> {
    let pid = read_pid(p).ok_or("OpenVPN não está rodando")?;
    admin_shell(&format!("kill {pid}"))?;
    let _ = std::fs::remove_file(&p.pid);
    Ok(())
}

#[cfg(target_os = "macos")]
fn process_alive(p: &Paths) -> bool {
    read_pid(p).is_some_and(|pid| {
        run(Command::new("ps").args(["-p", &pid.to_string()])).map(|o| o.status.success()).unwrap_or(false)
    })
}

#[cfg(unix)]
fn read_pid(p: &Paths) -> Option<u32> {
    std::fs::read_to_string(&p.pid).ok()?.trim().parse().ok()
}

// ---------------------------------------------------------------- Windows
#[cfg(windows)]
fn openvpn_exe() -> String {
    let base = std::env::var("ProgramFiles").unwrap_or_else(|_| "C:\\Program Files".into());
    format!("{base}\\OpenVPN\\bin\\openvpn.exe")
}

#[cfg(windows)]
fn ps_args(args: &[String]) -> String {
    args.iter().map(|a| format!("'\"{}\"'", a.replace('\'', "''"))).collect::<Vec<_>>().join(",")
}

#[cfg(windows)]
pub fn up(p: &Paths) -> Result<(), String> {
    let exe = openvpn_exe();
    if !Path::new(&exe).exists() {
        return Err("Instale o OpenVPN Community: https://openvpn.net/community-downloads/".into());
    }
    let _ = std::fs::remove_file(&p.log);
    let args = ps_args(&[
        "--config".into(), p.config.display().to_string(),
        "--log".into(), p.log.display().to_string(),
    ]);
    // Sem -Wait: o openvpn.exe continua rodando (elevado) em segundo plano
    let ps = format!("Start-Process -FilePath '{exe}' -ArgumentList {args} -Verb RunAs -WindowStyle Hidden");
    check(run(Command::new("powershell").args(["-NoProfile", "-NonInteractive", "-Command", &ps]))?)
}

#[cfg(windows)]
pub fn down(_p: &Paths) -> Result<(), String> {
    let ps = "$p = Start-Process -FilePath 'taskkill.exe' -ArgumentList '/F','/IM','openvpn.exe' -Verb RunAs -Wait -PassThru -WindowStyle Hidden; exit $p.ExitCode";
    check(run(Command::new("powershell").args(["-NoProfile", "-NonInteractive", "-Command", ps]))?)
}

#[cfg(windows)]
fn process_alive(_p: &Paths) -> bool {
    run(Command::new("tasklist").args(["/FI", "IMAGENAME eq openvpn.exe", "/NH"]))
        .map(|o| String::from_utf8_lossy(&o.stdout).to_lowercase().contains("openvpn.exe"))
        .unwrap_or(false)
}
