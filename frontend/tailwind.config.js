/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
    "./prototypes/**/*.{html,js}",
  ],
  theme: {
    extend: {
      colors: {
        ivory: {
          DEFAULT: '#E8E0D2',
          50: '#FAF8F5',
          100: '#F4EFE6',
          200: '#E8E0D2',
          300: '#DDD2BF',
          400: '#C8BA9F'
        },
        gold: {
          DEFAULT: '#B89A5A',
          300: '#D6C08D',
          400: '#C7AD72',
          500: '#B89A5A',
          600: '#9B7E3E',
          700: '#7E6329'
        },
        navy: {
          DEFAULT: '#0E1D61',
          800: '#142578',
          900: '#0E1D61',
          950: '#070F35'
        },
        brand: {
          500: '#0E1D61',
          600: '#070F35'
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'],
        display: ['Unbounded', 'Cinzel', 'system-ui', 'sans-serif'],
        mono: ['Space Mono', 'IBM Plex Mono', 'monospace'],
        carto: ['Space Mono', 'monospace'],
        blueprint: ['IBM Plex Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
