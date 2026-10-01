/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#3370ff',
        'primary-hover': '#295ce0',
        muted: '#6b7280',
        border: '#e5e7eb',
        'card-bg': '#ffffff',
        'chip-bg': '#f3f4f6',
        'canvas-bg': '#fafbfc',
      },
    },
  },
  plugins: [],
}