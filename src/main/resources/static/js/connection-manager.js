/**
 * 連線狀態管理器（使用 WebSocket）
 * 共用模組，可在不同頁面中使用
 */
class ConnectionManager {
    // sessionStorage key 用於跨頁面保存連線狀態
    static STORAGE_KEY = 'connectionState';
    
    constructor(options = {}) {
        // 從 sessionStorage 讀取先前的狀態
        const savedState = this.loadStateFromStorage();
        
        this.lastOnlineState = savedState.lastOnlineState;
        this.browserOnline = navigator.onLine;
        // 如果瀏覽器報告離線，serverOnline 也應該是 false
        // 否則使用儲存的狀態（預設 false）
        this.serverOnline = navigator.onLine ? savedState.serverOnline : false;
        this.websocket = null;
        this.reconnectTimeout = null;
        this.wsEndpoint = null;
        // 如果有儲存的狀態，視為已確認過連線
        this.connectionConfirmed = savedState.connectionConfirmed;
        this.pingInterval = null;
        this.pongTimeout = null;
        this.intentionalClose = false;
        this.errorOccurred = false; // 標記是否發生連線錯誤
        
        // 回調函數（可由各頁面自訂）
        this.onUpdateUI = options.onUpdateUI || null;
        this.onMessage = options.onMessage || null;
        this.onOnline = options.onOnline || null;
        this.onOffline = options.onOffline || null;
    }
    
    // 從 sessionStorage 載入狀態
    loadStateFromStorage() {
        try {
            const saved = sessionStorage.getItem(ConnectionManager.STORAGE_KEY);
            if (saved) {
                const state = JSON.parse(saved);
                console.log('[Connection] 從 sessionStorage 載入狀態:', state);
                return {
                    lastOnlineState: state.lastOnlineState ?? null,
                    serverOnline: state.serverOnline ?? false,
                    connectionConfirmed: state.connectionConfirmed ?? false
                };
            }
        } catch (e) {
            console.warn('[Connection] 無法從 sessionStorage 載入狀態:', e);
        }
        return {
            lastOnlineState: null,
            serverOnline: false,
            connectionConfirmed: false
        };
    }
    
    // 儲存狀態到 sessionStorage
    saveStateToStorage() {
        try {
            const state = {
                lastOnlineState: this.lastOnlineState,
                serverOnline: this.serverOnline,
                connectionConfirmed: this.connectionConfirmed
            };
            sessionStorage.setItem(ConnectionManager.STORAGE_KEY, JSON.stringify(state));
            console.log('[Connection] 狀態已儲存到 sessionStorage:', state);
        } catch (e) {
            console.warn('[Connection] 無法儲存狀態到 sessionStorage:', e);
        }
    }
    
