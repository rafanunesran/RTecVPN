# RTecVPN

VPN gratuita e de código aberto, baseada em **WireGuard**. Você roda o próprio servidor (dá para usar o plano gratuito da Oracle Cloud) e conecta pelo app.

## O que tem aqui

| Pasta | Conteúdo |
|---|---|
| `server/` | Scripts que instalam o WireGuard num servidor Linux e criam clientes com QR code |
| `android/` | App Android (Kotlin) |
| *(em breve)* | Apps para Windows, macOS e Linux |

## Funcionalidades do app Android

- Botão único para conectar/desconectar
- Importa a configuração por **QR code**, arquivo `.conf` ou colando o texto
- **Conexão automática por Wi-Fi**: escolha redes (ex.: Wi-Fi do café, do trabalho). Ao entrar nelas a VPN liga sozinha; ao sair ou o Wi-Fi cair, desliga. Se você ligou a VPN na mão, ela não é desligada automaticamente.
- O monitor de Wi-Fi volta a funcionar depois de reiniciar o celular

> O Android só informa o nome da rede Wi-Fi para apps com permissão de localização, por isso o app pede essa permissão ao ativar a conexão automática.

## 1. Subir o servidor (grátis)

1. Crie uma VM *Always Free* na [Oracle Cloud](https://www.oracle.com/cloud/free/) com Ubuntu 22.04 ou 24.04 (qualquer VPS Linux também serve).
2. No painel da nuvem, libere a porta **51820/UDP** na *security list* da VM.
3. Na VM:

```bash
git clone https://github.com/rafanunesran/RTecVPN.git
cd RTecVPN/server
sudo bash install.sh
sudo bash add-client.sh meu-celular   # mostra um QR code
```

Variáveis opcionais do `install.sh`: `WG_PORT` (padrão 51820), `WG_NET` (padrão `10.8.0`), `PUBLIC_IP`, `WAN_IF`. Do `add-client.sh`: `DNS` (padrão `1.1.1.1, 9.9.9.9`).

## 2. Instalar o app Android

Baixe o APK gerado pelo CI (aba *Actions* → último build → artefato `rtecvpn-debug-apk`) ou compile:

```bash
cd android
./gradlew assembleDebug
# APK em android/app/build/outputs/apk/debug/
```

Abra o app, toque em **Escanear QR code** e aponte para o QR mostrado pelo `add-client.sh`.

A configuração gerada também funciona no app oficial do WireGuard, em qualquer sistema.
