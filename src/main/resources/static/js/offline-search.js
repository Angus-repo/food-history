/**
 * 離線搜尋模組
 * 處理前端離線搜尋功能與 Service Worker 通訊
 */

class OfflineSearchManager {
    constructor() {
        this.browserOnline = navigator.onLine; // 瀏覽器網路狀態
        this.serverOnline = false; // 伺服器連線狀態
        this.isOnline = false; // 總體狀態（browserOnline && serverOnline）
        this.cachedData = null;
        this.cacheVersion = null;
        this.isInitialized = false;
        this.prefetchProgress = 0;
        this.eventSource = null;
        this.reconnectTimeout = null;
        this.heartbeatTimeout = null;
        this.reconnectDelay = 1000;
        this.maxReconnectDelay = 30000;
        this.sseEndpoint = '/api/foods/connection-stream';
        this.connectionConfirmed = false; // 是否已確認 SSE 連線成功
        
        this.init();
    }
    
    async init() {
        // 註冊 Service Worker
        if ('serviceWorker' in navigator) {
            try {
                const registration = await navigator.serviceWorker.register('/sw.js');
                console.log('[OfflineSearch] Service Worker 註冊成功:', registration.scope);
                
                // 等待 Service Worker 啟動
                if (registration.installing) {
                    console.log('[OfflineSearch] Service Worker 安裝中...');
                } else if (registration.waiting) {
                    console.log('[OfflineSearch] Service Worker 等待中...');
                } else if (registration.active) {
                    console.log('[OfflineSearch] Service Worker 已啟動');
                    this.onServiceWorkerReady();
                }
                
                registration.addEventListener('updatefound', () => {
                    const newWorker = registration.installing;
                    newWorker.addEventListener('statechange', () => {
                        if (newWorker.state === 'activated') {
                            this.onServiceWorkerReady();
                        }
                    });
                });
                
            } catch (error) {
                console.error('[OfflineSearch] Service Worker 註冊失敗:', error);
            }
        }
        
        // 使用 SSE 監測伺服器連線狀態
        this.connectSSE();
        
        // 監聽瀏覽器的 online/offline 事件
        window.addEventListener('online', () => {
            console.log('[OfflineSearch] 瀏覽器回報網路已連線');
            this.browserOnline = true;
            // 網路恢復時，重置重連延遲並立即重新建立 SSE 連線
            this.reconnectDelay = 1000;
            // 清除任何現有的重連計時器
            if (this.reconnectTimeout) {
                clearTimeout(this.reconnectTimeout);
                this.reconnectTimeout = null;
            }
            // 立即嘗試連線伺服器
            this.connectSSE();
        });
        
        window.addEventListener('offline', () => {
            console.log('[OfflineSearch] 瀏覽器回報網路已斷線');
            this.browserOnline = false;
            // 瀏覽器離線，立即顯示離線（不管伺服器狀態）
            // 立即關閉 SSE 連線
            if (this.eventSource) {
                this.eventSource.close();
                this.eventSource = null;
            }
            this.serverOnline = false;
            this.connectionConfirmed = false;
            this.updateConnectionState();
        });
        
        // 監聽 Service Worker 訊息
        navigator.serviceWorker.addEventListener('message', (event) => this.handleServiceWorkerMessage(event));
        
        // 頁面可見性變化時重新連線
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                if (!this.eventSource || this.eventSource.readyState === EventSource.CLOSED) {
                    this.connectSSE();
                }
            }
        }, { passive: true });
    }
    
    connectSSE() {
        // 清理舊連線和心跳計時器
        if (this.eventSource) {
            this.eventSource.close();
        }
        
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
        
        if (this.heartbeatTimeout) {
            clearTimeout(this.heartbeatTimeout);
            this.heartbeatTimeout = null;
        }
        
        // 如果瀏覽器報告離線，不嘗試連線
        if (!navigator.onLine) {
            console.log('[OfflineSearch] 瀏覽器報告離線，跳過 SSE 連線');
            this.onOffline();
            return;
        }
        
        console.log('[OfflineSearch] 正在建立 SSE 連線...');
        this.connectionConfirmed = false;
        
        try {
            this.eventSource = new EventSource(this.sseEndpoint);
        } catch (error) {
            // EventSource 建立失敗（可能是離線）
            console.log('[OfflineSearch] 無法建立 SSE 連線:', error);
            this.onOffline();
            this.scheduleReconnect();
            return;
        }
        
        // 連線成功（收到 connected 事件）
        this.eventSource.addEventListener('connected', (event) => {
            console.log('[OfflineSearch] SSE 連線已建立:', event.data);
            this.reconnectDelay = 1000;
            this.connectionConfirmed = true;
            this.setServerOnline(true);
            // 開始心跳檢測
            this.startHeartbeat();
        });
        
        // 監聽心跳事件
        this.eventSource.addEventListener('heartbeat', (event) => {
            console.log('[OfflineSearch] 收到心跳');
            this.resetHeartbeatTimeout();
        });
        
        // 連線開啟 - 此時還不能確認伺服器真的在線，要等 connected 事件
        this.eventSource.onopen = () => {
            console.log('[OfflineSearch] SSE 連線開啟，等待伺服器確認...');
            // 給伺服器 3 秒時間發送 connected 事件
            setTimeout(() => {
                if (!this.connectionConfirmed && this.eventSource && this.eventSource.readyState === EventSource.OPEN) {
                    console.log('[OfflineSearch] 伺服器未在預期時間內確認連線，視為已連線');
                    this.connectionConfirmed = true;
                    this.setServerOnline(true);
                    this.startHeartbeat();
                }
            }, 3000);
        };
        
        // 連線錯誤
        this.eventSource.onerror = (error) => {
            console.log('[OfflineSearch] SSE 連線錯誤');
            // 只有在已經確認過連線成功後，錯誤才代表斷線
            // 或者連線從未成功過且多次重試失敗
            if (this.connectionConfirmed || this.reconnectDelay > 4000) {
                this.setServerOnline(false);
            }
            if (this.eventSource) {
                this.eventSource.close();
            }
            this.connectionConfirmed = false;
            this.scheduleReconnect();
        };
        
        // 監聽資料更新事件
        this.eventSource.addEventListener('data-updated', (event) => {
            console.log('[OfflineSearch] 收到資料更新通知:', event.data);
            this.sendMessageToSW({ type: 'CHECK_CACHE_VERSION' });
        });
    }
    
    scheduleReconnect() {
        if (this.reconnectTimeout) return;
        
        // 如果瀏覽器報告離線，不排程重連
        if (!navigator.onLine) {
            console.log('[OfflineSearch] 瀏覽器報告離線，暫不排程重連');
            return;
        }
        
        console.log(`[OfflineSearch] 將在 ${this.reconnectDelay / 1000} 秒後嘗試重新連線...`);
        
        this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this.connectSSE();
        }, this.reconnectDelay);
        
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay);
    }
    
    // 心跳機制 - 定期重新連線以防止連線被中間設備關閉
    startHeartbeat() {
        this.resetHeartbeatTimeout();
    }
    
    resetHeartbeatTimeout() {
        if (this.heartbeatTimeout) {
            clearTimeout(this.heartbeatTimeout);
        }
        
        // 如果 35 秒內沒有收到任何事件，認為連線已斷開
        this.heartbeatTimeout = setTimeout(() => {
            console.log('[OfflineSearch] 心跳超時，重新建立連線');
            if (this.eventSource) {
                this.eventSource.close();
            }
            // 不立即顯示離線，而是嘗試重連
            this.connectSSE();
        }, 35000);
    }
    
    onServiceWorkerReady() {
        console.log('[OfflineSearch] Service Worker 就緒');
        this.isInitialized = true;
        
        // 檢查是否需要預載資料
        this.checkAndPrefetchData();
    }
    
    async checkAndPrefetchData() {
        // 先檢查是否有快取資料
        this.sendMessageToSW({ type: 'GET_CACHED_DATA' });
        
        // 如果線上，檢查版本
        if (this.isOnline) {
            this.sendMessageToSW({ type: 'CHECK_CACHE_VERSION' });
        }
    }
    
    handleServiceWorkerMessage(event) {
        const { type, ...data } = event.data;
        
        switch (type) {
            case 'PREFETCH_STARTED':
                this.onPrefetchStarted();
                break;
                
            case 'PREFETCH_PROGRESS':
                this.onPrefetchProgress(data.progress, data.message);
                break;
                
            case 'PREFETCH_COMPLETE':
                this.onPrefetchComplete(data);
                break;
                
            case 'PREFETCH_ERROR':
                this.onPrefetchError(data.error);
                break;
                
            case 'CACHED_DATA':
                this.onCachedDataReceived(data.data);
                break;
                
            case 'SEARCH_RESULT':
                this.onSearchResult(data);
                break;
                
            case 'CACHE_VERSION_INFO':
                this.onCacheVersionInfo(data);
                break;
                
            case 'BACKGROUND_SYNC_COMPLETE':
                this.onBackgroundSyncComplete(data);
                break;
        }
    }
    
    // ========== 事件處理 ==========
    
    // 設定伺服器連線狀態
    setServerOnline(online) {
        console.log('[OfflineSearch] setServerOnline:', online);
        this.serverOnline = online;
        this.updateConnectionState();
    }
    
    // 計算並更新總體連線狀態
    // 在線條件：瀏覽器在線 AND 伺服器連線成功
    // 離線條件：瀏覽器離線 OR 伺服器離線
    updateConnectionState() {
        const newOnlineState = this.browserOnline && this.serverOnline;
        console.log('[OfflineSearch] updateConnectionState - 瀏覽器:', this.browserOnline, '伺服器:', this.serverOnline, '=> 總體:', newOnlineState);
        
        if (this.isOnline === newOnlineState) return; // 狀態沒變，不處理
        
        const wasOnline = this.isOnline;
        this.isOnline = newOnlineState;
        
        if (newOnlineState) {
            console.log('[OfflineSearch] 已連線（瀏覽器+伺服器都在線）');
            this.updateConnectionStatus();
            // 檢查是否需要更新快取
            this.sendMessageToSW({ type: 'CHECK_CACHE_VERSION' });
        } else {
            console.log('[OfflineSearch] 已離線（瀏覽器或伺服器離線）');
            this.updateConnectionStatus();
            // 切換到離線搜尋模式
            this.enableOfflineSearch();
        }
    }

    async onOnline() {
        // 過時方法，保留相容性
        this.setServerOnline(true);
    }
    
    async onOffline() {
        // 過時方法，保留相容性
        this.setServerOnline(false);
    }
    
    onPrefetchStarted() {
        console.log('[OfflineSearch] 預載開始');
        this.showPrefetchIndicator();
    }
    
    onPrefetchProgress(progress, message) {
        console.log(`[OfflineSearch] 預載進度: ${progress}% - ${message}`);
        this.prefetchProgress = progress;
        this.updatePrefetchIndicator(progress, message);
    }
    
    onPrefetchComplete(data) {
        console.log('[OfflineSearch] 預載完成:', data);
        this.hidePrefetchIndicator();
        this.showNotification(`離線資料已準備就緒！共 ${data.totalFoods} 筆食物，${data.totalImages} 張圖片`, 'success');
        
        // 重新載入快取資料
        this.sendMessageToSW({ type: 'GET_CACHED_DATA' });
    }
    
    onPrefetchError(error) {
        console.error('[OfflineSearch] 預載錯誤:', error);
        this.hidePrefetchIndicator();
        this.showNotification('離線資料預載失敗: ' + error, 'error');
    }
    
    onCachedDataReceived(data) {
        if (data) {
            this.cachedData = data;
            console.log('[OfflineSearch] 已載入快取資料:', data.foods?.length || 0, '筆');
            
            // 更新 UI 顯示快取狀態
            this.updateCacheStatus(true, data.foods?.length || 0);
        } else {
            console.log('[OfflineSearch] 無快取資料，開始預載...');
            this.startPrefetch();
        }
    }
    
    onSearchResult(data) {
        console.log('[OfflineSearch] 搜尋結果:', data.results?.length || 0, '筆');
        
        // 觸發自定義事件，讓頁面處理搜尋結果
        const event = new CustomEvent('offlineSearchResult', {
            detail: {
                results: data.results,
                keyword: data.keyword,
                totalCached: data.totalCached,
                error: data.error
            }
        });
        window.dispatchEvent(event);
    }
    
    onCacheVersionInfo(data) {
        console.log('[OfflineSearch] 快取版本資訊:', data);
        
        if (data.needsUpdate) {
            console.log('[OfflineSearch] 需要更新快取');
            this.showNotification('有新資料可用，正在更新...', 'info');
            this.startPrefetch();
        } else if (data.localVersion) {
            this.cacheVersion = data.localVersion;
            console.log('[OfflineSearch] 快取是最新的');
        }
    }
    
    onBackgroundSyncComplete(data) {
        console.log('[OfflineSearch] 背景同步完成:', data);
        this.showNotification(`背景同步完成，共 ${data.totalFoods} 筆食物`, 'success');
        
        // 重新載入快取資料
        this.sendMessageToSW({ type: 'GET_CACHED_DATA' });
    }
    
    // ========== 公開方法 ==========
    
    /**
     * 執行搜尋（自動判斷線上/離線）
     */
    search(keyword) {
        if (this.isOnline) {
            // 線上搜尋：使用原有的表單提交
            return false; // 返回 false 讓表單正常提交
        } else {
            // 離線搜尋：使用 Service Worker
            this.searchOffline(keyword);
            return true; // 返回 true 阻止表單提交
        }
    }
    
    /**
     * 執行離線搜尋
     */
    searchOffline(keyword) {
        console.log('[OfflineSearch] 離線搜尋:', keyword);
        this.sendMessageToSW({ type: 'SEARCH_OFFLINE', keyword });
    }
    
    /**
     * 開始預載所有資料
     */
    startPrefetch() {
        console.log('[OfflineSearch] 開始預載');
        this.sendMessageToSW({ type: 'PREFETCH_ALL_DATA' });
    }
    
    /**
     * 手動更新快取
     */
    refreshCache() {
        console.log('[OfflineSearch] 手動更新快取');
        this.startPrefetch();
    }
    
    /**
     * 獲取快取的食物資料
     */
    getCachedFoods() {
        return this.cachedData?.foods || [];
    }
    
    // ========== UI 更新方法 ==========
    
    updateConnectionStatus() {
        const indicator = document.getElementById('connectionIndicator');
        const offlineBanner = document.getElementById('offlineBanner');
        const refreshCacheBtn = document.getElementById('refreshCacheBtn');
        
        if (this.isOnline) {
            if (indicator) {
                indicator.classList.remove('offline');
                indicator.classList.add('online');
                indicator.innerHTML = '<i class="bi bi-wifi"></i> 線上';
            }
            if (offlineBanner) {
                offlineBanner.style.display = 'none';
            }
            // 線上時啟用更新快取按鈕
            if (refreshCacheBtn) {
                refreshCacheBtn.disabled = false;
                refreshCacheBtn.title = '更新離線快取';
            }
        } else {
            if (indicator) {
                indicator.classList.remove('online');
                indicator.classList.add('offline');
                indicator.innerHTML = '<i class="bi bi-wifi-off"></i> 離線';
            }
            if (offlineBanner) {
                offlineBanner.style.display = 'block';
            }
            // 離線時停用更新快取按鈕
            if (refreshCacheBtn) {
                refreshCacheBtn.disabled = true;
                refreshCacheBtn.title = '離線時無法更新快取';
            }
        }
    }
    
    updateCacheStatus(hasCached, count) {
        const cacheStatus = document.getElementById('cacheStatus');
        if (cacheStatus) {
            if (hasCached) {
                cacheStatus.innerHTML = `<i class="bi bi-database-check"></i> 已快取 ${count} 筆`;
                cacheStatus.classList.add('cached');
            } else {
                cacheStatus.innerHTML = '<i class="bi bi-database"></i> 未快取';
                cacheStatus.classList.remove('cached');
            }
        }
    }
    
    showPrefetchIndicator() {
        let indicator = document.getElementById('prefetchIndicator');
        if (!indicator) {
            indicator = document.createElement('div');
            indicator.id = 'prefetchIndicator';
            indicator.className = 'prefetch-indicator';
            indicator.innerHTML = `
                <div class="prefetch-content">
                    <div class="prefetch-spinner"></div>
                    <span class="prefetch-message">準備離線資料中...</span>
                    <div class="prefetch-progress-bar">
                        <div class="prefetch-progress-fill" style="width: 0%"></div>
                    </div>
                </div>
            `;
            document.body.appendChild(indicator);
        }
        indicator.style.display = 'flex';
    }
    
    updatePrefetchIndicator(progress, message) {
        const indicator = document.getElementById('prefetchIndicator');
        if (indicator) {
            const messageEl = indicator.querySelector('.prefetch-message');
            const progressFill = indicator.querySelector('.prefetch-progress-fill');
            if (messageEl) messageEl.textContent = message;
            if (progressFill) progressFill.style.width = `${progress}%`;
        }
    }
    
    hidePrefetchIndicator() {
        const indicator = document.getElementById('prefetchIndicator');
        if (indicator) {
            setTimeout(() => {
                indicator.style.display = 'none';
            }, 500);
        }
    }
    
    enableOfflineSearch() {
        // 替換搜尋表單的提交行為
        const searchForm = document.querySelector('.search-container form');
        if (searchForm && !searchForm.dataset.offlineEnabled) {
            searchForm.dataset.offlineEnabled = 'true';
            searchForm.addEventListener('submit', (e) => {
                if (!this.isOnline) {
                    e.preventDefault();
                    const keyword = searchForm.querySelector('input[name="keyword"]').value;
                    this.searchOffline(keyword);
                }
            });
        }
    }
    
    showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        const alertType = type === 'error' ? 'danger' : type;
        notification.className = `alert alert-${alertType}`;
        notification.style.cssText = `
            position: fixed !important;
            top: 20px !important;
            left: 50% !important;
            right: auto !important;
            transform: translateX(-50%) !important;
            z-index: 10000 !important;
            min-width: 420px;
            max-width: 90vw;
            padding: 1rem 2rem;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            border-radius: 0.5rem;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            text-align: center;
        `;
        
        const icon = type === 'success' ? 'check-circle' : 
                     type === 'error' ? 'exclamation-triangle' : 'info-circle';
        
        notification.innerHTML = `
            <i class="bi bi-${icon}"></i>
            <span>${message}</span>
            <button type="button" class="btn-close" onclick="this.parentElement.remove()"></button>
        `;
        
        document.body.appendChild(notification);
        
        setTimeout(() => {
            if (notification.parentElement) {
                notification.remove();
            }
        }, 5000);
    }
    
    // ========== 工具方法 ==========
    
    sendMessageToSW(message) {
        if (navigator.serviceWorker.controller) {
            navigator.serviceWorker.controller.postMessage(message);
        } else {
            // Service Worker 尚未啟動，等待一下再試
            navigator.serviceWorker.ready.then((registration) => {
                if (registration.active) {
                    registration.active.postMessage(message);
                }
            });
        }
    }
}

