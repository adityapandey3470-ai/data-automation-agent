import React, { useState } from 'react';
import { Check, X, Sliders, ArrowRight, AlertCircle, Table2 } from '../icons/Icons';

export default function ChangePreviewModal({
  isOpen,
  onClose,
  previewData,
  onApply,
  applying
}) {
  const [viewTab, setViewTab] = useState('after'); // 'after' | 'before'

  if (!isOpen || !previewData) return null;

  const {
    operation,
    sheetName,
    rowsBefore,
    rowsAfter,
    rowsRemoved,
    cellsChanged,
    columnCountBefore,
    columnCountAfter,
    headersBefore = [],
    headersAfter = [],
    beforeRows = [],
    afterRows = [],
    affectedColumns = [],
    summary
  } = previewData;

  const currentHeaders = viewTab === 'after' ? headersAfter : headersBefore;
  const currentRows = viewTab === 'after' ? afterRows : beforeRows;

  return (
    <div className="modal-backdrop">
      <div className="modal-card change-preview-modal" style={{ maxWidth: 900, width: '92vw', maxHeight: '90vh' }}>
        <div className="modal-header flex-between">
          <div className="flex-gap">
            <Sliders size={20} className="text-emerald" />
            <div>
              <h3 className="modal-title" style={{ fontSize: 16 }}>
                Change Preview: {operation?.replace(/_/g, ' ')}
              </h3>
              <p className="card-subtitle" style={{ fontSize: 12 }}>
                Target Sheet: <strong>{sheetName}</strong> · Review simulated changes before applying
              </p>
            </div>
          </div>
          <button
            className="modal-close-btn btn-icon"
            onClick={onClose}
            disabled={applying}
            aria-label="Close modal"
          >
            <X size={15} />
          </button>
        </div>

        <div className="modal-body" style={{ overflowY: 'auto', maxHeight: 'calc(90vh - 140px)', padding: '16px 20px' }}>
          <div className="kpi-grid mb-16" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10 }}>
            <div className="kpi-card" style={{ padding: '10px 14px' }}>
              <div className="kpi-value" style={{ fontSize: 18 }}>
                {rowsBefore} → {rowsAfter}
              </div>
              <div className="kpi-label">Total Rows</div>
            </div>
            <div className="kpi-card" style={{ padding: '10px 14px' }}>
              <div className="kpi-value" style={{ fontSize: 18, color: rowsRemoved > 0 ? '#ef4444' : 'var(--text-primary)' }}>
                {rowsRemoved > 0 ? `-${rowsRemoved}` : '0'}
              </div>
              <div className="kpi-label">Rows Removed</div>
            </div>
            <div className="kpi-card" style={{ padding: '10px 14px' }}>
              <div className="kpi-value" style={{ fontSize: 18, color: cellsChanged > 0 ? 'var(--emerald)' : 'var(--text-primary)' }}>
                {cellsChanged}
              </div>
              <div className="kpi-label">Cells Modified</div>
            </div>
            <div className="kpi-card" style={{ padding: '10px 14px' }}>
              <div className="kpi-value" style={{ fontSize: 18 }}>
                {columnCountBefore} → {columnCountAfter}
              </div>
              <div className="kpi-label">Columns</div>
            </div>
          </div>

          {summary && (
            <div className="alert alert-info mb-16" style={{ padding: '8px 12px', fontSize: 13 }}>
              <AlertCircle size={15} />
              <span>{summary}</span>
            </div>
          )}

          {affectedColumns.length > 0 && (
            <div className="flex-gap mb-12" style={{ flexWrap: 'wrap', alignItems: 'center' }}>
              <span className="text-xs font-semibold text-secondary">AFFECTED COLUMNS:</span>
              {affectedColumns.map((c, i) => (
                <span key={i} className="badge badge-warning" style={{ fontSize: 11 }}>
                  {c}
                </span>
              ))}
            </div>
          )}

          <div className="flex-between mb-12">
            <div className="pill-tabs">
              <button
                className={`pill-tab ${viewTab === 'after' ? 'active' : ''}`}
                onClick={() => setViewTab('after')}
              >
                <Check size={13} />
                <span>Proposed Output ({rowsAfter} rows)</span>
              </button>
              <button
                className={`pill-tab ${viewTab === 'before' ? 'active' : ''}`}
                onClick={() => setViewTab('before')}
              >
                <Table2 size={13} />
                <span>Original State ({rowsBefore} rows)</span>
              </button>
            </div>
            <span className="text-xs text-secondary">
              Showing preview sample (up to 20 rows)
            </span>
          </div>

          <div className="data-table-wrap mb-12">
            <div className="data-table-scroll" style={{ maxHeight: 260 }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: 44 }}>#</th>
                    {currentHeaders.map(h => {
                      const isAffected = affectedColumns.some(ac => ac.equalsIgnoreCase ? ac.equalsIgnoreCase(h) : ac === h);
                      return (
                        <th key={h} style={isAffected ? { background: 'rgba(16, 185, 129, 0.15)', color: 'var(--emerald)' } : {}}>
                          {h} {isAffected && '★'}
                        </th>
                      );
                    })}
                  </tr>
                </thead>
                <tbody>
                  {currentRows.length > 0 ? (
                    currentRows.map((row, idx) => (
                      <tr key={idx}>
                        <td className="row-num">{idx + 1}</td>
                        {currentHeaders.map(h => (
                          <td key={h}>
                            {row[h] != null ? String(row[h]) : <span className="null-val">null</span>}
                          </td>
                        ))}
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={currentHeaders.length + 1} style={{ textAlign: 'center', padding: 24, color: 'var(--text-secondary)' }}>
                        No rows in current view.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div className="modal-footer flex-between" style={{ padding: '12px 20px', borderTop: '1px solid var(--border)' }}>
          <button className="btn btn-outline btn-sm" onClick={onClose} disabled={applying}>
            Cancel
          </button>
          <div className="flex-gap">
            <span className="text-xs text-secondary" style={{ marginRight: 8 }}>
              No data modified yet
            </span>
            <button
              className="btn btn-primary btn-sm"
              onClick={onApply}
              disabled={applying}
            >
              {applying ? (
                <>
                  <div className="spinner" /> Applying...
                </>
              ) : (
                <>
                  <Check size={14} /> Apply Changes
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
