import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  uploadExcel, getPreview, getAllData,
  removeDuplicates, handleMissing, cleanStrings, replaceValues,
  renameColumn, removeColumn, convertType, transformPipeline,
  previewCleaning, previewPipeline, undoWorkbook, redoWorkbook,
  getErrorMessage
} from '../../services/api';
import {
  Upload, FileSpreadsheet, Sliders, Check, AlertCircle,
  Table2, ArrowRight, RefreshCw, X, Layers, Undo, Redo, Play, Folder, Search, Zap
} from '../icons/Icons';
import ChangePreviewModal from '../workspace/ChangePreviewModal';
import { useVirtualizer } from '@tanstack/react-virtual';

export default function DataWorkspace({
  fileInfo,
  activeSheet,
  setActiveSheet,
  workspaceFiles = [],
  onSelectFile,
  onDeleteFile,
  onUploadSuccess,
  onRefresh,
  refreshKey,
  onOpenProjects,
  currentUser,
  onRequestAuth
}) {
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [viewMode, setViewMode] = useState(() => {
    try {
      return localStorage.getItem('tabufinery_view_mode') || 'preview';
    } catch {
      return 'preview';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('tabufinery_view_mode', viewMode);
    } catch {}
  }, [viewMode]);

  const sheetDataCacheRef = useRef({});

  const [searchQuery, setSearchQuery] = useState('');
  const [sortColumn, setSortColumn] = useState(null);
  const [sortDirection, setSortDirection] = useState('asc');

  const rawRows = preview?.rows || [];

  const processedRows = useMemo(() => {
    if (!rawRows.length) return [];
    let list = rawRows;
    const q = searchQuery.trim().toLowerCase();
    if (q) {
      list = list.filter(row => {
        for (const k in row) {
          const v = row[k];
          if (v != null && String(v).toLowerCase().includes(q)) return true;
        }
        return false;
      });
    }
    if (sortColumn) {
      const isAsc = sortDirection === 'asc';
      list = [...list].sort((a, b) => {
        const va = a[sortColumn];
        const vb = b[sortColumn];
        if (va == null && vb == null) return 0;
        if (va == null) return 1;
        if (vb == null) return -1;
        const na = Number(va);
        const nb = Number(vb);
        if (!isNaN(na) && !isNaN(nb)) {
          return isAsc ? na - nb : nb - na;
        }
        const sa = String(va).toLowerCase();
        const sb = String(vb).toLowerCase();
        return isAsc ? sa.localeCompare(sb) : sb.localeCompare(sa);
      });
    }
    return list;
  }, [rawRows, searchQuery, sortColumn, sortDirection]);

  const displayRows = useMemo(() => {
    return viewMode === 'preview' ? processedRows.slice(0, 100) : processedRows;
  }, [processedRows, viewMode]);

  const tableContainerRef = useRef(null);
  const rowVirtualizer = useVirtualizer({
    count: viewMode === 'full' ? processedRows.length : 0,
    getScrollElement: () => tableContainerRef.current,
    estimateSize: () => 37,
    overscan: 12,
    useFlushSync: false,
  });

  const virtualRows = rowVirtualizer.getVirtualItems();
  const totalVirtualHeight = rowVirtualizer.getTotalSize();
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom = virtualRows.length > 0
    ? totalVirtualHeight - virtualRows[virtualRows.length - 1].end
    : 0;

  useEffect(() => {
    setSearchQuery('');
    setSortColumn(null);
  }, [activeSheet?.sheetName]);

  useEffect(() => {
    if (tableContainerRef.current) {
      tableContainerRef.current.scrollTop = 0;
    }
  }, [viewMode, activeSheet?.sheetName]);

  useEffect(() => {
    sheetDataCacheRef.current = {};
  }, [refreshKey, fileInfo?.fileId]);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyToast, setHistoryToast] = useState(null);

  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewApplying, setPreviewApplying] = useState(false);
  const [pendingApplyAction, setPendingApplyAction] = useState(null);

  const [cleaningPanel, setCleaningPanel] = useState(false);
  const [cleanOp, setCleanOp] = useState('duplicates');
  const [cleanCol, setCleanCol] = useState('');
  const [cleanResult, setCleanResult] = useState(null);
  const [cleanError, setCleanError] = useState(null);
  const [dupStrategy, setDupStrategy] = useState('KEEP_FIRST');
  const [missingStrategy, setMissingStrategy] = useState('DROP_ROW');
  const [fillValue, setFillValue] = useState('');
  const [oldVal, setOldVal] = useState('');
  const [newVal, setNewVal] = useState('');
  const [targetType, setTargetType] = useState('STRING');

  const [pipeline, setPipeline] = useState([]);

  const loaded = !!fileInfo;
  const sheetName = activeSheet?.sheetName || null;
  const headers = activeSheet?.headers || [];

  useEffect(() => {
    if (headers && headers.length > 0 && !cleanCol) {
      setCleanCol(headers[0]);
    }
  }, [headers, cleanCol]);

  const loadData = useCallback(async () => {
    if (!sheetName) return;
    const cached = sheetDataCacheRef.current[sheetName];

    if (viewMode === 'full') {
      if (cached?.fullData) {
        setPreview(cached.fullData);
        setLoadingPreview(false);
        return;
      }
      setLoadingPreview(true);
      try {
        const res = await getAllData(sheetName);
        if (!sheetDataCacheRef.current[sheetName]) {
          sheetDataCacheRef.current[sheetName] = {};
        }
        sheetDataCacheRef.current[sheetName].fullData = res.data;
        setPreview(res.data);
      } catch (err) {
        setPreview(null);
      } finally {
        setLoadingPreview(false);
      }
    } else {
      if (cached?.fullData) {
        setPreview(cached.fullData);
        setLoadingPreview(false);
        return;
      }
      if (cached?.previewData) {
        setPreview(cached.previewData);
        setLoadingPreview(false);
        return;
      }
      setLoadingPreview(true);
      try {
        const res = await getPreview(sheetName, 100);
        if (!sheetDataCacheRef.current[sheetName]) {
          sheetDataCacheRef.current[sheetName] = {};
        }
        sheetDataCacheRef.current[sheetName].previewData = res.data;
        setPreview(res.data);
      } catch (err) {
        setPreview(null);
      } finally {
        setLoadingPreview(false);
      }
    }
  }, [sheetName, viewMode]);

  useEffect(() => {
    loadData();
  }, [loadData, refreshKey]);

  const handleHeaderSort = (header) => {
    if (sortColumn === header) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else {
        setSortColumn(null);
        setSortDirection('asc');
      }
    } else {
      setSortColumn(header);
      setSortDirection('asc');
    }
  };

  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
        e.preventDefault();
        if (e.shiftKey) {
          handleRedo();
        } else {
          handleUndo();
        }
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
        e.preventDefault();
        handleRedo();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [fileInfo?.canUndo, fileInfo?.canRedo]);

  const handleFile = useCallback(async (file) => {
    if (!file) return;
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    if (!file.name.endsWith('.xlsx')) {
      setUploadError('Only .xlsx files are supported by the engine.');
      return;
    }
    setUploadError(null);
    setUploading(true);
    try {
      const res = await uploadExcel(file);
      onUploadSuccess(res.data);
      if (onRefresh) onRefresh();
    } catch (err) {
      if (err.response?.status === 401) {
        if (onRequestAuth) onRequestAuth();
      } else {
        setUploadError(getErrorMessage(err, 'File upload failed. Please ensure file is a valid .xlsx spreadsheet.'));
      }
    } finally {
      setUploading(false);
    }
  }, [currentUser, onRequestAuth, onUploadSuccess]);

  const handleUndo = async () => {
    if (!fileInfo?.canUndo || historyLoading) return;
    setHistoryLoading(true);
    try {
      const res = await undoWorkbook();
      setHistoryToast({ type: 'info', message: res.data?.message || 'Undid last change' });
      onRefresh();
    } catch (err) {
      setHistoryToast({ type: 'error', message: getErrorMessage(err, 'Failed to undo previous change.') });
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleRedo = async () => {
    if (!fileInfo?.canRedo || historyLoading) return;
    setHistoryLoading(true);
    try {
      const res = await redoWorkbook();
      setHistoryToast({ type: 'info', message: res.data?.message || 'Redone last change' });
      onRefresh();
    } catch (err) {
      setHistoryToast({ type: 'error', message: getErrorMessage(err, 'Failed to redo change.') });
    } finally {
      setHistoryLoading(false);
    }
  };

  const buildCurrentRequest = () => {
    const base = { sheetName };
    switch (cleanOp) {
      case 'duplicates':
        return { ...base, operation: 'REMOVE_DUPLICATES', duplicateStrategy: dupStrategy };
      case 'missing':
        return { ...base, operation: 'HANDLE_MISSING_VALUES', columnName: cleanCol, missingValueStrategy: missingStrategy, value: missingStrategy === 'FILL_CONSTANT' ? fillValue : undefined };
      case 'trim':
        return { ...base, operation: 'TRIM', columnName: cleanCol };
      case 'lowercase':
        return { ...base, operation: 'LOWERCASE', columnName: cleanCol };
      case 'uppercase':
        return { ...base, operation: 'UPPERCASE', columnName: cleanCol };
      case 'replace':
        return { ...base, operation: 'REPLACE_VALUE', columnName: cleanCol, oldValue: oldVal, newValue: newVal };
      case 'rename':
        return { ...base, operation: 'RENAME_COLUMN', columnName: cleanCol, newValue: newVal };
      case 'remove':
        return { ...base, operation: 'REMOVE_COLUMN', columnName: cleanCol };
      case 'convert':
        return { ...base, operation: 'CONVERT_TYPE', columnName: cleanCol, targetType };
      default:
        return null;
    }
  };

  const handleRequestPreview = async () => {
    const req = buildCurrentRequest();
    if (!req) return;
    setCleanError(null);
    setPreviewLoading(true);

    try {
      const res = await previewCleaning(req);
      setPreviewData(res.data);
      setPendingApplyAction(() => async () => {
        let applyRes;
        switch (cleanOp) {
          case 'duplicates': applyRes = await removeDuplicates(req); break;
          case 'missing': applyRes = await handleMissing(req); break;
          case 'trim': applyRes = await cleanStrings(req); break;
          case 'lowercase': applyRes = await cleanStrings(req); break;
          case 'uppercase': applyRes = await cleanStrings(req); break;
          case 'replace': applyRes = await replaceValues(req); break;
          case 'rename': applyRes = await renameColumn(req); break;
          case 'remove': applyRes = await removeColumn(req); break;
          case 'convert': applyRes = await convertType(req); break;
        }
        return applyRes?.data;
      });
      setPreviewModalOpen(true);
    } catch (err) {
      setCleanError(getErrorMessage(err, 'Preview generation failed. Please check column settings.'));
    } finally {
      setPreviewLoading(false);
    }
  };

  const handlePipelinePreview = async () => {
    if (pipeline.length === 0) return;
    setCleanError(null);
    setPreviewLoading(true);

    try {
      const res = await previewPipeline(pipeline);
      setPreviewData(res.data);
      setPendingApplyAction(() => async () => {
        const applyRes = await transformPipeline(pipeline);
        const last = applyRes.data[applyRes.data.length - 1];
        setPipeline([]);
        return {
          message: `Pipeline executed: ${applyRes.data.length} atomic transformations applied.`,
          rowsBefore: applyRes.data[0]?.rowsBefore,
          rowsAfter: last?.rowsAfter,
          rowsRemoved: applyRes.data.reduce((acc, r) => acc + (r.rowsRemoved || 0), 0),
          cellsChanged: applyRes.data.reduce((acc, r) => acc + (r.cellsChanged || 0), 0),
        };
      });
      setPreviewModalOpen(true);
    } catch (err) {
      setCleanError(getErrorMessage(err, 'Pipeline preview failed.'));
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleConfirmApply = async () => {
    if (!pendingApplyAction) return;
    setPreviewApplying(true);
    try {
      const result = await pendingApplyAction();
      setCleanResult(result);
      setPreviewModalOpen(false);
      setPendingApplyAction(null);
      setHistoryToast({ type: 'success', message: result?.message || 'Changes applied successfully.' });
      onRefresh();
    } catch (err) {
      setCleanError(getErrorMessage(err, 'Failed to apply transformation. Please verify your data.'));
    } finally {
      setPreviewApplying(false);
    }
  };

  const addToPipeline = () => {
    const req = buildCurrentRequest();
    if (!req) return;
    setPipeline(prev => [...prev, req]);
  };

  const needsCol = ['missing', 'trim', 'lowercase', 'uppercase', 'replace', 'rename', 'remove', 'convert'].includes(cleanOp);

  return (
    <div className="workspace-page">
      <div className="page-header flex-between mb-20">
        <div>
          <h1>Data Workspace</h1>
          <p>Direct workbook ingestion, live tabular inspection, change preview, and undo/redo history.</p>
        </div>

        {loaded && (
          <div className="flex-gap undo-redo-toolbar">
            <button
              className="btn btn-sm btn-outline flex-gap"
              onClick={handleUndo}
              disabled={!fileInfo?.canUndo || historyLoading}
              title={fileInfo?.undoAction ? `Undo: ${fileInfo.undoAction} (Ctrl+Z)` : 'Undo (Ctrl+Z)'}
            >
              <Undo size={14} />
              <span>Undo</span>
              {fileInfo?.canUndo && fileInfo?.undoAction && (
                <span className="badge badge-neutral" style={{ fontSize: 10, padding: '1px 5px' }}>
                  {fileInfo.undoAction}
                </span>
              )}
            </button>

            <button
              className="btn btn-sm btn-outline flex-gap"
              onClick={handleRedo}
              disabled={!fileInfo?.canRedo || historyLoading}
              title={fileInfo?.redoAction ? `Redo: ${fileInfo.redoAction} (Ctrl+Y)` : 'Redo (Ctrl+Y)'}
            >
              <Redo size={14} />
              <span>Redo</span>
              {fileInfo?.canRedo && fileInfo?.redoAction && (
                <span className="badge badge-neutral" style={{ fontSize: 10, padding: '1px 5px' }}>
                  {fileInfo.redoAction}
                </span>
              )}
            </button>

            <button
              className="btn btn-sm btn-outline flex-gap"
              onClick={onOpenProjects}
              title="Save current workbook or load a project"
            >
              <Folder size={14} className="text-emerald" />
              <span>Save Project</span>
            </button>
          </div>
        )}
      </div>

      {historyToast && (
        <div className={`alert alert-${historyToast.type === 'error' ? 'error' : 'info'} mb-16 flex-between`}>
          <div className="flex-gap">
            <AlertCircle size={15} />
            <span>{historyToast.message}</span>
          </div>
          <button className="btn-ghost-sm" onClick={() => setHistoryToast(null)}>
            <X size={13} />
          </button>
        </div>
      )}

      {currentUser && workspaceFiles && workspaceFiles.length > 0 && (
        <div className="card mb-20">
          <div className="card-header flex-between">
            <div className="flex-gap">
              <FileSpreadsheet size={16} className="text-emerald" />
              <span className="card-title">User Workspace Workbooks ({workspaceFiles.length}/20)</span>
            </div>
            <span className="text-xs text-secondary">Switch between uploaded workbooks without losing session data</span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
            {workspaceFiles.map(file => (
              <div
                key={file.fileId}
                style={{
                  border: file.active ? '2px solid #10b981' : '1px solid var(--border-color)',
                  borderRadius: 8,
                  padding: 14,
                  background: file.active ? 'rgba(16, 185, 129, 0.05)' : 'var(--bg-card)',
                  transition: 'all 0.2s ease'
                }}
              >
                <div className="flex-between mb-8">
                  <div className="flex-gap" style={{ overflow: 'hidden' }}>
                    <FileSpreadsheet size={16} className={file.active ? 'text-emerald' : 'text-secondary'} />
                    <span style={{ fontWeight: 600, fontSize: 13, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }} title={file.fileName}>
                      {file.fileName}
                    </span>
                  </div>
                  {file.active && <span className="badge badge-success" style={{ fontSize: 10 }}>Active</span>}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 12 }}>
                  {file.sheetCount} {file.sheetCount === 1 ? 'sheet' : 'sheets'} ({file.sheetNames?.join(', ') || ''}) · {file.totalRows?.toLocaleString()} rows
                </div>
                <div className="flex-between">
                  {!file.active ? (
                    <button
                      className="btn btn-xs btn-primary"
                      onClick={() => onSelectFile && onSelectFile(file.fileId)}
                      style={{ fontSize: 11 }}
                    >
                      Switch to this
                    </button>
                  ) : (
                    <span style={{ fontSize: 11, color: '#10b981', fontWeight: 600 }}>Active Dataset</span>
                  )}
                  <button
                    className="btn btn-xs btn-outline"
                    onClick={() => {
                      if (window.confirm(`Delete '${file.fileName}' from your workspace?`)) {
                        if (onDeleteFile) onDeleteFile(file.fileId);
                      }
                    }}
                    title="Remove workbook from workspace"
                    style={{ fontSize: 11, color: 'var(--text-danger, #ef4444)' }}
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card mb-20">
        <div className="card-header flex-between">
          <div className="flex-gap">
            <Upload size={16} className="text-emerald" />
            <span className="card-title">Dataset Ingestion</span>
          </div>
          <div className="flex-gap">
            <span className="badge badge-neutral">Max 10MB</span>
            <span className="badge badge-info">.xlsx OpenXML</span>
            {loaded && <span className="badge badge-success">Active: {fileInfo.fileName}</span>}
          </div>
        </div>

        {uploadError && (
          <div className="alert alert-error mb-12">
            <AlertCircle size={15} />
            <span>{uploadError}</span>
          </div>
        )}

        <div
          className={`upload-zone ${dragging ? 'dragover' : ''}`}
          onDragOver={e => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => {
            e.preventDefault();
            setDragging(false);
            if (!currentUser) {
              if (onRequestAuth) onRequestAuth();
              return;
            }
            handleFile(e.dataTransfer.files[0]);
          }}
          onClick={() => {
            if (!currentUser) {
              if (onRequestAuth) onRequestAuth();
              return;
            }
            document.getElementById('file-input').click();
          }}
        >
          <input id="file-input" type="file" accept=".xlsx" onChange={e => handleFile(e.target.files[0])} />
          <div className="upload-icon-circle">
            <Upload size={24} className="text-emerald" />
          </div>
          {uploading ? (
            <div className="loading-text">
              <div className="spinner" />
              <span>Parsing sheets and generating data preview...</span>
            </div>
          ) : (
            <div>
              <div className="upload-title">Drop your Excel file (.xlsx) here or click to browse</div>
              <div className="upload-desc">Deterministic POI parsing · In-memory session · Instant column recognition</div>
            </div>
          )}
        </div>
      </div>

      {loaded && fileInfo.sheets?.length > 1 && (
        <div className="card mb-20">
          <div className="flex-between">
            <span className="text-xs text-secondary font-semibold uppercase tracking-wider">Workbook Sheets</span>
            <span className="text-xs text-secondary">{fileInfo.sheets.length} sheets in memory</span>
          </div>
          <div className="pill-tabs mt-8">
            {fileInfo.sheets.map(s => (
              <button
                key={s.sheetName}
                className={`pill-tab ${activeSheet?.sheetName === s.sheetName ? 'active' : ''}`}
                onClick={() => setActiveSheet(s)}
              >
                <Table2 size={13} />
                <span>{s.sheetName}</span>
                <span className="pill-count">{s.rowCount?.toLocaleString()} rows</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {loaded && (
        <div className="card mb-20">
          <div className="card-header flex-between" style={{ flexWrap: 'wrap', gap: 12 }}>
            <div>
              <h3 className="card-title">Spreadsheet Preview — {activeSheet?.sheetName}</h3>
              {preview && (
                <p className="card-subtitle">
                  Rendering {viewMode === 'full' ? `${processedRows.length.toLocaleString()} (virtualized)` : `${displayRows.length.toLocaleString()} (preview)`} of {preview.totalRows?.toLocaleString()} rows · {preview.headers?.length} detected columns
                  {searchQuery && (
                    <span style={{ color: 'var(--emerald)', marginLeft: 6 }}>
                      (Filtered from {rawRows.length.toLocaleString()} rows)
                    </span>
                  )}
                  {sortColumn && (
                    <span style={{ color: 'var(--emerald)', marginLeft: 6 }}>
                      · Sorted by {sortColumn} ({sortDirection.toUpperCase()})
                    </span>
                  )}
                </p>
              )}
            </div>

            <div className="flex-gap" style={{ flexWrap: 'wrap' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  background: 'var(--bg-input, rgba(255, 255, 255, 0.04))',
                  border: '1px solid var(--border)',
                  borderRadius: 6,
                  padding: '4px 10px',
                }}
              >
                <Search size={13} className="text-secondary" style={{ flexShrink: 0 }} />
                <input
                  id="workspace-row-filter"
                  name="rowFilter"
                  type="search"
                  placeholder="Quick filter rows..."
                  aria-label="Quick filter rows"
                  autoComplete="off"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    outline: 'none',
                    color: 'var(--text-primary)',
                    fontSize: 12,
                    width: 140,
                  }}
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    style={{
                      background: 'none',
                      border: 'none',
                      padding: 0,
                      cursor: 'pointer',
                      color: 'var(--text-secondary)',
                      display: 'flex',
                      alignItems: 'center',
                    }}
                    title="Clear filter"
                  >
                    <X size={12} />
                  </button>
                )}
              </div>

              <div className="pill-tabs">
                <button
                  className={`pill-tab ${viewMode === 'preview' ? 'active' : ''}`}
                  onClick={() => setViewMode('preview')}
                  title="Inspect Top 100 rows preview"
                >
                  Top 100 Preview
                </button>
                <button
                  className={`pill-tab ${viewMode === 'full' ? 'active' : ''}`}
                  onClick={() => setViewMode('full')}
                  title={
                    (activeSheet?.rowCount || preview?.totalRows || 0) > 10000
                      ? 'Large dataset — virtualized rendering'
                      : 'Fetch all rows in current sheet'
                  }
                >
                  Full Sheet Data
                  {(activeSheet?.rowCount || preview?.totalRows || 0) > 10000 && (
                    <span style={{ fontSize: '10px', opacity: 0.85, marginLeft: '6px', display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                      <Zap size={10} /> Virtualized
                    </span>
                  )}
                </button>
              </div>

              <button
                className={`btn btn-sm ${cleaningPanel ? 'btn-primary' : 'btn-outline'}`}
                onClick={() => setCleaningPanel(!cleaningPanel)}
              >
                <Sliders size={14} /> {cleaningPanel ? 'Close Cleaning' : 'Clean & Transform'}
              </button>
            </div>
          </div>

          <div className="data-table-wrap">
            {loadingPreview && (
              <div className="data-table-loading-overlay">
                <div className="spinner" />
                <span>Fetching tabular records...</span>
              </div>
            )}

            <div
              className="data-table-scroll"
              ref={tableContainerRef}
            >
              <table
                className={`data-table ${viewMode === 'full' ? 'data-table-virtual' : ''}`}
                style={{ minWidth: Math.max(700, ((preview?.headers?.length || 0) * 160) + 60) }}
              >
                <colgroup>
                  <col style={{ width: 55, minWidth: 55, maxWidth: 55 }} />
                  {(preview?.headers || []).map(h => (
                    <col key={h} style={{ width: 170, minWidth: 140 }} />
                  ))}
                </colgroup>
                <thead>
                  <tr>
                    <th style={{ width: 55 }}>#</th>
                    {(preview?.headers || []).map(h => (
                      <th
                        key={h}
                        className="sortable-th"
                        onClick={() => handleHeaderSort(h)}
                        title={`Click to sort by ${h}`}
                      >
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                          <span>{h}</span>
                          {sortColumn === h && (
                            <span style={{ fontSize: 11, color: 'var(--emerald)' }}>
                              {sortDirection === 'asc' ? '▲' : '▼'}
                            </span>
                          )}
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {viewMode === 'preview' ? (

                    displayRows.length === 0 ? (
                      <tr>
                        <td
                          colSpan={(preview?.headers?.length || 0) + 1}
                          style={{ textAlign: 'center', padding: 28, color: 'var(--text-secondary)' }}
                        >
                          {searchQuery ? `No records matching "${searchQuery}"` : (loadingPreview ? 'Loading rows...' : 'No records available.')}
                        </td>
                      </tr>
                    ) : (
                      displayRows.map((row, i) => (
                        <tr key={i}>
                          <td className="row-num">{i + 1}</td>
                          {(preview?.headers || []).map(h => (
                            <td key={h} title={row[h] != null ? String(row[h]) : 'null'}>
                              {row[h] != null ? (
                                String(row[h])
                              ) : (
                                <span className="null-val">null</span>
                              )}
                            </td>
                          ))}
                        </tr>
                      ))
                    )
                  ) : (

                    processedRows.length === 0 ? (
                      <tr>
                        <td
                          colSpan={(preview?.headers?.length || 0) + 1}
                          style={{ textAlign: 'center', padding: 28, color: 'var(--text-secondary)' }}
                        >
                          {searchQuery ? `No records matching "${searchQuery}"` : (loadingPreview ? 'Loading full dataset...' : 'No records available.')}
                        </td>
                      </tr>
                    ) : (
                      <>
                        {paddingTop > 0 && (
                          <tr aria-hidden="true" style={{ pointerEvents: 'none' }}>
                            <td
                              style={{ height: `${paddingTop}px`, padding: 0, border: 'none' }}
                              colSpan={(preview?.headers?.length || 0) + 1}
                            />
                          </tr>
                        )}
                        {virtualRows.map((virtualRow) => {
                          const row = processedRows[virtualRow.index];
                          if (!row) return null;
                          return (
                            <tr key={virtualRow.key} data-index={virtualRow.index}>
                              <td className="row-num">{virtualRow.index + 1}</td>
                              {(preview?.headers || []).map(h => (
                                <td key={h} title={row[h] != null ? String(row[h]) : 'null'}>
                                  {row[h] != null ? (
                                    String(row[h])
                                  ) : (
                                    <span className="null-val">null</span>
                                  )}
                                </td>
                              ))}
                            </tr>
                          );
                        })}
                        {paddingBottom > 0 && (
                          <tr aria-hidden="true" style={{ pointerEvents: 'none' }}>
                            <td
                              style={{ height: `${paddingBottom}px`, padding: 0, border: 'none' }}
                              colSpan={(preview?.headers?.length || 0) + 1}
                            />
                          </tr>
                        )}
                      </>
                    )
                  )}
                </tbody>
              </table>
              </div>
            </div>
        </div>
      )}

      {cleaningPanel && loaded && (
        <div className="card">
          <div className="card-header flex-between">
            <div>
              <h3 className="card-title">Deterministic Cleaning Engine</h3>
              <p className="card-subtitle">Preview before/after differences and apply changes safely</p>
            </div>
            {pipeline.length > 0 && (
              <span className="badge badge-warning flex-gap">
                <Layers size={13} /> {pipeline.length} in pipeline
              </span>
            )}
          </div>

          <div className="pill-tabs mb-16">
            {[
              ['duplicates', 'Remove Duplicates'],
              ['missing', 'Missing Values'],
              ['trim', 'Trim Whitespace'],
              ['lowercase', 'To Lowercase'],
              ['uppercase', 'To Uppercase'],
              ['replace', 'Replace Value'],
              ['rename', 'Rename Column'],
              ['remove', 'Remove Column'],
              ['convert', 'Convert Type']
            ].map(([k, l]) => (
              <button
                key={k}
                className={`pill-tab ${cleanOp === k ? 'active' : ''}`}
                onClick={() => { setCleanOp(k); setCleanResult(null); setCleanError(null); }}
              >
                {l}
              </button>
            ))}
          </div>

          <div className="form-row mb-16">
            {needsCol && (
              <div className="form-group">
                <label className="form-label">Target Column</label>
                <select className="form-select" value={cleanCol} onChange={e => setCleanCol(e.target.value)}>
                  <option value="">Select column...</option>
                  {headers.map(h => <option key={h} value={h}>{h}</option>)}
                </select>
              </div>
            )}

            {cleanOp === 'duplicates' && (
              <div className="form-group">
                <label className="form-label">Strategy</label>
                <select className="form-select" value={dupStrategy} onChange={e => setDupStrategy(e.target.value)}>
                  <option value="KEEP_FIRST">Keep First Occurrence</option>
                  <option value="KEEP_LAST">Keep Last Occurrence</option>
                </select>
              </div>
            )}

            {cleanOp === 'missing' && (
              <>
                <div className="form-group">
                  <label className="form-label">Strategy</label>
                  <select className="form-select" value={missingStrategy} onChange={e => setMissingStrategy(e.target.value)}>
                    <option value="DROP_ROW">Drop Row with Missing Value</option>
                    <option value="FILL_CONSTANT">Fill Constant Value</option>
                    <option value="FILL_NUMERIC_ZERO">Fill Numeric Zero (0)</option>
                    <option value="FORWARD_FILL">Forward Fill (Previous Row)</option>
                    <option value="BACKWARD_FILL">Backward Fill (Next Row)</option>
                  </select>
                </div>
                {missingStrategy === 'FILL_CONSTANT' && (
                  <div className="form-group">
                    <label className="form-label">Fill Value</label>
                    <input className="form-input" value={fillValue} onChange={e => setFillValue(e.target.value)} placeholder="Fill value..." />
                  </div>
                )}
              </>
            )}

            {cleanOp === 'replace' && (
              <>
                <div className="form-group">
                  <label className="form-label">Old Value</label>
                  <input className="form-input" value={oldVal} onChange={e => setOldVal(e.target.value)} placeholder="Search for..." />
                </div>
                <div className="form-group">
                  <label className="form-label">New Value</label>
                  <input className="form-input" value={newVal} onChange={e => setNewVal(e.target.value)} placeholder="Replace with..." />
                </div>
              </>
            )}

            {cleanOp === 'rename' && (
              <div className="form-group">
                <label className="form-label">New Column Name</label>
                <input className="form-input" value={newVal} onChange={e => setNewVal(e.target.value)} placeholder="Enter new header..." />
              </div>
            )}

            {cleanOp === 'convert' && (
              <div className="form-group">
                <label className="form-label">Target Data Type</label>
                <select className="form-select" value={targetType} onChange={e => setTargetType(e.target.value)}>
                  {['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'].map(t => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
            )}

            <div className="form-action-group" style={{ marginTop: 'auto' }}>
              <button
                className="btn btn-primary"
                onClick={handleRequestPreview}
                disabled={previewLoading || (needsCol && !cleanCol)}
              >
                {previewLoading ? (
                  <>
                    <div className="spinner" /> Generating Preview...
                  </>
                ) : (
                  <>
                    <Play size={13} /> Preview & Apply
                  </>
                )}
              </button>
              <button
                className="btn btn-outline"
                onClick={addToPipeline}
                disabled={previewLoading || (needsCol && !cleanCol)}
              >
                + Queue in Pipeline
              </button>
            </div>
          </div>

          {pipeline.length > 0 && (
            <div className="pipeline-box mb-16">
              <div className="flex-between mb-8">
                <span className="text-xs font-semibold uppercase tracking-wider text-secondary">
                  Batch Pipeline Queue ({pipeline.length} Actions)
                </span>
                <div className="flex-gap">
                  <button className="btn btn-xs btn-ghost" onClick={() => setPipeline([])}>Clear</button>
                  <button
                    className="btn btn-xs btn-primary"
                    onClick={handlePipelinePreview}
                    disabled={previewLoading}
                  >
                    {previewLoading ? 'Evaluating...' : 'Preview Pipeline'}
                  </button>
                </div>
              </div>
              <ol className="pipeline-list">
                {pipeline.map((p, idx) => (
                  <li key={idx}>
                    <code>{p.operation}</code> {p.columnName ? `on '${p.columnName}'` : ''}
                  </li>
                ))}
              </ol>
            </div>
          )}

          {cleanError && (
            <div className="alert alert-error">
              <AlertCircle size={15} />
              <span>{cleanError}</span>
            </div>
          )}

          {cleanResult && (
            <div className="alert alert-success">
              <Check size={15} />
              <span>
                {cleanResult.message}
                {cleanResult.rowsRemoved != null && ` · ${cleanResult.rowsRemoved} rows removed`}
                {cleanResult.cellsChanged != null && ` · ${cleanResult.cellsChanged} cells altered`}
                {cleanResult.rowsBefore != null && ` · Rows: ${cleanResult.rowsBefore} → ${cleanResult.rowsAfter}`}
              </span>
            </div>
          )}
        </div>
      )}

      {!loaded && (
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon-wrap">
              <FileSpreadsheet size={28} className="text-secondary" />
            </div>
            <h4 className="empty-title">Workspace Ready</h4>
            <p className="empty-desc">Upload any valid Excel (.xlsx) file in the ingestion area above to start inspecting, previewing, and transforming records.</p>
          </div>
        </div>
      )}

      <ChangePreviewModal
        isOpen={previewModalOpen}
        onClose={() => setPreviewModalOpen(false)}
        previewData={previewData}
        onApply={handleConfirmApply}
        applying={previewApplying}
      />
    </div>
  );
}