// 離線搜尋結果渲染器
class OfflineSearchRenderer {
    constructor() {
        this.offlineContainer = document.getElementById('offlineSearchResults');
        this.originalContainer = document.getElementById('originalContent');
        this.paginationContainer = document.querySelector('.pagination-container');
        
        // 監聽離線搜尋結果事件
        window.addEventListener('offlineSearchResult', (event) => {
            this.renderResults(event.detail);
        });
    }
    
    // 清除離線搜尋，恢復原始內容
    clearOfflineSearch() {
        if (this.offlineContainer) {
            this.offlineContainer.style.display = 'none';
            this.offlineContainer.innerHTML = '';
        }
        if (this.originalContainer) {
            this.originalContainer.style.display = '';
        }
        if (this.paginationContainer) {
            this.paginationContainer.style.display = '';
        }
        
        // 清空搜尋框
        const searchInput = document.querySelector('.search-container input[name="keyword"]');
        if (searchInput) {
            searchInput.value = '';
        }
        
        // 恢復統計資訊
        const statsContainer = document.querySelector('.stats-container');
        if (statsContainer) {
            const totalEl = statsContainer.querySelector('.text-primary-600');
            if (totalEl && this.originalTotal !== undefined) {
                totalEl.textContent = this.originalTotal;
            }
        }
    }
    
