/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // A restrained, finance-desk palette: one accent, generous
        // neutrals, and status colours that read at a glance.
        brand: {
          50: '#eef4ff',
          100: '#dbe6fe',
          600: '#2f5bd7',
          700: '#2748ad',
          900: '#1b2f6e',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
