import React, { useState, useEffect } from 'react';
import {
  X, Check, RotateCcw, Palette, Sliders, Layers, Eye, EyeOff,
  MoveUp, MoveDown, Sparkles, ImageIcon, Pipette, Type
} from '../icons/Icons';
import {
  DASHBOARD_STYLE_PRESETS,
  COLOR_PALETTES,
  GRADIENT_PRESETS,
  PATTERN_PRESETS,
  DEFAULT_PREFERENCES
} from './dashboardCustomizationConstants';

export default function DashboardCustomizerModal({
  isOpen,
  onClose,
  draft,
  onUpdateDraft,
  onSave,
  onReset,
  hasUnsavedChanges,
  livePreview
}) {
  const [activeTab, setActiveTab] = useState('styles');

  useEffect(() => {
    document.body.classList.add('customizer-drawer-open');
    return () => {
      document.body.classList.remove('customizer-drawer-open');
    };
  }, []);

  if (!isOpen) return null;

  const handlePresetSelect = (presetId) => {
    onUpdateDraft(prev => ({
      ...prev,
      style: presetId,
      ...(presetId === 'dark' ? { palette: 'midnight_navy' } : {}),
      ...(presetId === 'cyberpunk' ? { palette: 'cyber_neon' } : {})
    }));
  };

  const handlePaletteSelect = (paletteId) => {
    const palette = COLOR_PALETTES.find(p => p.id === paletteId);
    if (!palette) return;

    onUpdateDraft(prev => ({
      ...prev,
      palette: paletteId,
      useCustomColors: false,
      customColors: {
        primary: palette.primary,
        secondary: palette.secondary,
        bg: palette.bg,
        surface: palette.surface,
        cardBg: palette.cardBg,
        border: palette.border,
        textPrimary: palette.textPrimary,
        textSecondary: palette.textSecondary
      }
    }));
  };

  const handleCustomColorChange = (key, value) => {
    onUpdateDraft(prev => ({
      ...prev,
      useCustomColors: true,
      customColors: {
        ...(prev.customColors || {}),
        [key]: value
      }
    }));
  };

  const handleResetToPalette = () => {
    const palette = COLOR_PALETTES.find(p => p.id === draft.palette) || COLOR_PALETTES[0];
    onUpdateDraft(prev => ({
      ...prev,
      useCustomColors: false,
      customColors: {
        primary: palette.primary,
        secondary: palette.secondary,
        bg: palette.bg,
        surface: palette.surface,
        cardBg: palette.cardBg,
        border: palette.border,
        textPrimary: palette.textPrimary,
        textSecondary: palette.textSecondary
      }
    }));
  };

  const handleBackgroundTypeChange = (type) => {
    onUpdateDraft(prev => ({
      ...prev,
      background: {
        ...(prev.background || {}),
        type
      }
    }));
  };

  const handleGradientPresetChange = (gradientPreset) => {
    onUpdateDraft(prev => ({
      ...prev,
      background: {
        ...(prev.background || {}),
        type: 'gradient',
        gradientPreset
      }
    }));
  };

  const handlePatternChange = (pattern) => {
    onUpdateDraft(prev => ({
      ...prev,
      background: {
        ...(prev.background || {}),
        pattern
      }
    }));
  };

  const handleCardUpdate = (key, val) => {
    onUpdateDraft(prev => ({
      ...prev,
      card: {
        ...(prev.card || {}),
        [key]: val
      }
    }));
  };

  const handleDensityChange = (density) => {
    onUpdateDraft(prev => ({
      ...prev,
      layout: {
        ...(prev.layout || {}),
        density
      }
    }));
  };

  const handleToggleWidget = (id) => {
    onUpdateDraft(prev => {
      const widgets = (prev.layout?.widgets || []).map(w => {
        if (w.id === id) return { ...w, visible: !w.visible };
        return w;
      });
      return {
        ...prev,
        layout: {
          ...(prev.layout || {}),
          widgets
        }
      };
    });
  };

  const handleMoveWidget = (index, direction) => {
    onUpdateDraft(prev => {
      const widgets = [...(prev.layout?.widgets || [])];
      const targetIndex = index + direction;
      if (targetIndex < 0 || targetIndex >= widgets.length) return prev;

      const temp = widgets[index];
      widgets[index] = widgets[targetIndex];
      widgets[targetIndex] = temp;

      const reordered = widgets.map((w, idx) => ({ ...w, order: idx }));
      return {
        ...prev,
        layout: {
          ...(prev.layout || {}),
          widgets: reordered
        }
      };
    });
  };

  const handleTypographyUpdate = (key, val) => {
    onUpdateDraft(prev => ({
      ...prev,
      typography: {
        ...(prev.typography || {}),
        [key]: val
      }
    }));
  };

  const activePreset = DASHBOARD_STYLE_PRESETS.find(p => p.id === draft.style) || DASHBOARD_STYLE_PRESETS[1];
  const activePalette = COLOR_PALETTES.find(p => p.id === draft.palette) || COLOR_PALETTES[0];

  return (
    <div className="dashboard-customizer-backdrop" onClick={onClose}>
      {livePreview && (
        <div className="dashboard-customizer-preview-container" onClick={e => e.stopPropagation()}>
          {livePreview}
        </div>
      )}
      <div className="dashboard-customizer-drawer" onClick={e => e.stopPropagation()}>
        <div className="dash-customizer-header">
          <div>
            <h3>
              <Sliders size={18} className="text-primary" />
              Customize Dashboard
            </h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
              <span className="dash-customizer-badge">{activePreset.name}</span>
              {hasUnsavedChanges && (
                <span className="dash-customizer-unsaved-badge">Unsaved changes</span>
              )}
            </div>
          </div>
          <button className="btn btn-icon btn-sm" onClick={onClose} title="Discard & Close">
            <X size={18} />
          </button>
        </div>

        <div className="dash-customizer-tabs">
          <button
            className={`dash-tab-btn ${activeTab === 'styles' ? 'active' : ''}`}
            onClick={() => setActiveTab('styles')}
          >
            <Sparkles size={14} /> Styles
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'palette' ? 'active' : ''}`}
            onClick={() => setActiveTab('palette')}
          >
            <Palette size={14} /> Palettes
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'custom_colors' ? 'active' : ''}`}
            onClick={() => setActiveTab('custom_colors')}
          >
            <Pipette size={14} /> Colors
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'background' ? 'active' : ''}`}
            onClick={() => setActiveTab('background')}
          >
            <ImageIcon size={14} /> Background
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'cards' ? 'active' : ''}`}
            onClick={() => setActiveTab('cards')}
          >
            <Layers size={14} /> Cards
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'layout' ? 'active' : ''}`}
            onClick={() => setActiveTab('layout')}
          >
            <Sliders size={14} /> Layout
          </button>
          <button
            className={`dash-tab-btn ${activeTab === 'typography' ? 'active' : ''}`}
            onClick={() => setActiveTab('typography')}
          >
            <Type size={14} /> Typography
          </button>
        </div>

        <div className="dash-customizer-body">
          {activeTab === 'styles' && (
            <div>
              <p className="text-secondary text-sm mb-16">
                Choose a visual theme designed exclusively for the Executive Overview Dashboard:
              </p>
              <div className="dash-presets-grid">
                {DASHBOARD_STYLE_PRESETS.map(preset => {
                  const isSelected = draft.style === preset.id;
                  return (
                    <div
                      key={preset.id}
                      className={`dash-preset-card ${isSelected ? 'active' : ''}`}
                      onClick={() => handlePresetSelect(preset.id)}
                    >
                      <div className="dash-preset-title">
                        <span>{preset.name}</span>
                        {isSelected && <Check size={14} className="text-primary" />}
                      </div>
                      <p className="dash-preset-desc">{preset.description}</p>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {activeTab === 'palette' && (
            <div>
              <p className="text-secondary text-sm mb-16">
                Select from 15 coordinated color schemes. Each harmonizes accents, surfaces, borders, and charts:
              </p>
              <div className="dash-palette-grid">
                {COLOR_PALETTES.map(p => {
                  const isSelected = draft.palette === p.id && !draft.useCustomColors;
                  return (
                    <div
                      key={p.id}
                      className={`dash-palette-card ${isSelected ? 'active' : ''}`}
                      onClick={() => handlePaletteSelect(p.id)}
                    >
                      <span style={{ fontSize: '12px', fontWeight: 600 }}>{p.name}</span>
                      <div className="dash-swatches">
                        <span className="dash-swatch" style={{ background: p.primary }} title="Primary" />
                        <span className="dash-swatch" style={{ background: p.secondary }} title="Secondary" />
                        <span className="dash-swatch" style={{ background: p.bg }} title="Background" />
                        <span className="dash-swatch" style={{ background: p.cardBg }} title="Card Surface" />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {activeTab === 'custom_colors' && (
            <div>
              <div className="flex-between mb-16">
                <p className="text-secondary text-sm" style={{ margin: 0 }}>
                  Customize exact HEX colors with live visual feedback:
                </p>
                <button className="btn btn-xs btn-outline" onClick={handleResetToPalette}>
                  <RotateCcw size={12} /> Reset to Palette
                </button>
              </div>

              {[
                { key: 'primary', label: 'Primary Accent' },
                { key: 'secondary', label: 'Secondary Accent' },
                { key: 'bg', label: 'Dashboard Background' },
                { key: 'cardBg', label: 'Card Surface' },
                { key: 'surface', label: 'Surface Container' },
                { key: 'border', label: 'Border Color' },
                { key: 'textPrimary', label: 'Primary Heading Text' },
                { key: 'textSecondary', label: 'Secondary Subtitle Text' }
              ].map(({ key, label }) => {
                const colorVal = draft.customColors?.[key] || activePalette[key] || '#ffffff';
                const isHexValid = /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(colorVal);

                return (
                  <div key={key} className="dash-color-row">
                    <span className="dash-color-label">{label}</span>
                    <div className="dash-color-inputs">
                      <input
                        type="color"
                        className="dash-native-picker"
                        value={isHexValid ? (colorVal.length === 4 ? `#${colorVal[1]}${colorVal[1]}${colorVal[2]}${colorVal[2]}${colorVal[3]}${colorVal[3]}` : colorVal) : '#000000'}
                        onChange={e => handleCustomColorChange(key, e.target.value)}
                        title={`Pick ${label}`}
                      />
                      <input
                        type="text"
                        className="dash-hex-input"
                        value={colorVal}
                        onChange={e => handleCustomColorChange(key, e.target.value)}
                        placeholder="#000000"
                        maxLength={7}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {activeTab === 'background' && (
            <div>
              <h4 className="text-sm font-semibold mb-8">Background Fill Mode</h4>
              <div style={{ display: 'flex', gap: '8px', marginBottom: '20px' }}>
                {['solid', 'gradient'].map(mode => (
                  <button
                    key={mode}
                    className={`btn btn-sm ${draft.background?.type === mode ? 'btn-primary' : 'btn-outline'}`}
                    onClick={() => handleBackgroundTypeChange(mode)}
                    style={{ flex: 1, textTransform: 'capitalize' }}
                  >
                    {mode}
                  </button>
                ))}
              </div>

              {draft.background?.type === 'gradient' && (
                <div className="mb-24">
                  <h4 className="text-sm font-semibold mb-8">Gradient Presets</h4>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px' }}>
                    {GRADIENT_PRESETS.map(g => {
                      const isSelected = draft.background?.gradientPreset === g.id;
                      return (
                        <div
                          key={g.id}
                          className={`dash-palette-card ${isSelected ? 'active' : ''}`}
                          style={{
                            height: '52px',
                            background: g.gradient,
                            position: 'relative'
                          }}
                          onClick={() => handleGradientPresetChange(g.id)}
                        >
                          <span style={{ fontSize: '11px', fontWeight: 700 }}>{g.name}</span>
                          {isSelected && <Check size={14} className="text-primary" />}
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              <div>
                <h4 className="text-sm font-semibold mb-8">Subtle Background Pattern</h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
                  {PATTERN_PRESETS.map(pat => {
                    const isSelected = (draft.background?.pattern || 'none') === pat.id;
                    return (
                      <button
                        key={pat.id}
                        className={`btn btn-sm ${isSelected ? 'btn-primary' : 'btn-outline'}`}
                        onClick={() => handlePatternChange(pat.id)}
                      >
                        {pat.name}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'cards' && (
            <div>
              <div className="mb-20">
                <h4 className="text-sm font-semibold mb-8">Card Elevation & Style</h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px' }}>
                  {[
                    { id: 'flat', label: 'Flat' },
                    { id: 'bordered', label: 'Bordered' },
                    { id: 'soft', label: 'Soft Shadow' },
                    { id: 'glass', label: 'Frosted Glass' },
                    { id: 'elevated', label: 'Elevated' }
                  ].map(c => (
                    <button
                      key={c.id}
                      className={`btn btn-sm ${(draft.card?.style || 'bordered') === c.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleCardUpdate('style', c.id)}
                    >
                      {c.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mb-20">
                <h4 className="text-sm font-semibold mb-8">Corner Radius</h4>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'sharp', label: 'Sharp (0px)' },
                    { id: 'medium', label: 'Medium (10px)' },
                    { id: 'rounded', label: 'Rounded (20px)' }
                  ].map(r => (
                    <button
                      key={r.id}
                      className={`btn btn-sm ${(draft.card?.radius || 'medium') === r.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleCardUpdate('radius', r.id)}
                      style={{ flex: 1 }}
                    >
                      {r.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold mb-8">Shadow Intensity</h4>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'none', label: 'None' },
                    { id: 'subtle', label: 'Subtle' },
                    { id: 'strong', label: 'Strong' }
                  ].map(s => (
                    <button
                      key={s.id}
                      className={`btn btn-sm ${(draft.card?.shadow || 'subtle') === s.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleCardUpdate('shadow', s.id)}
                      style={{ flex: 1 }}
                    >
                      {s.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'layout' && (
            <div>
              <div className="mb-20">
                <h4 className="text-sm font-semibold mb-8">Density & Spacing</h4>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'compact', label: 'Compact' },
                    { id: 'comfortable', label: 'Comfortable' },
                    { id: 'spacious', label: 'Spacious' }
                  ].map(d => (
                    <button
                      key={d.id}
                      className={`btn btn-sm ${(draft.layout?.density || 'comfortable') === d.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleDensityChange(d.id)}
                      style={{ flex: 1 }}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold mb-8">Visible Widgets & Ordering</h4>
                <p className="text-secondary text-xs mb-12">
                  Toggle visibility or use arrow buttons to arrange widgets:
                </p>
                <div className="dash-widget-list">
                  {(draft.layout?.widgets || []).map((w, idx) => (
                    <div key={w.id} className="dash-widget-item">
                      <div className="dash-widget-info">
                        <button
                          className="dash-icon-btn"
                          onClick={() => handleToggleWidget(w.id)}
                          title={w.visible ? 'Hide widget' : 'Show widget'}
                        >
                          {w.visible ? <Eye size={15} className="text-primary" /> : <EyeOff size={15} />}
                        </button>
                        <span style={{ fontSize: '13px', fontWeight: 600, opacity: w.visible ? 1 : 0.5 }}>
                          {w.label}
                        </span>
                      </div>
                      <div className="dash-widget-actions">
                        <button
                          className="dash-icon-btn"
                          disabled={idx === 0}
                          onClick={() => handleMoveWidget(idx, -1)}
                          title="Move up"
                        >
                          <MoveUp size={14} />
                        </button>
                        <button
                          className="dash-icon-btn"
                          disabled={idx === (draft.layout?.widgets?.length || 1) - 1}
                          onClick={() => handleMoveWidget(idx, 1)}
                          title="Move down"
                        >
                          <MoveDown size={14} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'typography' && (
            <div>
              <div className="mb-20">
                <h4 className="text-sm font-semibold mb-8">Typography Scale</h4>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'small', label: 'Small (90%)' },
                    { id: 'medium', label: 'Standard (100%)' },
                    { id: 'large', label: 'Large (110%)' }
                  ].map(t => (
                    <button
                      key={t.id}
                      className={`btn btn-sm ${(draft.typography?.size || 'medium') === t.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleTypographyUpdate('size', t.id)}
                      style={{ flex: 1 }}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mb-20">
                <h4 className="text-sm font-semibold mb-8">Visual Emphasis</h4>
                <div style={{ display: 'flex', gap: '8px' }}>
                  {[
                    { id: 'standard', label: 'Standard Emphasis' },
                    { id: 'subtle', label: 'Subtle Weights' }
                  ].map(e => (
                    <button
                      key={e.id}
                      className={`btn btn-sm ${(draft.typography?.emphasis || 'standard') === e.id ? 'btn-primary' : 'btn-outline'}`}
                      onClick={() => handleTypographyUpdate('emphasis', e.id)}
                      style={{ flex: 1 }}
                    >
                      {e.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold mb-8">Accessibility & Animation</h4>
                <div className="dash-widget-item">
                  <div>
                    <span style={{ fontSize: '13px', fontWeight: 600 }}>Reduced Motion</span>
                    <p className="text-secondary text-xs" style={{ margin: '2px 0 0 0' }}>
                      Disable transitions and effects for optimal comfort and accessibility.
                    </p>
                  </div>
                  <button
                    className={`btn btn-sm ${draft.typography?.reducedMotion ? 'btn-primary' : 'btn-outline'}`}
                    onClick={() => handleTypographyUpdate('reducedMotion', !draft.typography?.reducedMotion)}
                  >
                    {draft.typography?.reducedMotion ? 'Enabled' : 'Disabled'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="dash-customizer-footer">
          <button className="btn btn-sm btn-outline text-error" onClick={onReset} title="Restore factory defaults">
            <RotateCcw size={14} /> Reset Defaults
          </button>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="btn btn-sm btn-outline" onClick={onClose}>
              Discard
            </button>
            <button className="btn btn-sm btn-primary" onClick={onSave}>
              <Check size={14} /> Apply & Save
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
