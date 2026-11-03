import React from 'react';

const iconProps = {
  xmlns: 'http://www.w3.org/2000/svg',
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.75,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

export const LogoMark = ({ size = 24, className = '', variant = 'auto' }) => {
  const isLight = variant === 'light' || (variant === 'auto' && typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'light');
  const gradId = isLight ? 'tf-brand-grad-light' : 'tf-brand-grad-dark';

  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" className={`logo-mark ${className}`} xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          {isLight ? (
            <>
              <stop offset="0%" stopColor="#047857" />
              <stop offset="55%" stopColor="#059669" />
              <stop offset="100%" stopColor="#10b981" />
            </>
          ) : (
            <>
              <stop offset="0%" stopColor="#064e3b" />
              <stop offset="55%" stopColor="#059669" />
              <stop offset="100%" stopColor="#10b981" />
            </>
          )}
        </linearGradient>
      </defs>

      <rect width="32" height="32" rx="8" fill={`url(#${gradId})`} />

      <rect
        x="0.75"
        y="0.75"
        width="30.5"
        height="30.5"
        rx="7.25"
        stroke={isLight ? "rgba(255, 255, 255, 0.4)" : "rgba(255, 255, 255, 0.2)"}
        strokeWidth="1.5"
      />

      <rect x="5.5" y="13.5" width="6" height="5" rx="1.75" fill="white" fillOpacity={isLight ? 0.55 : 0.45} />
      <rect x="20.5" y="13.5" width="6" height="5" rx="1.75" fill="white" fillOpacity={isLight ? 0.55 : 0.45} />

      <rect x="5.5" y="20.5" width="6" height="5" rx="1.75" fill="white" fillOpacity={isLight ? 0.3 : 0.22} />
      <rect x="20.5" y="20.5" width="6" height="5" rx="1.75" fill="white" fillOpacity={isLight ? 0.3 : 0.22} />

      <rect x="5.5" y="6.5" width="21" height="5.25" rx="2" fill="#FFFFFF" />

      <rect x="13" y="10" width="6" height="15.5" rx="2" fill="#FFFFFF" />

      <path
        d="M16 14.2L16.7 16.2L18.7 17L16.7 17.8L16 19.8L15.3 17.8L13.3 17L15.3 16.2Z"
        fill={isLight ? "#047857" : "#059669"}
      />
    </svg>
  );
};

export const EngineCore = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <rect x="4" y="4" width="16" height="16" rx="3" stroke="#10b981" />
    <rect x="8.5" y="8.5" width="7" height="7" rx="1.5" fill="#10b981" fillOpacity="0.25" stroke="#34d399" />
    <circle cx="12" cy="12" r="1.5" fill="#6ee7b7" />
    <path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" stroke="#34d399" strokeOpacity="0.8" />
  </svg>
);

export const LayoutDashboard = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="7" height="9" x="3" y="3" rx="1" />
    <rect width="7" height="5" x="14" y="3" rx="1" />
    <rect width="7" height="9" x="14" y="12" rx="1" />
    <rect width="7" height="5" x="3" y="16" rx="1" />
  </svg>
);

export const Table2 = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18" />
  </svg>
);

export const Sparkles = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" />
  </svg>
);

export const LineChart = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 3v18h18" />
    <path d="m19 9-5 5-4-4-3 3" />
  </svg>
);

export const Download = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" x2="12" y1="15" y2="3" />
  </svg>
);

export const Settings = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

export const Upload = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="17 8 12 3 7 8" />
    <line x1="12" x2="12" y1="3" y2="15" />
  </svg>
);

export const FileSpreadsheet = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
    <path d="M14 2v4a2 2 0 0 0 2 2h4" />
    <path d="M8 13h2" />
    <path d="M14 13h2" />
    <path d="M8 17h2" />
    <path d="M14 17h2" />
  </svg>
);

export const Check = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

export const ArrowRight = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M5 12h14" />
    <path d="m12 5 7 7-7 7" />
  </svg>
);

export const ChevronRight = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m9 18 6-6-6-6" />
  </svg>
);

export const ChevronDown = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m6 9 6 6 6-6" />
  </svg>
);

export const Search = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="11" cy="11" r="8" />
    <path d="m21 21-4.3-4.3" />
  </svg>
);

