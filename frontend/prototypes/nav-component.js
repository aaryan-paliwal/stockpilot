// StockPilot Prototype Theme Enforcer & Motion Engine - Warm Ivory Paper Edition
// Color Palette: Ivory (#E8E0D2), Gold (#B89A5A), Deep Atlas Navy (#0E1D61)
(function() {
  // Enforce warm ivory light mode (dark mode permanently removed)
  document.documentElement.classList.remove('dark');
  document.documentElement.classList.add('light');
  localStorage.setItem('stockpilot_theme', 'light');

  // Non-blocking Atlas Toast notification helper for interactive prototypes
  window.showAtlasToast = function(message, tone = 'gold') {
    let container = document.getElementById('atlas-toast-stack');
    if (!container) {
      container = document.createElement('div');
      container.id = 'atlas-toast-stack';
      container.className = 'fixed bottom-5 right-5 z-[100] flex flex-col gap-2 pointer-events-none';
      document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    const accent = tone === 'rose'
      ? 'border-rose-400 bg-[#FAF7F2] text-[#0E1D61]'
      : 'border-[#B89A5A] bg-[#0E1D61] text-[#E8E0D2]';
    toast.className = `pointer-events-auto flex items-center gap-2.5 px-4 py-2.5 rounded-xl border-2 shadow-xl text-xs font-bold animate-scale ${accent}`;
    toast.innerHTML = `<span class="w-2 h-2 rounded-full ${tone === 'rose' ? 'bg-rose-500' : 'bg-[#B89A5A]'}"></span><span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.transition = 'opacity 220ms ease, transform 220ms ease';
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(6px)';
      setTimeout(() => toast.remove(), 240);
    }, 2800);
  };

  document.addEventListener('DOMContentLoaded', () => {
    const prefersReduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // 1. Auto-animate main workspace container entrance
    const main = document.querySelector('main');
    if (main && !main.classList.contains('animate-page-enter') && !main.classList.contains('animate-carto-reveal')) {
      main.classList.add('animate-page-enter');
    }

    // 2. Auto-stagger ledger table rows + gold left-border hover
    document.querySelectorAll('tbody').forEach((tbody) => {
      const rows = tbody.querySelectorAll('tr');
      rows.forEach((tr, idx) => {
        tr.style.setProperty('--row-index', idx);
        tr.classList.add('animate-ledger-row', 'ledger-row-hover');
      });
    });

    // 3. IntersectionObserver for scroll-revealed elements
    const revealEls = document.querySelectorAll('.reveal-on-scroll');
    if (revealEls.length > 0 && 'IntersectionObserver' in window && !prefersReduced) {
      const obs = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            obs.unobserve(entry.target);
          }
        });
      }, { threshold: 0.12 });
      revealEls.forEach((el) => obs.observe(el));
    } else {
      revealEls.forEach((el) => el.classList.add('is-visible'));
    }

    // 4. Tabular-nums count-up for KPI counters marked with .kpi-count
    if (!prefersReduced) {
      document.querySelectorAll('.kpi-count').forEach((el) => {
        const raw = el.textContent.trim();
        const match = raw.match(/^([^0-9.-]*)([0-9][0-9,.]*)(.*)$/);
        if (!match) return;
        const prefix = match[1];
        const numStr = match[2];
        const suffix = match[3];
        const hasDecimal = numStr.includes('.');
        const decimals = hasDecimal ? numStr.split('.')[1].length : 0;
        const target = parseFloat(numStr.replace(/,/g, ''));
        if (isNaN(target) || target === 0) return;

        const duration = 650;
        const start = performance.now();
        el.classList.add('tabular-nums');

        function tick(now) {
          const progress = Math.min((now - start) / duration, 1);
          const eased = 1 - Math.pow(1 - progress, 3);
          const current = target * eased;
          const formatted = current.toLocaleString('en-US', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals,
          });
          el.textContent = `${prefix}${formatted}${suffix}`;
          if (progress < 1) {
            requestAnimationFrame(tick);
          } else {
            el.textContent = raw;
          }
        }
        requestAnimationFrame(tick);
      });
    }
  });
})();
