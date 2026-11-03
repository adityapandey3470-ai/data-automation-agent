export const DASHBOARD_STYLE_PRESETS = [
  {
    id: 'matte',
    name: 'Matte / Soft UI',
    description: 'Premium velvety surfaces, subtle shadows, and soft ambient depth.',
    badge: 'Soft Depth'
  },
  {
    id: 'modern',
    name: 'Modern / Corporate',
    description: 'Professional enterprise SaaS layout with crisp borders and clean typography.',
    badge: 'Enterprise'
  },
  {
    id: 'glass',
    name: 'Glassmorphism',
    description: 'Frosted translucent cards with smooth backdrop blur and glowing borders.',
    badge: 'Frosted Glass'
  },
  {
    id: 'minimal',
    name: 'Minimal',
    description: 'Ultra-clean spacious layout with restrained colors and hairline borders.',
    badge: 'Clean'
  },
  {
    id: 'dark',
    name: 'Dark / Midnight',
    description: 'Deep midnight canvas with high-contrast surfaces and glowing accents.',
    badge: 'High Contrast'
  },
  {
    id: 'bento',
    name: 'Bento Grid',
    description: 'Modern modular asymmetric grid with prominent hero metrics and visual hierarchy.',
    badge: 'Modular'
  },
  {
    id: 'neumorphic',
    name: 'Neumorphism',
    description: 'Soft dual-layer raised elements with embossed depth and tactile buttons.',
    badge: 'Tactile'
  },
  {
    id: 'cyberpunk',
    name: 'Cyberpunk / Neon Glow',
    description: 'Futuristic high-tech aesthetic with luminous neon borders, electric emerald glow, and deep cyber depth.',
    badge: 'Neon Glow'
  }
];

