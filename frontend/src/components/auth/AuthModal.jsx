import React, { useState, useEffect } from 'react';
import { signupUser, loginUser, forgotPassword, resetPassword, setStoredAuth, getErrorMessage } from '../../services/api';
import { LogoMark, Shield, X, AlertCircle, CheckCircle, Zap, Eye, EyeOff } from '../icons/Icons';

export default function AuthModal({ isOpen, onClose, onAuthSuccess, initialMode = 'login', forced = false }) {
  const [mode, setMode] = useState(initialMode); // 'login' | 'signup' | 'forgot' | 'reset'
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resetToken, setResetToken] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [smtpNotice, setSmtpNotice] = useState(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const urlToken = params.get('resetToken');
    if (urlToken) {
      setResetToken(urlToken);
      setMode('reset');
    }
  }, []);

  useEffect(() => {
    setMode(initialMode);
    setError(null);
    setSuccess(null);
    setSmtpNotice(null);
    setShowPassword(false);
    setShowConfirmPassword(false);
  }, [initialMode, isOpen]);

  if (!isOpen) return null;

  const handleLogin = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);

    try {
      const res = await loginUser(email.trim(), password);
      const { token, user } = res.data;
      setStoredAuth(token, user);
      setSuccess(`Welcome back, ${user.name}!`);

      setTimeout(() => {
        onAuthSuccess(user);
        if (onClose) onClose();
      }, 500);
    } catch (err) {
      setError(getErrorMessage(err, 'Login failed. Please check your credentials and try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (!name.trim()) {
      setError('Please enter your full name.');
      return;
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match. Please re-type your password confirmation.');
      return;
    }

    setLoading(true);
    try {
      const res = await signupUser(name.trim(), email.trim(), password, confirmPassword);
      const { token, user } = res.data;
      setStoredAuth(token, user);
      setSuccess(`Account created successfully! Welcome to Tabufinery, ${user.name}.`);

      setTimeout(() => {
        onAuthSuccess(user);
        if (onClose) onClose();
      }, 600);
    } catch (err) {
      setError(getErrorMessage(err, 'Signup failed. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleForgotPassword = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setSmtpNotice(null);
    setLoading(true);

    try {
      const res = await forgotPassword(email.trim());
      setSuccess(res.data.message || 'Password reset link generated.');

      if (!res.data.smtpConfigured) {
        setSmtpNotice({
          instructions: res.data.smtpInstructions || 'SMTP server is not configured in this environment. Set SMTP_HOST to deliver physical emails.',
          devToken: res.data.devResetToken
        });
        if (res.data.devResetToken) {
          setResetToken(res.data.devResetToken);
        }
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to process password reset. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    if (!resetToken.trim()) {
      setError('Please provide your reset token.');
      return;
    }
    if (password.length < 6) {
      setError('New password must be at least 6 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      const res = await resetPassword(resetToken.trim(), password, confirmPassword);
      setSuccess(res.data.message || 'Password reset successfully! Please sign in with your new credentials.');
      setTimeout(() => {
        setMode('login');
        setPassword('');
        setConfirmPassword('');
        setResetToken('');
        setSuccess('Password updated. You may now sign in.');
      }, 1500);
    } catch (err) {
      setError(getErrorMessage(err, 'Password reset failed. The token may be invalid or expired.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-overlay">
      <div className="auth-card">
        {!forced && onClose && (
          <button
            className="modal-close-btn btn-icon"
            onClick={onClose}
            aria-label="Close modal"
          >
            <X size={15} />
          </button>
        )}

        <div className="auth-header-brand">
          <div className="auth-brand-logo">
            <LogoMark size={36} />
          </div>
          <h2 className="auth-title">
            {mode === 'login' && 'Sign In to Tabufinery'}
            {mode === 'signup' && 'Create Your Account'}
            {mode === 'forgot' && 'Reset Your Password'}
            {mode === 'reset' && 'Set New Password'}
          </h2>
          <p className="auth-subtitle">
            {forced
              ? 'Authentication is required to access data automation tools and workspaces.'
              : 'Enterprise data refinery, AI analytics, and multi-tenant persistence.'}
          </p>
        </div>

        {error && (
          <div className="alert alert-error mb-16 flex-gap" style={{ alignItems: 'flex-start' }}>
            <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 12 }}>{error}</span>
          </div>
        )}

        {success && (
          <div
            className="alert alert-success mb-16 flex-gap"
            style={{
              background: 'rgba(16, 185, 129, 0.12)',
              color: 'var(--emerald)',
              border: '1px solid rgba(16, 185, 129, 0.25)',
              alignItems: 'flex-start'
            }}
          >
            <CheckCircle size={15} style={{ flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 12 }}>{success}</span>
          </div>
        )}

        {/* SMTP Configuration Notice (Clear SaaS compliance) */}
        {smtpNotice && (
          <div
            className="card p-12 mb-16"
            style={{
              background: 'var(--surface-secondary)',
              border: '1px dashed #f59e0b',
              borderRadius: 8,
              fontSize: 12
            }}
          >
            <div className="flex-gap mb-4" style={{ color: '#f59e0b', fontWeight: 600 }}>
              <Zap size={13} /> SMTP Server Notice
            </div>
            <p style={{ margin: '0 0 8px 0', color: 'var(--text-secondary)' }}>
              {smtpNotice.instructions}
            </p>
            {smtpNotice.devToken && (
              <div style={{ marginTop: 6 }}>
                <span className="text-secondary" style={{ fontSize: 11 }}>Development Reset Token:</span>
                <div
                  style={{
                    fontFamily: 'monospace',
                    fontSize: 11,
                    padding: '6px 8px',
                    background: 'var(--input-bg)',
                    border: '1px solid var(--border)',
                    borderRadius: 4,
                    wordBreak: 'break-all',
                    marginTop: 2
                  }}
                >
                  {smtpNotice.devToken}
                </div>
                <button
                  type="button"
                  className="btn btn-xs btn-outline mt-8"
                  onClick={() => {
                    setResetToken(smtpNotice.devToken);
                    setMode('reset');
                  }}
                >
                  Proceed to Reset with Token &rarr;
                </button>
              </div>
            )}
          </div>
        )}

        {(mode === 'login' || mode === 'signup') && (
          <div className="auth-tab-group">
            <button
              type="button"
              className={`auth-tab-btn ${mode === 'login' ? 'active' : ''}`}
              onClick={() => { setMode('login'); setError(null); }}
            >
              Sign In
            </button>
            <button
              type="button"
              className={`auth-tab-btn ${mode === 'signup' ? 'active' : ''}`}
              onClick={() => { setMode('signup'); setError(null); }}
            >
              Create Account
            </button>
          </div>
        )}

        {mode === 'login' && (
          <form onSubmit={handleLogin}>
            <div className="auth-form-group">
              <label htmlFor="login-email">Email Address</label>
              <input
                id="login-email"
                name="email"
                type="email"
                className="input-field"
                placeholder="name@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={loading}
                autoComplete="email"
                autoFocus
              />
            </div>

            <div className="auth-form-group">
              <div className="flex-between mb-4">
                <label htmlFor="login-password" style={{ margin: 0 }}>Password</label>
                <button
                  type="button"
                  className="auth-link"
                  onClick={() => { setMode('forgot'); setError(null); setSuccess(null); }}
                  tabIndex={-1}
                >
                  Forgot password?
                </button>
              </div>
              <div className="password-input-wrap">
                <input
                  id="login-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className="input-field"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  disabled={loading}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(prev => !prev)}
                  title={showPassword ? 'Hide password' : 'Show password'}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-btn-primary"
              disabled={loading}
            >
              {loading ? <div className="spinner" /> : 'Sign In'}
            </button>
          </form>
        )}

        {mode === 'signup' && (
          <form onSubmit={handleSignup}>
            <div className="auth-form-group">
              <label htmlFor="signup-name">Full Name</label>
              <input
                id="signup-name"
                name="name"
                type="text"
                className="input-field"
                placeholder="e.g. Alex Mercer"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={loading}
                autoComplete="name"
                autoFocus
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="signup-email">Work Email</label>
              <input
                id="signup-email"
                name="email"
                type="email"
                className="input-field"
                placeholder="name@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={loading}
                autoComplete="email"
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="signup-password">Password</label>
              <div className="password-input-wrap">
                <input
                  id="signup-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className="input-field"
                  placeholder="At least 6 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={6}
                  disabled={loading}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(prev => !prev)}
                  title={showPassword ? 'Hide password' : 'Show password'}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <div className="auth-form-group">
              <label htmlFor="signup-confirm-password">Confirm Password</label>
              <div className="password-input-wrap">
                <input
                  id="signup-confirm-password"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className="input-field"
                  placeholder="Re-enter password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  minLength={6}
                  disabled={loading}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowConfirmPassword(prev => !prev)}
                  title={showConfirmPassword ? 'Hide password' : 'Show password'}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {confirmPassword && password !== confirmPassword && (
                <span className="form-hint" style={{ color: '#ef4444' }}>
                  Passwords do not match
                </span>
              )}
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-btn-primary"
              disabled={loading}
            >
              {loading ? <div className="spinner" /> : 'Create Free Account'}
            </button>
          </form>
        )}

        {mode === 'forgot' && (
          <form onSubmit={handleForgotPassword}>
            <p className="text-secondary mb-16" style={{ fontSize: 13, lineHeight: 1.5 }}>
              Enter the email address registered with your account. We will send a secure single-use password reset link.
            </p>

            <div className="auth-form-group">
              <label htmlFor="forgot-email">Email Address</label>
              <input
                id="forgot-email"
                name="email"
                type="email"
                className="input-field"
                placeholder="name@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={loading}
                autoComplete="email"
                autoFocus
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-btn-primary"
              disabled={loading}
            >
              {loading ? <div className="spinner" /> : 'Send Reset Link'}
            </button>

            <div className="auth-footer-links">
              <div>
                Have a reset token?{' '}
                <button
                  type="button"
                  className="auth-link"
                  onClick={() => { setMode('reset'); setError(null); }}
                >
                  Enter token here
                </button>
              </div>
              <button
                type="button"
                className="auth-link"
                onClick={() => { setMode('login'); setError(null); }}
              >
                &larr; Back to Sign In
              </button>
            </div>
          </form>
        )}

        {mode === 'reset' && (
          <form onSubmit={handleResetPassword}>
            <p className="text-secondary mb-16" style={{ fontSize: 13, lineHeight: 1.5 }}>
              Enter your reset token and choose a new secure password.
            </p>

            <div className="auth-form-group">
              <label htmlFor="reset-token">Reset Token</label>
              <input
                id="reset-token"
                name="token"
                type="text"
                className="input-field"
                placeholder="Paste token received in email"
                value={resetToken}
                onChange={(e) => setResetToken(e.target.value)}
                required
                disabled={loading}
                autoFocus
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="reset-password">New Password</label>
              <div className="password-input-wrap">
                <input
                  id="reset-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className="input-field"
                  placeholder="At least 6 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={6}
                  disabled={loading}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(prev => !prev)}
                  title={showPassword ? 'Hide password' : 'Show password'}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <div className="auth-form-group">
              <label htmlFor="reset-confirm-password">Confirm New Password</label>
              <div className="password-input-wrap">
                <input
                  id="reset-confirm-password"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className="input-field"
                  placeholder="Re-type new password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  minLength={6}
                  disabled={loading}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowConfirmPassword(prev => !prev)}
                  title={showConfirmPassword ? 'Hide password' : 'Show password'}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-btn-primary"
              disabled={loading}
            >
              {loading ? <div className="spinner" /> : 'Update Password'}
            </button>

            <div className="auth-footer-links">
              <button
                type="button"
                className="auth-link"
                onClick={() => { setMode('login'); setError(null); }}
              >
                &larr; Back to Sign In
              </button>
            </div>
          </form>
        )}

        {(mode === 'login' || mode === 'signup') && (
          <div className="auth-footer-links">
            <div>
              {mode === 'login' ? "Don't have an account? " : 'Already registered? '}
              <button
                type="button"
                className="auth-link"
                onClick={() => {
                  setMode(mode === 'login' ? 'signup' : 'login');
                  setError(null);
                  setSuccess(null);
                }}
              >
                {mode === 'login' ? 'Create an account' : 'Sign in to account'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
