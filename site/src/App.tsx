// Página de download do RTecVPN. Design gerado no Lovable ("RTecVPN Landing Page")
// e portado para um SPA Vite estático para o GitHub Pages.
import { useEffect, useState, type ReactNode } from "react";
import {
  Github, Download, Shield, Zap, Server, Wifi, MonitorSmartphone, Code2, Lock,
  Smartphone, Monitor, Apple, Terminal, Copy, Check, ArrowDown, ArrowUp, MapPin, Power, ExternalLink, Menu, X,
} from "lucide-react";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";

const REPO = "https://github.com/rafanunesran/RTecVPN";
const RELEASES = `${REPO}/releases`;
const API = "https://api.github.com/repos/rafanunesran/RTecVPN/releases/latest";
const INSTALL_CMD = [
  "git clone https://github.com/rafanunesran/RTecVPN.git",
  "cd RTecVPN/server",
  "sudo bash install.sh",
  "sudo bash add-client.sh meu-celular",
].join("\n");

type Platform = "android" | "windows" | "macos" | "linux";
type Asset = { name: string; size: number; browser_download_url: string };
type Release = { tag_name: string; published_at: string; assets: Asset[] };

// A ordem das extensões define qual arquivo é o botão principal de cada plataforma.
const PLATFORMS: { id: Platform; label: string; icon: typeof Monitor; exts: string[] }[] = [
  { id: "android", label: "Android", icon: Smartphone, exts: [".apk"] },
  { id: "windows", label: "Windows", icon: Monitor, exts: ["-setup.exe", ".exe", ".msi"] },
  { id: "macos", label: "macOS", icon: Apple, exts: [".dmg", ".pkg"] },
  { id: "linux", label: "Linux", icon: Terminal, exts: [".appimage", ".deb", ".rpm", ".tar.gz"] },
];

function detectOS(): Platform | null {
  const ua = navigator.userAgent;
  if (/android/i.test(ua)) return "android";
  if (/windows/i.test(ua)) return "windows";
  if (/iphone|ipad/i.test(ua)) return null;
  if (/mac os/i.test(ua)) return "macos";
  if (/linux|x11/i.test(ua)) return "linux";
  return null;
}
const fmtSize = (b: number) => (b > 1e6 ? `${(b / 1048576).toFixed(1)} MB` : `${(b / 1024).toFixed(0)} KB`);

function useRelease() {
  const [state, setState] = useState<{ release: Release | null; loading: boolean }>({ release: null, loading: true });
  useEffect(() => {
    fetch(API, { headers: { Accept: "application/vnd.github+json" } })
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((release: Release) => setState({ release, loading: false }))
      .catch(() => setState({ release: null, loading: false }));
  }, []);
  return state;
}

/** Todos os arquivos do release para a plataforma, do preferido para o menos preferido. */
function assetsFor(r: Release | null, p: Platform): Asset[] {
  if (!r) return [];
  const { exts } = PLATFORMS.find((x) => x.id === p)!;
  const rank = (a: Asset) => exts.findIndex((e) => a.name.toLowerCase().endsWith(e));
  const seen = new Set<string>();
  return r.assets
    .filter((a) => rank(a) >= 0)
    .sort((a, b) => rank(a) - rank(b))
    .filter((a) => (seen.has(a.name) ? false : (seen.add(a.name), true)));
}
const extOf = (name: string) => {
  const n = name.toLowerCase();
  if (n.endsWith(".tar.gz")) return ".tar.gz";
  if (n.endsWith("-setup.exe")) return ".exe";
  return name.slice(n.lastIndexOf("."));
};

function Logo({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden>
      <defs>
        <linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--primary)" /><stop offset="1" stopColor="var(--accent)" />
        </linearGradient>
      </defs>
      <path d="M16 2 4 6.5v8.2C4 22 9.2 27.8 16 30c6.8-2.2 12-8 12-15.3V6.5L16 2Z" fill="url(#lg)" />
      <path d="M17.5 8 11 17.5h4.5L14 24l7-10h-4.6L17.5 8Z" fill="var(--background)" />
    </svg>
  );
}

const btn = "inline-flex items-center justify-center gap-2 rounded-full font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background";
const btnPrimary = `${btn} bg-gradient-accent text-primary-foreground shadow-glow hover:brightness-110 hover:-translate-y-0.5`;
const btnGhost = `${btn} glass text-foreground hover:bg-secondary`;

