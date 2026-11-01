import React from 'react';
import {
  Upload, FileSpreadsheet, Sparkles, LineChart, Download,
  ArrowRight, Sliders, Table2
} from '../icons/Icons';
import { formatMetricNumber } from '../../utils/numberFormat';

export default function DashboardPage({ loaded, fileInfo, activeSheet, onNavigate, currentUser, onRequestAuth }) {
  const totalRows = loaded
    ? fileInfo.sheets?.reduce((acc, s) => acc + (s.rowCount || 0), 0) || 0
    : 0;

  const handleUploadClick = () => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    onNavigate('workspace');
  };

  return (
    <div className="dashboard-page">
      <div className="page-header flex-between mb-24">
        <div>
          <h1>Executive Overview</h1>
          <p>Automate spreadsheet ingestion, deterministic data cleaning, and instant analytics.</p>
        </div>
        <div className="flex-gap">
          <button className="btn btn-primary" onClick={handleUploadClick}>
            <Upload size={15} /> {loaded ? 'Switch / Upload File' : 'Upload Dataset'}
          </button>
        </div>
      </div>

      <div className="stats-grid mb-24">
        <div className="stat-card">
          <div className="stat-label">Active Workbook</div>
          <div className="stat-value">{loaded ? '1' : '0'}</div>
          <div className="stat-sub" title={loaded ? fileInfo.fileName : ''}>
            {loaded ? fileInfo.fileName : 'No dataset active'}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Total Sheets</div>
          <div className="stat-value">{fileInfo?.sheetCount || 0}</div>
          <div className="stat-sub">{loaded ? `${fileInfo.sheets?.length} sheets available` : '—'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Total Processed Rows</div>
          <div className="stat-value" title={`Exact Total: ${totalRows.toLocaleString()}`}>
            {formatMetricNumber(totalRows)}
          </div>
          <div className="stat-sub">{loaded ? `${fileInfo.sheets?.length || 1} sheet(s) total` : '—'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Active Sheet Columns</div>
          <div className="stat-value">{activeSheet?.columnCount || 0}</div>
          <div className="stat-sub" title={activeSheet ? activeSheet.sheetName : ''}>
            {activeSheet ? activeSheet.sheetName : '—'}
          </div>
        </div>
      </div>

      {!loaded ? (
        <div className="card empty-hero-card mb-24">
          <div className="empty-state">
            <div className="empty-icon-wrap">
              <FileSpreadsheet size={32} className="text-emerald" />
            </div>
            <h3 className="empty-title">No workbook loaded in current session</h3>
            <p className="empty-desc">
              Upload an Excel (.xlsx) file to inspect sheets, eliminate duplicates, filter records, and create real-time charts.
            </p>
            <button className="btn btn-primary mt-16" onClick={handleUploadClick}>
              <Upload size={16} /> Upload Excel Spreadsheet
            </button>
          </div>
        </div>
      ) : (
        <div className="card mb-24">
          <div className="card-header flex-between">
            <div>
              <h3 className="card-title">Workbook Sheets & Structure</h3>
              <p className="card-subtitle">Real-time metadata from <code>{fileInfo.fileName}</code></p>
            </div>
            <button className="btn btn-sm btn-outline" onClick={() => onNavigate('workspace')}>
              Open in Data Workspace <ArrowRight size={13} />
            </button>
          </div>

          <div className="data-table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Sheet Name</th>
                  <th>Row Count</th>
                  <th>Column Count</th>
                  <th>Detected Headers</th>
                </tr>
              </thead>
              <tbody>
                {(fileInfo.sheets || []).map(s => (
                  <tr key={s.sheetName}>
                    <td style={{ fontWeight: 600 }}>
                      <span className="flex-gap">
                        <Table2 size={14} className="text-emerald" />
                        {s.sheetName}
                      </span>
                    </td>
                    <td>{s.rowCount?.toLocaleString()}</td>
                    <td>{s.columnCount}</td>
                    <td className="text-secondary text-sm">
                      {s.headers?.slice(0, 5).join(', ')}{s.headers?.length > 5 ? ` +${s.headers.length - 5} more` : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h3 className="card-title">Engine Quick Actions</h3>
          <p className="card-subtitle">Direct shortcuts to primary automated pipelines</p>
        </div>
        <div className="quick-actions">
          <button className="quick-action" onClick={() => onNavigate('workspace')}>
            <div className="qa-icon-wrap bg-emerald-subtle">
              <Table2 size={20} className="text-emerald" />
            </div>
            <div>
              <div className="qa-text">Data Workspace</div>
              <div className="qa-desc">Inspect table preview & trigger 9 cleaning routines</div>
            </div>
          </button>

          <button className="quick-action" onClick={() => onNavigate('assistant')}>
            <div className="qa-icon-wrap bg-indigo-subtle">
              <Sparkles size={20} className="text-indigo" />
            </div>
            <div>
              <div className="qa-text">AI Analyst & Insights</div>
              <div className="qa-desc">Automatic data profiling and conversational workbook analysis</div>
            </div>
          </button>

          <button className="quick-action" onClick={() => onNavigate('analytics')}>
            <div className="qa-icon-wrap bg-blue-subtle">
              <LineChart size={20} className="text-blue" />
            </div>
            <div>
              <div className="qa-text">Analytics & Charts</div>
              <div className="qa-desc">Instant KPI metrics, multi-filters, and visual charts</div>
            </div>
          </button>

          <button className="quick-action" onClick={() => onNavigate('export')}>
            <div className="qa-icon-wrap bg-amber-subtle">
              <Download size={20} className="text-amber" />
            </div>
            <div>
              <div className="qa-text">Export Hub</div>
              <div className="qa-desc">Stream processed datasets to .xlsx or UTF-8 .csv</div>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
}
