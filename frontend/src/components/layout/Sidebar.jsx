import React from 'react';
import { LogoMark, LayoutDashboard, Table2, Sparkles, LineChart, Download, Settings, X, EngineCore, Shield } from '../icons/Icons';

export default function Sidebar({ activePage, onNavigate, isMobileOpen, onClose, currentUser }) {
  const handleItemClick = (key) => {
    onNavigate(key);
    if (onClose) onClose();
  };

  const navSections = [
    { section: 'Workspace', items: [
      { key: 'overview', icon: <LayoutDashboard size={17} />, label: 'Overview' },
      { key: 'workspace', icon: <Table2 size={17} />, label: 'Data Workspace' },
      { key: 'assistant', icon: <Sparkles size={17} />, label: 'AI Analyst & Insights' },
    ]},
    { section: 'Intelligence', items: [
      { key: 'analytics', icon: <LineChart size={17} />, label: 'Analytics & Charts' },
      { key: 'export', icon: <Download size={17} />, label: 'Export Hub' },
    ]},
    ...(currentUser?.role === 'ROLE_ADMIN' ? [{
      section: 'Administration', items: [
        { key: 'admin', icon: <Shield size={17} />, label: 'Admin Console' },
      ]
    }] : []),
    { section: 'System', items: [
      { key: 'settings', icon: <Settings size={17} />, label: 'Settings & Help' },
    ]},
  ];

  return (
    <>
      {/* Mobile Drawer Backdrop */}
      <div
        className={`sidebar-backdrop ${isMobileOpen ? 'active' : ''}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside className={`sidebar ${isMobileOpen ? 'mobile-open' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-brand-group">
            <LogoMark size={28} />
            <div className="sidebar-brand-wrap">
              <span className="sidebar-brand">Tabufinery</span>
              <span className="sidebar-badge">v0.1.0</span>
            </div>
          </div>
          <button className="sidebar-close-btn" onClick={onClose} aria-label="Close navigation drawer">
            <X size={18} />
          </button>
        </div>

        <nav className="sidebar-nav">
          {navSections.map(section => (
            <div key={section.section} className="sidebar-section">
              <div className="sidebar-section-label">{section.section}</div>
              {section.items.map(item => (
                <button
                  key={item.key}
                  className={`sidebar-item ${activePage === item.key ? 'active' : ''}`}
                  onClick={() => handleItemClick(item.key)}
                >
                  <span className="icon">{item.icon}</span>
                  <span className="label">{item.label}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-profile">
            <div className="sidebar-engine-badge">
              <EngineCore size={18} />
              <span className="engine-pulse-dot" />
            </div>
            <div className="sidebar-profile-info">
              <div className="sidebar-profile-name">Workspace Active</div>
              <div className="sidebar-profile-role">Deterministic Engine</div>
            </div>
            <span className="sidebar-live-pill">LIVE</span>
          </div>
        </div>
      </aside>
    </>
  );
}