    renderResults(data) {
        if (!this.offlineContainer) return;
        
        const { results, keyword, error } = data;
        
        // 儲存原始統計數據
        const statsContainer = document.querySelector('.stats-container');
        if (statsContainer) {
            const totalEl = statsContainer.querySelector('.text-primary-600');
            if (totalEl && this.originalTotal === undefined) {
                this.originalTotal = totalEl.textContent;
            }
            if (totalEl) totalEl.textContent = results.length;
        }
        
        // 隱藏原始內容與分頁
        if (this.originalContainer) {
            this.originalContainer.style.display = 'none';
        }
        if (this.paginationContainer) {
            this.paginationContainer.style.display = 'none';
        }
        
        // 顯示離線結果容器
        this.offlineContainer.style.display = '';
        this.offlineContainer.innerHTML = '';
        
        if (error) {
            this.offlineContainer.innerHTML = `
                <div class="alert alert-warning">
                    <i class="bi bi-exclamation-triangle"></i>
                    離線搜尋錯誤: ${error}
                </div>
                <button type="button" class="btn btn-secondary mt-3" onclick="offlineSearchRenderer.clearOfflineSearch()">
                    <i class="bi bi-x-lg"></i>
                    清除搜尋
                </button>
            `;
            return;
        }
        
        if (results.length === 0) {
            this.offlineContainer.innerHTML = `
                <div class="text-center" style="padding: var(--space-16) 0;">
                    <i class="bi bi-inbox" style="font-size: 4rem; color: var(--secondary-400); margin-bottom: var(--space-4);"></i>
                    <h3 style="color: var(--secondary-600); margin-bottom: var(--space-2);">
                        ${keyword ? '找不到相關食物' : '還沒有任何食物記錄'}
                    </h3>
                    <p style="color: var(--secondary-500);">
                        <i class="bi bi-wifi-off"></i> 目前為離線模式，顯示的是快取資料
                    </p>
                    <button type="button" class="btn btn-secondary mt-4" onclick="offlineSearchRenderer.clearOfflineSearch()">
                        <i class="bi bi-x-lg"></i>
                        清除搜尋
                    </button>
                </div>
            `;
            return;
        }
        
        // 添加離線指示與清除按鈕
        const headerDiv = document.createElement('div');
        headerDiv.className = 'd-flex justify-content-between align-items-center mb-4';
        headerDiv.style.cssText = 'display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;';
        headerDiv.innerHTML = `
            <div class="alert alert-info mb-0" style="flex: 1; margin-right: 1rem; margin-bottom: 0;">
                <i class="bi bi-wifi-off"></i>
                離線模式 - 顯示 ${results.length} 筆快取資料
                ${keyword ? ` (搜尋: "${keyword}")` : ''}
            </div>
            <button type="button" class="btn btn-secondary" onclick="offlineSearchRenderer.clearOfflineSearch()">
                <i class="bi bi-x-lg"></i>
                清除搜尋
            </button>
        `;
        this.offlineContainer.appendChild(headerDiv);
        
        // 建立網格容器
        const grid = document.createElement('div');
        grid.className = 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6';
        
        results.forEach((food, index) => {
            const card = this.createFoodCard(food, index);
            grid.appendChild(card);
        });
        
        this.offlineContainer.appendChild(grid);
    }
    
