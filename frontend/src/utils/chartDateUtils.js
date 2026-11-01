/**
 * Date parsing, formatting, and X-axis density optimization for Tabufinery charts.
 */

const MONTH_NAMES_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTH_NAMES_FULL = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/**
 * Robustly parses a date value from various spreadsheet and database representations.
 * Handles Excel serial numbers, ISO strings, standard US/EU formats, timestamps, and 4-digit years.
 */
export function parseDateSafe(val) {
  if (val == null || val === '' || val === '(Blank)' || val === 'null' || val === 'undefined') {
    return null;
  }
  if (val instanceof Date) {
    return isNaN(val.getTime()) ? null : val;
  }

  // Handle numbers
  if (typeof val === 'number') {
    // Excel serial number (typically 20000 [1954] to 70000 [2091])
    if (val >= 20000 && val <= 70000) {
      const ms = Math.round((val - 25569) * 86400 * 1000);
      const d = new Date(ms);
      return isNaN(d.getTime()) ? null : d;
    }
    // Unix timestamp in seconds
    if (val > 100000000 && val < 2500000000) {
      return new Date(val * 1000);
    }
    // Unix timestamp in ms
    if (val >= 2500000000) {
      const d = new Date(val);
      return isNaN(d.getTime()) ? null : d;
    }
    // 4-digit year e.g. 2024
    if (val >= 1900 && val <= 2100) {
      return new Date(val, 0, 1);
    }
    return null;
  }

  const str = String(val).trim();
  if (!str) return null;

  // If pure numeric string
  if (/^\d+(\.\d+)?$/.test(str)) {
    const num = parseFloat(str);
    return parseDateSafe(num);
  }

  // Check 4-digit year
  if (/^\d{4}$/.test(str)) {
    const yr = parseInt(str, 10);
    if (yr >= 1900 && yr <= 2100) {
      return new Date(yr, 0, 1);
    }
  }

  // ISO format or standard date strings (e.g. 2024-06-15, 2024-06-15T14:30:00)
  const isoMatch = str.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/);
  if (isoMatch) {
    const yr = parseInt(isoMatch[1], 10);
    const mo = parseInt(isoMatch[2], 10) - 1;
    const da = parseInt(isoMatch[3], 10);
    const d = new Date(yr, mo, da);
    if (!isNaN(d.getTime())) {
      // Check if time is attached
      const timeMatch = str.match(/T|\s(\d{1,2}):(\d{1,2})(:(\d{1,2}))?/);
      if (timeMatch) {
        d.setHours(parseInt(timeMatch[1], 10) || 0);
        d.setMinutes(parseInt(timeMatch[2], 10) || 0);
        d.setSeconds(parseInt(timeMatch[4], 10) || 0);
      }
      return d;
    }
  }

  // DD/MM/YYYY or MM/DD/YYYY
  const slashMatch = str.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})/);
  if (slashMatch) {
    const p1 = parseInt(slashMatch[1], 10);
    const p2 = parseInt(slashMatch[2], 10);
    let yr = parseInt(slashMatch[3], 10);
    if (yr < 100) yr += (yr >= 50 ? 1900 : 2000);

    if (p1 > 12 && p2 <= 12) {
      // Must be DD/MM/YYYY
      const d = new Date(yr, p2 - 1, p1);
      if (!isNaN(d.getTime())) return d;
    } else if (p2 > 12 && p1 <= 12) {
      // Must be MM/DD/YYYY
      const d = new Date(yr, p1 - 1, p2);
      if (!isNaN(d.getTime())) return d;
    } else {
      // Ambiguous: default to YYYY-MM-DD standard month-day
      const d = new Date(yr, p1 - 1, p2);
      if (!isNaN(d.getTime())) return d;
    }
  }

  // Standard JavaScript Date parse fallback (e.g. "15-Jun-2024", "June 2024")
  const parsed = new Date(str);
  if (!isNaN(parsed.getTime()) && parsed.getFullYear() >= 1950 && parsed.getFullYear() <= 2100) {
    return parsed;
  }

  return null;
}

/**
 * Checks whether the majority of points in data represent valid dates.
 */
export function isDateSeries(dataPoints) {
  if (!dataPoints || dataPoints.length === 0) return false;
  const sample = dataPoints.slice(0, 30);
  let validCount = 0;
  for (const p of sample) {
    if (parseDateSafe(p.label) != null) {
      validCount++;
    }
  }
  return (validCount / sample.length) >= 0.6;
}

/**
 * Formats a Date into a concise label based on the dataset's overall time span.
 */
export function formatDateLabel(date, spanDays) {
  if (!date || isNaN(date.getTime())) return '';
  const month = MONTH_NAMES_SHORT[date.getMonth()];
  const day = date.getDate();
  const yearFull = date.getFullYear();
  const year2d = String(yearFull).slice(-2);

  // Multi-year span (> 2 years / 730 days)
  if (spanDays > 730) {
    return `${month} '${year2d}`;
  }

  // Medium span (> 90 days up to 2 years)
  if (spanDays > 90) {
    return `${month} '${year2d}`;
  }

  // Short span (between 2 and 90 days)
  if (spanDays > 1) {
    return `${month} ${day}`;
  }

  // Intra-day (<= 1 day / 24 hours)
  const hrs = String(date.getHours()).padStart(2, '0');
  const mins = String(date.getMinutes()).padStart(2, '0');
  return `${hrs}:${mins}`;
}

