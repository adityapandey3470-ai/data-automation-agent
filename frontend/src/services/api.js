import axios from 'axios';

const api = axios.create({ baseURL: '/api/v1' });

// ---- Request Interceptor: Attach JWT Token ----
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('tabufinery_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => Promise.reject(error));

// ---- Response Interceptor: Auto-logout on 401 ----
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const url = error.config?.url || '';
      if (!url.includes('/auth/login') && !url.includes('/auth/signup') && !url.includes('/auth/forgot') && !url.includes('/auth/reset')) {
        clearStoredAuth();
        window.dispatchEvent(new CustomEvent('tabufinery_unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);

// ---- User-Friendly Error Formatting (No raw HTTP status codes in UI) ----
export const getErrorMessage = (err, fallback = 'An unexpected error occurred. Please try again.') => {
  if (!err) return fallback;

  // 1. If backend returned a clear user-friendly string message
  const serverMsg = err.response?.data?.message || err.response?.data?.error;
  if (typeof serverMsg === 'string' && serverMsg.trim()) {
    if (!/status code \d{3}/i.test(serverMsg) && !/HTTP \d{3}/i.test(serverMsg)) {
      return serverMsg.trim();
    }
  }

  // 2. Check HTTP status code and map to friendly human message
  const status = err.response?.status;
  if (status === 401) {
    return 'Please sign in to continue.';
  }
  if (status === 403) {
    return 'You do not have permission to perform this action.';
  }
  if (status === 404) {
    return 'The requested resource or dataset was not found.';
  }
  if (status === 413) {
    return 'File is too large. Maximum supported upload size is 10 MB.';
  }
  if (status === 429) {
    return 'Too many requests. Please wait a moment before trying again.';
  }
  if (status >= 500) {
    return 'Server encountered a temporary issue. Please try again in a moment.';
  }

  // 3. Network or connection errors
  if (err.message === 'Network Error' || !err.response) {
    return 'Unable to connect to the server. Please check your network connection.';
  }

  // 4. Fallback (strip out raw "Request failed with status code XXX")
  if (typeof err.message === 'string') {
    if (/status code \d{3}/i.test(err.message) || /Network Error/i.test(err.message)) {
      return fallback;
    }
    return err.message;
  }

  return fallback;
};

// ---- Auth Storage Helpers ----
export const getStoredToken = () => localStorage.getItem('tabufinery_token');
export const setStoredAuth = (token, user) => {
  if (token) localStorage.setItem('tabufinery_token', token);
  if (user) localStorage.setItem('tabufinery_user', JSON.stringify(user));
};
export const getStoredUser = () => {
  try {
    const u = localStorage.getItem('tabufinery_user');
    return u ? JSON.parse(u) : null;
  } catch {
    return null;
  }
};
export const clearStoredAuth = () => {
  localStorage.removeItem('tabufinery_token');
  localStorage.removeItem('tabufinery_user');
};

// ---- Health ----
export const getHealth = () => api.get('/health');

// ---- Authentication ----
export const signupUser = (name, email, password, confirmPassword) =>
  api.post('/auth/signup', { name, email, password, confirmPassword });

export const loginUser = (email, password) =>
  api.post('/auth/login', { email, password });

export const forgotPassword = (email) =>
  api.post('/auth/forgot-password', { email });

export const resetPassword = (token, newPassword, confirmPassword) =>
  api.post('/auth/reset-password', { token, newPassword, confirmPassword });

export const getCurrentUser = () => api.get('/auth/me');

// ---- Admin Management ----
export const getAdminUsers = (page = 0, size = 50) =>
  api.get('/admin/users', { params: { page, size } });
export const getAdminStats = () => api.get('/admin/stats');
export const updateAdminUserStatus = (id, enabled) =>
  api.patch(`/admin/users/${id}/status`, { enabled });
export const updateAdminUserRole = (id, role) =>
  api.patch(`/admin/users/${id}/role`, { role });

// ---- Saved Projects (Persistence) ----
export const saveProject = (name, description, activeSheetName, projectId = null) =>
  api.post('/projects/save', { name, description, activeSheetName, projectId });

export const listProjects = () => api.get('/projects');

export const getProject = (id) => api.get(`/projects/${id}`);

export const openProject = (id) => api.post(`/projects/${id}/open`);

export const renameProject = (id, name) =>
  api.put(`/projects/${id}/rename`, { name });

export const deleteProject = (id) => api.delete(`/projects/${id}`);

// ---- File Upload ----
export const uploadExcel = (file) => {
  const form = new FormData();
  form.append('file', file);
  return api.post('/files/excel', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

// ---- Workbook & Multi-File Workspace ----
export const getWorkbookStatus = () => api.get('/workbook/status');
export const getWorkspaceFiles = () => api.get('/workbook/files');
export const selectWorkspaceFile = (fileId) => api.post(`/workbook/files/${fileId}/select`);
export const deleteWorkspaceFile = (fileId) => api.delete(`/workbook/files/${fileId}`);
export const getPreview = (sheetName, limit = 50) =>
  api.get('/workbook/preview', { params: { sheetName, limit } });
export const getAllData = (sheetName) =>
  api.get('/workbook/data', { params: { sheetName } });

// ---- Analysis ----
export const getStatistics = (sheetName, column) =>
  api.post('/analysis/statistics', { sheetName, column });

export const filterRows = (sheetName, conditions) =>
  api.post('/analysis/filter', { sheetName, conditions });

export const sortRows = (sheetName, sortBy) =>
  api.post('/analysis/sort', { sheetName, sortBy });

export const groupBy = (sheetName, groupByColumn, aggregationColumn, aggregation) =>
  api.post('/analysis/group', { sheetName, groupByColumn, aggregationColumn, aggregation });

export const findDuplicates = (sheetName, columns) =>
  api.post('/analysis/duplicates', { sheetName, columns });

// ---- Cleaning ----
export const removeDuplicates = (req) => api.post('/cleaning/duplicates', req);
export const handleMissing = (req) => api.post('/cleaning/missing-values', req);
export const cleanStrings = (req) => api.post('/cleaning/strings', req);
export const replaceValues = (req) => api.post('/cleaning/replace', req);
export const renameColumn = (req) => api.post('/cleaning/rename-column', req);
export const removeColumn = (req) => api.post('/cleaning/remove-column', req);
export const convertType = (req) => api.post('/cleaning/convert-type', req);
export const transformPipeline = (reqs) => api.post('/cleaning/transform', reqs);
export const previewCleaning = (req) => api.post('/cleaning/preview', req);
export const previewPipeline = (reqs) => api.post('/cleaning/preview-pipeline', reqs);

// ---- Workbook Undo / Redo ----
export const undoWorkbook = () => api.post('/workbook/undo');
export const redoWorkbook = () => api.post('/workbook/redo');
export const getWorkbookHistory = () => api.get('/workbook/history');

// ---- Dashboard ----
export const createDashboard = (name, sheetName) =>
  api.post('/dashboard/create', { name, sheetName });

export const autoGenerateDashboard = (sheetName, name) =>
  api.post('/dashboard/auto-generate', { sheetName, name });

export const addWidget = (req) => api.post('/dashboard/widget', req);

export const removeWidget = (dashboardId, widgetId) =>
  api.delete(`/dashboard/widget/${widgetId}`, { params: { dashboardId } });

export const getDashboard = (dashboardId) =>
  api.get(`/dashboard/${dashboardId}`);

export const listDashboards = () =>
  api.get('/dashboard/list');

export const deleteDashboard = (dashboardId) =>
  api.delete(`/dashboard/${dashboardId}`);

// ---- Export ----
export const exportExcel = (sheetName) =>
  api.get('/export/excel', {
    params: sheetName ? { sheetName } : {},
    responseType: 'blob'
  });

export const exportCsv = (sheetName) =>
  api.get('/export/csv', {
    params: { sheetName },
    responseType: 'blob'
  });

export const exportPdfReport = (sheetName) =>
  api.get('/export/report/pdf', {
    params: sheetName ? { sheetName } : {},
    responseType: 'blob'
  });

// ---- AI Analyst & Automatic Insights ----
export const getAiStatus = () => api.get('/ai/status');
export const getDataProfile = (sheetName) =>
  api.get('/ai/profile', { params: sheetName ? { sheetName } : {} });
export const askAiAnalyst = (question, sheetName) =>
  api.post('/ai/ask', { question, sheetName });

// ---- AI Support Chatbot & Helpdesk ----
export const sendSupportMessage = (message, history = []) =>
  api.post('/support/chat', { message, history });

export const submitSupportTicket = (category, subject, message) =>
  api.post('/support/ticket', { category, subject, message });

export const getSupportQuickQuestions = () =>
  api.get('/support/quick-questions');
