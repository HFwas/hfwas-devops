/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // 语义 token。视图与 components/ui/* 按 shadcn 命名书写（border-border /
        // text-muted-foreground / text-primary-foreground ...），缺一个就静默失效。
        // 用字面量而非 var()：Tailwind v3 对 var() 颜色会丢弃 /60、/50 这类透明度修饰符。
        // 取值须与 src/style.css 的 --color-* 保持一致。
        background: '#f5f6fa',
        foreground: '#1a1a2e',
        card: { DEFAULT: '#ffffff', foreground: '#1a1a2e' },
        popover: { DEFAULT: '#ffffff', foreground: '#1a1a2e' },
        primary: { DEFAULT: '#2563eb', foreground: '#ffffff' },
        secondary: { DEFAULT: '#f1f3f5', foreground: '#1a1a2e' },
        muted: { DEFAULT: '#f1f3f5', foreground: '#6b7280' },
        accent: { DEFAULT: '#eff6ff', foreground: '#2563eb' },
        destructive: { DEFAULT: '#ef4444', foreground: '#ffffff' },
        border: '#e5e7eb',
        input: '#e5e7eb',
        ring: '#2563eb',
      },
    },
  },
  plugins: [],
}