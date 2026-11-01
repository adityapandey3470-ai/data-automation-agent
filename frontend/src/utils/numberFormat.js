/**
 * Formats a metric or KPI number for executive dashboard display.
 * Uses compact notation for large numbers (K, M, B) while preserving readability.
 */
export function formatMetricNumber(val, maxDecimals = 2) {
  if (val == null || val === '') return '—';
  const num = typeof val === 'number' ? val : Number(val);
  if (isNaN(num)) return String(val);

  const abs = Math.abs(num);
  const sign = num < 0 ? '-' : '';

  if (abs >= 1_000_000_000) {
    const formatted = (abs / 1_000_000_000).toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: maxDecimals
    });
    return `${sign}${formatted}B`;
  }

  if (abs >= 1_000_000) {
    const formatted = (abs / 1_000_000).toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: maxDecimals
    });
    return `${sign}${formatted}M`;
  }

  if (abs >= 100_000) {
    const formatted = (abs / 1_000).toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 1
    });
    return `${sign}${formatted}K`;
  }

  if (Number.isInteger(num)) {
    return num.toLocaleString();
  }

  return num.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: maxDecimals
  });
}

/**
 * Returns the exact numeric string formatted with standard locale thousands separators.
 * Used for tooltips and hover inspections.
 */
export function formatExactNumber(val) {
  if (val == null || val === '') return '—';
  const num = typeof val === 'number' ? val : Number(val);
  if (isNaN(num)) return String(val);

  if (Number.isInteger(num)) {
    return num.toLocaleString();
  }

  return num.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4
  });
}