    createFoodCard(food, index) {
        const card = document.createElement('div');
        card.className = 'food-card card slide-in enhanced-card';
        card.style.animationDelay = `${index * 0.1}s`;
        card.id = `food-${food.id}`;
        
        // 圖片區域
        const imageHtml = food.imagePath 
            ? `<img src="/foods/images/${food.imagePath}" class="food-card-image enhanced-image" alt="${food.name}">`
            : `<div class="food-card-image flex items-center justify-center enhanced-placeholder" style="background: linear-gradient(135deg, #e5e7eb 0%, #d1d5db 100%);">
                   <i class="bi bi-image" style="font-size: 3.5rem; color: #9ca3af;"></i>
               </div>`;
        
        // 份數標籤
        let portionBadge = '';
        if (food.quantity && food.unit) {
            portionBadge = `<span class="food-unit">(${food.quantity}${food.unit})</span>`;
        } else if (food.unit) {
            portionBadge = `<span class="food-unit">(${food.unit})</span>`;
        }
        
        // 碳水資訊
        let carbInfoHtml = '';
        if (food.coefficient) {
            carbInfoHtml += `
                <div class="carb-info-item">
                    <div class="carb-info-label">
                        <i class="bi bi-percent text-primary-500"></i>
                        <span>系數</span>
                    </div>
                    <div class="carb-info-value">
                        <span class="coefficient-green">${food.coefficient}</span>
                        <small>比例值</small>
                    </div>
                </div>
            `;
        }
        if (food.carbGrams) {
            carbInfoHtml += `
                <div class="carb-info-item">
                    <div class="carb-info-label">
                        <i class="bi bi-graph-up text-success-500"></i>
                        <span>碳水化合物(克)</span>
                    </div>
                    <div class="carb-info-value">
                        <span class="carb-orange">${food.carbGrams}</span>
                        <small>此份食物</small>
                    </div>
                </div>
            `;
        }
        if (!food.coefficient && !food.carbGrams) {
            carbInfoHtml = `
                <div class="carb-info-item">
                    <div class="carb-info-empty">
                        <i class="bi bi-dash-circle text-secondary-400"></i>
                        <span class="text-secondary-500">未設定碳水資訊</span>
                    </div>
                </div>
            `;
        }
        
        // 備註
        const notesHtml = food.notes ? `
            <div class="food-card-notes-section">
                <div class="notes-label">
                    <i class="bi bi-chat-text text-secondary-500"></i>
                    <span>備註</span>
                </div>
                <p class="food-card-notes">${food.notes}</p>
            </div>
        ` : '';
        
        card.innerHTML = `
            ${food.isFavorite ? '<div class="favorite-badge"><i class="bi bi-star-fill"></i></div>' : ''}
            
            <div class="food-card-title-wrapper" style="padding: var(--space-5) var(--space-5) 0 var(--space-5);">
                <h3 class="food-card-title">
                    <span class="food-name">${food.name}</span>
                </h3>
            </div>
            
            <div class="relative food-card-image-wrapper" style="height: 220px; overflow: hidden;">
                ${portionBadge ? `<div class="food-portion-badge food-portion-badge-image">${portionBadge}</div>` : ''}
                ${imageHtml}
                <div class="edit-overlay enhanced-overlay">
                    <div class="edit-hint">
                        <i class="bi bi-wifi-off"></i>
                        <span>離線模式</span>
                    </div>
                </div>
            </div>
            
            <div class="food-card-content enhanced-content">
                <div class="food-card-carb-info">
                    ${carbInfoHtml}
                </div>
                ${notesHtml}
                <div class="food-card-actions">
                    <div class="edit-prompt">
                        <i class="bi bi-info-circle text-secondary-500"></i>
                        <span class="text-secondary-600">離線模式 - 僅供查看</span>
                    </div>
                </div>
            </div>
        `;
        
        return card;
    }
}

