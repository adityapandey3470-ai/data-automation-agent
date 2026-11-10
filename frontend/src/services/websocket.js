import { Client } from '@stomp/stompjs';
import { getStoredToken } from './api';

class DashboardWebSocketClient {
  constructor() {
    this.client = null;
    this.subscriptions = new Map();
    this.activeStompSubs = new Map();
    this.listeners = new Set();
    this.isConnected = false;
  }

  connect() {
    if (typeof window === 'undefined') return;
    const token = getStoredToken();
    if (!token) return;

    if (this.client && this.client.active) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    this.client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.isConnected = true;
        this.subscriptions.forEach((callbacks, destination) => {
          this._bindDestination(destination, callbacks);
        });
      },
      onDisconnect: () => {
        this.isConnected = false;
        this.activeStompSubs.clear();
      },
      onStompError: () => {
        this.isConnected = false;
      },
      onWebSocketClose: () => {
        this.isConnected = false;
        this.activeStompSubs.clear();
      }
    });

    this.client.activate();
  }

  _bindDestination(destination, callbackSet) {
    if (!this.client || !this.client.connected) return;
    if (this.activeStompSubs.has(destination)) return;

    const sub = this.client.subscribe(destination, (message) => {
      let data = message.body;
      try {
        data = JSON.parse(message.body);
      } catch (_) {}
      callbackSet.forEach(cb => {
        try { cb(data); } catch (_) {}
      });
      this.listeners.forEach(cb => {
        try { cb(data); } catch (_) {}
      });
    });

    this.activeStompSubs.set(destination, sub);
  }

  subscribeToTopic(destination, callback) {
    if (!this.subscriptions.has(destination)) {
      this.subscriptions.set(destination, new Set());
    }
    const set = this.subscriptions.get(destination);
    set.add(callback);

    this.connect();
    if (this.isConnected) {
      this._bindDestination(destination, set);
    }

    return () => {
      set.delete(callback);
      if (set.size === 0) {
        this.subscriptions.delete(destination);
        const stompSub = this.activeStompSubs.get(destination);
        if (stompSub) {
          stompSub.unsubscribe();
          this.activeStompSubs.delete(destination);
        }
      }
    };
  }

  subscribe(callback) {
    this.listeners.add(callback);
    this.connect();
    return () => {
      this.listeners.delete(callback);
    };
  }

  disconnect() {
    this.subscriptions.clear();
    this.activeStompSubs.clear();
    this.listeners.clear();
    if (this.client) {
      try {
        this.client.deactivate();
      } catch (_) {}
      this.client = null;
    }
    this.isConnected = false;
  }
}

export const dashboardWs = new DashboardWebSocketClient();
