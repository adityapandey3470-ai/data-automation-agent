import React, { useState, useEffect, useCallback } from 'react';
import {
  signupUser, loginUser, forgotPassword, resetPassword,
  verifyEmail, resendVerification, setStoredAuth, getErrorMessage
} from '../../services/api';
import {
  LogoMark, Shield, X, AlertCircle, CheckCircle, Zap,
  Eye, EyeOff, MailCheck, RefreshCw, Mail
} from '../icons/Icons';

export default function AuthModal({ isOpen, onClose, onAuthSuccess, initialMode = 'login', forced = false }) {
  const [mode, setMode] = useState(initialMode);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [resetToken, setResetToken] = useState('');
  const [verifyToken, setVerifyToken] = useState('');
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [verificationNotice, setVerificationNotice] = useState(null);

  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [smtpNotice, setSmtpNotice] = useState(null);

  const executeVerification = useCallback(async (tokenStr) => {
    if (!tokenStr) return;
    setLoading(true);
    setError(null);
    setSuccess(null);
    setMode('verifying');

    try {
      const res = await verifyEmail(tokenStr.trim());
      const { token: jwtToken, user, message } = res.data;
      if (jwtToken && user) {
        setStoredAuth(jwtToken, user);
      }
      setSuccess(message || 'Email successfully verified! Welcome to Tabufinery.');
      setMode('verify-success');

      if (typeof window !== 'undefined' && window.history?.replaceState) {
        const cleanUrl = window.location.pathname + window.location.hash;
        window.history.replaceState({}, document.title, cleanUrl);
      }

      setTimeout(() => {
        if (user && onAuthSuccess) {
          onAuthSuccess(user);
        }
        if (onClose) onClose();
      }, 1600);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to verify email. The activation link may be invalid or expired.'));
      setMode('verify-failed');
    } finally {
      setLoading(false);
    }
  }, [onAuthSuccess, onClose]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const urlResetToken = params.get('resetToken');
    if (urlResetToken) {
      setResetToken(urlResetToken);
      setMode('reset');
      return;
    }

    const urlVerifyToken = params.get('verifyToken');
    if (urlVerifyToken) {
      setVerifyToken(urlVerifyToken);
      executeVerification(urlVerifyToken);
    }
  }, [executeVerification]);

  useEffect(() => {
    if (mode !== 'verifying' && mode !== 'verify-success') {
      setMode(initialMode);
    }
    setError(null);
    setSuccess(null);
    setSmtpNotice(null);
    setShowPassword(false);
    setShowConfirmPassword(false);
  }, [initialMode, isOpen]);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

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
      const msg = getErrorMessage(err, 'Login failed. Please check your credentials and try again.');
      setError(msg);

      if (msg.toLowerCase().includes('verify your email')) {
        setRegisteredEmail(email.trim());
      }
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
      if (res.data?.emailVerificationRequired) {
        setRegisteredEmail(email.trim());
        setVerificationNotice({
          message: res.data.message || 'Please verify your email address to activate your account.',
          smtpConfigured: res.data.smtpConfigured
        });
        setMode('verify-pending');
      } else {
        const { token, user } = res.data;
        if (token) setStoredAuth(token, user);
        setSuccess(`Account created successfully! Welcome to Tabufinery, ${user?.name || ''}.`);
        setTimeout(() => {
          if (user) onAuthSuccess(user);
          if (onClose) onClose();
        }, 600);
      }
    } catch (err) {
      setError(getErrorMessage(err, 'Signup failed. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async (targetEmail) => {
    const emailToUse = targetEmail || registeredEmail || email;
    if (!emailToUse || !emailToUse.trim()) {
      setError('Please provide an email address.');
      return;
    }

    setResendLoading(true);
    setError(null);
    try {
      const res = await resendVerification(emailToUse.trim());
      setSuccess(res.data.message || 'A new verification link has been sent to your email.');
      setResendCooldown(60);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to resend verification email. Please try again.'));
    } finally {
      setResendLoading(false);
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
      <div className="auth-card" style={{ maxWidth: 460 }}>
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
            {mode === 'verify-pending' && 'Verify Your Email'}
            {mode === 'verifying' && 'Verifying Email'}
            {mode === 'verify-success' && 'Account Activated'}
            {mode === 'verify-failed' && 'Verification Required'}
            {mode === 'resend-verify' && 'Resend Verification'}
          </h2>
          <p className="auth-subtitle">
            {mode === 'verify-pending'
              ? 'A confirmation link has been dispatched to your mailbox.'
              : mode === 'verifying'
              ? 'Validating single-use cryptographic verification token...'
              : forced
              ? 'Authentication is required to access data automation tools and workspaces.'
              : 'Enterprise data refinery, AI analytics, and multi-tenant persistence.'}
          </p>
        </div>

        {error && (
          <div className="alert alert-error mb-16 flex-gap" style={{ alignItems: 'flex-start' }}>
            <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 2 }} />
            <div style={{ fontSize: 12, flex: 1 }}>
              <div>{error}</div>
              {error.toLowerCase().includes('verify your email') && (
                <button
                  type="button"
                  className="auth-link mt-4"
                  style={{ display: 'inline-block', fontWeight: 600, color: 'var(--emerald)' }}
                  onClick={() => {
                    setError(null);
                    setRegisteredEmail(email.trim());
                    setMode('resend-verify');
                  }}
                >
                  Resend verification link &rarr;
                </button>
              )}
            </div>
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

        {mode === 'verify-pending' && (
          <div style={{ textAlign: 'center', padding: '12px 4px' }}>
            <div style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'rgba(16, 185, 129, 0.12)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 16,
              border: '1px solid rgba(16, 185, 129, 0.25)'
            }}>
              <MailCheck size={32} className="text-emerald" />
            </div>

            <h3 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px 0', color: 'var(--text-primary)' }}>
              Check your inbox
            </h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, margin: '0 0 16px 0' }}>
              We've dispatched an account activation link to:
            </p>

            <div style={{
              fontFamily: 'monospace',
              fontSize: 13,
              fontWeight: 600,
              padding: '10px 14px',
              background: 'var(--surface-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              color: 'var(--text-primary)',
              wordBreak: 'break-all',
              marginBottom: 16
            }}>
              {registeredEmail || email}
            </div>

            <p style={{ fontSize: 12, color: 'var(--text-tertiary)', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Click the link inside the email to verify your address and activate your account. If you don't see the email within 2 minutes, check your spam or junk folder.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <button
                type="button"
                className="btn btn-outline w-full flex-gap justify-center"
                onClick={() => handleResend(registeredEmail)}
                disabled={resendLoading || resendCooldown > 0}
                style={{ minHeight: 38 }}
              >
                <RefreshCw size={13} className={resendLoading ? 'spin' : ''} />
                {resendLoading
                  ? 'Sending Link...'
                  : resendCooldown > 0
                  ? `Resend available in ${resendCooldown}s`
                  : 'Resend Verification Email'}
              </button>

              <button
                type="button"
                className="auth-link mt-8"
                onClick={() => { setMode('login'); setError(null); }}
              >
                &larr; Back to Sign In
              </button>
            </div>
          </div>
        )}

        {mode === 'verifying' && (
          <div style={{ textAlign: 'center', padding: '36px 12px' }}>
            <div className="spinner" style={{ width: 36, height: 36, margin: '0 auto 18px auto', borderWidth: 3 }} />
            <h3 style={{ fontSize: 17, fontWeight: 700, margin: '0 0 8px 0' }}>
              Verifying your email address...
            </h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
              Confirming account credentials and establishing secure session.
            </p>
          </div>
        )}

        {mode === 'verify-success' && (
          <div style={{ textAlign: 'center', padding: '24px 12px' }}>
            <div style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'rgba(16, 185, 129, 0.15)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 16
            }}>
              <CheckCircle size={34} className="text-emerald" />
            </div>
            <h3 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px 0', color: 'var(--emerald)' }}>
              Email Successfully Verified!
            </h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Your account is now activated. Launching your data automation workspace...
            </p>
            <div className="spinner" style={{ margin: '0 auto' }} />
          </div>
        )}

        {mode === 'verify-failed' && (
          <div style={{ textAlign: 'center', padding: '16px 8px' }}>
            <div style={{
              width: 58,
              height: 58,
              borderRadius: '50%',
              background: 'rgba(239, 68, 68, 0.12)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 14
            }}>
              <AlertCircle size={30} style={{ color: '#ef4444' }} />
            </div>
            <h3 style={{ fontSize: 17, fontWeight: 700, margin: '0 0 8px 0', color: 'var(--text-primary)' }}>
              Verification Link Invalid or Expired
            </h3>
            <p style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Verification links expire after 24 hours. Request a new link below to activate your account.
            </p>

            <button
              type="button"
              className="btn btn-primary w-full"
              onClick={() => { setMode('resend-verify'); setError(null); }}
            >
              Request New Verification Link
            </button>

            <button
              type="button"
              className="auth-link mt-14"
              onClick={() => { setMode('login'); setError(null); }}
            >
              &larr; Back to Sign In
            </button>
          </div>
        )}

        {mode === 'resend-verify' && (
          <form onSubmit={(e) => { e.preventDefault(); handleResend(registeredEmail || email); }}>
            <p className="text-secondary mb-16" style={{ fontSize: 13, lineHeight: 1.5 }}>
              Enter your registered email address and we will dispatch a new account activation link.
            </p>

            <div className="auth-form-group">
              <label htmlFor="resend-email">Email Address</label>
              <input
                id="resend-email"
                name="email"
                type="email"
                className="input-field"
                placeholder="name@company.com"
                value={registeredEmail || email}
                onChange={(e) => {
                  setRegisteredEmail(e.target.value);
                  setEmail(e.target.value);
                }}
                required
                disabled={resendLoading}
                autoFocus
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary auth-btn-primary"
              disabled={resendLoading || resendCooldown > 0}
            >
              {resendLoading
                ? <div className="spinner" />
                : resendCooldown > 0
                ? `Wait ${resendCooldown}s`
                : 'Send Activation Link'}
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
