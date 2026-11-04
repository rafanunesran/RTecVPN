//! Lista pública de servidores do VPN Gate (Universidade de Tsukuba).

use base64::Engine;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct VgServer {
    pub host: String,
    pub ip: String,
    pub score: u64,
    pub ping: u32,
    pub speed: u64,
    pub country: String,
    pub code: String,
    pub sessions: u32,
    #[serde(skip_serializing_if = "String::is_empty", default)]
    pub config: String,
    #[serde(default)]
    pub transport: String,
}

impl VgServer {
    pub fn id(&self) -> String {
        format!("vg:{}", self.ip)
    }

    /// Versão sem a configuração, para mandar à interface.
    pub fn summary(&self) -> VgServer {
        VgServer { config: String::new(), ..self.clone() }
    }
}

const URLS: [&str; 2] = ["https://www.vpngate.net/api/iphone/", "http://www.vpngate.net/api/iphone/"];

pub fn fetch() -> Result<(Vec<VgServer>, String), String> {
    let mut last = String::from("lista vazia");
    for url in URLS {
        match ureq::get(url).header("User-Agent", "RTecVPN").call() {
            Ok(mut resp) => match resp.body_mut().with_config().limit(50 * 1024 * 1024).read_to_string() {
                Ok(text) => {
                    let list = parse(&text);
                    if !list.is_empty() {
                        return Ok((list, text));
                    }
                }
                Err(e) => last = e.to_string(),
            },
            Err(e) => last = e.to_string(),
        }
    }
    Err(format!("Não foi possível baixar a lista do VPN Gate: {last}"))
}

pub fn parse(text: &str) -> Vec<VgServer> {
    let mut list: Vec<VgServer> = text
        .lines()
        .filter(|l| !l.trim().is_empty() && !l.starts_with('*') && !l.starts_with('#'))
        .filter_map(|line| {
            let c: Vec<&str> = line.split(',').collect();
            if c.len() < 15 {
                return None;
            }
            let raw = base64::engine::general_purpose::STANDARD.decode(c[14].trim()).ok()?;
            let config = with_modern_ciphers(&String::from_utf8_lossy(&raw));
            if !config.contains("remote ") {
                return None;
            }
            Some(VgServer {
                host: c[0].into(),
                ip: c[1].into(),
                score: c[2].parse().unwrap_or(0),
                ping: c[3].parse().unwrap_or(0),
                speed: c[4].parse().unwrap_or(0),
                country: c[5].into(),
                code: c[6].into(),
                sessions: c[7].parse().unwrap_or(0),
                transport: transport(&config),
                config,
            })
        })
        .collect();
    list.sort_by_key(|v| std::cmp::Reverse(v.score));
    list
}

fn transport(config: &str) -> String {
    let field = |key: &str, idx: usize| {
        config
            .lines()
            .find(|l| l.trim_start().starts_with(key))
            .and_then(|l| l.split_whitespace().nth(idx))
            .unwrap_or("?")
            .to_string()
    };
    format!("{} {}", field("proto ", 1).to_uppercase(), field("remote ", 2))
}

/// OpenVPN 2.6 só aceita AES-128-CBC (usado pelo VPN Gate) se ele estiver em data-ciphers.
pub fn with_modern_ciphers(config: &str) -> String {
    let cfg = config.replace("\r\n", "\n");
    if cfg.contains("data-ciphers") {
        return cfg;
    }
    let extra = "data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC\ndata-ciphers-fallback AES-128-CBC\n";
    match cfg.find("\n<ca>") {
        Some(i) => format!("{}{}{}", &cfg[..=i], extra, &cfg[i + 1..]),
        None => format!("{cfg}\n{extra}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn le_csv_do_vpngate() {
        let cfg = "client\nproto tcp\nremote 1.2.3.4 443\ncipher AES-128-CBC\n<ca>\nX\n</ca>\n";
        let b64 = base64::engine::general_purpose::STANDARD.encode(cfg);
        let csv = format!(
            "*vpn_servers\n#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64\n\
             vpn1,1.2.3.4,100,10,5000000,Japan,JP,3,1,1,1,2weeks,op,,{b64}\n\
             vpn2,5.6.7.8,900,20,9000000,Korea Republic of,KR,3,1,1,1,2weeks,op,,{b64}\n*\n"
        );
        let list = parse(&csv);
        assert_eq!(list.len(), 2);
        assert_eq!(list[0].code, "KR"); // maior pontuação primeiro
        assert_eq!(list[1].transport, "TCP 443");
        assert!(list[0].config.contains("data-ciphers-fallback AES-128-CBC\n<ca>"));
    }
}
