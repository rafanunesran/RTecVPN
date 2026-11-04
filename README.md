# RTecVPN

VPN gratuita e de código aberto, baseada em **WireGuard**. Você roda o próprio servidor (dá para usar o plano gratuito da Oracle Cloud) e conecta pelo app.

## O que tem aqui

| Pasta | Conteúdo |
|---|---|
| `server/` | Scripts que instalam o WireGuard num servidor Linux e criam clientes com QR code |
| `android/` | App Android (Kotlin) |
| `desktop/` | App para Windows, macOS e Linux (Tauri) |
| `site/` | Página de download publicada no GitHub Pages: https://rafanunesran.github.io/RTecVPN/ |

## Funcionalidades do app Android

- Botão único para conectar/desconectar, com cronômetro e tráfego da sessão
- **Vários servidores**: importe por **QR code**, arquivo `.conf` ou colando o texto; renomeie, edite, compartilhe o QR ou exclua
- **Kill switch** (VPN sempre ativa do Android), conectar ao ligar o celular, **split tunneling** por app e **DNS personalizado**
- Tema escuro e claro, onboarding e tela de detalhes da conexão
- **Conexão automática por Wi-Fi**: escolha redes (ex.: Wi-Fi do café, do trabalho). Ao entrar nelas a VPN liga sozinha; ao sair ou o Wi-Fi cair, desliga. Se você ligou ou desligou a VPN na mão, o app respeita até você trocar de rede. Opcional: conectar em qualquer Wi-Fi aberto.
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

## 3. App para Windows, macOS e Linux

Os instaladores ficam na página de [Releases](https://github.com/rafanunesran/RTecVPN/releases): `.msi`/`-setup.exe` (Windows), `.dmg` (macOS), `.AppImage`/`.deb`/`.rpm` (Linux).

O app tem as mesmas funções do Android: botão de conectar, importação da configuração e conexão automática em redes Wi-Fi escolhidas. Ele fica na bandeja do sistema ao fechar a janela, para continuar monitorando o Wi-Fi. Ele usa o WireGuard do sistema por baixo:

| Sistema | Precisa ter instalado | Pedido de permissão |
|---|---|---|
| Windows | [WireGuard para Windows](https://www.wireguard.com/install/) | UAC ao conectar/desconectar |
| macOS | `brew install wireguard-tools` | Senha de administrador ao conectar/desconectar |
| Linux | `wireguard-tools` (o `.deb`/`.rpm` já instala) | Senha via polkit ao conectar/desconectar |

> No macOS 14.4+ e no Windows 11 24H2 o sistema pode esconder o nome do Wi-Fi de apps sem permissão de localização. Se o app mostrar "Wi-Fi atual: —", libere a localização para o RTecVPN nas configurações do sistema.

Para compilar localmente: `cd desktop && npm ci && npx tauri build`.

## Publicar uma versão

```bash
git tag v0.1.0 && git push origin v0.1.0
```

O workflow `Release` gera todos os instaladores e o APK e publica no GitHub Releases. Para o APK ter assinatura fixa (necessário para atualizar o app sem desinstalar), crie os secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`; sem eles o APK sai assinado com chave de debug.

A configuração gerada também funciona no app oficial do WireGuard, em qualquer sistema.

## Página de download

A página em `site/` (React + Vite + Tailwind, design feito no Lovable) lê o último GitHub Release e mostra o botão certo para cada sistema, pela extensão do arquivo: `.apk` (Android), `.exe`/`.msi` (Windows), `.dmg`/`.pkg` (macOS), `.AppImage`/`.deb`/`.rpm`/`.tar.gz` (Linux). O workflow `pages.yml` publica a página a cada push em `site/`. Não precisa republicar a cada release: a página consulta o último release ao abrir.

Para rodar localmente: `cd site && npm install && npm run dev`.
