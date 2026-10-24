import React, { useState, useEffect, useCallback } from 'react';
import {
  getStatistics, filterRows, sortRows, groupBy, findDuplicates,
  removeDuplicates, handleMissing, cleanStrings,
  getAiStatus, getDataProfile, askAiAnalyst, getErrorMessage
} from '../../services/api';
import {
  Sparkles, Play, Check, AlertCircle, Copy, ArrowRight,
  RefreshCw, Lightbulb, Zap, X, Shield, CheckCircle
} from '../icons/Icons';

// Safe sessionStorage writer with automatic LRU eviction for QuotaExceededError
const safeSessionStorageSet = (key, value) => {
  try {
    sessionStorage.setItem(key, value);
  } catch (e) {
    if (e.name === 'QuotaExceededError' || e.code === 22) {
      try {
        const keysToRemove = [];
        for (let i = 0; i < sessionStorage.length; i++) {
          const k = sessionStorage.key(i);
          if (k && k.startsWith('tabufinery_profile_') && k !== key) {
            keysToRemove.push(k);
          }
        }
        keysToRemove.forEach(k => sessionStorage.removeItem(k));
        sessionStorage.setItem(key, value);
      } catch {
      }
    }
  }
};

export default function AIAssistant({ sheetName, loaded, onRefresh, onNavigate, currentUser, onRequestAuth }) {
  const [tab, setTab] = useState(() => {
    try {
      const saved = localStorage.getItem('tabufinery_ai_tab');
      if (saved === 'analyst' || saved === 'insights') return saved;
    } catch {}
    return 'analyst';
  });

  useEffect(() => {
    try {
      localStorage.setItem('tabufinery_ai_tab', tab);
    } catch {}
  }, [tab]);

  const [aiStatus, setAiStatus] = useState({ available: false, model: 'Deterministic Engine' });
  const [profile, setProfile] = useState(() => {
    try {
      const cached = sessionStorage.getItem(`tabufinery_profile_${sheetName}`);
      if (cached) return JSON.parse(cached);
    } catch {}
    return null;
  });
  const [loadingProfile, setLoadingProfile] = useState(() => {
    try {
      return !sessionStorage.getItem(`tabufinery_profile_${sheetName}`);
    } catch {}
    return false;
  });
  const [profileError, setProfileError] = useState(null);

  const [input, setInput] = useState('');
  const [analystResponses, setAnalystResponses] = useState([]);
  const [running, setRunning] = useState(false);

  const [pendingAction, setPendingAction] = useState(null);

  const loadProfile = useCallback(async (forceRefresh = false) => {
    if (!loaded || !sheetName) return;
    if (forceRefresh) {
      try {
        sessionStorage.removeItem(`tabufinery_profile_${sheetName}`);
      } catch {}
      setLoadingProfile(true);
    } else {
      try {
        const cached = sessionStorage.getItem(`tabufinery_profile_${sheetName}`);
        if (!cached) setLoadingProfile(true);
      } catch {
        setLoadingProfile(true);
      }
    }
    setProfileError(null);
    try {
      const [statusRes, profileRes] = await Promise.all([
        getAiStatus().catch(() => ({ data: { available: false, model: 'Deterministic Engine' } })),
        getDataProfile(sheetName)
      ]);
      setAiStatus(statusRes.data || { available: false, model: 'Deterministic Engine' });
      setProfile(profileRes.data);
      safeSessionStorageSet(`tabufinery_profile_${sheetName}`, JSON.stringify(profileRes.data));
    } catch (err) {
      setProfileError(getErrorMessage(err, 'Failed to generate profile'));
    } finally {
      setLoadingProfile(false);
    }
  }, [loaded, sheetName]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const handleAsk = async (questionText) => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    const q = (questionText || input).trim();
    if (!q || !sheetName) return;
    if (!questionText) setInput('');
    setRunning(true);

    try {
      const res = await askAiAnalyst(q, sheetName);
      const respData = res.data;
      setAnalystResponses(prev => [
        ...prev,
        {
          question: q,
          response: respData,
          time: new Date().toLocaleTimeString()
        }
      ]);
    } catch (err) {
      setAnalystResponses(prev => [
        ...prev,
        {
          question: q,
          error: getErrorMessage(err, 'Analysis request failed'),
          time: new Date().toLocaleTimeString()
        }
      ]);
    } finally {
      setRunning(false);
    }
  };

  const requestAction = (cmd, requiresConfirmation = true, explanation = '') => {
    if (!currentUser) {
      if (onRequestAuth) onRequestAuth();
      return;
    }
    if (requiresConfirmation) {
      setPendingAction({ cmd, explanation });
    } else {
      executeAction(cmd);
    }
  };

  const executeAction = async (rawCmd) => {
    const cmd = rawCmd.trim();
    setPendingAction(null);
    setRunning(true);

    try {
      const lower = cmd.toLowerCase();
      let resMsg = 'Operation completed.';

      if (lower.includes('remove duplicate') || lower.startsWith('remove duplicates')) {
        const res = await removeDuplicates({ sheetName, duplicateStrategy: 'KEEP_FIRST' });
        resMsg = res.data?.message || 'Duplicate rows removed successfully.';
        onRefresh();
        loadProfile();
      } else if (lower.includes('trim')) {
        const colMatch = cmd.match(/column\s+([\w\s]+)/i);
        const col = colMatch ? colMatch[1].trim() : null;
        const res = await cleanStrings({ sheetName, column: col, operation: 'TRIM' });
        resMsg = res.data?.message || `Whitespace trimmed successfully${col ? ` on ${col}` : ''}.`;
        onRefresh();
        loadProfile();
      } else if (lower.includes('missing')) {
        const colMatch = cmd.match(/column\s+([\w\s]+)/i);
        const col = colMatch ? colMatch[1].trim() : null;
        const res = await handleMissing({ sheetName, column: col, strategy: 'FILL_CUSTOM', customValue: 'N/A' });
        resMsg = res.data?.message || `Missing values handled${col ? ` on ${col}` : ''}.`;
        onRefresh();
        loadProfile();
      } else if (lower.startsWith('sort ')) {
        const parts = cmd.split(' ');
        const col = parts[1];
        const dir = parts[2]?.toUpperCase() === 'DESC' ? 'DESC' : 'ASC';
        const res = await sortRows(sheetName, [{ column: col, direction: dir }]);
        resMsg = `Sorted ${res.data?.resultRows || 0} rows by '${col}' ${dir}.`;
        onRefresh();
      } else if (lower.startsWith('filter ')) {
        const match = cmd.match(/^filter\s+(\S+)\s+(equals|greater_than|less_than|contains)\s+(.+)/i);
        if (match) {
          const res = await filterRows(sheetName, [{ column: match[1], operator: match[2].toUpperCase(), value: match[3].trim() }]);
          resMsg = `Filtered: ${res.data?.resultRows || 0} matching rows identified.`;
        }
      }

      setAnalystResponses(prev => [
        ...prev,
        {
          question: `Action: ${cmd}`,
          response: {
            answer: resMsg,
            status: 'ACTION_EXECUTED',
            engine: 'EXECUTION_ENGINE'
          },
          time: new Date().toLocaleTimeString()
        }
      ]);
    } catch (err) {
      setAnalystResponses(prev => [
        ...prev,
        {
          question: `Action: ${rawCmd}`,
          error: getErrorMessage(err, 'Failed to execute requested action'),
          time: new Date().toLocaleTimeString()
        }
      ]);
    } finally {
      setRunning(false);
    }
  };

  if (!loaded) {
    return (
      <div className="assistant-page">
        <div className="page-header mb-20">
          <h1>AI Analyst & Insights</h1>
          <p>Automated statistical profiling and natural language workbook investigation.</p>
        </div>
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon-wrap">
              <Sparkles size={28} className="text-emerald" />
            </div>
            <h4 className="empty-title">Analyst Awaiting Workbook</h4>
            <p className="empty-desc">Upload a workbook in Data Workspace to generate automatic data profiles and explore insights with AI.</p>
            <button className="btn btn-primary mt-16" onClick={() => onNavigate('workspace')}>
              Open Data Workspace <ArrowRight size={14} />
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="assistant-page">
      <div className="page-header flex-between mb-20">
        <div>
          <h1>AI Analyst & Automatic Insights</h1>
          <p>
            Active Sheet: <strong>{sheetName}</strong> ·{' '}
            <span className={aiStatus.available ? 'text-emerald' : 'text-secondary'}>
              {aiStatus.available ? `Gemini AI Online (${aiStatus.model})` : 'Deterministic Statistical Engine'}
            </span>
          </p>
        </div>

        <div className="flex-gap">
          <button
            className="btn btn-sm btn-outline flex-gap"
            onClick={() => loadProfile(true)}
            disabled={loadingProfile}
            title="Reload fresh data profile"
          >
            <RefreshCw size={13} className={loadingProfile ? 'spin' : ''} /> Refresh Profile
          </button>
        </div>
      </div>

      <div className="pill-tabs mb-20">
        <button
          className={`pill-tab ${tab === 'analyst' ? 'active' : ''}`}
          onClick={() => setTab('analyst')}
        >
          <Sparkles size={14} />
          <span>AI Analyst Console</span>
        </button>
        <button
          className={`pill-tab ${tab === 'insights' ? 'active' : ''}`}
          onClick={() => setTab('insights')}
        >
          <Lightbulb size={14} />
          <span>Automatic Insights {profile?.insights?.length ? `(${profile.insights.length})` : ''}</span>
        </button>
      </div>

      {tab === 'analyst' && (
        <>
          <div className="card mb-20 p-12 flex-between" style={{ background: 'var(--surface)', borderLeft: aiStatus.available ? '3px solid var(--emerald)' : '3px solid #6366f1', flexWrap: 'wrap', gap: 10 }}>
            <div className="flex-gap" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <div className="status-indicator">
                <span className="dot" style={{ background: aiStatus.available ? 'var(--emerald)' : '#6366f1' }} />
              </div>
              <div style={{ fontSize: 13 }}>
                <strong>Active Engine:</strong> {aiStatus.available ? `${aiStatus.model} (Official Gemini API)` : 'Tabufinery Deterministic Engine (Zero Hallucinations)'}
                {!aiStatus.available && (
                  <span className="text-secondary" style={{ marginLeft: 6, fontSize: 12 }}>
                    · To activate Gemini, set <code>GEMINI_API_KEY</code> in environment.
                  </span>
                )}
              </div>
            </div>
            <span className="badge badge-neutral" style={{ fontSize: 11, alignSelf: 'center' }}>
              {aiStatus.available ? 'LLM Enhanced' : 'Rule-Based Fallback'}
            </span>
          </div>

          <div className="card mb-20 p-16">
            <div className="ai-analyst-input-box">
              <Sparkles size={18} className="text-emerald" style={{ flexShrink: 0 }} />
              <input
                id="ai-analyst-question-input"
                name="aiQuestion"
                className="ai-analyst-input"
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') handleAsk(); }}
                placeholder={`Ask any analytical question about ${sheetName} (e.g., 'What are the main quality issues?', 'Are there extreme outliers?')...`}
                aria-label="Ask analytical question"
                autoComplete="off"
                disabled={running}
              />
              <button
                className="btn btn-primary btn-sm ai-ask-btn"
                onClick={() => handleAsk()}
                disabled={running || !input.trim()}
              >
                {running ? <><div className="spinner" /> Analyzing...</> : <><Play size={13} /> Ask Analyst</>}
              </button>
            </div>

            {profile?.suggestedQuestions?.length > 0 && (
              <div className="suggested-prompts-container mt-12">
                <span className="text-xs text-secondary" style={{ fontWeight: 600 }}>Suggested:</span>
                <div className="suggested-chips-flow">
                  {profile.suggestedQuestions.slice(0, 3).map((sq, i) => (
                    <button
                      key={i}
                      className="suggested-prompt-chip"
                      onClick={() => handleAsk(sq)}
                      disabled={running}
                    >
                      {sq}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {analystResponses.length > 0 ? (
            <div className="analyst-stream mb-20">
              {[...analystResponses].reverse().map((entry, idx) => (
                <div key={idx} className="card analyst-response-card mb-16">
                  <div className="analyst-query-bar flex-between">
                    <div className="flex-gap">
                      <span className="user-q-badge">Q</span>
                      <strong style={{ fontSize: 14 }}>{entry.question}</strong>
                    </div>
                    <span className="text-xs text-secondary">{entry.time}</span>
                  </div>

                  {entry.error ? (
                    <div className="alert alert-error mt-12">
                      <AlertCircle size={15} />
                      <span>{entry.error}</span>
                    </div>
                  ) : (
                    <div className="analyst-answer-body mt-12">
                      <p className="analyst-answer-text">{entry.response.answer}</p>

                      {entry.response.verifiedEvidence && Object.keys(entry.response.verifiedEvidence).length > 0 && (
                        <div className="verified-evidence-wrap mt-12">
                          <span className="evidence-label">Verified Backend Evidence:</span>
                          <div className="evidence-chips">
                            {Object.entries(entry.response.verifiedEvidence).map(([k, v]) => (
                              <span key={k} className="evidence-chip">
                                <strong>{k}:</strong> {String(v)}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {entry.response.insights?.length > 0 && (
                        <div className="analyst-insights-grid mt-16">
                          {entry.response.insights.map((ins, i) => (
                            <div key={i} className={`mini-insight-card ${ins.severity?.toLowerCase() || 'info'}`}>
                              <div className="mini-insight-header">
                                <span className="mini-insight-title">{ins.title}</span>
                                <span className={`badge badge-${ins.severity === 'WARNING' ? 'warning' : 'info'}`}>
                                  {ins.category || 'ANALYSIS'}
                                </span>
                              </div>
                              {ins.statisticalFact && (
                                <div className="fact-block">
                                  <strong>Fact:</strong> {ins.statisticalFact}
                                </div>
                              )}
                              {ins.interpretation && (
                                <div className="interp-block">
                                  <strong>Interpretation:</strong> {ins.interpretation}
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      {entry.response.suggestedActions?.length > 0 && (
                        <div className="suggested-actions-box mt-16">
                          <div className="text-xs font-semibold text-secondary mb-8">RECOMMENDED VERIFIED ACTIONS:</div>
                          <div className="flex-gap" style={{ flexWrap: 'wrap' }}>
                            {entry.response.suggestedActions.map((act, i) => (
                              <button
                                key={i}
                                className="btn btn-xs btn-outline"
                                onClick={() => requestAction(act.command, act.requiresConfirmation, act.explanation)}
                                disabled={running}
                              >
                                <Zap size={12} className="text-emerald" /> {act.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}

                      {entry.response.suggestedFollowUps?.length > 0 && (
                        <div className="suggested-followups-row mt-16">
                          <span className="text-xs text-secondary">Follow up:</span>
                          {entry.response.suggestedFollowUps.slice(0, 3).map((fup, i) => (
                            <button
                              key={i}
                              className="suggested-prompt-chip"
                              onClick={() => handleAsk(fup)}
                              disabled={running}
                            >
                              {fup}
                            </button>
                          ))}
                        </div>
                      )}

                      <div className="analyst-card-footer mt-12 flex-between">
                        <span className="text-xs text-secondary">
                          Engine: {entry.response.engine || 'Verified Engine'} {entry.response.model ? `· ${entry.response.model}` : ''}
                        </span>
                        {entry.response.latencyMs != null && (
                          <span className="text-xs text-secondary">{entry.response.latencyMs}ms</span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="card mb-20 p-24 text-center ai-starter-hero-card">
              <div className="ai-starter-hero-icon mb-12">
                <Sparkles size={26} className="text-emerald" />
              </div>
              <h3 className="ai-starter-hero-title">Ready for Analytical Questions</h3>
              <p className="ai-starter-hero-desc">
                Ask specific questions about <strong>{sheetName}</strong> or choose an instant audit scenario below to trigger real-time dataset analysis.
              </p>
              <div className="ai-quick-starter-grid">
                <button
                  type="button"
                  className="ai-starter-card"
                  onClick={() => handleAsk("What are the main data quality issues, missing values, and anomalies in this sheet?")}
                  disabled={running}
                >
                  <div className="ai-starter-card-top flex-between">
                    <div className="ai-starter-icon-box">
                      <Shield size={18} />
                    </div>
                    <span className="ai-starter-action-hint">
                      Audit <ArrowRight size={12} />
                    </span>
                  </div>
                  <div className="ai-starter-title">Data Quality Audit</div>
                  <div className="ai-starter-desc">Inspect missing cells, duplicate records, and schema validity issues.</div>
                </button>

                <button
                  type="button"
                  className="ai-starter-card"
                  onClick={() => handleAsk("Are there any significant outliers or extreme values in numerical columns?")}
                  disabled={running}
                >
                  <div className="ai-starter-card-top flex-between">
                    <div className="ai-starter-icon-box">
                      <Zap size={18} />
                    </div>
                    <span className="ai-starter-action-hint">
                      Scan <ArrowRight size={12} />
                    </span>
                  </div>
                  <div className="ai-starter-title">Outlier & Anomaly Scan</div>
                  <div className="ai-starter-desc">Detect statistical deviations and potential data entry typos.</div>
                </button>

                <button
                  type="button"
                  className="ai-starter-card"
                  onClick={() => handleAsk("Summarize key trends, record distributions, and notable column patterns.")}
                  disabled={running}
                >
                  <div className="ai-starter-card-top flex-between">
                    <div className="ai-starter-icon-box">
                      <Lightbulb size={18} />
                    </div>
                    <span className="ai-starter-action-hint">
                      Summarize <ArrowRight size={12} />
                    </span>
                  </div>
                  <div className="ai-starter-title">Summary & Patterns</div>
                  <div className="ai-starter-desc">Generate narrative summaries and distributions across key dimensions.</div>
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {tab === 'insights' && (
        <div className="automatic-insights-view">
          {loadingProfile ? (
            <div className="card p-24 text-center">
              <div className="spinner mb-12" style={{ margin: '0 auto' }} />
              <h4>Analyzing Sheet Structure & Computing Distribution Metrics...</h4>
              <p className="text-secondary" style={{ fontSize: 13 }}>
                Evaluating null rates, standard deviations, duplicates, and category distributions.
              </p>
            </div>
          ) : profileError ? (
            <div className="alert alert-error mb-20">
              <AlertCircle size={16} />
              <span>{profileError}</span>
            </div>
          ) : profile ? (
            <>
              <div className="kpi-grid mb-20">
                <div className="kpi-card">
                  <div className="kpi-value">{profile.totalRows.toLocaleString()}</div>
                  <div className="kpi-label">Total Records</div>
                </div>
                <div className="kpi-card">
                  <div className="kpi-value">{profile.totalColumns}</div>
                  <div className="kpi-label">Columns Profiled</div>
                </div>
                <div className="kpi-card">
                  <div className="kpi-value" style={{ color: profile.completenessPercentage > 95 ? 'var(--emerald)' : '#f59e0b' }}>
                    {profile.completenessPercentage}%
                  </div>
                  <div className="kpi-label">Data Completeness</div>
                </div>
                <div className="kpi-card">
                  <div className="kpi-value" style={{ color: profile.duplicateRowsCount > 0 ? '#ef4444' : 'var(--emerald)' }}>
                    {profile.duplicateRowsCount}
                  </div>
                  <div className="kpi-label">Duplicates ({profile.duplicatePercentage}%)</div>
                </div>
              </div>

              {profile.qualityReport && (
                <div className="card p-24 mb-24 data-quality-score-card" style={{ border: '1px solid var(--border-color)', background: 'var(--card-bg)' }}>
                  <div className="flex-between mb-20" style={{ alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
                    <div className="flex-gap" style={{ alignItems: 'center', gap: 16 }}>
                      <div
                        style={{
                          width: 58,
                          height: 58,
                          borderRadius: 14,
                          background: profile.qualityReport.overallScore >= 80 ? 'rgba(16,185,129,0.15)' : (profile.qualityReport.overallScore >= 60 ? 'rgba(245,158,11,0.15)' : 'rgba(239,68,68,0.15)'),
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 700,
                          flexShrink: 0,
                          border: `1px solid ${profile.qualityReport.overallScore >= 80 ? 'rgba(16,185,129,0.3)' : (profile.qualityReport.overallScore >= 60 ? 'rgba(245,158,11,0.3)' : 'rgba(239,68,68,0.3)')}`,
                          color: profile.qualityReport.overallScore >= 80 ? 'var(--emerald)' : (profile.qualityReport.overallScore >= 60 ? '#f59e0b' : '#ef4444')
                        }}
                      >
                        <span style={{ fontSize: 22, lineHeight: 1.1, fontWeight: 800 }}>{profile.qualityReport.grade}</span>
                        <span style={{ fontSize: 10, opacity: 0.85, fontWeight: 600 }}>{profile.qualityReport.overallScore}/100</span>
                      </div>
                      <div>
                        <div className="flex-gap" style={{ alignItems: 'center', gap: 10 }}>
                          <h3 style={{ fontSize: 17, margin: 0, fontWeight: 700, color: 'var(--text-primary)' }}>AI Data Quality Score</h3>
                          <span className={`badge ${profile.qualityReport.status === 'EXCELLENT' ? 'badge-success' : (profile.qualityReport.status === 'GOOD' ? 'badge-info' : 'badge-warning')}`}>
                            {profile.qualityReport.status}
                          </span>
                        </div>
                        <p className="text-secondary mt-6" style={{ fontSize: 12.5, margin: 0, lineHeight: 1.5 }}>
                          {profile.qualityReport.summaryText}
                        </p>
                      </div>
                    </div>

                    <div className="flex-gap" style={{ alignItems: 'center', gap: 8 }}>
                      {profile.qualityReport.totalIssuesCount === 0 ? (
                        <span className="badge badge-success" style={{ fontSize: 11.5, padding: '5px 10px', display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                          <CheckCircle size={13} /> Zero Quality Issues Detected
                        </span>
                      ) : (
                        <>
                          <span className="badge badge-warning" style={{ fontSize: 11.5, padding: '5px 10px' }}>
                            {profile.qualityReport.totalIssuesCount} Issues Identified
                          </span>
                          {profile.qualityReport.highSeverityCount > 0 && (
                            <span className="badge badge-danger" style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444', fontSize: 11.5, padding: '5px 10px' }}>
                              {profile.qualityReport.highSeverityCount} High Severity
                            </span>
                          )}
                        </>
                      )}
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 14, marginBottom: 20 }}>
                    <div style={{ padding: '12px 14px', background: 'var(--bg)', borderRadius: 10, border: '1px solid var(--border)' }}>
                      <div className="flex-between mb-6" style={{ fontSize: 11.5 }}>
                        <span className="text-secondary font-medium">Completeness</span>
                        <strong style={{ color: 'var(--text-primary)' }}>{profile.qualityReport.completenessScore}%</strong>
                      </div>
                      <div style={{ height: 7, background: 'rgba(148, 163, 184, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${profile.qualityReport.completenessScore}%`, height: '100%', background: 'var(--emerald)', borderRadius: 4 }} />
                      </div>
                    </div>

                    <div style={{ padding: '12px 14px', background: 'var(--bg)', borderRadius: 10, border: '1px solid var(--border)' }}>
                      <div className="flex-between mb-6" style={{ fontSize: 11.5 }}>
                        <span className="text-secondary font-medium">Uniqueness</span>
                        <strong style={{ color: 'var(--text-primary)' }}>{profile.qualityReport.uniquenessScore}%</strong>
                      </div>
                      <div style={{ height: 7, background: 'rgba(148, 163, 184, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${profile.qualityReport.uniquenessScore}%`, height: '100%', background: '#3b82f6', borderRadius: 4 }} />
                      </div>
                    </div>

                    <div style={{ padding: '12px 14px', background: 'var(--bg)', borderRadius: 10, border: '1px solid var(--border)' }}>
                      <div className="flex-between mb-6" style={{ fontSize: 11.5 }}>
                        <span className="text-secondary font-medium">Consistency</span>
                        <strong style={{ color: 'var(--text-primary)' }}>{profile.qualityReport.consistencyScore}%</strong>
                      </div>
                      <div style={{ height: 7, background: 'rgba(148, 163, 184, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${profile.qualityReport.consistencyScore}%`, height: '100%', background: '#8b5cf6', borderRadius: 4 }} />
                      </div>
                    </div>

                    <div style={{ padding: '12px 14px', background: 'var(--bg)', borderRadius: 10, border: '1px solid var(--border)' }}>
                      <div className="flex-between mb-6" style={{ fontSize: 11.5 }}>
                        <span className="text-secondary font-medium">Validity</span>
                        <strong style={{ color: 'var(--text-primary)' }}>{profile.qualityReport.validityScore}%</strong>
                      </div>
                      <div style={{ height: 7, background: 'rgba(148, 163, 184, 0.15)', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${profile.qualityReport.validityScore}%`, height: '100%', background: '#f59e0b', borderRadius: 4 }} />
                      </div>
                    </div>
                  </div>

                  {profile.qualityReport.issues?.length > 0 && (
                    <div className="quality-issues-box">
                      <div className="quality-issues-header">
                        <div className="flex-gap" style={{ alignItems: 'center', gap: 8 }}>
                          <Shield size={16} className="text-emerald" />
                          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', letterSpacing: '0.2px' }}>
                            Actionable Data Hygiene Recommendations:
                          </span>
                        </div>
                        <span className="text-xs text-secondary" style={{ fontWeight: 500 }}>
                          {profile.qualityReport.issues.length} {profile.qualityReport.issues.length === 1 ? 'Action Recommended' : 'Actions Recommended'}
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        {profile.qualityReport.issues.map((iss) => (
                          <div
                            key={iss.id}
                            className="quality-issue-card"
                            style={{
                              borderLeft: `4px solid ${iss.severity === 'HIGH' ? '#ef4444' : (iss.severity === 'MEDIUM' ? '#f59e0b' : '#3b82f6')}`
                            }}
                          >
                            <div className="quality-issue-main">
                              <div className="quality-issue-meta">
                                <span className={`badge ${iss.severity === 'HIGH' ? 'badge-warning' : 'badge-neutral'}`} style={{ fontSize: 10.5, fontWeight: 700 }}>
                                  {iss.dimension}
                                </span>
                                {iss.column && (
                                  <span className="badge badge-neutral" style={{ fontSize: 10.5 }}>Col: {iss.column}</span>
                                )}
                                <span className="quality-issue-title">{iss.title}</span>
                              </div>
                              <p className="quality-issue-desc">
                                {iss.description} — <em style={{ color: 'var(--text-primary)', fontStyle: 'normal', fontWeight: 500 }}>{iss.recommendation}</em>
                              </p>
                            </div>

                            {iss.operation && (
                              <button
                                className="quality-autofix-btn"
                                onClick={() => {
                                  if (iss.operation === 'REMOVE_DUPLICATES') {
                                    requestAction('remove duplicates', true, 'Remove all duplicate rows');
                                  } else if (iss.operation === 'TRIM') {
                                    requestAction(`trim whitespace on column ${iss.column}`, true, `Trim whitespace on column ${iss.column}`);
                                  } else if (iss.operation === 'HANDLE_MISSING_VALUES') {
                                    requestAction(`handle missing on column ${iss.column}`, true, `Fill missing values on column ${iss.column}`);
                                  }
                                }}
                                disabled={running}
                              >
                                <Zap size={13} className="text-emerald" />
                                <span>Auto-Fix</span>
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              <div className="insights-feed mb-24">
                <div className="flex-between mb-16" style={{ alignItems: 'center' }}>
                  <div className="flex-gap" style={{ alignItems: 'center', gap: 8 }}>
                    <Lightbulb size={18} className="text-emerald" />
                    <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                      Automated Findings ({profile.insights?.length || 0})
                    </h3>
                  </div>
                  <span className="text-xs text-secondary">
                    Key data facts with recommended actions
                  </span>
                </div>

                {profile.insights?.map((ins) => (
                  <div key={ins.id} className={`card insight-block-card mb-20 border-${ins.severity?.toLowerCase() || 'info'}`}>
                    <div className="insight-card-header flex-between mb-14" style={{ alignItems: 'center' }}>
                      <div className="flex-gap" style={{ alignItems: 'center', gap: 10 }}>
                        <span className={`badge badge-${ins.severity === 'WARNING' ? 'warning' : (ins.severity === 'SUCCESS' ? 'success' : 'info')}`} style={{ fontSize: 11, fontWeight: 700 }}>
                          {ins.category}
                        </span>
                        {ins.affectedColumn && (
                          <span className="badge badge-neutral" style={{ fontSize: 11 }}>Column: {ins.affectedColumn}</span>
                        )}
                        <h4 className="insight-card-title">{ins.title}</h4>
                      </div>
                      {ins.confidence && (
                        <span className="text-xs text-secondary font-medium">{(ins.confidence * 100).toFixed(0)}% confidence</span>
                      )}
                    </div>

                    <div className="insight-content-split">
                      <div className="fact-box">
                        <div className="box-tag">STATISTICAL FACT</div>
                        <p>{ins.statisticalFact}</p>
                      </div>
                      <div className="interpretation-box">
                        <div className="box-tag">POTENTIAL INTERPRETATION</div>
                        <p>{ins.interpretation}</p>
                      </div>
                    </div>

                    {ins.suggestedAction && (
                      <div className="insight-action-banner">
                        <div className="insight-action-info">
                          <div className="insight-action-tag">
                            <Sparkles size={13} />
                            <span>RECOMMENDED ACTION</span>
                          </div>
                          <p className="insight-action-text">{ins.suggestedAction}</p>
                        </div>
                        {ins.suggestedCommand && (
                          <button
                            className="insight-execute-btn"
                            onClick={() => requestAction(ins.suggestedCommand, true, ins.suggestedAction)}
                            disabled={running}
                          >
                            <Play size={12} style={{ fill: 'currentColor' }} />
                            <span>Execute Fix</span>
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </>
          ) : null}
        </div>
      )}

      {pendingAction && (
        <div className="auth-overlay">
          <div className="auth-card" style={{ maxWidth: 480, position: 'relative' }}>
            <button
              className="modal-close-btn btn-icon"
              onClick={() => setPendingAction(null)}
              aria-label="Close modal"
            >
              <X size={15} />
            </button>

            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 20 }}>
              <div style={{
                width: 44,
                height: 44,
                borderRadius: 12,
                background: 'rgba(16, 185, 129, 0.12)',
                color: 'var(--emerald)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <Zap size={22} />
              </div>
              <div>
                <h3 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 2px 0', color: 'var(--text-primary)' }}>
                  Confirm Automated Fix
                </h3>
                <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
                  Safe Execution · Target Sheet: <strong>{sheetName}</strong>
                </p>
              </div>
            </div>

            <div style={{ marginBottom: 20 }}>
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12, lineHeight: 1.5 }}>
                Tabufinery will automatically execute the following deterministic transformation pipeline on your dataset:
              </p>

              <div style={{
                background: 'var(--surface-secondary, rgba(0, 0, 0, 0.3))',
                borderRadius: 8,
                border: '1px solid var(--border)',
                padding: '12px 14px',
                fontFamily: 'var(--font-mono, monospace)',
                fontSize: 13,
                color: 'var(--emerald)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 12
              }}>
                <span style={{ color: 'var(--text-muted)', userSelect: 'none' }}>$</span>
                <span style={{ fontWeight: 600 }}>{pendingAction.cmd}</span>
              </div>

              {pendingAction.explanation && (
                <div style={{
                  padding: '10px 14px',
                  background: 'var(--card-bg, rgba(255, 255, 255, 0.03))',
                  borderRadius: 8,
                  border: '1px solid var(--border)',
                  fontSize: 12,
                  color: 'var(--text-secondary)',
                  lineHeight: 1.5,
                  marginBottom: 14
                }}>
                  <strong style={{ color: 'var(--text-primary)' }}>Planned Action:</strong> {pendingAction.explanation}
                </div>
              )}

              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '10px 12px',
                borderRadius: 8,
                background: 'rgba(245, 158, 11, 0.08)',
                border: '1px solid rgba(245, 158, 11, 0.25)',
                color: '#f59e0b',
                fontSize: 12
              }}>
                <Shield size={15} style={{ flexShrink: 0 }} />
                <span>Safeguard active: Updates active session workbook. Undo available in Data Workspace.</span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, borderTop: '1px solid var(--border)', paddingTop: 16 }}>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => setPendingAction(null)}
                disabled={running}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={() => executeAction(pendingAction.cmd)}
                disabled={running}
                style={{ background: 'var(--emerald)', borderColor: 'var(--emerald)', display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                {running ? (
                  <><div className="spinner" /> Applying Fix...</>
                ) : (
                  <><Zap size={13} /> Confirm & Apply Fix</>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
