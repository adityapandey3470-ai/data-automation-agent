import React, { useState } from 'react';
import { exportExcel, exportCsv, exportPdfReport, getErrorMessage } from '../../services/api';
import { Download, FileSpreadsheet, Check, AlertCircle, ArrowRight, Shield } from '../icons/Icons';

export default function ExportPage({ sheetName, sheets, loaded, onNavigate, currentUser, onRequestAuth }) {
  const [loading, setLoading] = useState(null);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const download = (blob, filename) => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleExportPdfReport = async (sheet) => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    setError(null);
    setSuccess(null);
    setLoading(sheet ? `pdf-${sheet}` : 'pdf-all');
    try {
      const res = await exportPdfReport(sheet);
      const filename = res.headers['content-disposition']
        ?.match(/filename="?(.+?)"?$/)?.[1]
        || (sheet ? `${sheet}_Executive_Report.pdf` : 'Executive_Analysis_Report.pdf');
      download(res.data, filename);
      setSuccess(`Exported and downloaded: ${filename}`);
    } catch (err) {
      let msg = null;
      if (err.response?.data instanceof Blob) {
        try {
          const text = await err.response.data.text();
          if (text.startsWith('{')) {
            const parsed = JSON.parse(text);
            msg = parsed.message || parsed.error;
          }
        } catch (_) {}
      }
      setError(msg || getErrorMessage(err, 'Failed to export PDF report.'));
    } finally {
      setLoading(null);
    }
  };

  const handleExportExcel = async (sheet) => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    setError(null);
    setSuccess(null);
    setLoading(sheet || 'all');
    try {
      const res = await exportExcel(sheet);
      const filename = res.headers['content-disposition']
        ?.match(/filename="?(.+?)"?$/)?.[1]
        || (sheet ? `${sheet}.xlsx` : 'export.xlsx');
      download(res.data, filename);
      setSuccess(`Exported and downloaded: ${filename}`);
    } catch (err) {
      let msg = null;
      if (err.response?.data instanceof Blob) {
        try {
          const text = await err.response.data.text();
          if (text.startsWith('{')) {
            const parsed = JSON.parse(text);
            msg = parsed.message || parsed.error;
          }
        } catch (_) {}
      }
      setError(msg || getErrorMessage(err, 'Failed to export Excel workbook.'));
    } finally {
      setLoading(null);
    }
  };

  const handleExportCsv = async (sheet) => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    setError(null);
    setSuccess(null);
    setLoading(`csv-${sheet}`);
    try {
      const res = await exportCsv(sheet);
      const filename = res.headers['content-disposition']
        ?.match(/filename="?(.+?)"?$/)?.[1]
        || `${sheet}.csv`;
      download(res.data, filename);
      setSuccess(`Exported and downloaded: ${filename}`);
    } catch (err) {
      let msg = null;
      if (err.response?.data instanceof Blob) {
        try {
          const text = await err.response.data.text();
          if (text.startsWith('{')) {
            const parsed = JSON.parse(text);
            msg = parsed.message || parsed.error;
          }
        } catch (_) {}
      }
      setError(msg || getErrorMessage(err, 'Failed to export CSV file.'));
    } finally {
      setLoading(null);
    }
  };

  if (!loaded) {
    return (
      <div className="export-page">
        <div className="page-header mb-20">
          <h1>Export Hub</h1>
          <p>Stream your processed, deduplicated, and transformed datasets to local storage.</p>
        </div>
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon-wrap">
              <Download size={28} className="text-secondary" />
            </div>
            <h4 className="empty-title">No Dataset Loaded</h4>
            <p className="empty-desc">Upload a workbook in Data Workspace to generate formatted Excel or CSV export streams.</p>
            <button className="btn btn-primary mt-16" onClick={() => onNavigate('workspace')}>
              Open Data Workspace <ArrowRight size={14} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="export-page">
      <div className="page-header mb-20">
        <h1>Export Hub</h1>
        <p>Export in-memory workbook state to OpenXML Excel (<code>.xlsx</code>) or standard UTF-8 CSV.</p>
      </div>

      {error && (
        <div className="alert alert-error mb-16">
          <AlertCircle size={15} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="alert alert-success mb-16">
          <Check size={15} />
          <span>{success}</span>
        </div>
      )}

      {/* Executive PDF Analysis Report */}
      <div className="card mb-20" style={{ border: '1px solid var(--border)', background: 'var(--card-bg)' }}>
        <div className="card-header flex-between" style={{ alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <div className="flex-gap" style={{ alignItems: 'center', marginBottom: 4 }}>
              <span className="badge badge-primary" style={{ background: 'var(--indigo)', color: '#ffffff' }}>PDF Executive Report</span>
              <h3 className="card-title" style={{ margin: 0 }}>Executive Analysis & Data Quality Report</h3>
            </div>
            <p className="card-subtitle" style={{ margin: 0 }}>
              Download branded PDF summary with executive KPIs, AI data quality scores, issues log, and column statistics.
            </p>
          </div>
          <button
            className="btn btn-primary"
            onClick={() => handleExportPdfReport(sheetName)}
            disabled={loading != null}
            style={{ background: 'var(--indigo)', borderColor: 'var(--indigo)' }}
          >
            {loading === `pdf-${sheetName}` || loading === 'pdf-all' ? (
              <><div className="spinner" /> Generating PDF...</>
            ) : (
              <><Download size={15} /> Download PDF Report</>
            )}
          </button>
        </div>
      </div>

      {/* Full Workbook Export */}
      {sheets && sheets.length > 1 && (
        <div className="card mb-20">
          <div className="card-header flex-between">
            <div>
              <h3 className="card-title">Consolidated Workbook Export</h3>
              <p className="card-subtitle">Download all {sheets.length} sheets bundled in a single multi-tab .xlsx spreadsheet</p>
            </div>
            <button
              className="btn btn-primary"
              onClick={() => handleExportExcel(null)}
              disabled={loading != null}
            >
              {loading === 'all' ? (
                <><div className="spinner" /> Generating...</>
              ) : (
                <><Download size={15} /> Download Full Workbook (.xlsx)</>
              )}
            </button>
          </div>
        </div>
      )}

      {/* Individual Sheets */}
      <div className="card">
        <div className="card-header">
          <h3 className="card-title">Individual Sheet Streams</h3>
          <p className="card-subtitle">Export targeted sheets in your format of choice</p>
        </div>

        <div className="export-list">
          {(sheets || [{ sheetName }]).map(s => (
            <div key={s.sheetName} className="export-item">
              <div className="export-item-icon">
                <FileSpreadsheet size={22} className="text-emerald" />
              </div>
              <div className="export-item-info">
                <div className="export-item-name">{s.sheetName}</div>
                <div className="export-item-meta">
                  {s.rowCount != null && `${s.rowCount.toLocaleString()} records`}
                  {s.columnCount != null && ` · ${s.columnCount} columns`}
                </div>
              </div>
              <div className="export-item-actions flex-gap">
                <button
                  className="btn btn-sm btn-primary"
                  onClick={() => handleExportExcel(s.sheetName)}
                  disabled={loading != null}
                >
                  {loading === s.sheetName ? <div className="spinner" /> : <><Download size={13} /> Excel (.xlsx)</>}
                </button>
                <button
                  className="btn btn-sm btn-outline"
                  onClick={() => handleExportCsv(s.sheetName)}
                  disabled={loading != null}
                >
                  {loading === `csv-${s.sheetName}` ? <div className="spinner" /> : <><Download size={13} /> CSV</>}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
