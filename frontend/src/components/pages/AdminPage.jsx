import React, { useState, useEffect, useCallback } from 'react';
import {
  getAdminUsers, getAdminStats, updateAdminUserStatus, updateAdminUserRole,
  getAdminTickets, updateAdminTicketStatus, getAdminFiles, deleteAdminFile,
  getAdminAuditLogs, getErrorMessage
} from '../../services/api';
import {
  Shield, Users, Folder, CheckCircle, AlertCircle, RefreshCw, X,
  Bug, Mail, Search, Filter, Trash2, Clock, ShieldCheck, FileSpreadsheet
} from '../icons/Icons';

export default function AdminPage({ currentUser }) {
  const [users, setUsers] = useState([]);
  const [stats, setStats] = useState(null);
  const [tickets, setTickets] = useState([]);
  const [files, setFiles] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [activeTab, setActiveTab] = useState('users');

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(null);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const [userSearch, setUserSearch] = useState('');
  const [userRoleFilter, setUserRoleFilter] = useState('ALL');
  const [userStatusFilter, setUserStatusFilter] = useState('ALL');

  const [ticketSearch, setTicketSearch] = useState('');
  const [ticketStatusFilter, setTicketStatusFilter] = useState('ALL');
  const [ticketCategoryFilter, setTicketCategoryFilter] = useState('ALL');
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [ticketUpdating, setTicketUpdating] = useState(false);

  const [fileSearch, setFileSearch] = useState('');

  const [auditSearch, setAuditSearch] = useState('');

  const [confirmModal, setConfirmModal] = useState(null);

  const fetchData = useCallback(async () => {
    if (currentUser?.role !== 'ROLE_ADMIN') return;
    setLoading(true);
    setError(null);
    try {
      const [usersRes, statsRes, ticketsRes, filesRes, auditRes] = await Promise.all([
        getAdminUsers(),
        getAdminStats(),
        getAdminTickets().catch(() => ({ data: [] })),
        getAdminFiles().catch(() => ({ data: [] })),
        getAdminAuditLogs().catch(() => ({ data: [] }))
      ]);
      setUsers(usersRes.data || []);
      setStats(statsRes.data || null);
      setTickets(ticketsRes.data || []);
      setFiles(filesRes.data || []);
      setAuditLogs(auditRes.data || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to load administrator data'));
    } finally {
      setLoading(false);
    }
  }, [currentUser?.role]);

  useEffect(() => {
    if (currentUser?.role === 'ROLE_ADMIN') {
      fetchData();
    }
  }, [fetchData, currentUser?.role]);

  const handleToggleStatus = (user) => {
    if (user.id === currentUser?.id) {
      setError("You cannot disable your own administrator account.");
      return;
    }

    const nextState = !user.enabled;
    const isEnabling = nextState;

    setConfirmModal({
      title: isEnabling ? 'Re-activate Account' : 'Disable Account Access',
      message: isEnabling
        ? `Re-activate platform access for ${user.name} (${user.email})?`
        : `Temporarily disable account for ${user.name} (${user.email})? They will be blocked from logging in.`,
      confirmText: isEnabling ? 'Enable Account' : 'Disable Account',
      confirmClass: isEnabling ? 'btn-primary' : 'btn-outline',
      confirmStyle: isEnabling ? {} : { color: '#ef4444', borderColor: '#ef4444' },
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`status-${user.id}`);
        setError(null);
        setSuccess(null);

        const prevUsers = [...users];
        setUsers(prev => prev.map(u => u.id === user.id ? { ...u, enabled: nextState } : u));

        try {
          await updateAdminUserStatus(user.id, nextState);
          setSuccess(`Account status updated for ${user.email}.`);
          getAdminStats().then(r => setStats(r.data || null)).catch(() => {});
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setUsers(prevUsers);
          setError(getErrorMessage(err, 'Failed to update user status'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handleToggleRole = (user) => {
    if (user.id === currentUser?.id) {
      setError("You cannot demote your own administrator account.");
      return;
    }

    const newRole = user.role === 'ROLE_ADMIN' ? 'ROLE_USER' : 'ROLE_ADMIN';
    const isPromoting = newRole === 'ROLE_ADMIN';

    setConfirmModal({
      title: isPromoting ? 'Promote to Administrator' : 'Demote to Standard User',
      message: `Change system role for ${user.name} (${user.email}) from ${user.role === 'ROLE_ADMIN' ? 'Admin' : 'User'} to ${isPromoting ? 'Administrator' : 'Standard User'}?`,
      confirmText: isPromoting ? 'Promote' : 'Demote',
      confirmClass: isPromoting ? 'btn-primary' : 'btn-outline',
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`role-${user.id}`);
        setError(null);
        setSuccess(null);

        const prevUsers = [...users];
        setUsers(prev => prev.map(u => u.id === user.id ? { ...u, role: newRole } : u));

        try {
          await updateAdminUserRole(user.id, newRole);
          setSuccess(`User role updated for ${user.email} to ${isPromoting ? 'Administrator' : 'Standard User'}.`);
          getAdminStats().then(r => setStats(r.data || null)).catch(() => {});
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setUsers(prevUsers);
          setError(getErrorMessage(err, 'Failed to update user role'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handleUpdateTicketStatus = async (ticketId, nextStatus) => {
    setTicketUpdating(true);
    setError(null);
    try {
      const res = await updateAdminTicketStatus(ticketId, nextStatus);
      const updated = res.data;
      setTickets(prev => prev.map(t => t.id === ticketId ? updated : t));
      if (selectedTicket && selectedTicket.id === ticketId) {
        setSelectedTicket(updated);
      }
      setSuccess(`Ticket ${updated.ticketCode} marked as ${nextStatus}.`);
      getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to update ticket status'));
    } finally {
      setTicketUpdating(false);
    }
  };

  const handleDeleteFile = (file) => {
    setConfirmModal({
      title: 'Permanently Delete Dataset',
      message: `Are you sure you want to permanently delete file '${file.fileName}' owned by ${file.ownerEmail}? This operation cannot be undone.`,
      confirmText: 'Delete Permanently',
      confirmClass: 'btn-outline',
      confirmStyle: { color: '#ef4444', borderColor: '#ef4444' },
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`file-${file.fileId}`);
        setError(null);

        const prevFiles = [...files];
        setFiles(prev => prev.filter(f => f.fileId !== file.fileId));

        try {
          await deleteAdminFile(file.fileId);
          setSuccess(`File '${file.fileName}' has been permanently deleted.`);
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setFiles(prevFiles);
          setError(getErrorMessage(err, 'Failed to delete file'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const filteredUsers = users.filter(u => {
    const q = userSearch.toLowerCase().trim();
    const matchesSearch = !q || (u.name && u.name.toLowerCase().includes(q)) || (u.email && u.email.toLowerCase().includes(q));
    const matchesRole = userRoleFilter === 'ALL' || u.role === userRoleFilter;
    const matchesStatus = userStatusFilter === 'ALL' || (userStatusFilter === 'ACTIVE' ? u.enabled : !u.enabled);
    return matchesSearch && matchesRole && matchesStatus;
  });

  const filteredTickets = tickets.filter(t => {
    const q = ticketSearch.toLowerCase().trim();
    const matchesSearch = !q ||
      (t.ticketCode && t.ticketCode.toLowerCase().includes(q)) ||
      (t.userName && t.userName.toLowerCase().includes(q)) ||
      (t.userEmail && t.userEmail.toLowerCase().includes(q)) ||
      (t.subject && t.subject.toLowerCase().includes(q)) ||
      (t.message && t.message.toLowerCase().includes(q));
    const matchesStatus = ticketStatusFilter === 'ALL' || t.status === ticketStatusFilter;
    const matchesCat = ticketCategoryFilter === 'ALL' || t.category === ticketCategoryFilter;
    return matchesSearch && matchesStatus && matchesCat;
  });

  const filteredFiles = files.filter(f => {
    const q = fileSearch.toLowerCase().trim();
    return !q ||
      (f.fileName && f.fileName.toLowerCase().includes(q)) ||
      (f.ownerEmail && f.ownerEmail.toLowerCase().includes(q)) ||
      (f.ownerName && f.ownerName.toLowerCase().includes(q)) ||
      (f.fileId && f.fileId.toLowerCase().includes(q));
  });

  const filteredAuditLogs = auditLogs.filter(a => {
    const q = auditSearch.toLowerCase().trim();
    return !q ||
      (a.adminEmail && a.adminEmail.toLowerCase().includes(q)) ||
      (a.action && a.action.toLowerCase().includes(q)) ||
      (a.targetResource && a.targetResource.toLowerCase().includes(q)) ||
      (a.details && a.details.toLowerCase().includes(q));
  });

  if (currentUser?.role !== 'ROLE_ADMIN') {
    return null;
  }

  return (
    <div className="admin-page">
      <div className="admin-header">
        <div>
          <div className="admin-header-title">
            <span className="badge admin-badge">System Administration</span>
            <h1>Executive Control Center</h1>
          </div>
          <p className="admin-header-desc">
            Manage platform users, inspect storage datasets, resolve bug reports, and audit security events.
          </p>
        </div>

        <button
          className="btn btn-sm btn-outline flex-gap"
          onClick={fetchData}
          disabled={loading}
        >
          <RefreshCw size={13} className={loading ? 'spin' : ''} /> Refresh Data
        </button>
      </div>

      {error && (
        <div className="alert alert-error mb-16 flex-gap">
          <AlertCircle size={15} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="alert alert-success mb-16 flex-gap" style={{ background: 'rgba(16,185,129,0.1)', color: 'var(--emerald)' }}>
          <CheckCircle size={15} />
          <span>{success}</span>
        </div>
      )}

      {stats && (
        <div className="admin-stats-grid">
          <div className="admin-stat-card">
            <div className="admin-stat-title">Total Accounts</div>
            <div className="admin-stat-number">{stats.totalUsers}</div>
            <span className="admin-stat-sub">Registered SaaS Users</span>
          </div>

          <div className="admin-stat-card">
            <div className="admin-stat-title">Active Users</div>
            <div className="admin-stat-number" style={{ color: 'var(--emerald)' }}>{stats.activeUsers}</div>
            <span className="admin-stat-sub">Unrestricted Access</span>
          </div>

          <div className="admin-stat-card">
            <div className="admin-stat-title">Active Datasets</div>
            <div className="admin-stat-number" style={{ color: '#3b82f6' }}>{files.length}</div>
            <span className="admin-stat-sub">Uploaded Files & Projects</span>
          </div>

          <div className="admin-stat-card">
            <div className="admin-stat-title">Pending Reports</div>
            <div className="admin-stat-number" style={{ color: tickets.filter(t => t.status === 'OPEN').length > 0 ? '#ef4444' : 'var(--emerald)' }}>
              {tickets.filter(t => t.status === 'OPEN').length}
            </div>
            <span className="admin-stat-sub">Open Bug Reports</span>
          </div>
        </div>
      )}

      <div className="admin-nav-tabs">
        <button
          className={`admin-nav-tab ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          <Users size={14} /> Registered Users <span className="admin-tab-count">{users.length}</span>
        </button>
        <button
          className={`admin-nav-tab ${activeTab === 'tickets' ? 'active' : ''}`}
          onClick={() => setActiveTab('tickets')}
        >
          <Bug size={14} /> Bug Reports & Inquiries <span className="admin-tab-count">{tickets.length}</span>
        </button>
        <button
          className={`admin-nav-tab ${activeTab === 'files' ? 'active' : ''}`}
          onClick={() => setActiveTab('files')}
        >
          <FileSpreadsheet size={14} /> Datasets & Files <span className="admin-tab-count">{files.length}</span>
        </button>
        <button
          className={`admin-nav-tab ${activeTab === 'audit' ? 'active' : ''}`}
          onClick={() => setActiveTab('audit')}
        >
          <ShieldCheck size={14} /> Security Audit Logs <span className="admin-tab-count">{auditLogs.length}</span>
        </button>
      </div>

      {activeTab === 'users' && (
        <div className="admin-card">
          <div className="admin-toolbar">
            <div className="admin-toolbar-controls">
              <div className="admin-search-box">
                <Search size={14} className="admin-search-icon" />
                <input
                  type="text"
                  placeholder="Search user by name or email..."
                  value={userSearch}
                  onChange={(e) => setUserSearch(e.target.value)}
                />
                {userSearch && (
                  <button
                    type="button"
                    className="admin-search-clear"
                    onClick={() => setUserSearch('')}
                    title="Clear search"
                  >
                    <X size={12} />
                  </button>
                )}
              </div>

              <div className="admin-filter-group">
                <Filter size={13} className="text-secondary" />
                <select
                  className="admin-filter-select"
                  value={userRoleFilter}
                  onChange={(e) => setUserRoleFilter(e.target.value)}
                >
                  <option value="ALL">All Roles</option>
                  <option value="ROLE_ADMIN">Admins Only</option>
                  <option value="ROLE_USER">Standard Users</option>
                </select>

                <select
                  className="admin-filter-select"
                  value={userStatusFilter}
                  onChange={(e) => setUserStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Status</option>
                  <option value="ACTIVE">Active Only</option>
                  <option value="DISABLED">Disabled Only</option>
                </select>

                {(userSearch || userRoleFilter !== 'ALL' || userStatusFilter !== 'ALL') && (
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => { setUserSearch(''); setUserRoleFilter('ALL'); setUserStatusFilter('ALL'); }}
                    title="Clear filters"
                  >
                    Reset
                  </button>
                )}
              </div>
            </div>

            <span className="admin-counter-badge">
              Showing {filteredUsers.length} of {users.length} Users
            </span>
          </div>

          {loading ? (
            <div className="admin-loading-state">
              <div className="spinner mb-8" style={{ margin: '0 auto' }} />
              <span className="text-secondary" style={{ fontSize: 12 }}>Loading user directory...</span>
            </div>
          ) : filteredUsers.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-icon">
                <Users size={24} />
              </div>
              <p className="admin-empty-text">No users match the search/filter criteria.</p>
            </div>
          ) : (
            <div className="admin-table-container">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>User Details</th>
                    <th>Role</th>
                    <th>Account Status</th>
                    <th style={{ textAlign: 'center' }}>Projects</th>
                    <th>Joined Date</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{u.name}</div>
                        <div className="text-secondary" style={{ fontSize: 11 }}>{u.email}</div>
                      </td>
                      <td>
                        <span className={`badge ${u.role === 'ROLE_ADMIN' ? 'admin-badge' : 'badge-neutral'}`}>
                          {u.role === 'ROLE_ADMIN' ? 'Administrator' : 'Standard User'}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${u.enabled ? 'badge-success' : 'badge-warning'}`} style={{ color: u.enabled ? 'var(--emerald)' : '#ef4444' }}>
                          {u.enabled ? 'Active' : 'Disabled'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        <span style={{ fontWeight: 600 }}>{u.projectCount}</span>
                      </td>
                      <td>
                        <span className="text-secondary" style={{ fontSize: 12 }}>
                          {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : 'N/A'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div className="admin-action-group">
                          <button
                            className="btn btn-xs btn-outline"
                            onClick={() => handleToggleRole(u)}
                            disabled={actionLoading != null || u.id === currentUser?.id}
                            title={u.role === 'ROLE_ADMIN' ? 'Demote to Standard User' : 'Promote to Administrator'}
                          >
                            {actionLoading === `role-${u.id}` ? (
                              <div className="spinner" />
                            ) : (
                              u.role === 'ROLE_ADMIN' ? 'Demote' : 'Make Admin'
                            )}
                          </button>

                          <button
                            className={`btn btn-xs ${u.enabled ? 'btn-outline' : 'btn-primary'}`}
                            onClick={() => handleToggleStatus(u)}
                            disabled={actionLoading != null || u.id === currentUser?.id}
                            style={u.enabled ? { color: '#ef4444', borderColor: '#ef4444' } : {}}
                            title={u.enabled ? 'Disable user access' : 'Enable user access'}
                          >
                            {actionLoading === `status-${u.id}` ? (
                              <div className="spinner" />
                            ) : (
                              u.enabled ? 'Disable' : 'Enable'
                            )}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'tickets' && (
        <div className="admin-card">
          <div className="admin-toolbar">
            <div className="admin-toolbar-controls">
              <div className="admin-search-box">
                <Search size={14} className="admin-search-icon" />
                <input
                  type="text"
                  placeholder="Search code, user, subject or message..."
                  value={ticketSearch}
                  onChange={(e) => setTicketSearch(e.target.value)}
                />
                {ticketSearch && (
                  <button
                    type="button"
                    className="admin-search-clear"
                    onClick={() => setTicketSearch('')}
                    title="Clear search"
                  >
                    <X size={12} />
                  </button>
                )}
              </div>

              <div className="admin-filter-group">
                <Filter size={13} className="text-secondary" />
                <select
                  className="admin-filter-select"
                  value={ticketStatusFilter}
                  onChange={(e) => setTicketStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="OPEN">Open Only</option>
                  <option value="IN_PROGRESS">In Progress</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="CLOSED">Closed</option>
                </select>

                <select
                  className="admin-filter-select"
                  value={ticketCategoryFilter}
                  onChange={(e) => setTicketCategoryFilter(e.target.value)}
                >
                  <option value="ALL">All Categories</option>
                  <option value="BUG">Bug Reports</option>
                  <option value="SUPPORT">General Support</option>
                </select>

                {(ticketSearch || ticketStatusFilter !== 'ALL' || ticketCategoryFilter !== 'ALL') && (
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => { setTicketSearch(''); setTicketStatusFilter('ALL'); setTicketCategoryFilter('ALL'); }}
                    title="Clear filters"
                  >
                    Reset
                  </button>
                )}
              </div>
            </div>

            <span className="admin-counter-badge">
              Showing {filteredTickets.length} of {tickets.length} Reports
            </span>
          </div>

          {loading ? (
            <div className="admin-loading-state">
              <div className="spinner mb-8" style={{ margin: '0 auto' }} />
              <span className="text-secondary" style={{ fontSize: 12 }}>Loading bug reports...</span>
            </div>
          ) : filteredTickets.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-icon">
                <Bug size={24} />
              </div>
              <p className="admin-empty-text">No tickets match the search/filter criteria.</p>
            </div>
          ) : (
            <div className="admin-table-container">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Category</th>
                    <th>Reported By</th>
                    <th>Subject</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map(t => {
                    const statusClass =
                      t.status === 'RESOLVED' ? 'badge-success' :
                      t.status === 'IN_PROGRESS' ? 'badge-info' :
                      t.status === 'CLOSED' ? 'badge-neutral' : 'badge-warning';

                    return (
                      <tr key={t.id}>
                        <td style={{ fontFamily: 'monospace', fontWeight: 600, color: '#0f766e' }}>
                          {t.ticketCode}
                        </td>
                        <td>
                          <span className={`badge ${t.category === 'BUG' ? 'badge-error' : 'badge-neutral'}`}>
                            {t.category}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontWeight: 600 }}>{t.userName}</div>
                          <div className="text-secondary" style={{ fontSize: 11 }}>{t.userEmail}</div>
                        </td>
                        <td style={{ maxWidth: 280 }}>
                          <div style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {t.subject}
                          </div>
                        </td>
                        <td>
                          <span className={`badge ${statusClass}`} style={{ fontSize: 10, fontWeight: 700 }}>
                            {t.status}
                          </span>
                        </td>
                        <td style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                          {t.createdAt ? new Date(t.createdAt).toLocaleDateString() : 'N/A'}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            className="btn btn-xs btn-outline"
                            onClick={() => setSelectedTicket(t)}
                          >
                            Manage Ticket
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'files' && (
        <div className="admin-card">
          <div className="admin-toolbar">
            <div className="admin-toolbar-controls">
              <div className="admin-search-box">
                <Search size={14} className="admin-search-icon" />
                <input
                  type="text"
                  placeholder="Search file name, owner or dataset ID..."
                  value={fileSearch}
                  onChange={(e) => setFileSearch(e.target.value)}
                />
                {fileSearch && (
                  <button
                    type="button"
                    className="admin-search-clear"
                    onClick={() => setFileSearch('')}
                    title="Clear search"
                  >
                    <X size={12} />
                  </button>
                )}
              </div>
            </div>

            <span className="admin-counter-badge">
              Showing {filteredFiles.length} of {files.length} Datasets
            </span>
          </div>

          {loading ? (
            <div className="admin-loading-state">
              <div className="spinner mb-8" style={{ margin: '0 auto' }} />
              <span className="text-secondary" style={{ fontSize: 12 }}>Loading uploaded datasets...</span>
            </div>
          ) : filteredFiles.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-icon">
                <Folder size={24} />
              </div>
              <p className="admin-empty-text">No datasets found matching your search.</p>
            </div>
          ) : (
            <div className="admin-table-container">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Dataset Name</th>
                    <th>Storage Type</th>
                    <th>Owner</th>
                    <th>File Size</th>
                    <th>Data Dimension</th>
                    <th>Last Updated</th>
                    <th style={{ textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredFiles.map((f) => (
                    <tr key={f.fileId}>
                      <td>
                        <div className="flex items-center gap-8">
                          <FileSpreadsheet size={15} className="text-emerald" style={{ flexShrink: 0 }} />
                          <div>
                            <div style={{ fontWeight: 600 }}>{f.fileName}</div>
                            <div className="text-secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>{f.fileId.slice(0, 12)}...</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className={`badge ${f.source === 'SAVED_PROJECT' ? 'badge-info' : 'badge-neutral'}`} style={{ fontSize: 10 }}>
                          {f.source === 'SAVED_PROJECT' ? 'Saved Project' : 'Live Session'}
                        </span>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{f.ownerName}</div>
                        <div className="text-secondary" style={{ fontSize: 11 }}>{f.ownerEmail}</div>
                      </td>
                      <td style={{ fontWeight: 500 }}>
                        {formatBytes(f.fileSize)}
                      </td>
                      <td style={{ fontSize: 12 }}>
                        {f.rowCount != null ? `${f.rowCount.toLocaleString()} rows · ${f.columnCount} cols` : 'Session Data'}
                      </td>
                      <td style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                        {f.uploadedAt ? new Date(f.uploadedAt).toLocaleString() : 'N/A'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="btn btn-xs btn-outline"
                          onClick={() => handleDeleteFile(f)}
                          disabled={actionLoading === `file-${f.fileId}`}
                          style={{ color: '#ef4444', borderColor: '#ef4444' }}
                          title="Permanently remove dataset"
                        >
                          <Trash2 size={12} /> Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'audit' && (
        <div className="admin-card">
          <div className="admin-toolbar">
            <div className="admin-toolbar-controls">
              <div className="admin-search-box">
                <Search size={14} className="admin-search-icon" />
                <input
                  type="text"
                  placeholder="Search action, target or admin email..."
                  value={auditSearch}
                  onChange={(e) => setAuditSearch(e.target.value)}
                />
                {auditSearch && (
                  <button
                    type="button"
                    className="admin-search-clear"
                    onClick={() => setAuditSearch('')}
                    title="Clear search"
                  >
                    <X size={12} />
                  </button>
                )}
              </div>
            </div>

            <span className="admin-counter-badge">
              Showing {filteredAuditLogs.length} of {auditLogs.length} Audit Events
            </span>
          </div>

          {loading ? (
            <div className="admin-loading-state">
              <div className="spinner mb-8" style={{ margin: '0 auto' }} />
              <span className="text-secondary" style={{ fontSize: 12 }}>Loading audit trail...</span>
            </div>
          ) : filteredAuditLogs.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-icon">
                <ShieldCheck size={24} />
              </div>
              <p className="admin-empty-text">No audit log entries recorded yet.</p>
            </div>
          ) : (
            <div className="admin-table-container">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Admin Operator</th>
                    <th>Action Type</th>
                    <th>Target Resource</th>
                    <th>Audit Description</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAuditLogs.map((log) => {
                    const actionClass =
                      log.action.includes('ROLE') ? 'badge-warning' :
                      log.action.includes('STATUS') ? 'badge-info' :
                      log.action.includes('DELETE') ? 'badge-error' : 'badge-neutral';

                    return (
                      <tr key={log.id}>
                        <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, verticalAlign: 'middle' }}>
                            <Clock size={13} style={{ flexShrink: 0, opacity: 0.75, transform: 'translateY(1px)' }} />
                            <span>{log.createdAt ? new Date(log.createdAt).toLocaleString() : 'N/A'}</span>
                          </span>
                        </td>
                        <td style={{ fontWeight: 600 }}>
                          {log.adminEmail}
                        </td>
                        <td>
                          <span className={`badge ${actionClass}`} style={{ fontSize: 10, fontWeight: 700 }}>
                            {log.action}
                          </span>
                        </td>
                        <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-secondary)' }}>
                          {log.targetResource || '—'}
                        </td>
                        <td style={{ color: 'var(--text-primary)' }}>
                          {log.details}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {selectedTicket && (
        <div className="modal-backdrop" onClick={() => setSelectedTicket(null)}>
          <div className="modal-card p-24" style={{ maxWidth: 640 }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingBottom: 16, marginBottom: 18, borderBottom: '1px solid var(--border)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 34, height: 34, borderRadius: 8, background: 'rgba(16, 185, 129, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Bug size={18} className="text-emerald" />
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--text-primary)' }}>
                    {selectedTicket.ticketCode}
                  </h3>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    Category: <strong>{selectedTicket.category?.replace(/_/g, ' ')}</strong>
                  </span>
                </div>
              </div>
              <button className="btn-icon" onClick={() => setSelectedTicket(null)} title="Close">
                <X size={16} />
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 18, padding: '12px 14px', background: 'var(--surface-secondary)', borderRadius: 8, border: '1px solid var(--border)' }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: 'var(--text-tertiary)', marginBottom: 4 }}>Reported By</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>{selectedTicket.userName || 'Anonymous User'}</div>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>{selectedTicket.userEmail}</div>
              </div>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: 'var(--text-tertiary)', marginBottom: 6 }}>Current Status</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <select
                    value={selectedTicket.status}
                    onChange={(e) => handleUpdateTicketStatus(selectedTicket.id, e.target.value)}
                    disabled={ticketUpdating}
                    style={{
                      padding: '6px 12px',
                      borderRadius: 6,
                      fontSize: 12,
                      fontWeight: 700,
                      border: '1px solid var(--border)',
                      background: 'var(--card-bg)',
                      color: 'var(--text-primary)',
                      cursor: 'pointer'
                    }}
                  >
                    <option value="OPEN">OPEN</option>
                    <option value="IN_PROGRESS">IN_PROGRESS</option>
                    <option value="RESOLVED">RESOLVED</option>
                    <option value="CLOSED">CLOSED</option>
                  </select>
                  {ticketUpdating && <RefreshCw size={14} className="spin text-emerald" />}
                </div>
              </div>
            </div>

            <div style={{ marginBottom: 18 }}>
              <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: 'var(--text-tertiary)', marginBottom: 4 }}>Subject</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>{selectedTicket.subject}</div>
            </div>

            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: 'var(--text-tertiary)', marginBottom: 6 }}>Reproduction Steps / Details</div>
              <div style={{ background: 'var(--surface-secondary)', padding: '14px 16px', borderRadius: 8, border: '1px solid var(--border)', whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6, maxHeight: 240, overflowY: 'auto', color: 'var(--text-primary)' }}>
                {selectedTicket.message}
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, padding: '14px 16px', marginBottom: 20, background: 'var(--surface-secondary)', borderRadius: 8, border: '1px solid var(--border)' }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>Quick Status Actions:</span>
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                {selectedTicket.status !== 'IN_PROGRESS' && (
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => handleUpdateTicketStatus(selectedTicket.id, 'IN_PROGRESS')}
                    disabled={ticketUpdating}
                    style={{ minHeight: 30, padding: '4px 12px', fontSize: 12 }}
                  >
                    Mark In Progress
                  </button>
                )}
                {selectedTicket.status !== 'RESOLVED' && (
                  <button
                    className="btn btn-xs btn-primary"
                    onClick={() => handleUpdateTicketStatus(selectedTicket.id, 'RESOLVED')}
                    disabled={ticketUpdating}
                    style={{ minHeight: 30, padding: '4px 14px', fontSize: 12 }}
                  >
                    Mark Resolved
                  </button>
                )}
                {selectedTicket.status !== 'CLOSED' && (
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => handleUpdateTicketStatus(selectedTicket.id, 'CLOSED')}
                    disabled={ticketUpdating}
                    style={{ minHeight: 30, padding: '4px 12px', fontSize: 12, color: 'var(--text-danger, #ef4444)' }}
                  >
                    Close Ticket
                  </button>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
              <a
                href={`mailto:${selectedTicket.userEmail}?subject=Regarding ${selectedTicket.ticketCode}: ${selectedTicket.subject}`}
                className="btn btn-sm btn-primary"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '8px 16px', fontSize: 13 }}
              >
                <Mail size={14} /> Reply via Email
              </a>
              <button
                className="btn btn-sm btn-outline"
                onClick={() => setSelectedTicket(null)}
                style={{ padding: '8px 18px', fontSize: 13 }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmModal && (
        <div className="modal-backdrop" onClick={() => setConfirmModal(null)}>
          <div
            className="modal-card p-24"
            style={{ maxWidth: 460, width: 'calc(100% - 24px)', margin: '16px auto', boxSizing: 'border-box' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex-between mb-14 pb-10 border-b">
              <h3 style={{ margin: 0, fontSize: 16 }}>{confirmModal.title}</h3>
              <button className="btn-icon" onClick={() => setConfirmModal(null)} title="Close">
                <X size={16} />
              </button>
            </div>

            <p style={{ fontSize: 13.5, color: 'var(--text-secondary)', margin: '0 0 20px 0', lineHeight: 1.55 }}>
              {confirmModal.message}
            </p>

            <div className="flex justify-end gap-10">
              <button
                type="button"
                className="btn btn-sm btn-outline"
                onClick={() => setConfirmModal(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className={`btn btn-sm ${confirmModal.confirmClass || 'btn-primary'}`}
                style={confirmModal.confirmStyle || {}}
                onClick={confirmModal.onConfirm}
              >
                {confirmModal.confirmText || 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
