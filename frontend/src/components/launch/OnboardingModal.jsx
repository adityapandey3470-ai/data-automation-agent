import React, { useState } from 'react';
import { LogoMark, Sparkles, Sliders, BarChart3, Download, ArrowRight, Check } from '../icons/Icons';

const FEATURES = [
  {
    icon: <Sliders size={22} className="feat-icon-svg text-emerald" />,
    title: 'Smart Data Cleaning',
    desc: 'Remove duplicate rows, handle missing values with 5 strategies, and trim or convert data types in one click.'
  },
  {
    icon: <Sparkles size={22} className="feat-icon-svg text-indigo" />,
    title: 'AI-Powered Analysis',
    desc: 'Query, sort, filter, and summarize your data using natural-language commands without writing complex formulas.'
  },
  {
    icon: <BarChart3 size={22} className="feat-icon-svg text-blue" />,
    title: 'Interactive Analytics',
    desc: 'Visualize key business metrics with instant KPI cards, breakdown tables, and dynamic bar, line, and pie charts.'
  },
  {
    icon: <Download size={22} className="feat-icon-svg text-amber" />,
    title: 'Easy Export',
    desc: 'Download your cleaned, transformed datasets as production-ready Excel (.xlsx) or UTF-8 CSV files anytime.'
  }
];

export default function OnboardingModal({ isOpen, onClose }) {
  if (!isOpen) return null;

  const handleComplete = () => {
    localStorage.setItem('tabufinery_onboarded', 'true');
    localStorage.setItem('datapilot_onboarded', 'true');
    onClose();
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-card onboarding-card">
        <div className="onboarding-header">
          <div className="onboarding-brand">
            <LogoMark size={36} />
            <span className="brand-name">Tabufinery</span>
          </div>
          <button className="btn-ghost-sm" onClick={handleComplete}>
            Skip
          </button>
        </div>

        <div className="onboarding-hero">
          <h2>Turn spreadsheets into insights.</h2>
          <p className="hero-sub">
            The modern workspace designed to inspect, clean, query, and visualize tabular datasets with zero friction.
          </p>
        </div>

        <div className="features-grid">
          {FEATURES.map((f, i) => (
            <div key={i} className="feature-card">
              <div className="feature-icon-wrap">{f.icon}</div>
              <div className="feature-body">
                <h4>{f.title}</h4>
                <p>{f.desc}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="onboarding-footer">
          <div className="onboarding-meta">
            <Check size={14} className="text-emerald" />
            <span>Local in-memory processing · Fast & private</span>
          </div>
          <button className="btn btn-primary btn-md" onClick={handleComplete}>
            Get Started <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
