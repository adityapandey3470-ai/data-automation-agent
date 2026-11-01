import React, { useState, useEffect, useRef } from 'react';
import { getHealth } from '../../services/api';
import { Search, FileSpreadsheet, Menu, Folder, User, LogOut, Shield } from '../icons/Icons';

export default function TopNav({
  title,
  loaded,
  fileName,
  fileId,
  workspaceFiles = [],
  onSelectFile,
  onToggleMobileNav,
  currentUser,
  onOpenAuth,
  onOpenProjects,
  onLogout,
  onNavigate
}) {
  const [backendHealth, setBackendHealth] = useState('checking');
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [fileDropdownOpen, setFileDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);
  const fileDropdownRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setUserDropdownOpen(false);
      }
      if (fileDropdownRef.current && !fileDropdownRef.current.contains(event.target)) {
        setFileDropdownOpen(false);
      }
    };

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setUserDropdownOpen(false);
        setFileDropdownOpen(false);
      }
    };

    if (userDropdownOpen || fileDropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [userDropdownOpen, fileDropdownOpen]);

  useEffect(() => {
    const checkHealth = async () => {
      try {
        const res = await getHealth();
        if (res.data?.status === 'UP') {
          setBackendHealth('up');
        } else {
          setBackendHealth('down');
        }
      } catch {
        setBackendHealth('down');
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 25000);
    return () => clearInterval(interval);
  }, []);

  const getInitials = (name) => {
    if (!name) return 'TF';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  };

  return (
    <header className="topnav">
      <button
        className="topnav-menu-trigger"
        onClick={onToggleMobileNav}
        aria-label="Open mobile navigation drawer"
      >
        <Menu size={18} />
      </button>

      <div className="topnav-left">
        <span className="topnav-title">{title}</span>
        {loaded && (
          <div className="relative" ref={fileDropdownRef} style={{ position: 'relative' }}>
            <button
              className="topnav-file-tag flex-gap"
              onClick={() => setFileDropdownOpen(prev => !prev)}
              title="Click to view workspace files"
              style={{
                cursor: 'pointer',
                background: 'var(--bg-card)',
                border: '1px solid var(--border-color)',
                borderRadius: '6px',
                padding: '4px 8px',
                display: 'inline-flex',
                alignItems: 'center'
              }}
            >
              <FileSpreadsheet size={13} className="text-emerald" style={{ flexShrink: 0 }} />
              <span style={{ fontWeight: 600, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{fileName}</span>
              {workspaceFiles.length > 1 && (
                <span className="badge badge-neutral" style={{ fontSize: 10, padding: '1px 5px', marginLeft: 4 }}>
                  {workspaceFiles.length} files ▾
                </span>
              )}
            </button>
            {fileDropdownOpen && workspaceFiles.length > 0 && (
              <div
                className="dropdown-menu shadow-lg"
                style={{
                  minWidth: 240,
                  position: 'absolute',
                  top: '100%',
                  left: 0,
                  marginTop: 6,
                  zIndex: 1000,
                  background: 'var(--bg-card)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 8,
                  padding: 6,
                  boxShadow: '0 8px 24px rgba(0,0,0,0.15)'
                }}
              >
                <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-secondary)', padding: '4px 8px' }}>
                  Workspace Files ({workspaceFiles.length})
                </div>
                {workspaceFiles.map(f => (
                  <div
                    key={f.fileId}
                    className="file-dropdown-item"
                    style={{
                      padding: '6px 8px',
                      borderRadius: 4,
                      cursor: 'pointer',
                      background: f.active ? 'rgba(16, 185, 129, 0.12)' : 'transparent',
                      color: f.active ? '#10b981' : 'var(--text-primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 8,
                      marginBottom: 2
                    }}
                    onClick={() => {
                      if (!f.active && onSelectFile) onSelectFile(f.fileId);
                      setFileDropdownOpen(false);
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, overflow: 'hidden' }}>
                      <FileSpreadsheet size={13} className={f.active ? 'text-emerald' : ''} />
                      <span style={{ fontSize: 12, fontWeight: f.active ? 600 : 400, textOverflow: 'ellipsis', whiteSpace: 'nowrap', overflow: 'hidden' }}>
                        {f.fileName}
                      </span>
                    </div>
                    <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                      {f.totalRows?.toLocaleString() || 0} rows
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="topnav-spacer" />

      {/* Projects Trigger */}
      <button
        className="btn btn-xs btn-outline mr-8 flex-gap"
        onClick={onOpenProjects}
        title="View and Manage Saved Projects"
        style={{ height: 32, padding: '0 10px', fontSize: 12 }}
      >
        <Folder size={13} className="text-emerald" />
        <span>Projects</span>
      </button>

      <div className="topnav-search">
        <Search size={14} className="search-icon" />
        <input
          id="topnav-command-search"
          name="commandSearch"
          type="search"
          placeholder="Search commands, operations..."
          aria-label="Search commands, operations"
          autoComplete="off"
        />
      </div>

      <div className={`topnav-status ${backendHealth === 'up' ? 'online' : 'offline'}`} title={`Backend Engine: ${backendHealth}`}>
        <span className="dot" />
        <span>{backendHealth === 'up' ? (loaded ? 'Engine Connected · Active Session' : 'Engine Ready') : 'API Offline'}</span>
      </div>

      {/* User Auth Section */}
      {currentUser ? (
        <div ref={dropdownRef} style={{ position: 'relative' }}>
          <div
            className="topnav-avatar"
            style={{ cursor: 'pointer', background: 'var(--accent-color, #3b82f6)' }}
            title={`${currentUser.name} (${currentUser.email})`}
            onClick={() => setUserDropdownOpen(prev => !prev)}
          >
            {getInitials(currentUser.name)}
          </div>

          {userDropdownOpen && (
            <div className="user-dropdown-menu">
              <div className="user-dropdown-header">
                <div className="user-dropdown-name">
                  <span>{currentUser.name}</span>
                  {currentUser.role === 'ROLE_ADMIN' && (
                    <span className="admin-badge" style={{ fontSize: 9, padding: '1px 5px', textTransform: 'uppercase' }}>
                      Admin
                    </span>
                  )}
                </div>
                <div className="user-dropdown-email">
                  {currentUser.email}
                </div>
              </div>

              <div className="user-dropdown-divider" />

              {currentUser.role === 'ROLE_ADMIN' && (
                <button
                  className="user-dropdown-item"
                  onClick={() => {
                    setUserDropdownOpen(false);
                    if (onNavigate) onNavigate('admin');
                  }}
                  style={{ color: '#10b981' }}
                >
                  <Shield size={14} /> <span>Admin Console</span>
                </button>
              )}

              <button
                className="user-dropdown-item"
                onClick={() => {
                  setUserDropdownOpen(false);
                  onOpenProjects();
                }}
              >
                <Folder size={14} className="text-emerald" /> <span>Saved Projects</span>
              </button>

              <div className="user-dropdown-divider" />

              <button
                className="user-dropdown-item danger"
                onClick={() => {
                  setUserDropdownOpen(false);
                  onLogout();
                }}
              >
                <LogOut size={14} /> <span>Sign Out</span>
              </button>
            </div>
          )}
        </div>
      ) : (
        <button
          className="btn btn-xs btn-primary ml-8"
          onClick={onOpenAuth}
          style={{ height: 32, fontSize: 12 }}
        >
          Sign In
        </button>
      )}
    </header>
  );
}
