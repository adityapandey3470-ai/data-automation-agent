import React from 'react';
import { AlertCircle, RefreshCw, Bug } from '../icons/Icons';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught rendering error:', error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    if (this.props.onReset) {
      this.props.onReset();
    }
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return typeof this.props.fallback === 'function'
          ? this.props.fallback(this.state.error, this.handleReset)
          : this.props.fallback;
      }

      return (
        <div className="error-boundary-container" role="alert">
          <div className="error-boundary-card">
            <div className="error-boundary-icon">
              <AlertCircle size={32} className="text-red-500" />
            </div>
            <h3 className="error-boundary-title">
              {this.props.title || 'Something went wrong rendering this section'}
            </h3>
            <p className="error-boundary-desc">
              {this.state.error?.message || 'An unexpected rendering error occurred. Your workbook session remains intact.'}
            </p>
            <div className="error-boundary-actions">
              <button
                type="button"
                className="btn btn-primary btn-sm flex-gap"
                onClick={this.handleReset}
              >
                <RefreshCw size={14} /> Try Again
              </button>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => window.location.reload()}
              >
                Reload Application
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
