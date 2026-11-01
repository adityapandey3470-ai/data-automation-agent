import React, { useState } from 'react';
import {
  Sliders, HelpCircle, Shield, FileText, Mail,
  Check, ArrowRight, RefreshCw, FileSpreadsheet, Sparkles, BarChart3, Download
} from '../icons/Icons';

export default function SettingsPage({ onReplayOnboarding }) {
  const [activeTab, setActiveTab] = useState('general');

  const navItems = [
    { id: 'general', label: 'General & Preferences', icon: <Sliders size={16} /> },
    { id: 'guide', label: 'How to Use Tabufinery', icon: <HelpCircle size={16} /> },
    { id: 'privacy', label: 'Privacy Policy', icon: <Shield size={16} /> },
    { id: 'terms', label: 'Terms & Conditions', icon: <FileText size={16} /> },
    { id: 'support', label: 'Help & Support', icon: <Mail size={16} /> },
  ];

  return (
    <div className="settings-container">
      <div className="page-header mb-20">
        <h1>Settings & Documentation</h1>
        <p>Configure preferences, review application guides, privacy protocols, and contact support.</p>
      </div>

      <div className="settings-layout">
        <aside className="settings-sidebar">
          {navItems.map(item => (
            <button
              key={item.id}
              className={`settings-nav-item ${activeTab === item.id ? 'active' : ''}`}
              onClick={() => setActiveTab(item.id)}
            >
              <span className="icon-wrap">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </aside>

        <main className="settings-content">
          {activeTab === 'general' && (
            <div className="settings-card">
              <div className="card-header">
                <div>
                  <h3 className="card-title">General Preferences</h3>
                  <p className="card-subtitle">Manage workspace defaults and onboarding experience</p>
                </div>
              </div>

              <div className="setting-row">
                <div>
                  <div className="setting-title">App Onboarding Tour</div>
                  <div className="setting-desc">Replay the introductory welcome screen and feature highlights.</div>
                </div>
                <button className="btn btn-sm btn-outline" onClick={onReplayOnboarding}>
                  <RefreshCw size={14} /> Replay Introduction
                </button>
              </div>

              <div className="setting-row">
                <div>
                  <div className="setting-title">Appearance & Theme</div>
                  <div className="setting-desc">Automatically adapts between Dark and Light mode based on your device and OS system settings.</div>
                </div>
                <span className="badge badge-neutral">System Auto-Detect</span>
              </div>

              <div className="setting-row">
                <div>
                  <div className="setting-title">Application Version</div>
                  <div className="setting-desc">Spring Boot 3.5.16 + Apache POI 5.3 + React 18 Engine.</div>
                </div>
                <span className="badge badge-info">v0.1.0-production-beta</span>
              </div>
            </div>
          )}

          {activeTab === 'guide' && (
            <div className="settings-card">
              <div className="card-header">
                <div>
                  <h3 className="card-title">How to Use Tabufinery</h3>
                  <p className="card-subtitle">Step-by-step documentation for end-to-end data refining and processing</p>
                </div>
              </div>

              <div className="guide-steps">
                <div className="guide-step">
                  <div className="step-num">1</div>
                  <div className="step-content">
                    <h4>Upload Excel Workbook</h4>
                    <p>
                      Navigate to <strong>Data Workspace</strong> or click <strong>Upload Dataset</strong> on Overview. Drag and drop any <code>.xlsx</code> file (up to 10MB). Tabufinery automatically parses sheets, rows, and data types.
                    </p>
                  </div>
                </div>

                <div className="guide-step">
                  <div className="step-num">2</div>
                  <div className="step-content">
                    <h4>Inspect & Preview Data</h4>
                    <p>
                      View column headers, row counts, and live tabular data. Switch between multiple sheets using the sheet selector pills, or toggle between <strong>Top 100 Preview</strong> and <strong>Full Dataset</strong>.
                    </p>
                  </div>
                </div>

                <div className="guide-step">
                  <div className="step-num">3</div>
                  <div className="step-content">
                    <h4>Clean and Transform</h4>
                    <p>
                      Open the <strong>Clean Data</strong> panel. Execute operations such as removing duplicates (Keep First/Last), filling or dropping missing values, trimming strings, casing conversions, column renaming, or queue them in a <strong>Batch Pipeline</strong>.
                    </p>
                  </div>
                </div>

                <div className="guide-step">
                  <div className="step-num">4</div>
                  <div className="step-content">
                    <h4>AI Analyst & Automatic Insights</h4>
                    <p>
                      In <strong>AI Analyst & Insights</strong>, explore automated data profiles, statistical facts vs interpretations, and ask conversational analytical questions to the AI Analyst (powered by Gemini with deterministic engine fallback).
                    </p>
                  </div>
                </div>

                <div className="guide-step">
                  <div className="step-num">5</div>
                  <div className="step-content">
                    <h4>Analytics & Dashboard Widgets</h4>
                    <p>
                      In <strong>Analytics & Charts</strong>, compute full column statistics, multi-criteria filters, group-by aggregations, or assemble custom dashboards with KPI cards, tables, and Bar/Line/Pie charts.
                    </p>
                  </div>
                </div>

                <div className="guide-step">
                  <div className="step-num">6</div>
                  <div className="step-content">
                    <h4>Download Processed Output</h4>
                    <p>
                      Go to the <strong>Export</strong> tab to download your updated, cleaned dataset as formatted Excel (<code>.xlsx</code>) or standard UTF-8 <code>.csv</code> files.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'privacy' && (
            <div className="settings-card legal-document">
              <div className="card-header">
                <div>
                  <h3 className="card-title">Privacy Policy</h3>
                  <p className="card-subtitle">Effective Date: October 2026 · Architectural Transparency</p>
                </div>
              </div>

              <div className="legal-body">
                <h4>1. Local In-Memory Processing</h4>
                <p>
                  Tabufinery is architected with strict in-memory execution. Uploaded workbooks are held transiently in the active server session memory (<code>WorkbookSessionHolder</code>) solely to compute previews, cleaning operations, analysis, and exports.
                </p>

                <h4>2. No Unverified Third-Party Transmission</h4>
                <p>
                  Tabular data, cell contents, and column values uploaded to Tabufinery are not transmitted to external cloud storage, third-party analytics trackers, or public AI models without your explicit configuration.
                </p>

                <h4>3. Data Retention & Lifecycle</h4>
                <p>
                  Your spreadsheet rows are stored in volatile server memory for the duration of the current session. Terminating the backend session or clearing the workbook immediately flushes the in-memory dataset.
                </p>

                <h4>4. Security Best Practices</h4>
                <p>
                  We recommend not uploading unencrypted proprietary credentials, authentication tokens, or government identification numbers into test or staging environments.
                </p>
              </div>
            </div>
          )}

          {activeTab === 'terms' && (
            <div className="settings-card legal-document">
              <div className="card-header">
                <div>
                  <h3 className="card-title">Terms & Conditions</h3>
                  <p className="card-subtitle">Last updated: October 2026 · Acceptable Use & Guidelines</p>
                </div>
              </div>

              <div className="legal-body">
                <h4>1. Acceptable Use</h4>
                <p>
                  Tabufinery is provided as a productivity tool for data cleaning, transformation, analysis, and visualization. You agree not to upload malicious binaries, corrupted archives, or exploit file parser vulnerabilities.
                </p>

                <h4>2. User Responsibility & Verification</h4>
                <p>
                  While Tabufinery implements deterministic algorithms for aggregations, sorting, duplicate removal, and type conversions, users remain solely responsible for reviewing and verifying the accuracy of output datasets before relying on them for critical commercial decisions.
                </p>

                <h4>3. Service Limitations</h4>
                <p>
                  Current file ingestion limits accept <code>.xlsx</code> formats up to 10MB per upload. Operations on exceptionally large sheets may be subject to hardware and memory limits.
                </p>

                <h4>4. Intellectual Property</h4>
                <p>
                  You retain complete and unrestricted ownership of all data files uploaded, transformed, and exported through Tabufinery.
                </p>

                <h4>5. Changes to the Service</h4>
                <p>
                  Software features and algorithmic tools may be updated periodically to enhance performance, reliability, and security.
                </p>
              </div>
            </div>
          )}

          {activeTab === 'support' && (
            <div className="settings-card">
              <div className="card-header">
                <div>
                  <h3 className="card-title">Help & Support</h3>
                  <p className="card-subtitle">Direct communication with the Tabufinery technical team</p>
                </div>
              </div>

              <div className="support-box">
                <div className="support-icon-circle">
                  <Mail size={24} className="text-emerald" />
                </div>
                <div className="support-info">
                  <h4>Official Support Channel</h4>
                  <p className="support-email-text">
                    Email: <a href="mailto:support0912@gmail.com" className="email-link">support0912@gmail.com</a>
                  </p>
                  <p className="support-desc">
                    For inquiries, bug reports, feature requests, or technical guidance, our engineering team responds promptly.
                  </p>
                  <a href="mailto:support0912@gmail.com?subject=Tabufinery%20Support%20Request" className="btn btn-primary btn-sm mt-12">
                    <Mail size={14} /> Contact Support
                  </a>
                </div>
              </div>

              <div className="issue-guidance mt-24">
                <h4>Recommended details when reporting an issue:</h4>
                <ul className="guidance-list">
                  <li><Check size={14} className="text-emerald" /> <strong>File Type & Size:</strong> e.g., <code>.xlsx</code>, ~2.5 MB, 12 columns, 8,000 rows.</li>
                  <li><Check size={14} className="text-emerald" /> <strong>Action Attempted:</strong> e.g., "Sort by Date descending" or "Remove duplicates".</li>
                  <li><Check size={14} className="text-emerald" /> <strong>Error Message:</strong> Copy any toast notification or alert text shown on screen.</li>
                  <li><Check size={14} className="text-emerald" /> <strong>Browser / Environment:</strong> Chrome/Firefox version and operating system.</li>
                </ul>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
