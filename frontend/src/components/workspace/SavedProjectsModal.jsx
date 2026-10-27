import React, { useState, useEffect, useCallback } from 'react';
import {
  listProjects, saveProject, openProject, renameProject, deleteProject, getErrorMessage
} from '../../services/api';
import {
  Folder, Plus, Check, Trash2, Edit2, X, AlertCircle, FileSpreadsheet, RefreshCw
} from '../icons/Icons';

export default function SavedProjectsModal({
  isOpen,
  onClose,
  currentUser,
  loaded,
  fileName,
  activeSheetName,
  onProjectLoaded,
  onRequestAuth
}) {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [saveSuccess, setSaveSuccess] = useState(null);

  const [saveName, setSaveName] = useState('');
  const [saveDesc, setSaveDesc] = useState('');
  const [saving, setSaving] = useState(false);

  const [editingId, setEditingId] = useState(null);
  const [renameValue, setRenameValue] = useState('');

  const fetchProjects = useCallback(async () => {
    if (!currentUser) return;
    setLoading(true);
    setError(null);
    try {
      const res = await listProjects();
      setProjects(res.data || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to load projects'));
    } finally {
      setLoading(false);
    }
  }, [currentUser]);

  useEffect(() => {
    if (isOpen) {
      if (!currentUser) {
        return;
      }
      fetchProjects();
      if (fileName) {
        setSaveName(fileName.replace(/\.[^.]+$/, '') + ' Analysis');
      }
    }
  }, [isOpen, currentUser, fileName, fetchProjects]);

  if (!isOpen) return null;

  const handleSaveCurrent = async (e) => {
    e.preventDefault();
    if (!saveName.trim()) return;
    setSaving(true);
    setError(null);
    setSaveSuccess(null);
    try {
      await saveProject(saveName.trim(), saveDesc.trim(), activeSheetName);
      setSaveSuccess('Project saved successfully to persistent database!');
      setSaveDesc('');
      fetchProjects();
      setTimeout(() => setSaveSuccess(null), 3000);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to save project'));
    } finally {
      setSaving(false);
    }
  };

  const handleOpen = async (projectId) => {
    setLoading(true);
    setError(null);
    try {
      const res = await openProject(projectId);
      if (res.data?.preview) {
        onProjectLoaded(res.data.preview, res.data.activeSheetName);
        onClose();
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to open project'));
    } finally {
      setLoading(false);
    }
  };

  const handleStartRename = (p) => {
    setEditingId(p.id);
    setRenameValue(p.name);
  };

  const handleSaveRename = async (projectId) => {
    if (!renameValue.trim()) return;
    try {
      await renameProject(projectId, renameValue.trim());
      setEditingId(null);
      fetchProjects();
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to rename project'));
    }
  };

  const handleDelete = async (projectId, projectName) => {
    if (!window.confirm(`Are you sure you want to delete project "${projectName}"?`)) return;
    try {
      await deleteProject(projectId);
      fetchProjects();
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to delete project'));
    }
  };

  return (
    <div className="auth-overlay">
      <div className="auth-card projects-modal-card">
        <button
          className="modal-close-btn btn-icon"
          onClick={onClose}
          aria-label="Close modal"
        >
          <X size={15} />
        </button>

        <div className="projects-modal-header">
          <div className="projects-modal-icon">
            <Folder size={20} className="text-emerald" />
          </div>
          <div>
            <h3 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 2px 0', color: 'var(--text-primary)' }}>
              Saved Projects & Persistence
            </h3>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
              {currentUser ? `Workspace of ${currentUser.name} (${currentUser.email})` : 'Multi-tenant Project Storage'}
            </p>
          </div>
        </div>

        <div>
          {!currentUser ? (
            <div className="card p-24 text-center">
              <Folder size={32} className="text-emerald mb-12" style={{ margin: '0 auto' }} />
              <h4>Authentication Required</h4>
              <p className="text-secondary" style={{ fontSize: 13, maxWidth: 440, margin: '8px auto 16px' }}>
                Sign in or register to securely save workbooks, analysis pipelines, and custom transformations across sessions and backend restarts.
              </p>
              <button
                className="btn btn-primary"
                onClick={() => {
                  onClose();
                  onRequestAuth();
                }}
              >
                Sign In to Access Projects
              </button>
            </div>
          ) : (
            <>
              {error && (
                <div className="alert alert-error mb-16 flex-gap">
                  <AlertCircle size={15} />
                  <span style={{ fontSize: 12 }}>{error}</span>
                </div>
              )}

              {saveSuccess && (
                <div className="alert alert-success mb-16 flex-gap" style={{ background: 'rgba(16,185,129,0.1)', color: 'var(--emerald)' }}>
                  <Check size={15} />
                  <span style={{ fontSize: 12 }}>{saveSuccess}</span>
                </div>
              )}

              {loaded && (
                <div style={{ background: 'var(--surface-secondary, rgba(255,255,255,0.03))', border: '1px solid var(--border)', borderRadius: 10, padding: '14px 16px', marginBottom: 20 }}>
                  <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-primary)' }}>
                    <Plus size={14} className="text-emerald" /> Save Active Session as Project
                  </h4>
                  <form onSubmit={handleSaveCurrent}>
                    <div className="projects-save-form">
                      <input
                        type="text"
                        className="input-field"
                        placeholder="Project Name..."
                        value={saveName}
                        onChange={(e) => setSaveName(e.target.value)}
                        required
                        disabled={saving}
                        style={{ height: 36, fontSize: 13 }}
                      />
                      <input
                        type="text"
                        className="input-field"
                        placeholder="Description (optional)"
                        value={saveDesc}
                        onChange={(e) => setSaveDesc(e.target.value)}
                        disabled={saving}
                        style={{ height: 36, fontSize: 13 }}
                      />
                      <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={saving || !saveName.trim()}
                        style={{ height: 36, padding: '0 16px', fontSize: 13 }}
                      >
                        {saving ? 'Saving...' : 'Save'}
                      </button>
                    </div>
                  </form>
                </div>
              )}

              <div className="projects-list-header flex-between mb-12">
                <span style={{ fontSize: 13, fontWeight: 600 }}>
                  Your Saved Projects ({projects.length})
                </span>
                <button
                  className="btn btn-xs btn-outline"
                  onClick={fetchProjects}
                  disabled={loading}
                >
                  <RefreshCw size={12} className={loading ? 'spin' : ''} /> Refresh
                </button>
              </div>

              {loading ? (
                <div className="text-center p-24">
                  <div className="spinner mb-8" style={{ margin: '0 auto' }} />
                  <span className="text-secondary" style={{ fontSize: 12 }}>Loading projects from database...</span>
                </div>
              ) : projects.length === 0 ? (
                <div className="card p-24 text-center">
                  <p className="text-secondary" style={{ fontSize: 13, margin: 0 }}>
                    No saved projects found. {loaded ? 'Save your current workbook above to keep it permanently.' : 'Upload an Excel file to start your first project.'}
                  </p>
                </div>
              ) : (
                <div className="projects-list" style={{ maxHeight: 280, overflowY: 'auto' }}>
                  {projects.map((p) => (
                    <div
                      key={p.id}
                      className="projects-card-item flex-between"
                    >
                      <div style={{ flex: 1, minWidth: 0, marginRight: 12 }}>
                        {editingId === p.id ? (
                          <div className="flex-gap">
                            <input
                              type="text"
                              className="input-field"
                              value={renameValue}
                              onChange={(e) => setRenameValue(e.target.value)}
                              autoFocus
                              style={{ height: 30, fontSize: 12 }}
                            />
                            <button
                              className="btn btn-xs btn-primary"
                              onClick={() => handleSaveRename(p.id)}
                            >
                              Save
                            </button>
                            <button
                              className="btn btn-xs btn-outline"
                              onClick={() => setEditingId(null)}
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <div>
                            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>
                              {p.name}
                            </div>
                            <div className="flex-gap mt-4" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                                <FileSpreadsheet size={11} className="text-emerald" /> {p.fileName}
                              </span>
                              <span>•</span>
                              <span>{p.rowCount?.toLocaleString() || 0} rows</span>
                              <span>•</span>
                              <span>{p.columnCount || 0} cols</span>
                              <span>•</span>
                              <span>{new Date(p.updatedAt).toLocaleDateString()}</span>
                            </div>
                            {p.description && (
                              <p className="text-secondary mt-4" style={{ fontSize: 11, margin: '2px 0 0 0' }}>
                                {p.description}
                              </p>
                            )}
                          </div>
                        )}
                      </div>

                      <div className="flex-gap">
                        <button
                          className="btn btn-xs btn-primary"
                          onClick={() => handleOpen(p.id)}
                          disabled={loading}
                          title="Open project into active workspace"
                        >
                          Open
                        </button>
                        <button
                          className="btn-icon"
                          onClick={() => handleStartRename(p)}
                          title="Rename project"
                        >
                          <Edit2 size={13} />
                        </button>
                        <button
                          className="btn-icon"
                          onClick={() => handleDelete(p.id, p.name)}
                          title="Delete project"
                          style={{ color: '#ef4444' }}
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
