// RTecVPN design tokens — shared visual identity (web + native app).
export const tokens = {
  color: {
    bg: "#0B1020",
    bgElevated: "#111831",
    surface: "rgba(255,255,255,0.04)",
    surfaceBorder: "rgba(255,255,255,0.08)",
    text: "#E8ECF8",
    textMuted: "#94A0BF",
    cyan: "#22D3EE",
    violet: "#8B5CF6",
    success: "#22C55E",
    danger: "#F43F5E",
  },
  gradient: {
    accent: "linear-gradient(135deg, #22D3EE 0%, #8B5CF6 100%)",
  },
  radius: { sm: 8, md: 12, lg: 16, xl: 24, full: 9999 },
  shadow: {
    glowCyan: "0 0 40px -8px rgba(34,211,238,0.55)",
    glowViolet: "0 0 60px -12px rgba(139,92,246,0.6)",
    card: "0 20px 50px -20px rgba(0,0,0,0.6)",
  },
  font: { sans: "'Plus Jakarta Sans', system-ui, sans-serif", mono: "'JetBrains Mono', monospace" },
} as const;
export type Tokens = typeof tokens;
