/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // Approved design tokens (docs/06-ui-ux-guidelines.md, mutable tokens)
      colors: {
        emerald: { deep: '#164638' },
        gold: { islamic: '#C7A75A' },
        ivory: { warm: '#F7F6F0' },
      },
    },
  },
  plugins: [],
}