// 全域離線搜尋管理器實例
let offlineSearchManager = null;
let offlineSearchRenderer = null;

// 頁面載入後初始化
document.addEventListener('DOMContentLoaded', function() {
    offlineSearchManager = new OfflineSearchManager();
    
    // 為列表頁面初始化渲染器 (使用 #offlineSearchResults 容器)
    const offlineResultsContainer = document.getElementById('offlineSearchResults');
    if (offlineResultsContainer) {
        offlineSearchRenderer = new OfflineSearchRenderer();
    }
    
    // 監聽搜尋表單
    const searchForm = document.querySelector('.search-container form');
    if (searchForm) {
        searchForm.addEventListener('submit', function(e) {
            if (offlineSearchManager && !offlineSearchManager.isOnline) {
                e.preventDefault();
                const keyword = this.querySelector('input[name="keyword"]').value;
                offlineSearchManager.searchOffline(keyword);
            }
        });
    }
    
    // 添加手動重新整理快取按鈕功能
    const refreshCacheBtn = document.getElementById('refreshCacheBtn');
    if (refreshCacheBtn) {
        refreshCacheBtn.addEventListener('click', function() {
            // 離線時不執行更新
            if (offlineSearchManager && offlineSearchManager.isOnline) {
                offlineSearchManager.refreshCache();
            }
        });
    }
});

