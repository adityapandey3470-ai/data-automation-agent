import React, { useState, useEffect, useCallback } from 'react';
import { getAdminUsers, getAdminStats, updateAdminUserStatus, updateAdminUserRole, getErrorMessage } from '../../services/api';
import { Shield, Users, Folder, CheckCircle, AlertCircle, RefreshCw, X, Zap } from '../icons/Icons';

export default function AdminPage({ currentUser }) {
  const [users, setUsers] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(null);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const fetchData = useCallback(async () => {
    if (currentUser?.role !== 'ROLE_ADMIN') return;
    setLoading(true);
    setError(null);
    try {
      const [usersRes, statsRes] = await Promise.all([
        getAdminUsers(),
        getAdminStats()
      ]);
      setUsers(usersRes.data || []);
      setStats(statsRes.data || null);
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

  const handleToggleStatus = async (user) => {
    if (user.id === currentUser?.id) {
      alert("You cannot disable your own administrator account.");
      return;
    }

    const nextState = !user.enabled;
    const confirmMsg = nextState
      ? `Re-activate account for ${user.name} (${user.email})?`
      : `Disable account for ${user.name} (${user.email})? They will be blocked from logging in.`;

    if (!window.confirm(confirmMsg)) return;

    setActionLoading(`status-${user.id}`);
    setError(null);
    setSuccess(null);
    try {
      await updateAdminUserStatus(user.id, nextState);
      setSuccess(`Account status updated for ${user.email}.`);
      fetchData();
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to update user status'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleToggleRole = async (user) => {
    if (user.id === currentUser?.id) {
      alert("You cannot demote your own administrator account.");
      return;
    }

    const newRole = user.role === 'ROLE_ADMIN' ? 'ROLE_USER' : 'ROLE_ADMIN';
    const confirmMsg = `Change role for ${user.name} from ${user.role} to ${newRole}?`;

    if (!window.confirm(confirmMsg)) return;

    setActionLoading(`role-${user.id}`);
    setError(null);
    setSuccess(null);
    try {
      await updateAdminUserRole(user.id, newRole);
      setSuccess(`User role updated for ${user.email} to ${newRole}.`);
      fetchData();
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to update user role'));
    } finally {
      setActionLoading(null);
    }
  };

  if (currentUser?.role !== 'ROLE_ADMIN') {
    return null;
  }

  return (
    <div className="admin-page">
      <div className="page-header flex-between mb-20">
        <div>
          <div className="flex-gap" style={{ alignItems: 'center', marginBottom: 4 }}>
            <span className="badge admin-badge">System Administration</span>
            <h1 style={{ margin: 0 }}>Platform & User Control</h1>
          </div>
          <p className="text-secondary" style={{ margin: 0, fontSize: 13 }}>
            Manage registered SaaS tenants, monitor storage usage, and regulate system authorization.
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

      {/* KPI Stats Cards */}
      {stats && (
        <div className="stats-grid mb-24" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
          <div className="stat-card p-16" style={{ borderRadius: 10, background: 'var(--card-bg)', border: '1px solid var(--border)' }}>
            <div className="text-secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Total Accounts</div>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '6px 0 2px 0' }}>{stats.totalUsers}</div>
            <span className="text-secondary" style={{ fontSize: 11 }}>Registered SaaS Users</span>
          </div>

          <div className="stat-card p-16" style={{ borderRadius: 10, background: 'var(--card-bg)', border: '1px solid var(--border)' }}>
            <div className="text-secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Active Users</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--emerald)', margin: '6px 0 2px 0' }}>{stats.activeUsers}</div>
            <span className="text-secondary" style={{ fontSize: 11 }}>Unrestricted Access</span>
          </div>

          <div className="stat-card p-16" style={{ borderRadius: 10, background: 'var(--card-bg)', border: '1px solid var(--border)' }}>
            <div className="text-secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Disabled Accounts</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: stats.disabledUsers > 0 ? '#ef4444' : 'var(--text-secondary)', margin: '6px 0 2px 0' }}>
              {stats.disabledUsers}
            </div>
            <span className="text-secondary" style={{ fontSize: 11 }}>Blocked From Access</span>
          </div>

          <div className="stat-card p-16" style={{ borderRadius: 10, background: 'var(--card-bg)', border: '1px solid var(--border)' }}>
            <div className="text-secondary" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>Total Saved Projects</div>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#3b82f6', margin: '6px 0 2px 0' }}>{stats.totalProjects}</div>
            <span className="text-secondary" style={{ fontSize: 11 }}>Persisted Workbooks</span>
          </div>
        </div>
      )}

      {/* Users Management Table */}
      <div className="card p-0" style={{ overflow: 'hidden', border: '1px solid var(--border)' }}>
        <div className="p-16 flex-between" style={{ borderBottom: '1px solid var(--border)' }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, margin: 0 }}>Registered User Directory</h3>
          <span className="badge badge-neutral" style={{ fontSize: 11 }}>{users.length} Users</span>
        </div>

        {loading ? (
          <div className="p-24 text-center">
            <div className="spinner mb-8" style={{ margin: '0 auto' }} />
            <span className="text-secondary" style={{ fontSize: 12 }}>Loading user directory...</span>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="admin-table w-full" style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: 'var(--surface-secondary)' }}>
                  <th style={{ textAlign: 'left' }}>User</th>
                  <th style={{ textAlign: 'left' }}>Role</th>
                  <th style={{ textAlign: 'left' }}>Status</th>
                  <th style={{ textAlign: 'center' }}>Projects</th>
                  <th style={{ textAlign: 'left' }}>Joined Date</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
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
                        {new Date(u.createdAt).toLocaleDateString()}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div className="flex-gap" style={{ justifyContent: 'flex-end' }}>
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
    </div>
  );
}
