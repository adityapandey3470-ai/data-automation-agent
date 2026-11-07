const LIVE_UPDATES_ENABLED = false;

class DashboardWebSocketClient {
  constructor() {
    this.socket = null;
    this.listeners = new Set();
    this.isConnected = false;
    this.reconnectTimer = null;
  }

  connect() {
    if (!LIVE_UPDATES_ENABLED || typeof window === 'undefined' || this.socket) return;

    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/ws`;

      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.isConnected = true;
      };

      this.socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.listeners.forEach((listener) => {
            try {
              listener(data);
            } catch (_) {}
          });
        } catch (_) {}
      };

      this.socket.onerror = () => {
        this.isConnected = false;
      };

      this.socket.onclose = () => {
        this.isConnected = false;
        this.socket = null;
        if (!this.reconnectTimer) {
          this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.connect();
          }, 10000);
        }
      };
    } catch (_) {
      this.isConnected = false;
    }
  }

  subscribe(callback) {
    this.listeners.add(callback);
    if (!this.socket) {
      this.connect();
    }
    return () => {
      this.listeners.delete(callback);
    };
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socket) {
      this.socket.close();
      this.socket = null;
      this.isConnected = false;
    }
  }
}

export const dashboardWs = new DashboardWebSocketClient();
