# RTecVPN — especificação de UI do app

Design feito no Lovable. Referência interativa (todas as telas, tema claro/escuro e a página de tokens):

- Editor: https://lovable.dev/projects/93675bf2-f5dc-4f98-988f-8ad0f5872628
- Preview: https://id-preview--93675bf2-f5dc-4f98-988f-8ad0f5872628.lovable.app (tokens em `/tokens`)

A página de download (`site/`) usa a mesma identidade.

## Cores

| Token | Escuro | Claro | Uso (Material 3) |
|---|---|---|---|
| Background | `#0B1020` | `#F5F7FC` | background |
| Surface | `#121933` | `#FFFFFF` | surface |
| Surface 2 | `#1A2242` | `#EEF1F8` | surfaceVariant |
| Border | `#FFFFFF` 8% | `#0B1020` 8% | outline |
| Text | `#E6E9F5` | `#0B1020` | onBackground |
| Muted | `#8A93B2` | `#5B6584` | onSurfaceVariant |
| Idle (desconectado) | `#4B5578` | `#A3ABC4` | estado |
| Cyan | `#22D3EE` | `#22D3EE` | primary, início do gradiente |
| Violet | `#8B5CF6` | `#8B5CF6` | secondary, fim do gradiente |
| Conectado | `#22C55E` | `#22C55E` | success |
| Conectando | `#F59E0B` | `#F59E0B` | warning |
| Erro | `#EF4444` | `#EF4444` | error |

Gradiente de marca, 135°: `Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF8B5CF6)), start = TopStart, end = BottomEnd)`.

## Tipografia (Inter; JetBrains Mono para IPs e chaves)

| Estilo | Spec | Exemplo |
|---|---|---|
| Display (status) | 22sp Bold, 28 lh | Conectado |
| Headline (onboarding) | 24sp Bold, 30 lh | Use seu servidor |
| Title (top bar) | 17sp SemiBold | Servidores |
| Body | 15sp Medium, 22 lh | Kill switch |
| Body small | 13.5sp Regular | Bloquear internet sem VPN |
| Caption | 12sp Regular | Último handshake: há 12 s |
| Overline | 11sp SemiBold, +0.08em, CAIXA ALTA | CONEXÃO |
| Mono | 12.5sp JetBrains Mono | vpn.rtec.dev:51820 |

## Espaçamento, raios e elevação

- Espaçamentos: 4, 8, 12, 16, 24, 32 dp. Padding de tela 16, gap entre cards 12, padding de card 14 a 16.
- Raios: chip 999, container de ícone 12 a 16, botão primário 16, FAB 18, card 20, onboarding/hero 24 a 32.
- Cards sem sombra, com borda de 1dp. Menus com sombra de 8dp.
- Glow: blur 40dp, spread −6dp, cor do estado a 70%. Halo do botão de conexão: blur 24dp, 35% alpha, pulso de 2.4s.

## Componentes

- **Botão de conexão**: container 224dp; anel de 3dp (em "Conectando" vira sweepGradient girando 1.2s linear infinito); botão central de 160dp em Surface; ícone Power 56dp. Cor do anel/ícone por estado: Idle `#4B5578`, Conectando `#F59E0B`, Conectado `#22C55E`.
- **Botão primário**: 56dp de altura, raio 16, fundo gradiente, texto 16sp SemiBold em `#0B1020`, glow cyan.
- **Card de servidor**: raio 20, Surface, borda 1dp; ícone 44dp raio 16; nome 15sp SemiBold; endpoint em mono 12sp; chip de ping 11sp verde. Ativo: borda cyan e check com gradiente de 24dp.
- **Switch**: 48×28dp, thumb 20dp. Ligado = trilho gradiente, thumb na cor do Background. Desligado = Surface 2 com borda.
- **Bottom navigation**: 68dp, Surface com borda no topo; indicador pill 56×32dp em Surface 2, ícone 20dp cyan; rótulo 11sp Medium. Abas: Início, Servidores, Wi‑Fi, Ajustes.
- **FAB**: 56dp, raio 18, gradiente, ícone "+" 26dp, margem 20dp.
- **Stat tile**: grade de 3 colunas, gap 8, padding 12; rótulo 11sp muted; valor 17sp Bold com números tabulares.
- **Aviso de permissão**: raio 20, fundo Warning 10%, borda Warning 30%, ícone 18dp.

## Telas

1. **Onboarding** (3 passos): privacidade com WireGuard, use seu próprio servidor, auto-conexão em Wi‑Fi. Botões Pular / Próximo / Começar.
2. **Adicionar servidor**: Escanear QR code, Importar arquivo .conf, Colar configuração; estado vazio.
3. **Início**: barra com logo e engrenagem; botão de conexão; status + cronômetro; card do servidor selecionado (nome, endpoint, ping, chevron); tiles Download / Upload / Sessão; link "Ver detalhes da conexão".
4. **Servidores**: lista de perfis (nome, endpoint, último handshake, check do ativo); FAB "+"; menu com editar, compartilhar QR, excluir.
5. **Wi‑Fi automático**: chave mestre "Conectar automaticamente em redes Wi‑Fi escolhidas"; rede atual destacada com "Adicionar rede atual"; lista de SSIDs com chaves; "Desconectar ao sair dessas redes"; "Conectar em redes abertas/desconhecidas"; aviso sobre a permissão de localização.
6. **Ajustes**: Kill switch, Iniciar com o sistema, Split tunneling, DNS personalizado, Tema (Escuro/Claro/Sistema), Idioma, Sobre/versão/GitHub, Verificar atualizações.
7. **Detalhes da conexão**: interface, chave pública (truncada, com copiar), endpoint, allowed IPs, handshake, tráfego, MTU.
