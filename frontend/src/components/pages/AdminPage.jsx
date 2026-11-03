import React, { useState, useEffect, useCallback } from 'react';
import {
  getAdminUsers, getAdminStats, updateAdminUserStatus, updateAdminUserRole, deleteAdminUser,
  getAdminTickets, updateAdminTicketStatus, deleteAdminTicket, getAdminFiles, deleteAdminFile,
  getAdminAuditLogs, pruneAdminAuditLogs, getAdminAiMetrics, updateAdminUserSubscription,
  resetAdminUserAiQuota, getErrorMessage
} from '../../services/api';
import {
  Shield, Users, Folder, CheckCircle, AlertCircle, RefreshCw, X,
  Bug, Mail, Search, Filter, Trash2, Clock, ShieldCheck, FileSpreadsheet,
  Sparkles, Cpu, Activity, Zap, Bot, Crown, Building2, Lock
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
  const [userPlanFilter, setUserPlanFilter] = useState('ALL');

  const [ticketSearch, setTicketSearch] = useState('');
  const [ticketStatusFilter, setTicketStatusFilter] = useState('ALL');
  const [ticketCategoryFilter, setTicketCategoryFilter] = useState('ALL');
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [ticketUpdating, setTicketUpdating] = useState(false);

  const [fileSearch, setFileSearch] = useState('');

  const [auditSearch, setAuditSearch] = useState('');

  const [aiMetrics, setAiMetrics] = useState(null);
  const [aiSearch, setAiSearch] = useState('');
  const [aiFeatureFilter, setAiFeatureFilter] = useState('ALL');
  const [aiStatusFilter, setAiStatusFilter] = useState('ALL');

  const [confirmModal, setConfirmModal] = useState(null);

  const fetchData = useCallback(async () => {
    if (currentUser?.role !== 'ROLE_ADMIN') return;
    setLoading(true);
    setError(null);
    try {
      const [usersRes, statsRes, ticketsRes, filesRes, auditRes, aiRes] = await Promise.all([
        getAdminUsers(),
        getAdminStats(),
        getAdminTickets().catch(() => ({ data: [] })),
        getAdminFiles().catch(() => ({ data: [] })),
        getAdminAuditLogs().catch(() => ({ data: [] })),
        getAdminAiMetrics().catch(() => ({ data: null }))
      ]);
      setUsers(usersRes.data || []);
      setStats(statsRes.data || null);
      setTickets(ticketsRes.data || []);
      setFiles(filesRes.data || []);
      setAuditLogs(auditRes.data || []);
      setAiMetrics(aiRes.data || null);
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

  const handleDeleteUser = (user) => {
    if (user.id === currentUser?.id) {
      setError('You cannot delete your own administrator account.');
      return;
    }
    setConfirmModal({
      title: 'Permanently Delete User Account',
      message: `Are you sure you want to permanently delete user '${user.name}' (${user.email})? All datasets, saved projects, active workbooks, and support tickets belonging to this user will be erased permanently.`,
      confirmText: 'Delete User Account',
      confirmClass: 'btn-outline',
      confirmStyle: { color: '#ef4444', borderColor: '#ef4444' },
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`delete-user-${user.id}`);
        setError(null);

        const prevUsers = [...users];
        setUsers(prev => prev.filter(u => u.id !== user.id));

        try {
          await deleteAdminUser(user.id);
          setStats(prev => prev ? { ...prev, totalUsers: Math.max(0, prev.totalUsers - 1) } : prev);
          setSuccess(`User account '${user.email}' and all associated datasets have been permanently deleted.`);
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setUsers(prevUsers);
          setError(getErrorMessage(err, 'Failed to delete user account'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handleUpdateSubscription = (user, newTier) => {
    setConfirmModal({
      title: `Update Subscription to ${newTier}`,
      message: `Change subscription tier for ${user.name} (${user.email}) to ${newTier}? Quota limit will automatically adjust.`,
      confirmText: 'Change Plan',
      confirmClass: 'btn-primary',
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`sub-${user.id}`);
        setError(null);
        setSuccess(null);

        try {
          const res = await updateAdminUserSubscription(user.id, newTier);
          const updated = res.data;
          setUsers(prev => prev.map(u => u.id === user.id ? { ...u, ...updated } : u));
          if (currentUser?.id === user.id) {
            window.dispatchEvent(new CustomEvent('tabufinery_user_updated', { detail: updated }));
          }
          setSuccess(`Subscription plan updated to ${newTier} for ${user.email}.`);
          getAdminStats().then(r => setStats(r.data || null)).catch(() => {});
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setError(getErrorMessage(err, 'Failed to update subscription'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handleResetQuota = (user) => {
    setConfirmModal({
      title: 'Reset AI Query Quota',
      message: `Reset AI usage counter back to 0 queries for ${user.name} (${user.email})? Current usage: ${user.aiQueriesUsed || 0} / ${user.aiQueriesLimit || 10}.`,
      confirmText: 'Reset Quota',
      confirmClass: 'btn-primary',
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`reset-${user.id}`);
        setError(null);
        setSuccess(null);

        try {
          const res = await resetAdminUserAiQuota(user.id);
          const updated = res.data;
          setUsers(prev => prev.map(u => u.id === user.id ? { ...u, ...updated } : u));
          setSuccess(`AI query counter reset to 0 for ${user.email}.`);
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setError(getErrorMessage(err, 'Failed to reset quota'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handleDeleteTicket = (ticket) => {
    setConfirmModal({
      title: 'Permanently Delete Support Ticket',
      message: `Are you sure you want to permanently delete ticket ${ticket.ticketCode} (${ticket.subject})? This entry will be erased from platform records.`,
      confirmText: 'Delete Ticket',
      confirmClass: 'btn-outline',
      confirmStyle: { color: '#ef4444', borderColor: '#ef4444' },
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading(`delete-ticket-${ticket.id}`);
        setError(null);

        const prevTickets = [...tickets];
        setTickets(prev => prev.filter(t => t.id !== ticket.id));
        if (selectedTicket?.id === ticket.id) {
          setSelectedTicket(null);
        }

        try {
          await deleteAdminTicket(ticket.id);
          setSuccess(`Support ticket ${ticket.ticketCode} has been permanently deleted.`);
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setTickets(prevTickets);
          setError(getErrorMessage(err, 'Failed to delete support ticket'));
        } finally {
          setActionLoading(null);
        }
      }
    });
  };

  const handlePruneAuditLogs = (days) => {
    const label = days > 0 ? `older than ${days} days` : 'all recorded records';
    setConfirmModal({
      title: 'Prune Security Audit Trail',
      message: `Are you sure you want to permanently prune audit log entries ${label}? This administrative action will itself be recorded in the security audit trail.`,
      confirmText: days > 0 ? 'Prune Audit Logs' : 'Clear All Logs',
      confirmClass: 'btn-outline',
      confirmStyle: { color: '#ef4444', borderColor: '#ef4444' },
      onConfirm: async () => {
        setConfirmModal(null);
        setActionLoading('prune-audit');
        setError(null);

        try {
          const res = await pruneAdminAuditLogs(days);
          setSuccess(res.data?.message || 'Audit logs successfully pruned.');
          getAdminAuditLogs().then(r => setAuditLogs(r.data || [])).catch(() => {});
        } catch (err) {
          setError(getErrorMessage(err, 'Failed to prune audit logs'));
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
    const tier = (u.subscriptionTier || 'FREE').toUpperCase();
    const matchesPlan = userPlanFilter === 'ALL' || tier === userPlanFilter;
    return matchesSearch && matchesRole && matchesStatus && matchesPlan;
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

  const filteredAiLogs = (aiMetrics?.recentLogs || []).filter(l => {
    const q = aiSearch.toLowerCase().trim();
    const matchesSearch = !q ||
      (l.userEmail && l.userEmail.toLowerCase().includes(q)) ||
      (l.modelName && l.modelName.toLowerCase().includes(q)) ||
      (l.queryPreview && l.queryPreview.toLowerCase().includes(q)) ||
      (l.feature && l.feature.toLowerCase().includes(q));
    const matchesFeature = aiFeatureFilter === 'ALL' || l.feature === aiFeatureFilter;
    const matchesStatus = aiStatusFilter === 'ALL' || l.status === aiStatusFilter;
    return matchesSearch && matchesFeature && matchesStatus;
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
            <div className="admin-stat-title">Subscribed Plans</div>
            <div className="admin-stat-number" style={{ color: '#8b5cf6' }}>
              {(stats.proUsers || 0) + (stats.enterpriseUsers || 0)} <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-secondary)' }}>/ {stats.totalUsers}</span>
            </div>
            <span className="admin-stat-sub">{stats.freeUsers || 0} Free · {stats.proUsers || 0} Pro · {stats.enterpriseUsers || 0} Enterprise</span>
          </div>

          <div className="admin-stat-card">
            <div className="admin-stat-title">AI Tokens Tracked</div>
            <div className="admin-stat-number" style={{ color: 'var(--emerald)' }}>
              {stats.totalTokensTracked ? Number(stats.totalTokensTracked).toLocaleString() : '0'}
            </div>
            <span className="admin-stat-sub">{stats.totalAiQueriesUsed || 0} Queries Audited</span>
          </div>

          <div className="admin-stat-card">
            <div className="admin-stat-title">Active Datasets</div>
            <div className="admin-stat-number" style={{ color: '#3b82f6' }}>{files.length}</div>
            <span className="admin-stat-sub">Uploaded Files & Projects</span>
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
        <button
          className={`admin-nav-tab ${activeTab === 'ai' ? 'active' : ''}`}
          onClick={() => setActiveTab('ai')}
        >
          <Sparkles size={14} className="text-emerald" /> AI Usage & Metrics <span className="admin-tab-count">{aiMetrics?.totalRequests || 0}</span>
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

                <select
                  className="admin-filter-select"
                  value={userPlanFilter}
                  onChange={(e) => setUserPlanFilter(e.target.value)}
                >
                  <option value="ALL">All Plans</option>
                  <option value="FREE">Free Tier</option>
                  <option value="PRO">Pro Plan</option>
                  <option value="ENTERPRISE">Enterprise</option>
                </select>

                {(userSearch || userRoleFilter !== 'ALL' || userStatusFilter !== 'ALL' || userPlanFilter !== 'ALL') && (
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => { setUserSearch(''); setUserRoleFilter('ALL'); setUserStatusFilter('ALL'); setUserPlanFilter('ALL'); }}
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
                    <th>Plan</th>
                    <th>AI Quota & Usage</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th style={{ textAlign: 'center' }}>Projects</th>
                    <th>Joined</th>
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
                        {u.subscriptionTier === 'ENTERPRISE' ? (
                          <span className="badge badge-emerald flex-gap" style={{ fontSize: 11, padding: '3px 8px' }}>
                            <Building2 size={12} /> Enterprise
                          </span>
                        ) : u.subscriptionTier === 'PRO' ? (
                          <span className="badge badge-indigo flex-gap" style={{ fontSize: 11, padding: '3px 8px' }}>
                            <Crown size={12} /> Pro
                          </span>
                        ) : (
                          <span className="badge badge-neutral flex-gap" style={{ fontSize: 11, padding: '3px 8px' }}>
                            <Zap size={12} /> Free
                          </span>
                        )}
                      </td>
                      <td>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>
                          {u.subscriptionTier === 'ENTERPRISE' ? 'Unlimited' : `${u.aiQueriesUsed || 0} / ${u.aiQueriesLimit || 10}`}
                        </div>
                        <div className="text-secondary" style={{ fontSize: 11 }}>
                          {u.totalTokensUsed ? `${Number(u.totalTokensUsed).toLocaleString()} tokens` : '0 tokens'}
                        </div>
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
                          <select
                            value={u.subscriptionTier || 'FREE'}
                            onChange={(e) => handleUpdateSubscription(u, e.target.value)}
                            disabled={actionLoading != null}
                            title="Change subscription plan"
                            style={{ fontSize: 11, padding: '3px 6px', borderRadius: 4, border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text-primary)', cursor: 'pointer' }}
                          >
                            <option value="FREE">Plan: Free</option>
                            <option value="PRO">Plan: Pro</option>
                            <option value="ENTERPRISE">Plan: Enterprise</option>
                          </select>

                          {(u.aiQueriesUsed || 0) > 0 && (
                            <button
                              className="btn btn-xs btn-outline"
                              onClick={() => handleResetQuota(u)}
                              disabled={actionLoading != null}
                              title="Reset AI query quota back to 0"
                            >
                              {actionLoading === `reset-${u.id}` ? <div className="spinner" /> : 'Reset Quota'}
                            </button>
                          )}

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

                          <button
                            className="btn btn-xs btn-outline"
                            onClick={() => handleDeleteUser(u)}
                            disabled={actionLoading != null || u.id === currentUser?.id}
                            style={{ color: '#ef4444', borderColor: '#ef4444' }}
                            title={u.id === currentUser?.id ? 'Cannot delete yourself' : 'Permanently delete user'}
                          >
                            {actionLoading === `delete-user-${u.id}` ? (
                              <div className="spinner" />
                            ) : (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                <Trash2 size={12} /> Delete
                              </span>
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
                          <div className="admin-action-group" style={{ justifyContent: 'flex-end' }}>
                            <button
                              className="btn btn-xs btn-outline"
                              onClick={() => setSelectedTicket(t)}
                            >
                              Manage
                            </button>
                            <button
                              className="btn btn-xs btn-outline"
                              onClick={() => handleDeleteTicket(t)}
                              disabled={actionLoading === `delete-ticket-${t.id}`}
                              style={{ color: '#ef4444', borderColor: '#ef4444' }}
                              title="Permanently delete ticket"
                            >
                              {actionLoading === `delete-ticket-${t.id}` ? (
                                <div className="spinner" />
                              ) : (
                                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                  <Trash2 size={12} /> Delete
                                </span>
                              )}
                            </button>
                          </div>
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

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <button
                  type="button"
                  className="btn btn-xs btn-outline"
                  onClick={() => handlePruneAuditLogs(30)}
                  disabled={actionLoading === 'prune-audit' || auditLogs.length === 0}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 5, minHeight: 30 }}
                  title="Purge audit logs older than 30 days"
                >
                  <Trash2 size={12} /> Prune &gt;30d
                </button>
                <button
                  type="button"
                  className="btn btn-xs btn-outline"
                  onClick={() => handlePruneAuditLogs(0)}
                  disabled={actionLoading === 'prune-audit' || auditLogs.length === 0}
                  style={{ color: '#ef4444', borderColor: '#ef4444', display: 'inline-flex', alignItems: 'center', gap: 5, minHeight: 30 }}
                  title="Clear all recorded test audit logs"
                >
                  <Trash2 size={12} /> Clear All
                </button>
              </div>

              <span className="admin-counter-badge">
                Showing {filteredAuditLogs.length} of {auditLogs.length} Audit Events
              </span>
            </div>
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

      {activeTab === 'ai' && (
        <div className="card p-0">
          <div style={{ padding: '20px', borderBottom: '1px solid var(--border)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
              <div className="admin-stat-card" style={{ margin: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span className="admin-stat-title" style={{ margin: 0 }}>Total AI Invocations</span>
                  <div style={{ width: 28, height: 28, borderRadius: 6, background: 'rgba(16, 185, 129, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Sparkles size={15} className="text-emerald" />
                  </div>
                </div>
                <div className="admin-stat-number" style={{ color: 'var(--emerald)' }}>
                  {aiMetrics?.totalRequests || 0}
                </div>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginTop: 4 }}>
                  Analyst: <strong>{aiMetrics?.analystRequests || 0}</strong> · Support: <strong>{aiMetrics?.supportRequests || 0}</strong>
                </div>
              </div>

              <div className="admin-stat-card" style={{ margin: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span className="admin-stat-title" style={{ margin: 0 }}>Tokens Consumed</span>
                  <div style={{ width: 28, height: 28, borderRadius: 6, background: 'rgba(59, 130, 246, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Cpu size={15} style={{ color: '#3b82f6' }} />
                  </div>
                </div>
                <div className="admin-stat-number" style={{ color: '#3b82f6' }}>
                  {(aiMetrics?.totalTokens || 0).toLocaleString()}
                </div>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginTop: 4 }}>
                  In: <strong>{(aiMetrics?.promptTokens || 0).toLocaleString()}</strong> · Out: <strong>{(aiMetrics?.completionTokens || 0).toLocaleString()}</strong>
                </div>
              </div>

              <div className="admin-stat-card" style={{ margin: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span className="admin-stat-title" style={{ margin: 0 }}>Active LLM Engine</span>
                  <div style={{ width: 28, height: 28, borderRadius: 6, background: 'rgba(99, 102, 241, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Bot size={15} style={{ color: '#818cf8' }} />
                  </div>
                </div>
                <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 6, minHeight: 36 }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: aiMetrics?.apiConfigured ? 'var(--emerald)' : '#eab308', display: 'inline-block', flexShrink: 0 }} />
                  <span style={{ fontFamily: 'monospace', fontSize: 14 }}>{aiMetrics?.activeModel || 'gemini-2.5-flash'}</span>
                </div>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginTop: 4 }}>
                  {aiMetrics?.apiConfigured ? 'Gemini API Live' : 'Deterministic Mode'}
                </div>
              </div>

              <div className="admin-stat-card" style={{ margin: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span className="admin-stat-title" style={{ margin: 0 }}>Avg Response Speed</span>
                  <div style={{ width: 28, height: 28, borderRadius: 6, background: 'rgba(234, 179, 8, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Activity size={15} style={{ color: '#eab308' }} />
                  </div>
                </div>
                <div className="admin-stat-number" style={{ color: '#eab308' }}>
                  {aiMetrics?.averageLatencyMs || 0} <span style={{ fontSize: 14, fontWeight: 500 }}>ms</span>
                </div>
                <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginTop: 4 }}>
                  Success Rate: <strong>{aiMetrics?.totalRequests ? Math.round((aiMetrics.successCount / aiMetrics.totalRequests) * 100) : 100}%</strong>
                </div>
              </div>
            </div>

            <div style={{ marginTop: 16, padding: '14px 16px', background: 'var(--surface-secondary)', borderRadius: 10, border: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 14 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: 'rgba(16, 185, 129, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Zap size={16} className="text-emerald" />
                </div>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>Google AI Studio Quotas & Protection</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Limit: 15 Requests/Min · 1,500 Requests/Day · Sliding Window Defense Active</div>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
                <div>
                  <span style={{ fontSize: 11, textTransform: 'uppercase', color: 'var(--text-tertiary)', fontWeight: 700 }}>Estimated Cost</span>
                  <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)' }}>
                    ${(aiMetrics?.estimatedCostUsd || 0).toFixed(4)} <span style={{ fontSize: 11, fontWeight: 500, color: 'var(--emerald)' }}>(Free Tier: $0.00)</span>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-xs btn-outline"
                  onClick={fetchData}
                  disabled={loading}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minHeight: 32 }}
                >
                  <RefreshCw size={12} className={loading ? 'spin' : ''} /> Refresh
                </button>
              </div>
            </div>
          </div>

          <div className="admin-toolbar">
            <div className="admin-search-box">
              <Search size={14} className="text-secondary" />
              <input
                type="text"
                placeholder="Search by caller, query preview, model..."
                value={aiSearch}
                onChange={(e) => setAiSearch(e.target.value)}
              />
              {aiSearch && (
                <button className="admin-search-clear" onClick={() => setAiSearch('')}>
                  <X size={12} />
                </button>
              )}
            </div>

            <div className="admin-toolbar-controls">
              <div className="admin-filter-group">
                <Filter size={13} className="text-tertiary" />
                <select
                  value={aiFeatureFilter}
                  onChange={(e) => setAiFeatureFilter(e.target.value)}
                  className="admin-filter-select"
                >
                  <option value="ALL">All AI Features</option>
                  <option value="AI_ANALYST">AI Data Analyst</option>
                  <option value="SUPPORT_CHAT">Support Chatbot</option>
                </select>

                <select
                  value={aiStatusFilter}
                  onChange={(e) => setAiStatusFilter(e.target.value)}
                  className="admin-filter-select"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="SUCCESS">Success (Live LLM)</option>
                  <option value="FALLBACK">Fallback (Engine/KB)</option>
                  <option value="ERROR">Error</option>
                </select>
              </div>

              <span className="admin-counter-badge">
                Showing {filteredAiLogs.length} of {aiMetrics?.recentLogs?.length || 0} events
              </span>
            </div>
          </div>

          {filteredAiLogs.length === 0 ? (
            <div className="admin-empty-state">
              <div className="admin-empty-icon">
                <Sparkles size={22} />
              </div>
              <p className="admin-empty-text">
                {aiSearch || aiFeatureFilter !== 'ALL' || aiStatusFilter !== 'ALL'
                  ? 'No AI invocation records match your filter criteria.'
                  : 'No AI requests logged yet. Invocations from AI Analyst or Support Chatbot will be tracked here in real-time.'}
              </p>
            </div>
          ) : (
            <div className="admin-table-container">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>User / Session</th>
                    <th>Feature</th>
                    <th>Query Preview</th>
                    <th>Model</th>
                    <th>Tokens</th>
                    <th>Latency</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAiLogs.map((log) => {
                    const isSuccess = log.status === 'SUCCESS';
                    return (
                      <tr key={log.id}>
                        <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7 }}>
                            <Clock size={13} style={{ flexShrink: 0, opacity: 0.75 }} />
                            <span>{log.createdAt ? new Date(log.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'N/A'}</span>
                          </span>
                        </td>
                        <td style={{ fontWeight: 600, fontSize: 12.5 }}>
                          {log.userEmail}
                        </td>
                        <td>
                          <span className={`badge ${log.feature === 'AI_ANALYST' ? 'badge-primary' : 'badge-info'}`} style={{ fontSize: 10, fontWeight: 700 }}>
                            {log.feature === 'AI_ANALYST' ? 'AI Analyst' : 'Support Chat'}
                          </span>
                        </td>
                        <td style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12.5, color: 'var(--text-primary)' }} title={log.queryPreview}>
                          {log.queryPreview || '—'}
                        </td>
                        <td style={{ fontFamily: 'monospace', fontSize: 11.5, color: 'var(--text-secondary)' }}>
                          {log.modelName}
                        </td>
                        <td style={{ fontSize: 12, fontWeight: 600 }}>
                          {log.totalTokens > 0 ? (
                            <span>{log.totalTokens} <span style={{ fontSize: 10, fontWeight: 400, color: 'var(--text-tertiary)' }}>({log.promptTokens}/{log.completionTokens})</span></span>
                          ) : '—'}
                        </td>
                        <td style={{ fontSize: 12 }}>
                          <span style={{ color: log.latencyMs < 1000 ? 'var(--emerald)' : log.latencyMs < 3000 ? '#eab308' : '#ef4444', fontWeight: 600 }}>
                            {log.latencyMs}ms
                          </span>
                        </td>
                        <td>
                          <span className={`badge ${isSuccess ? 'badge-success' : 'badge-warning'}`} style={{ fontSize: 10, fontWeight: 700 }}>
                            {log.status}
                          </span>
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

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, paddingTop: 16, borderTop: '1px solid var(--border)', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-sm btn-outline"
                onClick={() => handleDeleteTicket(selectedTicket)}
                style={{ color: '#ef4444', borderColor: '#ef4444', display: 'inline-flex', alignItems: 'center', gap: 6 }}
                title="Permanently remove ticket"
              >
                <Trash2 size={13} /> Delete Ticket
              </button>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
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