export const Play = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polygon points="6 3 20 12 6 21 6 3" />
  </svg>
);

export const Undo = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 7v6h6" />
    <path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13" />
  </svg>
);

export const Redo = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M21 7v6h-6" />
    <path d="M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3l3 2.7" />
  </svg>
);

export const RefreshCw = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
    <path d="M21 3v5h-5" />
    <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
    <path d="M8 16H3v5" />
  </svg>
);

export const X = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M18 6 6 18" />
    <path d="m6 6 12 12" />
  </svg>
);

export const AlertCircle = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="12" cy="12" r="10" />
    <line x1="12" x2="12" y1="8" y2="12" />
    <line x1="12" x2="12.01" y1="16" y2="16" />
  </svg>
);

export const Bug = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="8" height="14" x="8" y="6" rx="4" />
    <path d="m19 7-3 2" />
    <path d="m5 7 3 2" />
    <path d="m19 19-3-2" />
    <path d="m5 19 3-2" />
    <path d="M20 13h-4" />
    <path d="M4 13h4" />
    <path d="m10 4 1 2" />
    <path d="m14 4-1 2" />
  </svg>
);

export const HelpCircle = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="12" cy="12" r="10" />
    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
    <line x1="12" x2="12.01" y1="17" y2="17" />
  </svg>
);

export const Shield = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
  </svg>
);

export const FileText = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
    <path d="M14 2v4a2 2 0 0 0 2 2h4" />
    <path d="M10 9H8" />
    <path d="M16 13H8" />
    <path d="M16 17H8" />
  </svg>
);

export const Mail = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="20" height="16" x="2" y="4" rx="2" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
);

export const MailCheck = ({ size = 18, className = '', style = {} }) => (
  <svg width={size} height={size} {...iconProps} className={className} style={style}>
    <path d="M22 13V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v12c0 1.1.9 2 2 2h9" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
    <path d="m16 19 2 2 4-4" />
  </svg>
);

export const Sliders = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <line x1="4" x2="4" y1="21" y2="14" />
    <line x1="4" x2="4" y1="10" y2="3" />
    <line x1="12" x2="12" y1="21" y2="12" />
    <line x1="12" x2="12" y1="8" y2="3" />
    <line x1="20" x2="20" y1="21" y2="16" />
    <line x1="20" x2="20" y1="12" y2="3" />
    <line x1="1" x2="7" y1="14" y2="14" />
    <line x1="9" x2="15" y1="8" y2="8" />
    <line x1="17" x2="23" y1="16" y2="16" />
  </svg>
);

export const Zap = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
  </svg>
);

export const Layers = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z" />
    <path d="m22 12.5-9.17 4.16a2 2 0 0 1-1.66 0L2 12.5" />
    <path d="m22 17.5-9.17 4.16a2 2 0 0 1-1.66 0L2 17.5" />
  </svg>
);

export const BarChart3 = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 3v18h18" />
    <path d="M18 17V9" />
    <path d="M13 17V5" />
    <path d="M8 17v-3" />
  </svg>
);

export const Filter = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
  </svg>
);

export const ArrowUpDown = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m21 16-4 4-4-4" />
    <path d="M17 20V4" />
    <path d="m3 8 4-4 4 4" />
    <path d="M7 4v16" />
  </svg>
);

export const Copy = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
    <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
  </svg>
);

export const Lightbulb = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5" />
    <path d="M9 18h6" />
    <path d="M10 22h4" />
  </svg>
);

export const Menu = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <line x1="4" x2="20" y1="12" y2="12" />
    <line x1="4" x2="20" y1="6" y2="6" />
    <line x1="4" x2="20" y1="18" y2="18" />
  </svg>
);

export const Folder = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
  </svg>
);

export const User = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="12" cy="8" r="5" />
    <path d="M20 21a8 8 0 0 0-16 0" />
  </svg>
);

export const Users = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </svg>
);

export const MessageSquare = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
  </svg>
);

export const Send = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <line x1="22" y1="2" x2="11" y2="13" />
    <polygon points="22 2 15 22 11 13 2 9 22 2" />
  </svg>
);

export const Headphones = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
    <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
  </svg>
);

export const Bot = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect x="3" y="11" width="18" height="10" rx="2" />
    <circle cx="12" cy="5" r="2" />
    <path d="M12 7v4" />
    <line x1="8" y1="16" x2="8.01" y2="16" strokeWidth="2.5" />
    <line x1="16" y1="16" x2="16.01" y2="16" strokeWidth="2.5" />
  </svg>
);