export const COLOR_PALETTES = [
  {
    id: 'purple_blue',
    name: 'Purple + Blue',
    primary: '#4f46e5',
    primaryHover: '#4338ca',
    primarySubtle: '#eef2ff',
    secondary: '#2563eb',
    bg: '#f8f9fc',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#e2e8f0',
    textPrimary: '#0f172a',
    textSecondary: '#475569',
    textMuted: '#94a3b8',
    chartColors: ['#4f46e5', '#2563eb', '#06b6d4', '#8b5cf6'],
    accentSubtle: '#e0e7ff'
  },
  {
    id: 'slate_teal',
    name: 'Slate + Teal',
    primary: '#0d9488',
    primaryHover: '#0f766e',
    primarySubtle: '#f0fdfa',
    secondary: '#0284c7',
    bg: '#f8fafc',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#cbd5e1',
    textPrimary: '#0f172a',
    textSecondary: '#334155',
    textMuted: '#64748b',
    chartColors: ['#0d9488', '#0284c7', '#38bdf8', '#14b8a6'],
    accentSubtle: '#ccfbf1'
  },
  {
    id: 'rose_indigo',
    name: 'Rose + Indigo',
    primary: '#e11d48',
    primaryHover: '#be123c',
    primarySubtle: '#fff1f2',
    secondary: '#6366f1',
    bg: '#fff5f7',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#fecdd3',
    textPrimary: '#1e1b4b',
    textSecondary: '#4c1d95',
    textMuted: '#831843',
    chartColors: ['#e11d48', '#6366f1', '#f43f5e', '#a855f7'],
    accentSubtle: '#ffe4e6'
  },
  {
    id: 'sunset_aurora',
    name: 'Sunset / Aurora',
    primary: '#ea580c',
    primaryHover: '#c2410c',
    primarySubtle: '#fff7ed',
    secondary: '#db2777',
    bg: '#fffaf5',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#fed7aa',
    textPrimary: '#18181b',
    textSecondary: '#52525b',
    textMuted: '#71717a',
    chartColors: ['#ea580c', '#db2777', '#f59e0b', '#f43f5e'],
    accentSubtle: '#ffedd5'
  },
  {
    id: 'ocean_blue',
    name: 'Ocean Blue',
    primary: '#0284c7',
    primaryHover: '#0369a1',
    primarySubtle: '#f0f9ff',
    secondary: '#0ea5e9',
    bg: '#f0f7fc',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#bae6fd',
    textPrimary: '#082f49',
    textSecondary: '#0369a1',
    textMuted: '#38bdf8',
    chartColors: ['#0284c7', '#06b6d4', '#0284c7', '#3b82f6'],
    accentSubtle: '#e0f2fe'
  },
  {
    id: 'emerald_forest',
    name: 'Emerald Forest',
    primary: '#059669',
    primaryHover: '#047857',
    primarySubtle: '#ecfdf5',
    secondary: '#10b981',
    bg: '#f4fbf7',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#a7f3d0',
    textPrimary: '#064e3b',
    textSecondary: '#047857',
    textMuted: '#34d399',
    chartColors: ['#059669', '#10b981', '#14b8a6', '#84cc16'],
    accentSubtle: '#d1fae5'
  },
  {
    id: 'lavender_dream',
    name: 'Lavender Dream',
    primary: '#7c3aed',
    primaryHover: '#6d28d9',
    primarySubtle: '#f5f3ff',
    secondary: '#a855f7',
    bg: '#faf5ff',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#e9d5ff',
    textPrimary: '#2e1065',
    textSecondary: '#581c87',
    textMuted: '#7e22ce',
    chartColors: ['#7c3aed', '#a855f7', '#c084fc', '#6366f1'],
    accentSubtle: '#ede9fe'
  },
  {
    id: 'midnight_navy',
    name: 'Midnight Navy',
    primary: '#38bdf8',
    primaryHover: '#0ea5e9',
    primarySubtle: 'rgba(56, 189, 248, 0.12)',
    secondary: '#818cf8',
    bg: '#0b1329',
    surface: '#111d3d',
    cardBg: '#132145',
    border: '#1e3366',
    textPrimary: '#f8fafc',
    textSecondary: '#94a3b8',
    textMuted: '#64748b',
    chartColors: ['#38bdf8', '#818cf8', '#34d399', '#f472b6'],
    accentSubtle: 'rgba(56, 189, 248, 0.2)'
  },
  {
    id: 'monochrome',
    name: 'Monochrome',
    primary: '#18181b',
    primaryHover: '#09090b',
    primarySubtle: '#f4f4f5',
    secondary: '#52525b',
    bg: '#fafafa',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#e4e4e7',
    textPrimary: '#09090b',
    textSecondary: '#3f3f46',
    textMuted: '#71717a',
    chartColors: ['#18181b', '#52525b', '#71717a', '#a1a1aa'],
    accentSubtle: '#e4e4e7'
  },
  {
    id: 'nordic_neutral',
    name: 'Nordic Neutral',
    primary: '#475569',
    primaryHover: '#334155',
    primarySubtle: '#f1f5f9',
    secondary: '#64748b',
    bg: '#f7f6f4',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#e6e4df',
    textPrimary: '#1e293b',
    textSecondary: '#475569',
    textMuted: '#64748b',
    chartColors: ['#475569', '#64748b', '#0ea5e9', '#d97706'],
    accentSubtle: '#eae8e3'
  },
  {
    id: 'warm_sand',
    name: 'Warm Sand',
    primary: '#c2410c',
    primaryHover: '#9a3412',
    primarySubtle: '#fff7ed',
    secondary: '#b45309',
    bg: '#faf6f0',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#ebdccb',
    textPrimary: '#431407',
    textSecondary: '#78350f',
    textMuted: '#9a3412',
    chartColors: ['#c2410c', '#b45309', '#d97706', '#eab308'],
    accentSubtle: '#fed7aa'
  },
  {
    id: 'rose_gold',
    name: 'Rose Gold',
    primary: '#be185d',
    primaryHover: '#9d174d',
    primarySubtle: '#fdf2f8',
    secondary: '#d97706',
    bg: '#fdf8f9',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#fbcfe8',
    textPrimary: '#500724',
    textSecondary: '#831843',
    textMuted: '#9d174d',
    chartColors: ['#be185d', '#d97706', '#f472b6', '#fbbf24'],
    accentSubtle: '#fce7f3'
  },
  {
    id: 'cherry_ruby',
    name: 'Cherry / Ruby',
    primary: '#dc2626',
    primaryHover: '#b91c1c',
    primarySubtle: '#fef2f2',
    secondary: '#991b1b',
    bg: '#fff5f5',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#fecaca',
    textPrimary: '#450a0a',
    textSecondary: '#7f1d1d',
    textMuted: '#991b1b',
    chartColors: ['#dc2626', '#991b1b', '#f87171', '#fb7185'],
    accentSubtle: '#fee2e2'
  },
  {
    id: 'cyber_neon',
    name: 'Cyber Neon',
    primary: '#10b981',
    primaryHover: '#059669',
    primarySubtle: 'rgba(16, 185, 129, 0.15)',
    secondary: '#8b5cf6',
    bg: '#080c14',
    surface: '#0d131f',
    cardBg: '#121929',
    border: '#1f2e4d',
    textPrimary: '#f9fafb',
    textSecondary: '#9ca3af',
    textMuted: '#6b7280',
    chartColors: ['#10b981', '#8b5cf6', '#06b6d4', '#f43f5e'],
    accentSubtle: 'rgba(139, 92, 246, 0.2)'
  },
  {
    id: 'mint_aqua',
    name: 'Mint / Aqua',
    primary: '#059669',
    primaryHover: '#047857',
    primarySubtle: '#f0fdf4',
    secondary: '#0891b2',
    bg: '#f0fdf9',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#99f6e4',
    textPrimary: '#042f2e',
    textSecondary: '#115e59',
    textMuted: '#0d9488',
    chartColors: ['#059669', '#0891b2', '#2dd4bf', '#38bdf8'],
    accentSubtle: '#ccfbf1'
  },
  {
    id: 'obsidian_gold',
    name: 'Obsidian & Gold',
    primary: '#f59e0b',
    primaryHover: '#d97706',
    primarySubtle: 'rgba(245, 158, 11, 0.15)',
    secondary: '#eab308',
    bg: '#0a0e17',
    surface: '#0f172a',
    cardBg: '#131c2e',
    border: '#293548',
    textPrimary: '#f8fafc',
    textSecondary: '#94a3b8',
    textMuted: '#64748b',
    chartColors: ['#f59e0b', '#eab308', '#f97316', '#fbbf24'],
    accentSubtle: 'rgba(245, 158, 11, 0.2)'
  }
];

