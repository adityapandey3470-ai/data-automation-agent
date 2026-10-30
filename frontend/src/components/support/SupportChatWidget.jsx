import React, { useState, useEffect, useRef } from 'react';
import {
  sendSupportMessage,
  submitSupportTicket,
  getSupportQuickQuestions,
  getErrorMessage
} from '../../services/api';
import {
  MessageSquare,
  X,
  Send,
  Headphones,
  Bot,
  AlertCircle,
  CheckCircle,
  RefreshCw,
  Mail,
  Zap
} from '../icons/Icons';

function FormattedMessage({ text }) {
  if (!text) return null;

  const lines = text.split('\n');
  const elements = [];
  let currentList = [];

  const flushList = () => {
    if (currentList.length > 0) {
      elements.push(
        <ul key={`ul-${elements.length}`} style={{ margin: '4px 0 6px 0', paddingLeft: 18 }}>
          {currentList.map((item, idx) => (
            <li key={idx} style={{ marginBottom: 3 }}>
              {renderInline(item)}
            </li>
          ))}
        </ul>
      );
      currentList = [];
    }
  };

  const renderInline = (str) => {
    const parts = [];
    const regex = /(\*\*([^*]+)\*\*|`([^`]+)`)/g;
    let lastIdx = 0;
    let match;

    while ((match = regex.exec(str)) !== null) {
      if (match.index > lastIdx) {
        parts.push(str.slice(lastIdx, match.index));
      }
      if (match[2]) {
        parts.push(<strong key={match.index}>{match[2]}</strong>);
      } else if (match[3]) {
        parts.push(<code key={match.index}>{match[3]}</code>);
      }
      lastIdx = regex.lastIndex;
    }
    if (lastIdx < str.length) {
      parts.push(str.slice(lastIdx));
    }
    return parts.length > 0 ? parts : str;
  };

  lines.forEach((line, idx) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('### ')) {
      flushList();
      elements.push(
        <h4 key={idx} style={{ margin: '8px 0 4px 0', fontSize: 13, fontWeight: 700, color: 'var(--emerald)' }}>
          {trimmed.replace('### ', '')}
        </h4>
      );
    } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      currentList.push(trimmed.slice(2));
    } else if (/^\d+\.\s/.test(trimmed)) {
      currentList.push(trimmed.replace(/^\d+\.\s/, ''));
    } else if (trimmed === '') {
      flushList();
    } else {
      flushList();
      elements.push(
        <p key={idx} style={{ margin: '3px 0' }}>
          {renderInline(line)}
        </p>
      );
    }
  });
  flushList();

  return <div className="formatted-chat-msg">{elements}</div>;
}

export default function SupportChatWidget({ currentUser, onRequestAuth }) {
  const [isOpen, setIsOpen] = useState(false);
  const [view, setView] = useState('chat');
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [quickQuestions, setQuickQuestions] = useState([]);

  const [fabPos, setFabPos] = useState(() => {
    try {
      const saved = localStorage.getItem('tabufinery_support_fab_pos');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
          return parsed;
        }
      }
    } catch {}
    return null;
  });
  const [isDragging, setIsDragging] = useState(false);
  const isDraggingRef = useRef(false);
  const dragMovedRef = useRef(false);
  const startPointerRef = useRef({ x: 0, y: 0 });
  const startPosRef = useRef({ x: 0, y: 0 });
  const fabRef = useRef(null);

  const handleFabPointerDown = (e) => {
    if (e.button !== 0 && e.pointerType === 'mouse') return;
    const target = e.currentTarget;
    try {
      target.setPointerCapture(e.pointerId);
    } catch {}

    isDraggingRef.current = true;
    dragMovedRef.current = false;
    setIsDragging(true);

    const rect = target.getBoundingClientRect();
    startPointerRef.current = { x: e.clientX, y: e.clientY };
    startPosRef.current = { x: rect.left, y: rect.top };
  };

  const handleFabPointerMove = (e) => {
    if (!isDraggingRef.current) return;
    const dx = e.clientX - startPointerRef.current.x;
    const dy = e.clientY - startPointerRef.current.y;

    if (!dragMovedRef.current && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
      dragMovedRef.current = true;
    }

    if (dragMovedRef.current) {
      const btnWidth = fabRef.current?.offsetWidth || 150;
      const btnHeight = fabRef.current?.offsetHeight || 44;
      const margin = 10;
      const maxX = Math.max(margin, window.innerWidth - btnWidth - margin);
      const maxY = Math.max(margin, window.innerHeight - btnHeight - margin);

      const newX = Math.max(margin, Math.min(maxX, startPosRef.current.x + dx));
      const newY = Math.max(margin, Math.min(maxY, startPosRef.current.y + dy));

      setFabPos({ x: newX, y: newY });
    }
  };

  const handleFabPointerUp = (e) => {
    if (!isDraggingRef.current) return;
    isDraggingRef.current = false;
    setIsDragging(false);
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}

    if (dragMovedRef.current) {
      try {
        const rect = fabRef.current?.getBoundingClientRect();
        if (rect) {
          localStorage.setItem('tabufinery_support_fab_pos', JSON.stringify({ x: rect.left, y: rect.top }));
        }
      } catch {}
    }
  };

  const handleFabClick = (e) => {
    if (dragMovedRef.current) {
      e.preventDefault();
      e.stopPropagation();
      dragMovedRef.current = false;
      return;
    }
    setIsOpen(true);
  };

  useEffect(() => {
    const handleResize = () => {
      setFabPos(prev => {
        if (!prev) return null;
        const btnWidth = fabRef.current?.offsetWidth || 150;
        const btnHeight = fabRef.current?.offsetHeight || 44;
        const margin = 10;
        const maxX = Math.max(margin, window.innerWidth - btnWidth - margin);
        const maxY = Math.max(margin, window.innerHeight - btnHeight - margin);
        return {
          x: Math.max(margin, Math.min(maxX, prev.x)),
          y: Math.max(margin, Math.min(maxY, prev.y))
        };
      });
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const [ticketCategory, setTicketCategory] = useState('BUG');
  const [ticketSubject, setTicketSubject] = useState('');
  const [ticketMessage, setTicketMessage] = useState('');
  const [ticketLoading, setTicketLoading] = useState(false);
  const [ticketSuccess, setTicketSuccess] = useState(null);
  const [ticketError, setTicketError] = useState(null);

  const [messages, setMessages] = useState(() => [
    {
      id: 'welcome',
      role: 'assistant',
      text: `Hi ${currentUser?.name ? currentUser.name.split(' ')[0] : 'there'}! 👋 I am your Tabufinery Support Assistant.\n\nI can help you upload files, understand data cleaning tools, use Undo/Redo, export PDF reports, and troubleshoot any issues. What would you like to know?`,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      engine: 'Tabufinery Support',
      suggestedFollowUps: [
        'How do I upload an Excel file?',
        'How does Undo/Redo work?',
        'What data cleaning transformations are supported?',
        'How can I export PDF reports?'
      ]
    }
  ]);

  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (isOpen && view === 'chat') {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen, view, loading]);

  useEffect(() => {
    const fetchQuickQuestions = async () => {
      try {
        const res = await getSupportQuickQuestions();
        if (Array.isArray(res.data) && res.data.length > 0) {
          setQuickQuestions(res.data);
        }
      } catch {
        setQuickQuestions([
          'How do I upload an Excel file?',
          'How does Undo/Redo work?',
          'What data cleaning operations are supported?',
          'How can I export PDF reports?'
        ]);
      }
    };
    if (isOpen) {
      fetchQuickQuestions();
    }
  }, [isOpen]);

  const handleSendMessage = async (userText) => {
    const textToSend = (userText || input).trim();
    if (!textToSend || loading) return;

    setInput('');

    const userMsg = {
      id: `u-${Date.now()}`,
      role: 'user',
      text: textToSend,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    const newMessages = [...messages, userMsg];
    setMessages(newMessages);
    setLoading(true);

    if (!currentUser) {
      setTimeout(() => {
        const errMsg = {
          id: `auth-${Date.now()}`,
          role: 'assistant',
          text: 'A temporary issue occurred while connecting to our support service. Please sign in and try again.',
          engine: 'Support Service',
          canEscalateToTicket: false,
          needsAuth: true,
          suggestedFollowUps: ['Sign In to Tabufinery'],
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, errMsg]);
        setLoading(false);
      }, 350);
      return;
    }

    try {
      const historyPayload = newMessages
        .filter(m => m.id !== 'welcome')
        .slice(-6)
        .map(m => ({ role: m.role, text: m.text }));

      const res = await sendSupportMessage(textToSend, historyPayload);
      const data = res.data;

      const assistantMsg = {
        id: `a-${Date.now()}`,
        role: 'assistant',
        text: data.reply || 'Here is the requested information.',
        engine: data.engine === 'GEMINI_AI' ? '⚡ Gemini AI' : '📚 Knowledge Base',
        suggestedFollowUps: data.suggestedFollowUps || [],
        canEscalateToTicket: !!data.canEscalateToTicket && !!currentUser,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };

      setMessages(prev => [...prev, assistantMsg]);
    } catch (err) {
      const isAuthErr = err.response?.status === 401;
      const friendlyNotice = isAuthErr
        ? 'A temporary issue occurred while connecting to our support service. Please sign in and try again.'
        : getErrorMessage(err, 'A temporary issue occurred while connecting to our support service. Please try again.');

      const errMsg = {
        id: `err-${Date.now()}`,
        role: 'assistant',
        text: friendlyNotice,
        engine: 'Support Service',
        canEscalateToTicket: !!currentUser && !isAuthErr,
        needsAuth: isAuthErr,
        suggestedFollowUps: isAuthErr
          ? ['Sign In to Tabufinery']
          : (currentUser ? ['Submit a support ticket', 'How do I upload an Excel file?'] : ['Sign In to Tabufinery']),
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages(prev => [...prev, errMsg]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleTicketSubmit = async (e) => {
    e.preventDefault();
    setTicketError(null);

    if (!currentUser) {
      setTicketError('A temporary issue occurred while connecting to our support service. Please sign in and try again.');
      if (onRequestAuth) onRequestAuth();
      return;
    }

    if (!ticketSubject.trim()) {
      setTicketError('Please provide a subject for your ticket.');
      return;
    }
    if (!ticketMessage.trim() || ticketMessage.trim().length < 10) {
      setTicketError('Please provide detailed message context (at least 10 characters).');
      return;
    }

    setTicketLoading(true);
    try {
      const res = await submitSupportTicket(ticketCategory, ticketSubject.trim(), ticketMessage.trim());
      setTicketSuccess(res.data);
      setTicketSubject('');
      setTicketMessage('');
    } catch (err) {
      if (err.response?.status === 401) {
        setTicketError('A temporary issue occurred while connecting to our support service. Please sign in and try again.');
      } else {
        setTicketError(getErrorMessage(err, 'A temporary issue occurred while connecting to our support service. Please try again.'));
      }
    } finally {
      setTicketLoading(false);
    }
  };

  const handleClearHistory = () => {
    setMessages([
      {
        id: `welcome-${Date.now()}`,
        role: 'assistant',
        text: `Chat reset. What question can I help you with?`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        engine: 'Tabufinery Support',
        suggestedFollowUps: quickQuestions.slice(0, 4)
      }
    ]);
  };

  return (
    <>
      {!isOpen && (
        <button
          ref={fabRef}
          className="support-fab"
          onClick={handleFabClick}
          onPointerDown={handleFabPointerDown}
          onPointerMove={handleFabPointerMove}
          onPointerUp={handleFabPointerUp}
          onPointerCancel={handleFabPointerUp}
          aria-label="Open AI Support Chat"
          title="Drag to reposition anywhere • Click to open Support & Help"
          style={fabPos ? {
            left: `${fabPos.x}px`,
            top: `${fabPos.y}px`,
            bottom: 'auto',
            right: 'auto',
            cursor: isDragging ? 'grabbing' : 'grab',
            userSelect: 'none',
            touchAction: 'none',
            transition: isDragging ? 'none' : 'box-shadow 0.2s ease, transform 0.2s ease'
          } : {
            cursor: isDragging ? 'grabbing' : 'grab',
            userSelect: 'none',
            touchAction: 'none'
          }}
        >
          <div className="support-fab-pulse" />
          <Headphones size={18} />
          <span>Support & Help</span>
        </button>
      )}

      {isOpen && (
        <div
          className="support-chat-window"
          style={(() => {
            if (typeof window !== 'undefined' && window.innerWidth <= 640) {
              return undefined;
            }
            if (!fabPos) return undefined;
            const chatWidth = Math.min(395, window.innerWidth - 32);
            const chatHeight = Math.min(560, window.innerHeight - 80);
            const margin = 16;
            const left = Math.max(margin, Math.min(window.innerWidth - chatWidth - margin, fabPos.x));
            let top;
            if (fabPos.y > window.innerHeight / 2) {
              top = Math.max(margin, fabPos.y - chatHeight - 12);
            } else {
              top = Math.min(window.innerHeight - chatHeight - margin, fabPos.y + 48);
            }
            return {
              left: `${left}px`,
              top: `${top}px`,
              bottom: 'auto',
              right: 'auto'
            };
          })()}
        >
          <div className="support-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: '50%',
                  background: 'var(--emerald)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#ffffff'
                }}
              >
                <Bot size={18} />
              </div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span>Tabufinery Support</span>
                  <span style={{ fontSize: 9, padding: '1px 5px', borderRadius: 4, background: 'rgba(16, 185, 129, 0.15)', color: 'var(--emerald)' }}>
                    ACTIVE
                  </span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  {view === 'chat' ? 'Answers in seconds · AI & Knowledge Base' : 'Direct Escalation to Engineers'}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <button
                className="btn btn-xs btn-outline"
                onClick={() => {
                  setView(v => (v === 'chat' ? 'ticket' : 'chat'));
                  setTicketError(null);
                  setTicketSuccess(null);
                }}
                style={{ height: 26, fontSize: 11, padding: '0 8px' }}
                title={view === 'chat' ? 'Switch to Ticket Form' : 'Switch to Chat'}
              >
                {view === 'chat' ? 'Ticket' : 'Chat'}
              </button>
              <button
                className="btn-icon"
                onClick={() => setIsOpen(false)}
                aria-label="Close Support Window"
                style={{ width: 28, height: 28 }}
              >
                <X size={15} />
              </button>
            </div>
          </div>

          {view === 'chat' && (
            <>
              <div className="support-body">
                {messages.map((m) => (
                  <div key={m.id} className={`support-msg ${m.role}`}>
                    {m.role === 'assistant' ? (
                      <>
                        <FormattedMessage text={m.text} />
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 6, fontSize: 10, color: 'var(--text-muted)' }}>
                          <span>{m.engine || 'Support Bot'}</span>
                          <span>{m.timestamp}</span>
                        </div>

                        {m.suggestedFollowUps && m.suggestedFollowUps.length > 0 && (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 8 }}>
                            {m.suggestedFollowUps.map((chip, i) => (
                              <button
                                key={i}
                                className="support-chip"
                                onClick={() => {
                                  if (chip.toLowerCase().includes('sign in') || chip.toLowerCase().includes('log in')) {
                                    if (onRequestAuth) onRequestAuth();
                                  } else if (chip.toLowerCase().includes('ticket')) {
                                    setView('ticket');
                                  } else {
                                    handleSendMessage(chip);
                                  }
                                }}
                              >
                                {chip}
                              </button>
                            ))}
                          </div>
                        )}

                        {!currentUser && m.needsAuth && (
                          <div style={{ marginTop: 8, paddingTop: 6, borderTop: '1px solid var(--border)', fontSize: 11 }}>
                            <button
                              onClick={() => { if (onRequestAuth) onRequestAuth(); }}
                              style={{ background: 'none', border: 'none', padding: 0, color: 'var(--emerald)', fontWeight: 600, cursor: 'pointer', textDecoration: 'underline' }}
                            >
                              Sign in to your account →
                            </button>
                          </div>
                        )}

                        {m.canEscalateToTicket && currentUser && (
                          <div style={{ marginTop: 8, paddingTop: 6, borderTop: '1px solid var(--border)', fontSize: 11 }}>
                            <span style={{ color: 'var(--text-muted)' }}>Need further help? </span>
                            <button
                              onClick={() => setView('ticket')}
                              style={{ background: 'none', border: 'none', padding: 0, color: 'var(--emerald)', fontWeight: 600, cursor: 'pointer', textDecoration: 'underline' }}
                            >
                              Open a Support Ticket →
                            </button>
                          </div>
                        )}
                      </>
                    ) : (
                      <>
                        <div>{m.text}</div>
                        <div style={{ alignSelf: 'flex-end', marginTop: 4, fontSize: 10, opacity: 0.85 }}>
                          {m.timestamp}
                        </div>
                      </>
                    )}
                  </div>
                ))}

                {loading && (
                  <div className="support-msg assistant" style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px' }}>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <span className="support-fab-pulse" style={{ width: 6, height: 6 }} />
                      <span className="support-fab-pulse" style={{ width: 6, height: 6, animationDelay: '0.2s' }} />
                      <span className="support-fab-pulse" style={{ width: 6, height: 6, animationDelay: '0.4s' }} />
                    </div>
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Assistant is typing...</span>
                  </div>
                )}

                <div ref={messagesEndRef} />
              </div>

              <div className="support-footer">
                <button
                  className="btn-icon"
                  onClick={handleClearHistory}
                  title="Clear conversation and restart"
                  style={{ width: 32, height: 32, flexShrink: 0 }}
                >
                  <RefreshCw size={13} />
                </button>
                <input
                  type="text"
                  className="input-field"
                  placeholder="Ask a question about Tabufinery..."
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={loading}
                  style={{ height: 36, fontSize: 12, flex: 1 }}
                />
                <button
                  className="btn btn-primary"
                  onClick={() => handleSendMessage()}
                  disabled={loading || !input.trim()}
                  style={{ height: 36, width: 36, padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
                  aria-label="Send support question"
                >
                  <Send size={14} />
                </button>
              </div>
            </>
          )}

          {view === 'ticket' && (
            <div className="support-body" style={{ display: 'flex', flexDirection: 'column' }}>
              {!currentUser ? (
                <div style={{ textAlign: 'center', padding: '32px 14px', margin: 'auto', maxWidth: 330 }}>
                  <div
                    style={{
                      width: 52,
                      height: 52,
                      borderRadius: '50%',
                      background: 'rgba(16, 185, 129, 0.12)',
                      color: 'var(--emerald)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      margin: '0 auto 16px auto'
                    }}
                  >
                    <Headphones size={24} />
                  </div>
                  <h3 style={{ fontSize: 16, fontWeight: 700, margin: '0 0 8px 0', color: 'var(--text-primary)' }}>
                    Support Ticket Escalation
                  </h3>
                  <p style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.6, margin: '0 0 20px 0' }}>
                    A temporary issue occurred while connecting to our support service. Please sign in and try again to submit an official engineering ticket.
                  </p>
                  <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      onClick={() => setView('chat')}
                      style={{ flex: 1 }}
                    >
                      Back to Chat
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => {
                        if (onRequestAuth) onRequestAuth();
                      }}
                      style={{ flex: 1 }}
                    >
                      Sign In
                    </button>
                  </div>
                </div>
              ) : ticketSuccess ? (
                <div style={{ textAlign: 'center', padding: '24px 12px', margin: 'auto' }}>
                  <div
                    style={{
                      width: 52,
                      height: 52,
                      borderRadius: '50%',
                      background: 'rgba(16, 185, 129, 0.15)',
                      color: 'var(--emerald)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      margin: '0 auto 14px auto'
                    }}
                  >
                    <CheckCircle size={28} />
                  </div>
                  <h3 style={{ fontSize: 16, fontWeight: 700, margin: '0 0 6px 0', color: 'var(--text-primary)' }}>
                    Ticket Submitted!
                  </h3>
                  <div
                    style={{
                      display: 'inline-block',
                      background: 'var(--surface-secondary)',
                      border: '1px dashed var(--emerald)',
                      padding: '4px 12px',
                      borderRadius: 6,
                      fontSize: 13,
                      fontWeight: 700,
                      color: 'var(--emerald)',
                      margin: '8px 0 12px 0'
                    }}
                  >
                    {ticketSuccess.ticketCode}
                  </div>
                  <p style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.5, margin: '0 0 20px 0' }}>
                    {ticketSuccess.message}
                  </p>
                  <button
                    className="btn btn-primary btn-sm w-full"
                    onClick={() => {
                      setTicketSuccess(null);
                      setView('chat');
                    }}
                  >
                    Return to Support Chat
                  </button>
                </div>
              ) : (
                <form onSubmit={handleTicketSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12, height: '100%' }}>
                  <div style={{ background: 'var(--surface-secondary)', border: '1px solid var(--border)', borderRadius: 8, padding: '10px 12px' }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 2 }}>
                      Direct Engineering Ticket
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      Our engineering team investigates every ticket and responds to <strong>{currentUser?.email}</strong>.
                    </div>
                  </div>

                  {ticketError && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px', borderRadius: 6, background: 'rgba(239, 68, 68, 0.12)', color: '#ef4444', fontSize: 12 }}>
                      <AlertCircle size={14} style={{ flexShrink: 0 }} />
                      <span>{ticketError}</span>
                    </div>
                  )}

                  <div>
                    <label className="form-label" style={{ fontSize: 11, marginBottom: 4, display: 'block' }}>
                      Category
                    </label>
                    <select
                      className="input-field"
                      value={ticketCategory}
                      onChange={(e) => setTicketCategory(e.target.value)}
                      style={{
                        height: 38,
                        minHeight: 38,
                        padding: '7px 32px 7px 12px',
                        fontSize: 13,
                        lineHeight: '22px',
                        cursor: 'pointer'
                      }}
                    >
                      <option value="BUG">Bug or Processing Failure</option>
                      <option value="DATA_PROCESSING">Data Cleaning / Formula Question</option>
                      <option value="FEATURE">Feature Suggestion</option>
                      <option value="ACCOUNT">Account or Security Inquiries</option>
                      <option value="OTHER">Other Assistance</option>
                    </select>
                  </div>

                  <div>
                    <label className="form-label" style={{ fontSize: 11, marginBottom: 4, display: 'block' }}>
                      Subject
                    </label>
                    <input
                      type="text"
                      className="input-field"
                      placeholder="Brief summary of the problem"
                      value={ticketSubject}
                      onChange={(e) => setTicketSubject(e.target.value)}
                      style={{
                        height: 38,
                        minHeight: 38,
                        padding: '7px 12px',
                        fontSize: 13,
                        lineHeight: '22px'
                      }}
                    />
                  </div>

                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                    <label className="form-label" style={{ fontSize: 11, marginBottom: 4, display: 'block' }}>
                      Issue Details
                    </label>
                    <textarea
                      className="input-field"
                      placeholder="Please include: file type, operation attempted, error message seen, or steps to reproduce..."
                      value={ticketMessage}
                      onChange={(e) => setTicketMessage(e.target.value)}
                      style={{ flex: 1, minHeight: 110, fontSize: 12, resize: 'none', padding: 8 }}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 8, marginTop: 'auto' }}>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      onClick={() => setView('chat')}
                      style={{ flex: 1 }}
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      className="btn btn-primary btn-sm"
                      disabled={ticketLoading}
                      style={{ flex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                    >
                      {ticketLoading ? (
                        <>
                          <RefreshCw size={13} className="spin" /> Submitting...
                        </>
                      ) : (
                        <>
                          <Mail size={13} /> Submit Ticket
                        </>
                      )}
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}
        </div>
      )}
    </>
  );
}