function Header() {
  const [open, setOpen] = useState(false);
  const links = [["Recursos", "#recursos"], ["Download", "#download"], ["Servidor próprio", "#servidor"], ["FAQ", "#faq"]];
  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <a href="#top" className="flex items-center gap-2 font-bold text-lg"><Logo /><span>RTec<span className="text-gradient">VPN</span></span></a>
        <nav className="hidden items-center gap-7 text-sm text-muted-foreground md:flex">
          {links.map(([l, h]) => <a key={h} href={h} className="hover:text-foreground transition-colors">{l}</a>)}
        </nav>
        <div className="flex items-center gap-2">
          <a href={REPO} target="_blank" rel="noreferrer" className={`${btnGhost} h-9 px-4 text-sm`}><Github className="h-4 w-4" /><span className="hidden sm:inline">GitHub</span></a>
          <button className="md:hidden p-2" aria-label="Menu" aria-expanded={open} onClick={() => setOpen(!open)}>{open ? <X /> : <Menu />}</button>
        </div>
      </div>
      {open && (
        <nav className="flex flex-col gap-1 border-t border-border px-5 py-3 md:hidden">
          {links.map(([l, h]) => <a key={h} href={h} onClick={() => setOpen(false)} className="py-2 text-muted-foreground">{l}</a>)}
        </nav>
      )}
    </header>
  );
}