export const GRADIENT_PRESETS = [
  {
    id: 'aurora',
    name: 'Aurora Borealis',
    gradient: 'linear-gradient(135deg, rgba(79, 70, 229, 0.08) 0%, rgba(6, 182, 212, 0.08) 50%, rgba(16, 185, 129, 0.08) 100%)',
    css: 'linear-gradient(135deg, rgba(79, 70, 229, 0.08) 0%, rgba(6, 182, 212, 0.08) 50%, rgba(16, 185, 129, 0.08) 100%)'
  },
  {
    id: 'cosmic',
    name: 'Cosmic Midnight',
    gradient: 'radial-gradient(ellipse at top, rgba(99, 102, 241, 0.15), rgba(15, 23, 42, 0) 70%)',
    css: 'radial-gradient(ellipse at top, rgba(99, 102, 241, 0.15), rgba(15, 23, 42, 0) 70%)'
  },
  {
    id: 'sunset_glow',
    name: 'Sunset Glow',
    gradient: 'linear-gradient(135deg, rgba(249, 115, 22, 0.08) 0%, rgba(219, 39, 119, 0.08) 100%)',
    css: 'linear-gradient(135deg, rgba(249, 115, 22, 0.08) 0%, rgba(219, 39, 119, 0.08) 100%)'
  },
  {
    id: 'oceanic_breeze',
    name: 'Oceanic Breeze',
    gradient: 'linear-gradient(180deg, rgba(2, 132, 199, 0.08) 0%, rgba(240, 249, 255, 0) 100%)',
    css: 'linear-gradient(180deg, rgba(2, 132, 199, 0.08) 0%, rgba(240, 249, 255, 0) 100%)'
  },
  {
    id: 'soft_slate',
    name: 'Soft Slate',
    gradient: 'linear-gradient(135deg, rgba(148, 163, 184, 0.08) 0%, rgba(241, 245, 249, 0.5) 100%)',
    css: 'linear-gradient(135deg, rgba(148, 163, 184, 0.08) 0%, rgba(241, 245, 249, 0.5) 100%)'
  },
  {
    id: 'frosted_mesh',
    name: 'Frosted Mesh',
    gradient: 'radial-gradient(circle at 10% 20%, rgba(124, 58, 237, 0.06) 0%, transparent 40%), radial-gradient(circle at 90% 80%, rgba(56, 189, 248, 0.06) 0%, transparent 40%)',
    css: 'radial-gradient(circle at 10% 20%, rgba(124, 58, 237, 0.06) 0%, transparent 40%), radial-gradient(circle at 90% 80%, rgba(56, 189, 248, 0.06) 0%, transparent 40%)'
  },
  {
    id: 'cyber_emerald',
    name: 'Cyber Emerald',
    gradient: 'radial-gradient(circle at 15% 15%, rgba(16, 185, 129, 0.12) 0%, transparent 45%), radial-gradient(circle at 85% 85%, rgba(6, 182, 212, 0.10) 0%, transparent 50%), linear-gradient(135deg, rgba(5, 150, 105, 0.04) 0%, rgba(15, 23, 42, 0.02) 100%)',
    css: 'radial-gradient(circle at 15% 15%, rgba(16, 185, 129, 0.12) 0%, transparent 45%), radial-gradient(circle at 85% 85%, rgba(6, 182, 212, 0.10) 0%, transparent 50%), linear-gradient(135deg, rgba(5, 150, 105, 0.04) 0%, rgba(15, 23, 42, 0.02) 100%)'
  },
  {
    id: 'neon_amethyst',
    name: 'Neon Amethyst',
    gradient: 'radial-gradient(circle at 80% 20%, rgba(168, 85, 247, 0.12) 0%, transparent 50%), radial-gradient(circle at 20% 80%, rgba(236, 72, 153, 0.10) 0%, transparent 50%), linear-gradient(135deg, rgba(99, 102, 241, 0.04) 0%, rgba(15, 23, 42, 0) 100%)',
    css: 'radial-gradient(circle at 80% 20%, rgba(168, 85, 247, 0.12) 0%, transparent 50%), radial-gradient(circle at 20% 80%, rgba(236, 72, 153, 0.10) 0%, transparent 50%), linear-gradient(135deg, rgba(99, 102, 241, 0.04) 0%, rgba(15, 23, 42, 0) 100%)'
  }
];