// CSS 樣式
const offlineStyles = document.createElement('style');
offlineStyles.textContent = `
    /* 連線狀態指示器 */
    .connection-indicator {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.25rem 0.75rem;
        border-radius: 999px;
        font-size: 0.875rem;
        font-weight: 500;
    }
    
    .connection-indicator.online {
        background: var(--success-100, #dcfce7);
        color: var(--success-700, #15803d);
    }
    
    .connection-indicator.offline {
        background: var(--warning-100, #fef3c7);
        color: var(--warning-700, #a16207);
    }
    
    /* 離線橫幅 */
    .offline-banner {
        background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
        color: white;
        padding: 0.75rem 1rem;
        text-align: center;
        font-weight: 500;
        display: none;
    }
    
    .offline-banner i {
        margin-right: 0.5rem;
    }
    
    /* 快取狀態 */
    .cache-status {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.25rem 0.75rem;
        background: var(--secondary-100, #f3f4f6);
        border-radius: 999px;
        font-size: 0.875rem;
        color: var(--secondary-600, #4b5563);
    }
    
    .cache-status.cached {
        background: var(--primary-100, #dbeafe);
        color: var(--primary-700, #1d4ed8);
    }
    
    /* 預載指示器 */
    .prefetch-indicator {
        position: fixed;
        bottom: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: white;
        padding: 1rem 1.5rem;
        border-radius: 1rem;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
        z-index: 10000;
        display: none;
        min-width: 300px;
    }
    
    .prefetch-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.75rem;
    }
    
    .prefetch-spinner {
        width: 24px;
        height: 24px;
        border: 3px solid var(--secondary-200, #e5e7eb);
        border-top-color: var(--primary-500, #3b82f6);
        border-radius: 50%;
        animation: spin 1s linear infinite;
    }
    
    @keyframes spin {
        to { transform: rotate(360deg); }
    }
    
    .prefetch-message {
        font-size: 0.875rem;
        color: var(--secondary-700, #374151);
    }
    
    .prefetch-progress-bar {
        width: 100%;
        height: 6px;
        background: var(--secondary-200, #e5e7eb);
        border-radius: 3px;
        overflow: hidden;
    }
    
    .prefetch-progress-fill {
        height: 100%;
        background: linear-gradient(90deg, var(--primary-500, #3b82f6), var(--primary-400, #60a5fa));
        border-radius: 3px;
        transition: width 0.3s ease;
    }
    
    /* 離線通知 */
    .offline-notification {
        animation: slideDown 0.3s ease;
    }
    
    @keyframes slideDown {
        from {
            transform: translateX(-50%) translateY(-100%);
            opacity: 0;
        }
        to {
            transform: translateX(-50%) translateY(0);
            opacity: 1;
        }
    }
`;
document.head.appendChild(offlineStyles);