function Phone() {
  const [s, setS] = useState(0);
  useEffect(() => { const t = setInterval(() => setS((x) => x + 1), 1000); return () => clearInterval(t); }, []);
  const t = 3725 + s;
  const time = [Math.floor(t / 3600), Math.floor((t % 3600) / 60), t % 60].map((n) => String(n).padStart(2, "0")).join(":");
  return (
    <div className="relative mx-auto w-[280px] sm:w-[300px]" aria-hidden>
      <div className="absolute -inset-10 rounded-full bg-gradient-accent opacity-25 blur-3xl" />
      <div className="relative rounded-[2.8rem] border border-border bg-popover p-3 shadow-glow-violet">
        <div className="relative overflow-hidden rounded-[2.2rem] bg-background px-5 pb-8 pt-4">
          <div className="mx-auto mb-5 h-5 w-24 rounded-full bg-popover" />
          <div className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-1.5 font-bold"><Logo className="h-5 w-5" />RTecVPN</span>
            <span className="rounded-full bg-success/15 px-2.5 py-0.5 text-xs font-semibold text-success">● Protegido</span>
          </div>
          <div className="relative mx-auto my-10 grid h-44 w-44 place-items-center">
            <span className="absolute inset-0 rounded-full border-2 border-primary animate-pulse-ring" />
            <span className="absolute inset-0 rounded-full border-2 border-accent animate-pulse-ring [animation-delay:1.2s]" />
            <span className="absolute inset-2 rounded-full bg-gradient-accent opacity-90 shadow-glow" />
            <span className="absolute inset-5 grid place-items-center rounded-full bg-background">
              <Power className="h-12 w-12 text-primary" strokeWidth={2.5} />
            </span>
          </div>
          <p className="text-center text-xl font-bold text-success">Conectado</p>
          <p className="mt-1 text-center font-mono text-sm text-muted-foreground">{time}</p>
          <div className="glass mt-6 flex items-center gap-3 rounded-2xl p-3">
            <MapPin className="h-5 w-5 text-primary" />
            <div className="text-sm"><p className="font-semibold">São Paulo, BR</p><p className="text-xs text-muted-foreground">Servidor próprio · WireGuard</p></div>
          </div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <div className="glass rounded-2xl p-3"><p className="flex items-center gap-1 text-xs text-muted-foreground"><ArrowDown className="h-3 w-3 text-primary" />Download</p><p className="font-bold">84,2 <span className="text-xs font-normal text-muted-foreground">Mb/s</span></p></div>
            <div className="glass rounded-2xl p-3"><p className="flex items-center gap-1 text-xs text-muted-foreground"><ArrowUp className="h-3 w-3 text-accent" />Upload</p><p className="font-bold">31,7 <span className="text-xs font-normal text-muted-foreground">Mb/s</span></p></div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Section({ id, eyebrow, title, sub, children }: { id: string; eyebrow: string; title: ReactNode; sub?: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-20 py-20 sm:py-28">
      <div className="mx-auto max-w-6xl px-5">
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <p className="text-sm font-semibold uppercase tracking-widest text-primary">{eyebrow}</p>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">{title}</h2>
          {sub && <p className="mt-4 text-muted-foreground">{sub}</p>}
        </div>
        {children}
      </div>
    </section>
  );
}

const FEATURES = [
  { icon: Zap, t: "Protocolo WireGuard", d: "Criptografia moderna, conexões instantâneas e consumo mínimo de bateria." },
  { icon: Server, t: "Servidor próprio", d: "Seu servidor, seus dados. Sem logs, sem intermediários, sem surpresas." },
  { icon: Wifi, t: "Auto-conexão por Wi-Fi", d: "Conecta sozinho nas redes Wi-Fi que você escolher e desconecta ao sair." },
  { icon: MonitorSmartphone, t: "Multiplataforma", d: "Android, Windows, macOS e Linux com a mesma experiência." },
  { icon: Code2, t: "Grátis e código aberto", d: "Licença MIT. Audite, modifique e contribua no GitHub." },
  { icon: Lock, t: "Kill switch e privacidade", d: "Bloqueia o tráfego se a VPN cair, para nada vazar fora do túnel." },
];

const FAQ = [
  ["O RTecVPN é grátis mesmo?", "Sim. O app é 100% gratuito e de código aberto (MIT). Você só precisa de um servidor, e dá para usar um gratuito, como o Oracle Cloud Free Tier."],
  ["É seguro?", "O RTecVPN usa WireGuard, um protocolo moderno e auditado. Como o servidor é seu, nenhum terceiro vê ou registra seu tráfego. E todo o código está aberto para inspeção."],
  ["O que é WireGuard?", "É um protocolo de VPN leve e rápido, com criptografia de ponta (ChaCha20, Curve25519). Ele é integrado ao kernel Linux e conhecido pela simplicidade e desempenho."],
  ["Como funciona a auto-conexão por Wi-Fi?", "Você marca as redes Wi-Fi em que quer proteção (ex.: café, aeroporto). Ao entrar nelas, o app conecta a VPN automaticamente; ao sair, desconecta."],
  ["Quais plataformas são suportadas?", "Android, Windows, macOS e Linux. Os instaladores ficam na seção Download e na página de releases do GitHub."],
];

function CopyCode() {
  const [ok, setOk] = useState(false);
  return (
    <div className="mt-4 flex items-start gap-2 rounded-xl border border-border bg-background p-3">
      <code className="flex-1 overflow-x-auto whitespace-pre font-mono text-xs leading-relaxed text-primary">{INSTALL_CMD}</code>
      <button
        aria-label="Copiar comando"
        onClick={() => { navigator.clipboard?.writeText(INSTALL_CMD); setOk(true); setTimeout(() => setOk(false), 1800); }}
        className="shrink-0 rounded-lg p-2 text-muted-foreground hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >{ok ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}</button>
    </div>
  );
}

export default function App() {
  const { release, loading } = useRelease();
  const [os, setOs] = useState<Platform | null>(null);
  useEffect(() => setOs(detectOS()), []);
  const osDef = PLATFORMS.find((p) => p.id === os);
  const osAsset = os ? assetsFor(release, os)[0] : undefined;
  const date = release ? new Date(release.published_at).toLocaleDateString("pt-BR", { day: "2-digit", month: "long", year: "numeric" }) : null;

  return (
    <div id="top" className="relative min-h-screen overflow-x-hidden font-sans">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[900px] bg-grid" />
      <div className="pointer-events-none absolute -top-40 left-1/2 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-gradient-accent opacity-15 blur-3xl" />
      <Header />
      <main className="relative">
        <section className="mx-auto grid max-w-6xl items-center gap-14 px-5 pb-16 pt-16 lg:grid-cols-2 lg:pt-24">
          <div className="text-center lg:text-left">
            <span className="glass inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs text-muted-foreground"><Shield className="h-3.5 w-3.5 text-primary" />Open source · WireGuard · Sem logs</span>
            <h1 className="mt-6 text-5xl font-extrabold leading-[1.05] tracking-tight sm:text-6xl">VPN grátis, rápida e <span className="text-gradient">sua.</span></h1>
            <p className="mx-auto mt-6 max-w-lg text-lg text-muted-foreground lg:mx-0">Proteção com WireGuard rodando no <strong className="text-foreground">seu próprio servidor</strong>. Nada de assinaturas, nada de terceiros lendo seu tráfego.</p>
            <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row lg:justify-start sm:justify-center">
              <a href={osAsset?.browser_download_url ?? "#download"} className={`${btnPrimary} h-14 px-8 text-base`}>
                <Download className="h-5 w-5" />{osDef ? `Baixar para ${osDef.label}` : "Baixar RTecVPN"}
              </a>
              <a href={REPO} target="_blank" rel="noreferrer" className={`${btnGhost} h-14 px-7 text-base`}><Github className="h-5 w-5" />Ver no GitHub</a>
            </div>
            {release && <p className="mt-4 text-xs text-muted-foreground">Versão {release.tag_name} · {date}</p>}
          </div>
          <Phone />
        </section>

        <Section id="recursos" eyebrow="Recursos" title="Tudo que uma VPN premium tem. Sem pagar nada." sub="Feito para quem quer privacidade de verdade e controle total.">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: I, t, d }) => (
              <div key={t} className="glass group rounded-2xl p-6 transition-all hover:-translate-y-1 hover:border-primary/40">
                <div className="grid h-12 w-12 place-items-center rounded-xl bg-gradient-accent text-primary-foreground shadow-glow"><I className="h-6 w-6" /></div>
                <h3 className="mt-5 text-lg font-bold">{t}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{d}</p>
              </div>
            ))}
          </div>
        </Section>

        <Section id="download" eyebrow="Download" title="Escolha sua plataforma" sub={release ? `Última versão: ${release.tag_name} · publicada em ${date}` : loading ? "Buscando a versão mais recente…" : "Ainda não há versão publicada. Acompanhe pela página de releases."}>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {PLATFORMS.map(({ id, label, icon: I }) => {
              const [a, ...others] = assetsFor(release, id);
              const hl = id === os;
              return (
                <div key={id} className={`glass relative flex flex-col rounded-2xl p-6 transition-all ${hl ? "border-primary/60 shadow-glow" : ""}`}>
                  {hl && <span className="absolute -top-3 left-6 rounded-full bg-gradient-accent px-3 py-0.5 text-xs font-bold text-primary-foreground">Seu sistema</span>}
                  <I className="h-9 w-9 text-primary" />
                  <h3 className="mt-4 text-xl font-bold">{label}</h3>
                  <p className="mt-1 min-h-10 text-sm text-muted-foreground">
                    {loading ? "Carregando…" : a ? <>{release!.tag_name} · {fmtSize(a.size)}<br /><span className="break-all text-xs">{a.name}</span></> : "Em breve"}
                  </p>
                  <div className="mt-6 flex-1" />
                  {a ? (
                    <a href={a.browser_download_url} className={`${btnPrimary} h-11 px-5 text-sm`}><Download className="h-4 w-4" />Baixar {extOf(a.name)}</a>
                  ) : (
                    <a href={RELEASES} target="_blank" rel="noreferrer" className={`${btnGhost} h-11 px-5 text-sm`}>Em breve <ExternalLink className="h-4 w-4" /></a>
                  )}
                  <p className="mt-3 flex min-h-4 flex-wrap justify-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    {others.length > 0 && <span>Também em:</span>}
                    {others.map((o) => (
                      <a key={o.name} href={o.browser_download_url} className="text-primary hover:underline" title={o.name}>{extOf(o.name)}</a>
                    ))}
                  </p>
                </div>
              );
            })}
          </div>
          <p className="mt-8 text-center text-sm"><a href={RELEASES} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">Todas as versões <ExternalLink className="h-3.5 w-3.5" /></a></p>
        </Section>

        <Section id="servidor" eyebrow="Servidor próprio" title={<>Servidor próprio em <span className="text-gradient">3 passos</span></>} sub="Em poucos minutos você tem sua VPN privada rodando de graça.">
          <div className="grid gap-5 lg:grid-cols-3">
            {[
              { t: "Crie uma VM grátis", d: <>Use um provedor com plano gratuito, como o <strong className="text-foreground">Oracle Cloud Free Tier</strong>, com Ubuntu ou Debian. Libere a porta <strong className="text-foreground">51820/UDP</strong> no firewall da nuvem.</> },
              { t: "Rode o script de instalação", d: <>Acesse a VM via SSH e execute:<CopyCode /></> },
              { t: "Importe o perfil no app", d: <>O <code className="font-mono text-foreground">add-client.sh</code> mostra um <strong className="text-foreground">QR code</strong> e gera o arquivo <code className="font-mono text-foreground">.conf</code>. Escaneie ou importe no RTecVPN e conecte.</> },
            ].map((s, i) => (
              <div key={s.t} className="glass min-w-0 rounded-2xl p-6">
                <span className="text-gradient font-mono text-4xl font-extrabold">0{i + 1}</span>
                <h3 className="mt-3 text-lg font-bold">{s.t}</h3>
                <div className="mt-2 text-sm text-muted-foreground">{s.d}</div>
              </div>
            ))}
          </div>
        </Section>

        <Section id="faq" eyebrow="FAQ" title="Perguntas frequentes">
          <Accordion type="single" collapsible className="glass mx-auto max-w-3xl rounded-2xl px-6">
            {FAQ.map(([q, a], i) => (
              <AccordionItem key={q} value={`q${i}`} className="border-border last:border-0">
                <AccordionTrigger className="text-left text-base font-semibold hover:no-underline">{q}</AccordionTrigger>
                <AccordionContent className="text-muted-foreground">{a}</AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </Section>
      </main>

      <footer className="border-t border-border py-10">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-5 text-sm text-muted-foreground sm:flex-row">
          <span className="flex items-center gap-2"><Logo className="h-6 w-6" />RTecVPN · Software livre sob licença MIT</span>
          <a href={REPO} target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 hover:text-foreground"><Github className="h-4 w-4" />rafanunesran/RTecVPN</a>
        </div>
      </footer>
    </div>
  );
}
