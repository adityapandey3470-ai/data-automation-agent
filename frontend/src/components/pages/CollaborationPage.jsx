import React, { useState, useEffect, useCallback, useRef } from 'react';
import './collaboration.css';
import {
  Users, Share2, UserPlus, Shield, ShieldCheck, Lock, RefreshCw,
  Clock, CheckCircle, AlertCircle, Trash2, X, Sparkles, LayoutDashboard, Mail, Check
} from '../icons/Icons';
import {
  getSharedWithMeDashboards,
  getSharedByMeDashboards,
  shareDashboard,
  updateCollaboratorPermissions,
  revokeCollaboratorShare,
  validateDeletePermission,
  getErrorMessage
} from '../../services/api';

export default function CollaborationPage({ currentUser, onNavigate, onRequestAuth }) {
  const [activeTab, setActiveTab] = useState('sharedWithMe');
  const [sharedWithMe, setSharedWithMe] = useState([]);
  const [sharedByMe, setSharedByMe] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const [liveSync, setLiveSync] = useState(true);
  const [secondsUntilSync, setSecondsUntilSync] = useState(30);

  const [showInviteModal, setShowInviteModal] = useState(false);
  const [inviteData, setInviteData] = useState({
    dashboardId: 'overview-main',
    dashboardName: 'Executive Operations Overview',
    managerEmail: '',
    canCustomize: false,
    canInvite: false,
  });
  const [submittingInvite, setSubmittingInvite] = useState(false);

  const [editingShare, setEditingShare] = useState(null);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  const timerRef = useRef(null);

  const fetchCollaborations = useCallback(async (isSilent = false) => {
    if (!currentUser) return;
    if (!isSilent) setLoading(true);
    setError(null);
    try {
      const [withMeRes, byMeRes] = await Promise.all([
        getSharedWithMeDashboards(),
        getSharedByMeDashboards()
      ]);
      setSharedWithMe(Array.isArray(withMeRes.data) ? withMeRes.data : []);
      setSharedByMe(Array.isArray(byMeRes.data) ? byMeRes.data : []);
    } catch (err) {
      if (!isSilent) {
        setError(getErrorMessage(err, 'Failed to fetch collaboration dashboards'));
      }
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, [currentUser]);

  useEffect(() => {
    fetchCollaborations(false);
  }, [fetchCollaborations]);

  useEffect(() => {
    if (!liveSync) return;
    const interval = setInterval(() => {
      setSecondsUntilSync((prev) => {
        if (prev <= 1) {
          fetchCollaborations(true);
          return 30;
        }
        return prev - 1;
      });
    }, 1000);
    timerRef.current = interval;
    return () => clearInterval(interval);
  }, [liveSync, fetchCollaborations]);

  const handleManualSync = () => {
    setSecondsUntilSync(30);
    fetchCollaborations(false);
  };

  const handleSendInvite = async (e) => {
    e.preventDefault();
    if (!inviteData.managerEmail.trim()) {
      setError('Please provide a valid manager or colleague email.');
      return;
    }
    setSubmittingInvite(true);
    setError(null);
    setSuccess(null);
    try {
      await shareDashboard({
        dashboardId: inviteData.dashboardId,
        dashboardName: inviteData.dashboardName,
        managerEmail: inviteData.managerEmail.trim(),
        canCustomize: inviteData.canCustomize,
        canInvite: inviteData.canInvite
      });
      setSuccess(`Dashboard successfully shared with ${inviteData.managerEmail}. Permissions & audit logged.`);
      setShowInviteModal(false);
      setInviteData({
        dashboardId: 'overview-main',
        dashboardName: 'Executive Operations Overview',
        managerEmail: '',
        canCustomize: false,
        canInvite: false
      });
      fetchCollaborations(false);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to share dashboard.'));
    } finally {
      setSubmittingInvite(false);
    }
  };

  const handleSavePermissions = async (e) => {
    e.preventDefault();
    if (!editingShare) return;
    setActionLoadingId(editingShare.id);
    try {
      await updateCollaboratorPermissions(editingShare.id, {
        canCustomize: editingShare.canCustomize,
        canInvite: editingShare.canInvite
      });
      setSuccess(`Permissions updated for ${editingShare.sharedWithEmail}`);
      setEditingShare(null);
      fetchCollaborations(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to update permissions.'));
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleRevokeShare = async (shareId, email) => {
    if (!window.confirm(`Are you sure you want to revoke access for ${email}?`)) return;
    setActionLoadingId(shareId);
    try {
      await revokeCollaboratorShare(shareId);
      setSuccess(`Revoked access for ${email}`);
      fetchCollaborations(false);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to revoke collaborator.'));
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleAttemptDelete = async (dashboardId) => {
    try {
      await validateDeletePermission(dashboardId);
      alert('Delete permission check passed.');
    } catch (err) {
      const msg = getErrorMessage(err, 'Collaborators are not permitted to delete shared dashboards. Only the owner can delete.');
      alert(msg);
    }
  };

  if (!currentUser) {
    return (
      <div className="collab-container">
        <div className="collab-auth-notice">
          <Shield size={38} className="collab-auth-icon" />
          <h2>Authentication Required</h2>
          <p>Please log in or create an account to access team dashboards, manager delegation, and real-time collaboration.</p>
          <button className="collab-btn primary" onClick={onRequestAuth}>
            Sign In / Register
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="collab-container">
      <div className="collab-header">
        <div className="collab-header-left">
          <div className="collab-title-row">
            <h1 className="collab-title">Collaboration & Shared Dashboards</h1>
            <span className="collab-badge-beta">Multi-Tenant RBAC</span>
          </div>
          <p className="collab-subtitle">
            Delegate live dashboards to managers with view-only or customizable privileges, safe-lock deletion invariants, and comprehensive admin audit logging.
          </p>
        </div>

        <div className="collab-header-actions">
          <div className="collab-sync-bar">
            <button
              className={`collab-live-pill ${liveSync ? 'active' : 'paused'}`}
              onClick={() => setLiveSync(!liveSync)}
              title={liveSync ? 'Live Sync Active (click to pause)' : 'Live Sync Paused (click to resume)'}
            >
              <span className={`pulse-indicator ${liveSync ? 'green' : 'gray'}`} />
              {liveSync ? `30s Sync (${secondsUntilSync}s)` : 'Sync Paused'}
            </button>
            <button
              className="collab-icon-btn"
              onClick={handleManualSync}
              title="Refresh now"
              disabled={loading}
            >
              <RefreshCw size={15} className={loading ? 'spin' : ''} />
            </button>
          </div>

          <button
            className="collab-btn primary"
            onClick={() => setShowInviteModal(true)}
          >
            <UserPlus size={16} />
            <span>Invite Manager / Share</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="collab-alert error">
          <AlertCircle size={18} />
          <span>{error}</span>
          <button className="collab-alert-close" onClick={() => setError(null)}><X size={15} /></button>
        </div>
      )}

      {success && (
        <div className="collab-alert success">
          <CheckCircle size={18} />
          <span>{success}</span>
          <button className="collab-alert-close" onClick={() => setSuccess(null)}><X size={15} /></button>
        </div>
      )}

      <div className="collab-tabs">
        <button
          className={`collab-tab-btn ${activeTab === 'sharedWithMe' ? 'active' : ''}`}
          onClick={() => setActiveTab('sharedWithMe')}
        >
          <Users size={16} />
          <span>Shared With Me ({sharedWithMe.length})</span>
        </button>
        <button
          className={`collab-tab-btn ${activeTab === 'sharedByMe' ? 'active' : ''}`}
          onClick={() => setActiveTab('sharedByMe')}
        >
          <Share2 size={16} />
          <span>Shared By Me ({sharedByMe.length})</span>
        </button>
      </div>

      <div className="collab-content">
        {loading && (
          <div className="collab-loading-state">
            <RefreshCw size={24} className="spin" />
            <p>Synchronizing dashboard permissions...</p>
          </div>
        )}

        {!loading && activeTab === 'sharedWithMe' && (
          <div className="collab-card-grid">
            {sharedWithMe.length === 0 ? (
              <div className="collab-empty-state">
                <Users size={40} />
                <h3>No Dashboards Shared With You Yet</h3>
                <p>When colleagues or executives invite you to their dashboards, they will automatically appear here with designated permissions.</p>
              </div>
            ) : (
              sharedWithMe.map((item) => (
                <div key={item.id} className="collab-card">
                  <div className="collab-card-top">
                    <div className="collab-card-icon">
                      <LayoutDashboard size={20} />
                    </div>
                    <div className="collab-card-main">
                      <h3 className="collab-card-title">{item.dashboardName}</h3>
                      <div className="collab-card-owner">
                        <span>Shared by</span> <strong>{item.ownerEmail}</strong>
                      </div>
                    </div>
                    <span className="collab-status-badge accepted">
                      <CheckCircle size={12} /> Active Access
                    </span>
                  </div>

                  <div className="collab-card-body">
                    <div className="collab-perm-list">
                      <div className="collab-perm-item">
                        <span className="perm-label">Customization:</span>
                        {item.canCustomize ? (
                          <span className="perm-tag allowed">
                            <Sparkles size={12} /> Customizable
                          </span>
                        ) : (
                          <span className="perm-tag restricted">
                            <Lock size={12} /> View Only (Locked)
                          </span>
                        )}
                      </div>

                      <div className="collab-perm-item">
                        <span className="perm-label">Delegation:</span>
                        {item.canInvite ? (
                          <span className="perm-tag allowed">
                            <UserPlus size={12} /> Can Invite Others
                          </span>
                        ) : (
                          <span className="perm-tag restricted">
                            <Lock size={12} /> Sub-inviting Disabled
                          </span>
                        )}
                      </div>

                      <div className="collab-perm-item">
                        <span className="perm-label">Deletion:</span>
                        <span className="perm-tag invariant-locked" title="Protected RBAC Invariant">
                          <ShieldCheck size={12} /> Delete Blocked (Owner Only)
                        </span>
                      </div>
                    </div>

                    <div className="collab-card-date">
                      <Clock size={12} />
                      <span>Shared on {new Date(item.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  <div className="collab-card-footer">
                    <button
                      className="collab-btn primary small"
                      onClick={() => onNavigate('overview')}
                    >
                      <LayoutDashboard size={14} />
                      <span>Open Dashboard</span>
                    </button>

                    {item.canCustomize ? (
                      <button
                        className="collab-btn secondary small"
                        onClick={() => onNavigate('overview')}
                      >
                        <Sparkles size={14} />
                        <span>Customize Layout</span>
                      </button>
                    ) : (
                      <button
                        className="collab-btn secondary small disabled"
                        disabled
                        title="Owner has restricted layout customization to View-Only"
                      >
                        <Lock size={14} />
                        <span>Customize Locked</span>
                      </button>
                    )}

                    <button
                      className="collab-btn danger small disabled"
                      onClick={() => handleAttemptDelete(item.dashboardId)}
                      title="Manager/Collaborators cannot delete shared dashboards"
                    >
                      <Trash2 size={14} />
                      <span>Delete Restricted</span>
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {!loading && activeTab === 'sharedByMe' && (
          <div className="collab-table-wrap">
            {sharedByMe.length === 0 ? (
              <div className="collab-empty-state">
                <Share2 size={40} />
                <h3>You Haven't Shared Any Dashboards Yet</h3>
                <p>Click "Invite Manager / Share" to safely grant executive or team access to your live dashboards.</p>
                <button
                  className="collab-btn primary"
                  onClick={() => setShowInviteModal(true)}
                  style={{ marginTop: '12px' }}
                >
                  <UserPlus size={16} />
                  <span>Share a Dashboard</span>
                </button>
              </div>
            ) : (
              <table className="collab-table">
                <thead>
                  <tr>
                    <th>Dashboard</th>
                    <th>Invited Manager / Colleague</th>
                    <th>Status</th>
                    <th>Customization</th>
                    <th>Invite Delegation</th>
                    <th>Invited At</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {sharedByMe.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <strong>{item.dashboardName}</strong>
                        <div className="collab-subtext">{item.dashboardId}</div>
                      </td>
                      <td>
                        <span className="collab-email">{item.sharedWithEmail}</span>
                      </td>
                      <td>
                        {item.invitationStatus === 'ACCEPTED' ? (
                          <span className="collab-status-badge accepted">
                            <CheckCircle size={12} /> Active Account
                          </span>
                        ) : (
                          <span className="collab-status-badge pending" title="Invited user will automatically gain access upon signup">
                            <Clock size={12} /> Pending Registration
                          </span>
                        )}
                      </td>
                      <td>
                        {item.canCustomize ? (
                          <span className="collab-chip green">Allowed</span>
                        ) : (
                          <span className="collab-chip gray">View Only</span>
                        )}
                      </td>
                      <td>
                        {item.canInvite ? (
                          <span className="collab-chip green">Allowed</span>
                        ) : (
                          <span className="collab-chip gray">Disabled</span>
                        )}
                      </td>
                      <td>{new Date(item.createdAt).toLocaleDateString()}</td>
                      <td>
                        <div className="collab-row-actions">
                          <button
                            className="collab-action-btn"
                            title="Edit Permissions"
                            onClick={() => setEditingShare({ ...item })}
                          >
                            <Shield size={14} />
                          </button>
                          <button
                            className="collab-action-btn danger"
                            title="Revoke Share"
                            disabled={actionLoadingId === item.id}
                            onClick={() => handleRevokeShare(item.id, item.sharedWithEmail)}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>

      {showInviteModal && (
        <div className="collab-modal-overlay" onClick={() => setShowInviteModal(false)}>
          <div className="collab-modal" onClick={(e) => e.stopPropagation()}>
            <div className="collab-modal-header">
              <div className="collab-modal-title">
                <div className="collab-modal-icon-badge">
                  <UserPlus size={18} />
                </div>
                <div>
                  <h2>Invite Manager / Share Dashboard</h2>
                  <span className="collab-modal-sub">Grant delegated view or customization rights with safety locks</span>
                </div>
              </div>
              <button
                type="button"
                className="collab-modal-close"
                onClick={() => setShowInviteModal(false)}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSendInvite} className="collab-modal-form">
              <div className="collab-modal-body">
                <div className="collab-form-group">
                  <label className="collab-field-label">
                    <span>Dashboard Selection</span>
                    <span className="collab-label-badge">Active View</span>
                  </label>
                  <div className="collab-input-wrapper">
                    <span className="collab-input-prefix">
                      <LayoutDashboard size={16} />
                    </span>
                    <input
                      type="text"
                      className="collab-input with-prefix"
                      value={inviteData.dashboardName}
                      onChange={(e) => setInviteData({ ...inviteData, dashboardName: e.target.value })}
                      required
                    />
                  </div>
                </div>

                <div className="collab-form-group">
                  <label className="collab-field-label">
                    <span>Manager / Colleague Email</span>
                    <span className="collab-label-required">*Required</span>
                  </label>
                  <div className="collab-input-wrapper">
                    <span className="collab-input-prefix">
                      <Mail size={16} />
                    </span>
                    <input
                      type="email"
                      className="collab-input with-prefix"
                      placeholder="manager@company.com"
                      value={inviteData.managerEmail}
                      onChange={(e) => setInviteData({ ...inviteData, managerEmail: e.target.value })}
                      required
                    />
                  </div>
                  <div className="collab-info-notice">
                    <AlertCircle size={14} className="collab-notice-icon" />
                    <span>If the account exists, access is granted immediately. If not, access will be linked automatically when they register with this email.</span>
                  </div>
                </div>

                <div className="collab-perm-section">
                  <div className="collab-perm-section-label">Permission Policy</div>
                  <div className="collab-perm-grid">
                    <label className={`collab-perm-toggle-card ${inviteData.canCustomize ? 'is-active' : ''}`}>
                      <div className="collab-perm-toggle-header">
                        <div className="collab-perm-toggle-info">
                          <div className="collab-perm-name">Allow Dashboard Customization</div>
                          <div className="collab-perm-desc">Manager can rearrange, add, or resize widgets and charts. Uncheck for strict View-Only.</div>
                        </div>
                        <div className={`collab-custom-checkbox ${inviteData.canCustomize ? 'checked' : ''}`}>
                          {inviteData.canCustomize && <Check size={13} strokeWidth={2.5} />}
                        </div>
                        <input
                          type="checkbox"
                          className="collab-sr-only"
                          checked={inviteData.canCustomize}
                          onChange={(e) => setInviteData({ ...inviteData, canCustomize: e.target.checked })}
                        />
                      </div>
                      <div className="collab-perm-status-bar">
                        {inviteData.canCustomize ? (
                          <span className="collab-perm-pill green">
                            <Sparkles size={11} /> Customizable Access
                          </span>
                        ) : (
                          <span className="collab-perm-pill gray">
                            <Lock size={11} /> Strict View-Only
                          </span>
                        )}
                      </div>
                    </label>

                    <label className={`collab-perm-toggle-card ${inviteData.canInvite ? 'is-active' : ''}`}>
                      <div className="collab-perm-toggle-header">
                        <div className="collab-perm-toggle-info">
                          <div className="collab-perm-name">Allow Sub-Delegation (Can Invite Others)</div>
                          <div className="collab-perm-desc">Allows manager to share this view with department leads or analysts.</div>
                        </div>
                        <div className={`collab-custom-checkbox ${inviteData.canInvite ? 'checked' : ''}`}>
                          {inviteData.canInvite && <Check size={13} strokeWidth={2.5} />}
                        </div>
                        <input
                          type="checkbox"
                          className="collab-sr-only"
                          checked={inviteData.canInvite}
                          onChange={(e) => setInviteData({ ...inviteData, canInvite: e.target.checked })}
                        />
                      </div>
                      <div className="collab-perm-status-bar">
                        {inviteData.canInvite ? (
                          <span className="collab-perm-pill green">
                            <UserPlus size={11} /> Can Invite Others
                          </span>
                        ) : (
                          <span className="collab-perm-pill gray">
                            <Lock size={11} /> Sub-inviting Disabled
                          </span>
                        )}
                      </div>
                    </label>
                  </div>
                </div>

                <div className="collab-security-banner">
                  <div className="collab-security-icon-wrap">
                    <ShieldCheck size={18} />
                  </div>
                  <div>
                    <strong>Protected Deletion Policy</strong>
                    <p>Collaborators are strictly prohibited from deleting shared dashboards. Only you, the creator, maintain deletion rights.</p>
                  </div>
                </div>
              </div>

              <div className="collab-modal-footer">
                <button
                  type="button"
                  className="collab-btn-cancel"
                  onClick={() => setShowInviteModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="collab-btn primary"
                  disabled={submittingInvite}
                >
                  {submittingInvite ? 'Sharing...' : 'Send Invitation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editingShare && (
        <div className="collab-modal-overlay" onClick={() => setEditingShare(null)}>
          <div className="collab-modal" onClick={(e) => e.stopPropagation()}>
            <div className="collab-modal-header">
              <div className="collab-modal-title">
                <div className="collab-modal-icon-badge">
                  <Shield size={18} />
                </div>
                <div>
                  <h2>Update Permissions</h2>
                  <span className="collab-modal-sub">{editingShare.sharedWithEmail}</span>
                </div>
              </div>
              <button
                type="button"
                className="collab-modal-close"
                onClick={() => setEditingShare(null)}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSavePermissions} className="collab-modal-form">
              <div className="collab-modal-body">
                <div className="collab-perm-section">
                  <div className="collab-perm-grid">
                    <label className={`collab-perm-toggle-card ${editingShare.canCustomize ? 'is-active' : ''}`}>
                      <div className="collab-perm-toggle-header">
                        <div className="collab-perm-toggle-info">
                          <div className="collab-perm-name">Allow Dashboard Customization</div>
                          <div className="collab-perm-desc">Manager can modify visual widgets and chart configurations.</div>
                        </div>
                        <div className={`collab-custom-checkbox ${editingShare.canCustomize ? 'checked' : ''}`}>
                          {editingShare.canCustomize && <Check size={13} strokeWidth={2.5} />}
                        </div>
                        <input
                          type="checkbox"
                          className="collab-sr-only"
                          checked={editingShare.canCustomize}
                          onChange={(e) => setEditingShare({ ...editingShare, canCustomize: e.target.checked })}
                        />
                      </div>
                      <div className="collab-perm-status-bar">
                        {editingShare.canCustomize ? (
                          <span className="collab-perm-pill green">
                            <Sparkles size={11} /> Customizable Access
                          </span>
                        ) : (
                          <span className="collab-perm-pill gray">
                            <Lock size={11} /> Strict View-Only
                          </span>
                        )}
                      </div>
                    </label>

                    <label className={`collab-perm-toggle-card ${editingShare.canInvite ? 'is-active' : ''}`}>
                      <div className="collab-perm-toggle-header">
                        <div className="collab-perm-toggle-info">
                          <div className="collab-perm-name">Allow Sub-Delegation</div>
                          <div className="collab-perm-desc">Manager can invite other stakeholders.</div>
                        </div>
                        <div className={`collab-custom-checkbox ${editingShare.canInvite ? 'checked' : ''}`}>
                          {editingShare.canInvite && <Check size={13} strokeWidth={2.5} />}
                        </div>
                        <input
                          type="checkbox"
                          className="collab-sr-only"
                          checked={editingShare.canInvite}
                          onChange={(e) => setEditingShare({ ...editingShare, canInvite: e.target.checked })}
                        />
                      </div>
                      <div className="collab-perm-status-bar">
                        {editingShare.canInvite ? (
                          <span className="collab-perm-pill green">
                            <UserPlus size={11} /> Can Invite Others
                          </span>
                        ) : (
                          <span className="collab-perm-pill gray">
                            <Lock size={11} /> Sub-inviting Disabled
                          </span>
                        )}
                      </div>
                    </label>
                  </div>
                </div>
              </div>

              <div className="collab-modal-footer">
                <button
                  type="button"
                  className="collab-btn-cancel"
                  onClick={() => setEditingShare(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="collab-btn primary"
                  disabled={actionLoadingId === editingShare.id}
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
