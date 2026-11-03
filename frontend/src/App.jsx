import React, { useState, useCallback, useEffect } from 'react';
import Sidebar from './components/layout/Sidebar';
import TopNav from './components/layout/TopNav';
import DashboardPage from './components/pages/DashboardPage';
import DataWorkspace from './components/pages/DataWorkspace';
import AIAssistant from './components/pages/AIAssistant';
import AnalyticsPage from './components/pages/AnalyticsPage';
import ExportPage from './components/pages/ExportPage';
import SettingsPage from './components/pages/SettingsPage';
import AdminPage from './components/pages/AdminPage';
import SupportChatWidget from './components/support/SupportChatWidget';
import SplashScreen from './components/launch/SplashScreen';
import OnboardingModal from './components/launch/OnboardingModal';
import AuthModal from './components/auth/AuthModal';
import SavedProjectsModal from './components/workspace/SavedProjectsModal';
import ErrorBoundary from './components/shared/ErrorBoundary';
import { getWorkbookStatus, selectWorkspaceFile, deleteWorkspaceFile, getStoredUser, clearStoredAuth, getCurrentUser, getStoredToken, setStoredAuth } from './services/api';

const VALID_PAGES = ['overview', 'workspace', 'assistant', 'analytics', 'export', 'admin', 'settings'];

