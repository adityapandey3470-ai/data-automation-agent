import React, { useState } from 'react';
import { submitBugReport } from '../../services/api';
import { X, Check, AlertCircle, RefreshCw, Copy } from '../icons/Icons';

export default function BugReportForm({ onClose, onCancel, currentUser }) {
  const [description, setDescription] = useState('');
  const [stepsToReproduce, setStepsToReproduce] = useState('');
  const [severity, setSeverity] = useState('');
  const [userEmail, setUserEmail] = useState(currentUser?.email || '');
  const [submitting, setSubmitting] = useState(false);
  const [successResult, setSuccessResult] = useState(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  const validate = () => {
    const errors = {};
    const descTrim = description.trim();
    const stepsTrim = stepsToReproduce.trim();

    if (!descTrim) {
      errors.description = 'Description is required.';
    } else if (descTrim.length < 10) {
      errors.description = 'Description must be at least 10 characters explaining expected vs actual behaviour.';
    }

    if (!stepsTrim) {
      errors.steps = 'Steps to reproduce are required (write each step on a new line).';
    } else if (stepsTrim.length < 5) {
      errors.steps = 'Steps must be at least 5 characters long.';
    }

    if (!severity) {
      errors.severity = 'Please select a severity level (Low, Medium, High, or Critical).';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) {
      setError('Please resolve the highlighted fields before submitting.');
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const payload = {
        description: description.trim(),
        stepsToReproduce: stepsToReproduce.trim(),
        severity: severity,
        userEmail: userEmail.trim() || undefined
      };

      const res = await submitBugReport(payload);
      setSuccessResult({
        ...res.data,
        timestamp: new Date().toLocaleString(),
        severity: severity,
        reporter: currentUser ? `${currentUser.name} (${currentUser.email})` : (userEmail || 'Guest User')
      });
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to submit bug report. Please verify your connection and try again.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopyCode = () => {
    if (successResult?.ticketCode) {
      navigator.clipboard.writeText(successResult.ticketCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleReset = () => {
    setDescription('');
    setStepsToReproduce('');
    setSeverity('');
    setError(null);
    setFieldErrors({});
    setSuccessResult(null);
  };

  const handleInsertStepsTemplate = () => {
    if (!stepsToReproduce.trim()) {
      setStepsToReproduce(
        '1. Navigate to Data Workspace\n2. Upload dataset (e.g. sales.xlsx)\n3. Perform action (e.g. Clean Data -> Remove Duplicates)\n4. Observe unexpected error or behaviour'
      );
      if (fieldErrors.steps) {
        setFieldErrors(prev => ({ ...prev, steps: null }));
      }
    }
  };

  const handleClose = () => {
    if (onClose) onClose();
    else if (onCancel) onCancel();
    else handleReset();
  };

  const ProfessionalBugIcon = () => (
    <div className="bug-icon-badge" aria-hidden="true">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <rect width="10" height="14" x="7" y="5" rx="5" />
        <path d="m19 6-3.5 2" />
        <path d="m5 6 3.5 2" />
        <path d="m19 18-3.5-2" />
        <path d="m5 18 3.5-2" />
        <path d="M21 12h-4" />
        <path d="M3 12h4" />
        <path d="m10 3 1.5 2" />
        <path d="m14 3-1.5 2" />
      </svg>
    </div>
  );

  if (successResult) {
    return (
      <div className="bug-report-card">
        <div className="bug-report-header">
          <div className="bug-report-title-wrap">
            <ProfessionalBugIcon />
            <div>
              <h3>Bug Report Submitted</h3>
              <span className="bug-header-sub">Reference ID assigned by server</span>
            </div>
          </div>
          {(onClose || onCancel) && (
            <button className="bug-close-btn" onClick={handleClose} title="Close" aria-label="Close dialog">
              <X size={18} />
            </button>
          )}
        </div>

        <div className="bug-success-body">
          <div className="bug-success-icon-wrap">
            <Check size={28} className="text-emerald" />
          </div>
          <h4>Report Logged in System</h4>
          <p className="bug-success-msg">
            Our engineering team has received your reproduction steps and will investigate promptly.
          </p>

          <div className="bug-ticket-badge-box">
            <div className="text-xs uppercase font-semibold text-secondary">Unique Report ID</div>
            <div className="bug-ticket-badge-row">
              <span className="bug-ticket-badge">{successResult.ticketCode}</span>
              <button
                type="button"
                className="btn btn-xs btn-outline bug-copy-btn"
                onClick={handleCopyCode}
                title="Copy Report ID"
              >
                {copied ? <Check size={12} className="text-emerald" /> : <Copy size={12} />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
          </div>

          <div className="bug-meta-summary">
            <div className="bug-meta-row">
              <span>Severity:</span>
              <strong className={`bug-severity-pill severity-${(successResult.severity || '').toLowerCase()}`}>
                {successResult.severity}
              </strong>
            </div>
            <div className="bug-meta-row">
              <span>Reported By:</span>
              <strong>{successResult.reporter}</strong>
            </div>
            <div className="bug-meta-row">
              <span>Timestamp:</span>
              <strong>{successResult.timestamp}</strong>
            </div>
          </div>

          <div className="bug-form-actions mt-24">
            <button className="btn-bug-cancel" onClick={handleReset}>
              <RefreshCw size={14} /> Submit Another Bug
            </button>
            {(onClose || onCancel) && (
              <button className="btn-bug-submit" onClick={handleClose}>
                Done
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bug-report-card">
      <div className="bug-report-header">
        <div className="bug-report-title-wrap">
          <ProfessionalBugIcon />
          <div>
            <h3>Bug Report</h3>
            <span className="bug-header-sub">Direct channel to technical engineering</span>
          </div>
        </div>
        {(onClose || onCancel) && (
          <button className="bug-close-btn" onClick={handleClose} title="Close" aria-label="Close dialog">
            <X size={18} />
          </button>
        )}
      </div>

      <p className="bug-report-subtitle">
        Please provide details of the bug to help our team replicate and resolve it quickly:
      </p>

      {error && (
        <div className="bug-error-banner" role="alert">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className={`bug-form-group ${fieldErrors.description ? 'has-error' : ''}`}>
          <div className="bug-label-row">
            <label htmlFor="bug-desc">
              Description <span className="required-star">*</span>
            </label>
            <span className="bug-field-hint">Detail expected vs actual behaviour</span>
          </div>
          <textarea
            id="bug-desc"
            placeholder={
`Expected Behaviour:
What did you expect to happen? (e.g. Clicking 'Sort' sorts rows by Date descending)

Actual Behaviour:
What actually happened? (e.g. Page froze, or error notification appeared)`}
            rows={5}
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
              if (fieldErrors.description) {
                setFieldErrors(prev => ({ ...prev, description: null }));
              }
            }}
            disabled={submitting}
            className={fieldErrors.description ? 'input-error' : ''}
            aria-required="true"
          />
          {fieldErrors.description && (
            <div className="bug-field-error-msg">{fieldErrors.description}</div>
          )}
        </div>

        <div className={`bug-form-group ${fieldErrors.steps ? 'has-error' : ''}`}>
          <div className="bug-label-row">
            <label htmlFor="bug-steps">
              Steps to Reproduce <span className="required-star">*</span>
            </label>
            <button
              type="button"
              className="btn-text-action"
              onClick={handleInsertStepsTemplate}
              title="Insert sample step list"
            >
              + Step Template
            </button>
          </div>
          <textarea
            id="bug-steps"
            placeholder={
`1. Navigate to Data Workspace and upload 'sales.xlsx'
2. Click on 'Clean Data' in the right-hand panel
3. Select operation 'Remove Duplicates' and click 'Apply Transformation'
4. Observe error message displayed on screen`}
            rows={5}
            value={stepsToReproduce}
            onChange={(e) => {
              setStepsToReproduce(e.target.value);
              if (fieldErrors.steps) {
                setFieldErrors(prev => ({ ...prev, steps: null }));
              }
            }}
            disabled={submitting}
            className={fieldErrors.steps ? 'input-error' : ''}
            aria-required="true"
          />
          <div className="bug-field-help">Write each step on a separate line (press Enter after each step).</div>
          {fieldErrors.steps && (
            <div className="bug-field-error-msg">{fieldErrors.steps}</div>
          )}
        </div>

        <div className={`bug-form-group ${fieldErrors.severity ? 'has-error' : ''}`}>
          <label htmlFor="bug-severity">
            Severity Level <span className="required-star">*</span>
          </label>
          <select
            id="bug-severity"
            value={severity}
            onChange={(e) => {
              setSeverity(e.target.value);
              if (fieldErrors.severity) {
                setFieldErrors(prev => ({ ...prev, severity: null }));
              }
            }}
            disabled={submitting}
            className={fieldErrors.severity ? 'input-error' : ''}
            aria-required="true"
          >
            <option value="" disabled>Select a severity level</option>
            <option value="Low">Low — Minor cosmetic or visual typo</option>
            <option value="Medium">Medium — Feature defect, but workaround exists</option>
            <option value="High">High — Core functionality broken or inaccurate data</option>
            <option value="Critical">Critical — System crash, freeze, or data loss</option>
          </select>
          {fieldErrors.severity && (
            <div className="bug-field-error-msg">{fieldErrors.severity}</div>
          )}
        </div>

        {!currentUser && (
          <div className="bug-form-group">
            <label htmlFor="bug-email">Contact Email (Optional for guest follow-up)</label>
            <input
              id="bug-email"
              type="email"
              placeholder="name@company.com"
              value={userEmail}
              onChange={(e) => setUserEmail(e.target.value)}
              disabled={submitting}
            />
          </div>
        )}

        <div className="bug-form-actions">
          <button
            type="button"
            className="btn-bug-cancel"
            onClick={handleClose}
            disabled={submitting}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn-primary btn-bug-submit"
            disabled={submitting}
          >
            {submitting ? (
              <>
                <RefreshCw size={14} className="spin" /> Submitting...
              </>
            ) : (
              'Submit'
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