/**
 * Formats a Date into a detailed, full string for tooltips.
 */
export function formatTooltipDate(date, fallback) {
  if (!date || isNaN(date.getTime())) return fallback || '';
  const month = MONTH_NAMES_FULL[date.getMonth()];
  const day = date.getDate();
  const year = date.getFullYear();
  const hrs = date.getHours();
  const mins = date.getMinutes();

  if (hrs !== 0 || mins !== 0) {
    const formattedTime = date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    return `${month} ${day}, ${year} (${formattedTime})`;
  }
  return `${month} ${day}, ${year}`;
}

/**
 * Prepares chart dataset with chronological sorting, density management, and formatted ticks.
 * Works seamlessly for both time-series (Area, Line) and categorical (Bar) charts.
 * 
 * @param {Array} rawPoints Array of { label, value }
 * @param {Object} options Configuration options
 * @returns {Object} { chartData, ticks, tickMap, isDate, rotateX }
 */
export function prepareChartSeries(rawPoints, options = {}) {
  const { maxTicks = 6, forceCategorical = false } = options;
  if (!rawPoints || rawPoints.length === 0) {
    return { chartData: [], ticks: [], tickMap: {}, isDate: false, rotateX: false };
  }

  const isDate = !forceCategorical && isDateSeries(rawPoints);

  if (isDate) {
    // 1. Parse and extract timestamps
    const parsedList = [];
    for (let i = 0; i < rawPoints.length; i++) {
      const p = rawPoints[i];
      const parsedDate = parseDateSafe(p.label);
      parsedList.push({
        rawLabel: String(p.label),
        date: parsedDate,
        timestamp: parsedDate ? parsedDate.getTime() : null,
        value: Number(p.value) || 0,
        origIndex: i
      });
    }

    // 2. Sort chronologically (valid dates first in chronological order, unparseables at the end)
    parsedList.sort((a, b) => {
      if (a.timestamp != null && b.timestamp != null) return a.timestamp - b.timestamp;
      if (a.timestamp != null) return -1;
      if (b.timestamp != null) return 1;
      return a.origIndex - b.origIndex;
    });

    // 3. Compute time span
    const validDates = parsedList.filter(p => p.timestamp != null);
    let spanDays = 30;
    if (validDates.length > 1) {
      const minMs = validDates[0].timestamp;
      const maxMs = validDates[validDates.length - 1].timestamp;
      spanDays = Math.max(1, (maxMs - minMs) / (1000 * 60 * 60 * 24));
    }

    // 4. Construct unique chartData items
    const chartData = [];
    const tickMap = {};

    for (let i = 0; i < parsedList.length; i++) {
      const item = parsedList[i];
      const key = `k_${i}_${item.timestamp || i}`;
      const axisLabel = item.date ? formatDateLabel(item.date, spanDays) : item.rawLabel;
      const tooltipLabel = item.date ? formatTooltipDate(item.date, item.rawLabel) : item.rawLabel;

      chartData.push({
        key,
        name: axisLabel,
        fullDate: tooltipLabel,
        value: item.value,
        timestamp: item.timestamp,
        rawLabel: item.rawLabel
      });

      tickMap[key] = {
        axisLabel,
        tooltipLabel
      };
    }

    // 5. Calculate evenly spaced ticks (target: 5-7 ticks)
    // Ensures start date, end date, and evenly spaced intervals are visible
    const N = chartData.length;
    const ticks = [];

    if (N <= maxTicks) {
      for (const cd of chartData) {
        ticks.push(cd.key);
      }
    } else {
      // Pick evenly spaced indices including index 0 and index N-1
      const count = Math.min(maxTicks, N);
      const step = (N - 1) / (count - 1);
      const usedIndices = new Set();

      for (let c = 0; c < count; c++) {
        const idx = Math.min(N - 1, Math.round(c * step));
        if (!usedIndices.has(idx)) {
          usedIndices.add(idx);
          ticks.push(chartData[idx].key);
        }
      }
    }

    return {
      chartData,
      ticks,
      tickMap,
      isDate: true,
      rotateX: false // Concise formatted dates like "Jun '24" fit horizontally without rotation!
    };
  }

  // Categorical Series (non-date)
  const chartData = rawPoints.map((p, i) => {
    const rawStr = String(p.label != null ? p.label : '');
    const key = `cat_${i}_${rawStr}`;
    const truncated = rawStr.length > 14 ? `${rawStr.slice(0, 13)}…` : rawStr;
    return {
      key,
      name: truncated,
      fullDate: rawStr,
      value: Number(p.value) || 0,
      rawLabel: rawStr
    };
  });

  const N = chartData.length;
  const ticks = [];
  const tickMap = {};

  for (const cd of chartData) {
    tickMap[cd.key] = { axisLabel: cd.name, tooltipLabel: cd.fullDate };
  }

  if (N <= maxTicks) {
    for (const cd of chartData) ticks.push(cd.key);
  } else {
    const step = (N - 1) / (maxTicks - 1);
    const usedIndices = new Set();
    for (let c = 0; c < maxTicks; c++) {
      const idx = Math.min(N - 1, Math.round(c * step));
      if (!usedIndices.has(idx)) {
        usedIndices.add(idx);
        ticks.push(chartData[idx].key);
      }
    }
  }

  const rotateX = chartData.some(d => d.name.length > 6) && chartData.length > 5;

  return {
    chartData,
    ticks,
    tickMap,
    isDate: false,
    rotateX
  };
}
