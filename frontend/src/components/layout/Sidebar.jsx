import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import BrandLogo from '../common/BrandLogo';

const Sidebar = ({ isOpen, onClose }) => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const location = useLocation();

  const menuItems = [
    { name: 'Dashboard', path: '/dashboard' },
    { name: 'Products', path: '/products' },
    { name: 'Categories', path: '/categories' },
    { name: 'Suppliers', path: '/suppliers' },
    { name: 'Stock', path: '/stock' },
    { name: 'Low Stock', path: '/alerts' },
    { name: 'Reports', path: '/reports' },
    { name: 'Audit Trail', path: '/audit-trail' },
    { name: 'Account', path: '/account' },
  ];

  const renderIcon = (name, active) => {
    const colorClass = active ? 'text-blue-400' : 'text-slate-400 group-hover:text-slate-200';
    switch (name) {
      case 'Dashboard':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <rect x="3" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="3" width="7" height="7" rx="1.5" />
            <rect x="14" y="14" width="7" height="7" rx="1.5" />
            <rect x="3" y="14" width="7" height="7" rx="1.5" />
          </svg>
        );
      case 'Products':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
        );
      case 'Categories':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
          </svg>
        );
      case 'Suppliers':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
          </svg>
        );
      case 'Stock':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 002 2h2a2 2 0 002-2z" />
          </svg>
        );
      case 'Low Stock':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
        );
      case 'Reports':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        );
      case 'Audit Trail':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        );
      case 'Account':
        return (
          <svg className={`w-4 h-4 ${colorClass}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        );
      default:
        return null;
    }
  };

  const isActive = (path) => location.pathname === path;

  return (
    <>
      {/* Backdrop overlay for mobile (always mounted for smooth fade) */}
      <div
        className={`fixed inset-0 bg-[#0E1D61]/40 backdrop-blur-xs z-40 md:hidden transition-opacity duration-300 ${
          isOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        }`}
        onClick={onClose}
      />
      
      <aside
        className={`w-56 bg-[#F4EFE6] border-r-2 border-[#B89A5A]/40 p-4 flex flex-col justify-between h-screen fixed left-0 top-0 overflow-y-auto z-50 transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] md:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="space-y-2">
          {/* Logo with Close button on mobile */}
          <div className="flex items-center justify-between px-1 mb-6 mt-1">
            <div
              className="flex items-center gap-2.5 cursor-pointer group"
              onClick={() => {
                navigate('/dashboard');
                if (onClose) onClose();
              }}
            >
              <BrandLogo className="w-9 h-9" />
              <div>
                <span className="font-display font-black text-sm text-[#0E1D61] tracking-tight block leading-none font-atlas">StockPilot</span>
                <span className="text-[8px] text-[#B89A5A] font-mono tracking-wider uppercase block mt-0.5 font-bold">Atlas Edition</span>
              </div>
            </div>

            {/* Mobile Sidebar Close Button */}
            <button
              onClick={onClose}
              className="md:hidden p-1.5 hover:bg-[#E8E0D2] rounded-lg text-[#0E1D61]/70 hover:text-[#0E1D61] transition-colors cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Navigation Menu */}
          <nav className="space-y-1">
            {menuItems.map((item) => {
              const active = isActive(item.path);
              return (
                <button
                  key={item.path}
                  onClick={() => {
                    navigate(item.path);
                    if (onClose) onClose();
                  }}
                  className={`relative w-full text-left px-3 py-2.5 rounded-xl text-xs font-semibold transition-all duration-200 flex items-center justify-between border cursor-pointer group active:scale-[0.985] ${
                    active
                      ? 'bg-[#0E1D61] text-[#E8E0D2] border-[#B89A5A] shadow-md shadow-[#0E1D61]/25 font-bold'
                      : 'text-[#0E1D61]/75 hover:text-[#0E1D61] hover:bg-[#B89A5A]/15 border-transparent hover:border-[#B89A5A]/30'
                  }`}
                >
                  {active && (
                    <span className="absolute left-0 top-1.5 bottom-1.5 w-[3px] rounded-r-full bg-[#B89A5A]" />
                  )}
                  <div className="flex items-center gap-2.5 transition-transform duration-150 group-hover:translate-x-0.5">
                    {renderIcon(item.name, active)}
                    <span>{item.name}</span>
                  </div>
                  {item.name === 'Low Stock' && (
                    <span className="w-2 h-2 bg-amber-500 text-amber-500 rounded-full beacon-dot" />
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Logout Button */}
        <button
          onClick={() => {
            logout();
            navigate('/login');
            if (onClose) onClose();
          }}
          className="group w-full px-3 py-2.5 rounded-xl text-xs font-semibold text-[#0E1D61]/75 hover:text-rose-700 hover:bg-rose-50/70 transition-all duration-200 border border-[#B89A5A]/30 hover:border-rose-300 flex items-center gap-2.5 justify-center cursor-pointer active:scale-[0.98]"
        >
          <svg className="w-4 h-4 transition-transform duration-200 group-hover:translate-x-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          <span>Logout</span>
        </button>
      </aside>
    </>
  );
};

export default Sidebar;
