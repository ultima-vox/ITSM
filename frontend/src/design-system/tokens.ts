/** Design tokens — JS mirror of CSS custom properties for programmatic use */
export const colors = {
  navy: {
    950: '#0b0f1c',
    900: '#10152a',
    800: '#171d36',
    700: '#1c233b',
    600: '#202741',
    500: '#292f4b',
  },
  violet: {
    700: '#5744bd',
    600: '#6249d7',
    500: '#7158df',
    400: '#8b73ff',
    100: '#f0edff',
    50: '#f7f6fd',
  },
  mint: { 600: '#30a996', 100: '#e8f8f4' },
  amber: { 600: '#d98b2c', 100: '#fff5e7' },
  rose: { 600: '#df6675', 100: '#fff0f1' },
  blue: { 500: '#5470b8', 100: '#edf5ff' },
  ink: {
    900: '#20263a',
    800: '#293148',
    700: '#343c51',
    400: '#6f788b',
    300: '#8d96a9',
  },
  surface: {
    0: '#ffffff',
    50: '#f6f7fb',
    soft: '#fafbfc',
  },
  line: {
    200: '#e5e7ef',
    100: '#ebedf2',
    50: '#f0f1f5',
  },
} as const;

export const spacing = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  7: 28,
  8: 32,
  9: 40,
  10: 48,
  12: 64,
} as const;

export const radii = {
  xs: 4,
  sm: 6,
  md: 8,
  lg: 10,
  xl: 12,
  '2xl': 14,
  full: 999,
} as const;

export const typography = {
  fontFamily: "'Manrope', 'Segoe UI', Arial, sans-serif",
  sizes: {
    '2xs': '0.625rem',
    xs: '0.6875rem',
    sm: '0.75rem',
    md: '0.8125rem',
    base: '0.875rem',
    lg: '1rem',
    xl: '1.125rem',
    '2xl': '1.375rem',
    '3xl': '1.6875rem',
  },
} as const;

export const shadows = {
  xs: '0 1px 2px rgba(32, 38, 58, 0.04)',
  sm: '0 4px 10px rgba(42, 51, 81, 0.06)',
  md: '0 10px 22px rgba(48, 60, 86, 0.08)',
  lg: '0 14px 35px rgba(36, 48, 69, 0.12)',
  xl: '0 25px 70px rgba(12, 17, 43, 0.27)',
  accent: '0 7px 15px rgba(112, 88, 223, 0.18)',
} as const;

export const motion = {
  fast: 120,
  normal: 160,
  slow: 220,
} as const;

export const layout = {
  sidebarWidth: 256,
  headerHeight: 56,
  contentMax: 1540,
} as const;