export const LogOut = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" />
    <line x1="21" x2="9" y1="12" y2="12" />
  </svg>
);

export const CheckCircle = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
    <polyline points="22 4 12 14.01 9 11.01" />
  </svg>
);

export const Trash2 = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 6h18" />
    <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
    <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
    <line x1="10" x2="10" y1="11" y2="17" />
    <line x1="14" x2="14" y1="11" y2="17" />
  </svg>
);

export const Edit2 = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
  </svg>
);

export const Plus = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <line x1="12" y1="5" x2="12" y2="19" />
    <line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);

export const Eye = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

export const EyeOff = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
    <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
    <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
    <line x1="2" x2="22" y1="2" y2="22" />
  </svg>
);

export const FilePdf = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
    <polyline points="14 2 14 8 20 8" />
    <path d="M10 12v5" />
    <path d="M10 12h2a1.5 1.5 0 0 1 0 3h-2" />
  </svg>
);

export const ImageIcon = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="18" height="18" x="3" y="3" rx="2" ry="2" />
    <circle cx="9" cy="9" r="2" />
    <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21" />
  </svg>
);

export const Printer = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polyline points="6 9 6 2 18 2 18 9" />
    <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
    <rect width="12" height="8" x="6" y="14" />
  </svg>
);

export const Calendar = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="18" height="18" x="3" y="4" rx="2" ry="2" />
    <line x1="16" x2="16" y1="2" y2="6" />
    <line x1="8" x2="8" y1="2" y2="6" />
    <line x1="3" x2="21" y1="10" y2="10" />
  </svg>
);

export const ShieldCheck = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
    <path d="m9 12 2 2 4-4" />
  </svg>
);

export const Clock = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="12" cy="12" r="10" />
    <polyline points="12 6 12 12 16 14" />
  </svg>
);

export const Palette = ({ size = 18, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="13.5" cy="6.5" r=".5" fill="currentColor" />
    <circle cx="17.5" cy="10.5" r=".5" fill="currentColor" />
    <circle cx="8.5" cy="7.5" r=".5" fill="currentColor" />
    <circle cx="6.5" cy="12.5" r=".5" fill="currentColor" />
    <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.563-2.512 5.563-5.563C22 6.5 17.5 2 12 2z" />
  </svg>
);

export const MoveUp = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m5 12 7-7 7 7" />
    <path d="M12 19V5" />
  </svg>
);

export const MoveDown = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m19 12-7 7-7-7" />
    <path d="M12 5v14" />
  </svg>
);

export const RotateCcw = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
    <path d="M3 3v5h5" />
  </svg>
);

export const Pipette = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m14 2-8.5 8.5a2.12 2.12 0 1 0 3 3L17 5" />
    <path d="m16 4 2 2" />
    <path d="m19 1 4 4" />
    <path d="m2 22 5-5" />
    <path d="M2 22v-3l3-3" />
  </svg>
);

export const Type = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polyline points="4 7 4 4 20 4 20 7" />
    <line x1="9" x2="15" y1="20" y2="20" />
    <line x1="12" x2="12" y1="4" y2="20" />
  </svg>
);

export const Target = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <circle cx="12" cy="12" r="10" />
    <circle cx="12" cy="12" r="6" />
    <circle cx="12" cy="12" r="2" />
  </svg>
);

export const Cpu = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect x="4" y="4" width="16" height="16" rx="2" />
    <rect x="9" y="9" width="6" height="6" />
    <path d="M15 2v2M9 2v2M15 20v2M9 20v2M2 15h2M2 9h2M20 15h2M20 9h2" />
  </svg>
);

export const Activity = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

export const Crown = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="m2 4 3 12h14l3-12-6 7-4-7-4 7-6-7zm3 16h14" />
  </svg>
);

export const Lock = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

export const Building2 = ({ size = 16, className = '' }) => (
  <svg width={size} height={size} {...iconProps} className={className}>
    <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z" />
    <path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2" />
    <path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2" />
    <path d="M10 6h4" />
    <path d="M10 10h4" />
    <path d="M10 14h4" />
    <path d="M10 18h4" />
  </svg>
);
