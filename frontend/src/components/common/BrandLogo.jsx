import React from 'react';

/**
 * Interactive StockPilot Cartographic Emblem.
 * On hover of a parent `.group`, the compass star rotates 45deg while the outer archival ring counter-rotates.
 */
const BrandLogo = ({ className = 'w-10 h-10', rounded = 'rounded-xl' }) => {
  return (
    <div
      className={`relative overflow-hidden border-2 border-[#B89A5A] shadow-md shadow-[#0E1D61]/20 bg-[#0E1D61] flex items-center justify-center shrink-0 transition-transform duration-300 group-hover:scale-105 ${rounded} ${className}`}
    >
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" fill="none" className="w-full h-full">
        {/* Outer Archival Ring (counter-rotates on hover) */}
        <g className="origin-center transition-transform duration-700 ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:-rotate-12">
          <circle cx="50" cy="50" r="46" stroke="#B89A5A" strokeWidth="1.75" strokeDasharray="2 2" opacity="0.6" />
          <circle cx="50" cy="50" r="43" stroke="#B89A5A" strokeWidth="1.25" />
          <line x1="50" y1="3" x2="50" y2="7" stroke="#B89A5A" strokeWidth="2" />
          <line x1="50" y1="93" x2="50" y2="97" stroke="#B89A5A" strokeWidth="2" />
          <line x1="3" y1="50" x2="7" y2="50" stroke="#B89A5A" strokeWidth="2" />
          <line x1="93" y1="50" x2="97" y2="50" stroke="#B89A5A" strokeWidth="2" />
        </g>

        {/* Deep Atlas Navy Solid Badge Shield */}
        <rect x="18" y="18" width="64" height="64" rx="16" fill="#0E1D61" stroke="#B89A5A" strokeWidth="2" />

        {/* Isometric Warehouse Cube */}
        <polygon points="50,27 69,38 50,49 31,38" fill="#162C8C" stroke="#B89A5A" strokeWidth="1.75" strokeLinejoin="round" />
        <polygon points="31,38 50,49 50,71 31,60" fill="#0A1545" stroke="#B89A5A" strokeWidth="1.75" strokeLinejoin="round" />
        <polygon points="50,49 69,38 69,60 50,71" fill="#0E1D61" stroke="#B89A5A" strokeWidth="1.75" strokeLinejoin="round" />

        {/* Navigational Compass Star Core (rotates 45deg on hover) */}
        <g
          style={{ transformOrigin: '50px 49px' }}
          className="transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:rotate-45"
        >
          <polygon points="50,34 53,46 65,49 53,52 50,64 47,52 35,49 47,46" fill="#B89A5A" stroke="#E8E0D2" strokeWidth="1" />
        </g>

        {/* Center Focal Axis Point */}
        <circle cx="50" cy="49" r="2.5" fill="#E8E0D2" />
      </svg>
    </div>
  );
};

export default BrandLogo;
