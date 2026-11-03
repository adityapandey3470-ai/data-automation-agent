import React, { useEffect, useState } from 'react';
import { LogoMark } from '../icons/Icons';

export default function SplashScreen({ onFinish, theme }) {
  const [fading, setFading] = useState(false);
  const activeTheme = theme || (typeof document !== 'undefined' ? document.documentElement.getAttribute('data-theme') : 'dark') || 'dark';

  useEffect(() => {
    const timer = setTimeout(() => {
      setFading(true);
      setTimeout(onFinish, 400);
    }, 1100);

    return () => clearTimeout(timer);
  }, [onFinish]);

  return (
    <div className={`splash-overlay ${fading ? 'fade-out' : ''}`} data-splash-theme={activeTheme}>
      <div className="splash-content">
        <div className="splash-logo-container">
          <LogoMark size={56} className="splash-logo" variant={activeTheme} />
        </div>
        <h1 className="splash-title">Tabufinery</h1>
        <p className="splash-tagline">Refine raw data. Automate insights.</p>
        <div className="splash-loader">
          <div className="splash-loader-bar" />
        </div>
      </div>
    </div>
  );
}