    init() {
        // 設定 WebSocket 端點（自動判斷 ws 或 wss）
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.wsEndpoint = `${protocol}//${window.location.host}/ws/connection`;
        
        // 更新瀏覽器狀態
        this.browserOnline = navigator.onLine;
        
        console.log('[Connection] 初始化 - 瀏覽器:', this.browserOnline, '伺服器:', this.serverOnline, '已確認:', this.connectionConfirmed, 'lastOnlineState:', this.lastOnlineState);
        
        // 如果瀏覽器報告離線，立即顯示離線 UI（不需要等待 WebSocket）
        if (!this.browserOnline) {
            console.log('[Connection] 瀏覽器報告離線，立即顯示離線 UI');
            this.serverOnline = false;
            this.lastOnlineState = false;
            this.connectionConfirmed = true; // 標記為已確認（離線狀態）
            this.updateUI(false);
            this.saveStateToStorage();
            return; // 不嘗試建立 WebSocket
        }
        
        // 如果有先前儲存的狀態，立即顯示 UI（不等待 WebSocket）
        if (this.lastOnlineState !== null) {
            const currentState = this.browserOnline && this.serverOnline;
            console.log('[Connection] 使用儲存的狀態立即更新 UI:', currentState);
            this.updateUI(currentState);
        }
        
        // 使用 WebSocket 建立持久連線
        this.connectWebSocket();
        
        // 監聽瀏覽器的 online/offline 事件
        window.addEventListener('online', () => {
            console.log('[Connection] 瀏覽器回報網路已連線，立即檢查伺服器狀態');
            this.browserOnline = true;
            if (this.reconnectTimeout) {
                clearTimeout(this.reconnectTimeout);
                this.reconnectTimeout = null;
            }
            // 立即用 HTTP 快速檢查伺服器是否可達，再建立 WebSocket
            this.quickServerCheck().then(serverReachable => {
                if (serverReachable) {
                    console.log('[Connection] 伺服器可達，建立 WebSocket 連線');
                    this.connectWebSocket();
                } else {
                    console.log('[Connection] 伺服器不可達，稍後重試');
                    this.setServerOnline(false);
                    this.scheduleReconnect();
                }
            });
        });
        
        window.addEventListener('offline', () => {
            console.log('[Connection] 瀏覽器回報網路已斷線');
            this.browserOnline = false;
            this.closeWebSocket();
            this.serverOnline = false;
            // 離線時保持 connectionConfirmed 為 true，這樣頁面切換時能顯示離線狀態
            // this.connectionConfirmed = false; // 移除這行
            this.updateConnectionState();
            // 儲存離線狀態
            this.saveStateToStorage();
        });
        
        // 頁面可見性變化時重新連線
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                console.log('[Connection] 頁面回到前景');
                // 頁面回到前景時，先快速檢查伺服器狀態
                // 這樣可以避免因為背景閒置導致 WebSocket 斷線而誤判為離線
                if (navigator.onLine) {
                    this.quickServerCheck().then(serverReachable => {
                        if (serverReachable) {
                            // 伺服器可達，如果 WebSocket 已斷開則重新連線
                            if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
                                // 先更新狀態為線上，避免短暫顯示離線
                                if (!this.serverOnline) {
                                    console.log('[Connection] 頁面回到前景，伺服器可達，更新為線上狀態');
                                    this.connectionConfirmed = true;
                                    this.setServerOnline(true);
                                }
                                this.connectWebSocket();
                            }
                        } else {
                            // 伺服器不可達，確認離線狀態
                            console.log('[Connection] 頁面回到前景，伺服器不可達');
                            this.setServerOnline(false);
                            this.scheduleReconnect();
                        }
                    });
                } else {
                    // 瀏覽器報告離線
                    this.browserOnline = false;
                    this.setServerOnline(false);
                }
            }
        }, { passive: true });
    }
    
    closeWebSocket() {
        if (this.pingInterval) {
            clearInterval(this.pingInterval);
            this.pingInterval = null;
        }
        if (this.pongTimeout) {
            clearTimeout(this.pongTimeout);
            this.pongTimeout = null;
        }
        if (this.websocket) {
            this.intentionalClose = true;
            this.websocket.close();
            this.websocket = null;
        }
    }
    
    connectWebSocket() {
        this.closeWebSocket();
        
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
        
        if (!navigator.onLine) {
            console.log('[Connection] 瀏覽器報告離線，跳過 WebSocket 連線');
            this.browserOnline = false;
            this.serverOnline = false;
            // 離線時保持 connectionConfirmed 為 true，讓 UI 能顯示離線狀態
            if (this.lastOnlineState !== null) {
                this.connectionConfirmed = true;
            }
            this.updateConnectionState();
            this.saveStateToStorage();
            return;
        }
        
        console.log('[Connection] 正在建立 WebSocket 連線...', this.wsEndpoint);
        // 只有在瀏覽器在線時才重設 connectionConfirmed，等待 WebSocket 確認
        // 但如果已經有儲存的離線狀態，保持 UI 可見
        if (this.lastOnlineState === null) {
            this.connectionConfirmed = false;
        }
        
        try {
            this.websocket = new WebSocket(this.wsEndpoint);
        } catch (error) {
            console.log('[Connection] 無法建立 WebSocket 連線:', error);
            this.setServerOnline(false);
            this.scheduleReconnect();
            return;
        }
        
        this.websocket.onopen = () => {
            console.log('[Connection] WebSocket 連線已建立');
            this.connectionConfirmed = true;
            this.setServerOnline(true);
            // 連線成功時儲存狀態
            this.saveStateToStorage();
            this.startPing();
        };
        
        this.websocket.onmessage = (event) => {
            const data = event.data;
            console.log('[Connection] 收到訊息:', data);
            
            try {
                const message = JSON.parse(data);
                
                if (message.type === 'pong') {
                    if (this.pongTimeout) {
                        clearTimeout(this.pongTimeout);
                        this.pongTimeout = null;
                    }
                } else if (message.type === 'heartbeat') {
                    console.log('[Connection] 收到伺服器心跳');
                } else if (message.type === 'connected') {
                    console.log('[Connection] 收到連線確認');
                }
                
                // 呼叫自訂訊息處理器
                if (this.onMessage) {
                    this.onMessage(message);
                }
            } catch (e) {
                if (data === 'pong') {
                    if (this.pongTimeout) {
                        clearTimeout(this.pongTimeout);
                        this.pongTimeout = null;
                    }
                }
            }
        };
        
        this.websocket.onclose = (event) => {
            console.log('[Connection] WebSocket 連線已關閉，code:', event.code, 'reason:', event.reason);
            
            if (this.pingInterval) {
                clearInterval(this.pingInterval);
                this.pingInterval = null;
            }
            if (this.pongTimeout) {
                clearTimeout(this.pongTimeout);
                this.pongTimeout = null;
            }
            
            // 判斷是否為正常關閉
            // 1000: 正常關閉, 1001: 離開頁面
            // 主動呼叫 closeWebSocket() 時會設置 intentionalClose 標記
            // errorOccurred 表示是由 onerror 觸發的關閉
            const isNormalClose = this.intentionalClose || (!this.errorOccurred && (event.code === 1000 || event.code === 1001));
            this.intentionalClose = false;
            this.errorOccurred = false; // 重設錯誤標記
            
            if (isNormalClose) {
                console.log('[Connection] 正常關閉連線，不視為離線');
                // 正常關閉時不改變 connectionConfirmed，保持當前狀態
            } else {
                console.log('[Connection] 異常斷線');
                // 如果頁面在背景中，不立即顯示離線，等回到前景時再檢查
                if (document.visibilityState === 'hidden') {
                    console.log('[Connection] 頁面在背景中，延遲處理離線狀態');
                    // 只排程重連，不更新 UI 狀態
                    this.scheduleReconnect();
                } else {
                    // 頁面在前景，正常處理離線
                    this.setServerOnline(false);
                    this.scheduleReconnect();
                }
            }
        };
        
        this.websocket.onerror = (error) => {
            console.log('[Connection] WebSocket 連線錯誤，視為網路異常');
            // 標記發生錯誤，讓 onclose 知道這是異常關閉
            this.errorOccurred = true;
            // onclose 會被觸發，在那裡處理離線狀態
        };
    }
    
    startPing() {
        this.pingInterval = setInterval(() => {
            if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
                // 如果頁面在背景中，跳過 ping（避免因背景閒置導致誤判離線）
                if (document.visibilityState === 'hidden') {
                    console.log('[Connection] 頁面在背景中，跳過 ping');
                    return;
                }
                
                console.log('[Connection] 發送 ping');
                this.websocket.send(JSON.stringify({ type: 'ping' }));
                
                this.pongTimeout = setTimeout(() => {
                    console.log('[Connection] ping 超時未收到 pong');
                    this.closeWebSocket();
                    // 只有頁面在前景時才更新離線狀態
                    if (document.visibilityState === 'visible') {
                        this.setServerOnline(false);
                    }
                    this.scheduleReconnect();
                }, 5000);
            }
        }, 15000);
    }
    
    scheduleReconnect() {
        if (this.reconnectTimeout) return;
        
        if (!navigator.onLine) {
            console.log('[Connection] 瀏覽器報告離線，暫不排程重連');
            return;
        }
        
        const retryDelay = 3000;
        console.log(`[Connection] 將在 ${retryDelay / 1000} 秒後嘗試重新連線...`);
        
        this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this.connectWebSocket();
        }, retryDelay);
    }
    
    /**
     * 快速檢查伺服器是否可達（使用 HTTP HEAD 請求）
     * 比建立 WebSocket 連線更快，可以立即知道伺服器狀態
     */
    async quickServerCheck() {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 3000); // 3 秒超時
            
            const response = await fetch('/api/foods/health', {
                method: 'HEAD',
                cache: 'no-store',
                signal: controller.signal
            });
            
            clearTimeout(timeoutId);
            console.log('[Connection] 快速檢查結果: 伺服器可達, status:', response.status);
            return response.ok;
        } catch (error) {
            if (error.name === 'AbortError') {
                console.log('[Connection] 快速檢查超時');
            } else {
                console.log('[Connection] 快速檢查失敗:', error.message);
            }
            return false;
        }
    }
    
    setServerOnline(online) {
        console.log('[Connection] setServerOnline:', online);
        this.serverOnline = online;
        this.updateConnectionState();
        // 儲存狀態到 sessionStorage
        this.saveStateToStorage();
    }
    
    updateConnectionState() {
        const isOnline = this.browserOnline && this.serverOnline;
        console.log('[Connection] updateConnectionState - 瀏覽器:', this.browserOnline, '伺服器:', this.serverOnline, '=> 總體:', isOnline);
        
        const wasOffline = this.lastOnlineState === false;
        const wasOnline = this.lastOnlineState === true;
        
        if (this.lastOnlineState === isOnline) return;
        
        this.lastOnlineState = isOnline;
        
        // 更新 UI（updateUI 內部會呼叫 onUpdateUI 回調）
        this.updateUI(isOnline);
        
        // 發送事件通知其他模組
        window.dispatchEvent(new CustomEvent('connectionStateChanged', {
            detail: { isOnline }
        }));
        
        if (isOnline) {
            console.log('[Connection] 已連線（瀏覽器+伺服器都在線）');
            if (wasOffline) {
                this.showNotification('已恢復連線', 'success');
            }
            if (this.onOnline) {
                this.onOnline(wasOffline);
            }
        } else {
            console.log('[Connection] 已離線（瀏覽器或伺服器離線）');
            if (wasOnline) {
                this.showNotification('連線已中斷，已切換至離線模式', 'warning');
            }
            if (this.onOffline) {
                this.onOffline(wasOnline);
            }
        }
    }
    
    updateUI(isOnline) {
        console.log('[Connection] updateUI 被呼叫, isOnline:', isOnline, 'connectionConfirmed:', this.connectionConfirmed, 'lastOnlineState:', this.lastOnlineState);
        const indicator = document.getElementById('connectionIndicator');
        const banner = document.getElementById('offlineBanner');
        
        // 判斷是否應該顯示連線狀態 UI
        const shouldShowUI = this.connectionConfirmed || this.lastOnlineState !== null;
        
        if (indicator) {
            if (shouldShowUI) {
                indicator.style.display = '';
                if (isOnline) {
                    indicator.className = 'connection-indicator online';
                    indicator.innerHTML = '<i class="bi bi-wifi"></i> 線上';
                } else {
                    indicator.className = 'connection-indicator offline';
                    indicator.innerHTML = '<i class="bi bi-wifi-off"></i> 離線';
                }
                console.log('[Connection] indicator 已更新為:', isOnline ? '線上' : '離線');
            } else {
                indicator.style.display = 'none';
                console.log('[Connection] indicator 保持隱藏（等待 WebSocket 確認）');
            }
        } else {
            console.warn('[Connection] 找不到 connectionIndicator 元素！');
        }
        
        if (banner) {
            if (isOnline) {
                banner.style.display = 'none';
                document.body.classList.remove('offline-mode');
            } else if (shouldShowUI) {
                banner.style.display = 'block';
                document.body.classList.add('offline-mode');
            }
        }
        
        // 呼叫自訂 UI 更新回調（讓各頁面可以自訂額外的 UI 變化）
        if (this.onUpdateUI && shouldShowUI) {
            this.onUpdateUI(isOnline, this.connectionConfirmed);
        }
    }
    
    showNotification(message, type) {
        const notification = document.createElement('div');
        notification.className = `alert alert-${type === 'success' ? 'success' : 'warning'}`;
        const topOffset = document.body.classList.contains('offline-mode') ? 68 : 20;
        notification.style.cssText = `
            position: fixed;
            top: ${topOffset}px;
            left: 50%;
            transform: translateX(-50%);
            z-index: 1100;
            padding: 12px 24px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            animation: slideDown 0.3s ease;
            min-width: 280px;
            max-width: 90vw;
            text-align: center;
            white-space: nowrap;
        `;
        notification.innerHTML = `<i class="bi bi-${type === 'success' ? 'wifi' : 'wifi-off'}"></i> ${message}`;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.style.animation = 'slideUp 0.3s ease';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }
    
    // 取得當前連線狀態
    isOnline() {
        return this.browserOnline && this.serverOnline;
    }
}

// 匯出為全域變數（供傳統 script 標籤使用）
window.ConnectionManager = ConnectionManager;