function getInitialPage() {
  if (typeof window !== 'undefined') {

    const hash = window.location.hash.replace(/^#\/?/, '').split('?')[0].split('/')[0];
    if (hash && VALID_PAGES.includes(hash)) {
      return hash;
    }

    const saved = localStorage.getItem('tabufinery_active_page');
    if (saved && VALID_PAGES.includes(saved)) {
      return saved;
    }
  }
  return 'overview';
}

export default function App() {
  const [page, setPage] = useState(getInitialPage);
  const [fileInfo, setFileInfo] = useState(null);
  const [workspaceFiles, setWorkspaceFiles] = useState([]);
  const [activeSheet, setActiveSheet] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const [currentUser, setCurrentUser] = useState(() => getStoredUser());
  const [showAuthModal, setShowAuthModal] = useState(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      return params.has('verifyToken') || params.has('resetToken');
    }
    return false;
  });
  const [showProjectsModal, setShowProjectsModal] = useState(false);

  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const [showSplash, setShowSplash] = useState(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.has('verifyToken') || params.has('resetToken')) {
        return false;
      }
      if (sessionStorage.getItem('tabufinery_session_active')) {
        return false;
      }
    }
    return true;
  });
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      sessionStorage.setItem('tabufinery_session_active', 'true');
    }
  }, []);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('tabufinery_active_page', page);
      const targetHash = `#${page}`;
      if (window.location.hash !== targetHash) {
        window.history.replaceState(null, '', targetHash);
      }
    }
  }, [page]);

  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash.replace(/^#\/?/, '').split('?')[0].split('/')[0];
      if (hash && VALID_PAGES.includes(hash)) {
        if (hash === 'admin' && currentUser?.role !== 'ROLE_ADMIN') {
          setPage('overview');
        } else {
          setPage(hash);
        }
      }
    };
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [currentUser]);

  const [currentTheme, setCurrentTheme] = useState(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'dark';
  });

  useEffect(() => {
    const handleUnauthorized = () => {
      clearStoredAuth();
      setCurrentUser(null);
      setFileInfo(null);
      setActiveSheet(null);
      localStorage.removeItem('tabufinery_active_page');
      localStorage.removeItem('tabufinery_active_sheet');
      setPage('overview');
      if (typeof window !== 'undefined') {
        window.history.replaceState(null, '', '#overview');
      }
    };
    window.addEventListener('tabufinery_unauthorized', handleUnauthorized);

    const handleUserUpdated = (e) => {
      if (e?.detail) {
        setCurrentUser(prev => {
          const merged = { ...(prev || {}), ...e.detail };
          setStoredAuth(getStoredToken(), merged);
          return merged;
        });
      }
    };
    window.addEventListener('tabufinery_user_updated', handleUserUpdated);

    if (getStoredToken()) {
      getCurrentUser()
        .then(res => {
          if (res?.data) {
            setCurrentUser(res.data);
            setStoredAuth(getStoredToken(), res.data);
          }
        })
        .catch(() => {});
    }

    return () => {
      window.removeEventListener('tabufinery_unauthorized', handleUnauthorized);
      window.removeEventListener('tabufinery_user_updated', handleUserUpdated);
    };
  }, []);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const updateTheme = (e) => {
      const active = e.matches ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', active);
      setCurrentTheme(active);
    };

    const active = mediaQuery.matches ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', active);
    setCurrentTheme(active);

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', updateTheme);
      return () => mediaQuery.removeEventListener('change', updateTheme);
    } else {
      mediaQuery.addListener(updateTheme);
      return () => mediaQuery.removeListener(updateTheme);
    }
  }, []);

  useEffect(() => {
    const onboarded = localStorage.getItem('tabufinery_onboarded') || localStorage.getItem('datapilot_onboarded');
    if (!onboarded) {
      setShowOnboarding(true);
    }
  }, []);

  useEffect(() => {
    if (page === 'admin' && currentUser?.role !== 'ROLE_ADMIN') {
      setPage('overview');
    }
  }, [currentUser, page]);

  useEffect(() => {
    if (!currentUser) {
      setFileInfo(null);
      setActiveSheet(null);
      setWorkspaceFiles([]);
      return;
    }
    const fetchStatus = async () => {
      try {
        const res = await getWorkbookStatus();
        setWorkspaceFiles(res.data?.files || []);
        if (res.data?.loaded && res.data?.sheets?.length > 0) {
          const info = {
            fileId: res.data.fileId,
            fileName: res.data.fileName || 'Active Workbook',
            sheetCount: res.data.sheets.length,
            sheets: res.data.sheets,
            canUndo: !!res.data.canUndo,
            canRedo: !!res.data.canRedo,
            undoAction: res.data.undoAction || '',
            redoAction: res.data.redoAction || '',
          };
          setFileInfo(info);
          setActiveSheet(prev => {
            if (prev) {
              const matched = info.sheets.find(s => s.sheetName === prev.sheetName);
              if (matched) return matched;
            }
            const savedSheet = localStorage.getItem('tabufinery_active_sheet');
            if (savedSheet) {
              const matched = info.sheets.find(s => s.sheetName === savedSheet);
              if (matched) return matched;
            }
            return info.sheets[0];
          });
        } else {
          setFileInfo(null);
          setActiveSheet(null);
        }
      } catch (err) {
      }
    };
    fetchStatus();
  }, [currentUser, refreshKey]);

  const handleSelectSheet = useCallback((sheet) => {
    setActiveSheet(sheet);
    if (sheet?.sheetName) {
      localStorage.setItem('tabufinery_active_sheet', sheet.sheetName);
    }
  }, []);

  const onUploadSuccess = useCallback((data) => {
    setFileInfo(data);
    if (data.sheets?.length > 0) handleSelectSheet(data.sheets[0]);
  }, [handleSelectSheet]);

  const onRefresh = useCallback(() => setRefreshKey(k => k + 1), []);

  const handleSelectFile = useCallback(async (fileId) => {
    try {
      const res = await selectWorkspaceFile(fileId);
      if (res.data?.success) {
        onRefresh();
      }
    } catch (err) {
      console.error('Failed to select active workspace file:', err);
    }
  }, [onRefresh]);

  const handleDeleteFile = useCallback(async (fileId) => {
    try {
      await deleteWorkspaceFile(fileId);
      onRefresh();
    } catch (err) {
      console.error('Failed to delete workspace file:', err);
    }
  }, [onRefresh]);

  const navigate = useCallback((p) => {
    if (p === 'admin' && currentUser?.role !== 'ROLE_ADMIN') {
      setPage('overview');
      setMobileNavOpen(false);
      return;
    }
    setPage(p);
    setMobileNavOpen(false);
  }, [currentUser]);

  const handleAuthSuccess = (user) => {
    setCurrentUser(user);
    setShowAuthModal(false);
  };

  const handleLogout = () => {
    clearStoredAuth();
    setCurrentUser(null);
    setFileInfo(null);
    setActiveSheet(null);
    setWorkspaceFiles([]);
    localStorage.removeItem('tabufinery_active_page');
    localStorage.removeItem('tabufinery_active_sheet');
    setPage('overview');
    if (typeof window !== 'undefined') {
      window.history.replaceState(null, '', '#overview');
    }
  };

  const handleProjectLoaded = (preview, activeSheetName) => {
    setFileInfo(preview);
    if (preview.sheets?.length > 0) {
      const match = preview.sheets.find(s => s.sheetName === activeSheetName);
      handleSelectSheet(match || preview.sheets[0]);
    }
    setPage('workspace');
    onRefresh();
  };

  const sheetName = activeSheet?.sheetName || null;
  const headers = activeSheet?.headers || [];
  const loaded = !!fileInfo;

  const pageNames = {
    overview: 'Overview',
    workspace: 'Data Workspace',
    assistant: 'AI Analyst & Insights',
    analytics: 'Analytics & Charts',
    export: 'Export Hub',
    admin: 'Admin Console',
    settings: 'Settings & Documentation',
  };

  return (
    <>
      {showSplash && <SplashScreen onFinish={() => setShowSplash(false)} theme={currentTheme} />}
      <OnboardingModal isOpen={!showSplash && showOnboarding} onClose={() => setShowOnboarding(false)} />

      <AuthModal
        isOpen={showAuthModal}
        forced={false}
        onClose={() => setShowAuthModal(false)}
        onAuthSuccess={handleAuthSuccess}
      />

      <SavedProjectsModal
        isOpen={showProjectsModal}
        onClose={() => setShowProjectsModal(false)}
        currentUser={currentUser}
        loaded={loaded}
        fileName={fileInfo?.fileName}
        activeSheetName={sheetName}
        onProjectLoaded={handleProjectLoaded}
        onRequestAuth={() => setShowAuthModal(true)}
      />

      <div className="app-layout">
        <Sidebar
          activePage={page}
          onNavigate={navigate}
          isMobileOpen={mobileNavOpen}
          onClose={() => setMobileNavOpen(false)}
          currentUser={currentUser}
        />

        <div className="main-area">
          <TopNav
            title={page === 'admin' && currentUser?.role !== 'ROLE_ADMIN' ? 'Overview' : (pageNames[page] || 'Overview')}
            loaded={loaded}
            fileName={fileInfo?.fileName}
            fileId={fileInfo?.fileId}
            workspaceFiles={workspaceFiles}
            onSelectFile={handleSelectFile}
            onDeleteFile={handleDeleteFile}
            onToggleMobileNav={() => setMobileNavOpen(prev => !prev)}
            currentUser={currentUser}
            onOpenAuth={() => setShowAuthModal(true)}
            onOpenProjects={() => {
              if (!currentUser) {
                setShowAuthModal(true);
              } else {
                setShowProjectsModal(true);
              }
            }}
            onLogout={handleLogout}
            onNavigate={navigate}
          />

          <div className="page-content">
            <ErrorBoundary key={page}>
              {page === 'overview' && (
                <DashboardPage
                  loaded={loaded}
                  fileInfo={fileInfo}
                  activeSheet={activeSheet}
                  onNavigate={navigate}
                  currentUser={currentUser}
                  onRequestAuth={() => setShowAuthModal(true)}
                />
              )}
              {page === 'workspace' && (
                <DataWorkspace
                  fileInfo={fileInfo}
                  activeSheet={activeSheet}
                  setActiveSheet={handleSelectSheet}
                  workspaceFiles={workspaceFiles}
                  onSelectFile={handleSelectFile}
                  onDeleteFile={handleDeleteFile}
                  onUploadSuccess={onUploadSuccess}
                  onRefresh={onRefresh}
                  refreshKey={refreshKey}
                  onOpenProjects={() => {
                    if (!currentUser) {
                      setShowAuthModal(true);
                    } else {
                      setShowProjectsModal(true);
                    }
                  }}
                  currentUser={currentUser}
                  onRequestAuth={() => setShowAuthModal(true)}
                />
              )}
              {page === 'assistant' && (
                <AIAssistant
                  sheetName={sheetName}
                  loaded={loaded}
                  onRefresh={onRefresh}
                  onNavigate={navigate}
                  currentUser={currentUser}
                  onRequestAuth={() => setShowAuthModal(true)}
                />
              )}
              {page === 'analytics' && (
                <AnalyticsPage
                  sheetName={sheetName}
                  sheets={fileInfo?.sheets}
                  onSelectSheet={handleSelectSheet}
                  headers={headers}
                  loaded={loaded}
                  onNavigate={navigate}
                  currentUser={currentUser}
                  onRequestAuth={() => setShowAuthModal(true)}
                />
              )}
              {page === 'export' && (
                <ExportPage
                  sheetName={sheetName}
                  sheets={fileInfo?.sheets}
                  loaded={loaded}
                  onNavigate={navigate}
                  currentUser={currentUser}
                  onRequestAuth={() => setShowAuthModal(true)}
                />
              )}
              {page === 'admin' && currentUser?.role === 'ROLE_ADMIN' && (
                <AdminPage currentUser={currentUser} />
              )}
              {page === 'settings' && (
                <SettingsPage
                  onReplayOnboarding={() => setShowOnboarding(true)}
                  currentUser={currentUser}
                />
              )}
            </ErrorBoundary>
          </div>
        </div>
      </div>

      <SupportChatWidget currentUser={currentUser} onRequestAuth={() => setShowAuthModal(true)} />
    </>
  );
}