export const PATTERN_PRESETS = [
  {
    id: 'none',
    name: 'No Pattern',
    css: 'none'
  },
  {
    id: 'grid',
    name: 'Subtle Grid',
    css: 'linear-gradient(to right, rgba(148, 163, 184, 0.08) 1px, transparent 1px), linear-gradient(to bottom, rgba(148, 163, 184, 0.08) 1px, transparent 1px)',
    size: '24px 24px'
  },
  {
    id: 'dots',
    name: 'Radial Dots',
    css: 'radial-gradient(circle, rgba(148, 163, 184, 0.15) 1px, transparent 1px)',
    size: '18px 18px'
  },
  {
    id: 'diagonal',
    name: 'Diagonal Micro-Lines',
    css: 'repeating-linear-gradient(45deg, rgba(148, 163, 184, 0.05), rgba(148, 163, 184, 0.05) 1px, transparent 1px, transparent 10px)',
    size: 'auto'
  }
];

export const DEFAULT_PREFERENCES = {
  style: 'modern',
  palette: 'purple_blue',
  useCustomColors: false,
  customColors: {
    primary: '#4f46e5',
    secondary: '#2563eb',
    bg: '#f8f9fa',
    surface: '#ffffff',
    cardBg: '#ffffff',
    border: '#e2e8f0',
    textPrimary: '#0f172a',
    textSecondary: '#475569'
  },
  background: {
    type: 'solid',
    gradientPreset: 'aurora',
    pattern: 'none'
  },
  card: {
    style: 'bordered',
    radius: 'medium',
    shadow: 'subtle'
  },
  layout: {
    density: 'comfortable',
    widgets: [
      { id: 'kpi_cards', label: 'KPI Summary Cards (Totals & Averages)', visible: true, order: 0 },
      { id: 'chart_visuals', label: 'Visual Charts & Data Grids', visible: true, order: 1 }
    ]
  },
  typography: {
    size: 'medium',
    emphasis: 'standard',
    reducedMotion: false
  }
};
