/**
 * 離線搜尋模組
 * 處理前端離線搜尋功能與 Service Worker 通訊
 * 注意：連線狀態由頁面上的 ConnectionManager 管理，此模組只處理離線搜尋邏輯
 */

class OfflineSearchManager {
    constructor() {
        this.isOnline = navigator.onLine; // 初始狀態依賴瀏覽器
        this.cachedData = null;
        this.cacheVersion = null;
        this.isInitialized = false;
        this.prefetchProgress = 0;
        
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
        
        // 監聽 Service Worker 訊息
        navigator.serviceWorker.addEventListener('message', (event) => this.handleServiceWorkerMessage(event));
        
        // 監聯頁面上 ConnectionManager 的狀態變化（如果存在）
        // ConnectionManager 會透過 window 事件通知狀態變化
        window.addEventListener('connectionStateChanged', (event) => {
            this.isOnline = event.detail.isOnline;
            console.log('[OfflineSearch] 收到連線狀態變化:', this.isOnline);
            
            if (this.isOnline) {
                // 線上時檢查快取版本
                this.sendMessageToSW({ type: 'CHECK_CACHE_VERSION' });
            }
        });
        
        // 備用：監聽瀏覽器的 online/offline 事件
        window.addEventListener('online', () => {
            // 等待 ConnectionManager 確認伺服器連線後再更新
            // 這裡只是備用，主要由 connectionStateChanged 事件處理
        });
        
        window.addEventListener('offline', () => {
            this.isOnline = false;
            console.log('[OfflineSearch] 瀏覽器回報網路已斷線');
        });
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
        // 注意：data.isOnline 是 Service Worker 判斷的，可能不準確
        // 我們應該使用 this.isOnline（由 ConnectionManager 透過事件更新）
        console.log('[OfflineSearch] 快取版本資訊:', {
            localVersion: data.localVersion,
            serverVersion: data.serverVersion,
            needsUpdate: data.needsUpdate,
            swReportsOnline: data.isOnline,  // SW 回報的狀態（僅供參考）
            managerIsOnline: this.isOnline   // ConnectionManager 維護的狀態
        });
        
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
    
    // 注意：連線狀態 UI 由 ConnectionManager 管理，這裡只處理快取相關 UI
    
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
                // 只有在瀏覽器確定離線且伺服器也離線時才攔截
                const browserOffline = !navigator.onLine;
                if (browserOffline && !this.isOnline) {
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
            // 只有在瀏覽器確定離線且 WebSocket 也離線時才攔截
            // 如果瀏覽器認為在線，讓表單正常提交到伺服器
            const browserOffline = !navigator.onLine;
            const serverOffline = offlineSearchManager && !offlineSearchManager.isOnline;
            
            if (browserOffline && serverOffline) {
                e.preventDefault();
                const keyword = this.querySelector('input[name="keyword"]').value;
                offlineSearchManager.searchOffline(keyword);
            }
            // 否則讓表單正常提交
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
        text-align: center;
        width: 100%;
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
