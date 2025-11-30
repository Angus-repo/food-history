/**
 * 食物歷史應用程式 - Service Worker
 * 使用 Workbox 實現離線查詢功能
 */

// 從 CDN 載入 Workbox
importScripts('https://storage.googleapis.com/workbox-cdn/releases/7.0.0/workbox-sw.js');

// 設定 Workbox
workbox.setConfig({ debug: false });

// 快取名稱配置
const CACHE_PREFIX = 'food-history';

// 程式碼版本 - 更新 JS/CSS 等靜態資源時修改此版本
const CODE_VERSION = 'c14';

// 資料版本 - 只有資料結構改變時才需要修改，一般不需要改
const DATA_VERSION = 'd1';

// HTML 版本 - HTML 頁面獨立版本，不跟著 CODE_VERSION，避免更新程式碼時清除 HTML 快取
// 只有在 HTML 結構有重大變更時才需要更新此版本
const HTML_VERSION = 'h2';

// 程式碼相關快取（更新程式時會清除）
const STATIC_CACHE = `${CACHE_PREFIX}-static-${CODE_VERSION}`;
const FONTS_CACHE = `${CACHE_PREFIX}-fonts-${CODE_VERSION}`;
const CDN_CACHE = `${CACHE_PREFIX}-cdn-${CODE_VERSION}`;

// HTML 快取（獨立版本，更新程式碼時保留）
const HTML_CACHE = `${CACHE_PREFIX}-html-${HTML_VERSION}`;

// 資料相關快取（更新程式時保留）
const OFFLINE_DATA_CACHE = `${CACHE_PREFIX}-offline-data-${DATA_VERSION}`;
const IMAGE_CACHE = `${CACHE_PREFIX}-images-${DATA_VERSION}`;

// 預快取靜態資源
workbox.precaching.precacheAndRoute([
    { url: '/css/main.css', revision: '3' },
    { url: '/css/recommendations.css', revision: '3' },
    { url: '/css/bootstrap-icons.css', revision: '3' },
    { url: '/fonts/bootstrap-icons.woff2', revision: '3' },
    { url: '/fonts/bootstrap-icons.woff', revision: '3' },
    { url: '/js/food.js', revision: '3' },
    { url: '/js/offline-search.js', revision: '3' },
    { url: '/js/connection-manager.js', revision: '4' },
    { url: '/manifest.json', revision: '3' }
]);

// 快取策略：靜態資源 (CSS, JS)
workbox.routing.registerRoute(
    ({ request }) => request.destination === 'style' || request.destination === 'script',
    new workbox.strategies.StaleWhileRevalidate({
        cacheName: STATIC_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 50,
                maxAgeSeconds: 30 * 24 * 60 * 60 // 30 天
            })
        ]
    })
);

// 快取策略：本地字體檔案
workbox.routing.registerRoute(
    ({ url }) => url.pathname.startsWith('/fonts/'),
    new workbox.strategies.CacheFirst({
        cacheName: FONTS_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 10,
                maxAgeSeconds: 365 * 24 * 60 * 60 // 1 年
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            })
        ]
    })
);

// 快取策略：食物圖片
workbox.routing.registerRoute(
    ({ url }) => url.pathname.startsWith('/foods/images/'),
    new workbox.strategies.CacheFirst({
        cacheName: IMAGE_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 200,
                maxAgeSeconds: 7 * 24 * 60 * 60 // 7 天
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            })
        ]
    })
);

// 快取策略：離線資料 API
workbox.routing.registerRoute(
    ({ url }) => url.pathname === '/api/foods/offline-cache',
    new workbox.strategies.NetworkFirst({
        cacheName: OFFLINE_DATA_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 1,
                maxAgeSeconds: 24 * 60 * 60 // 1 天
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            })
        ]
    })
);

// 快取策略：CDN 資源 (Bootstrap CSS/JS)
workbox.routing.registerRoute(
    ({ url }) => url.hostname === 'cdn.jsdelivr.net',
    new workbox.strategies.CacheFirst({
        cacheName: CDN_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 30,
                maxAgeSeconds: 30 * 24 * 60 * 60 // 30 天
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            })
        ]
    })
);

// 快取策略：HTML 頁面（食物列表頁面、編輯頁面）- 使用 NetworkFirst，離線時回退到快取
// 注意：新增頁面 /foods/new 不需要快取，因為離線時無法新增資料
// 
// 重要：此路由會將帶查詢參數的 URL（如 /foods/374/edit?page=0&foodId=374）
// 轉換為不帶參數的快取 key（/foods/374/edit），確保離線時可以正確讀取快取
workbox.routing.registerRoute(
    ({ request, url }) => {
        // 匹配 /foods 相關頁面（列表、編輯）
        if (request.destination === 'document') {
            const pathname = url.pathname;
            // 匹配：/foods, /foods/{id}/edit（不包含 /foods/new）
            // 注意：這裡只匹配 pathname，查詢參數會被 cacheKeyWillBeUsed 處理
            const isMatch = pathname === '/foods' || 
                   pathname.match(/^\/foods\/\d+\/edit$/);
            if (isMatch) {
                console.log('[Service Worker] HTML 路由匹配:', url.href);
            }
            return isMatch;
        }
        return false;
    },
    new workbox.strategies.NetworkFirst({
        cacheName: HTML_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 50, // 支援多個編輯頁面的快取
                maxAgeSeconds: 7 * 24 * 60 * 60 // 7 天
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            }),
            // 忽略查詢參數，確保帶參數和不帶參數的 URL 使用相同的快取
            // 例如：/foods/374/edit?page=0&foodId=374 -> /foods/374/edit
            //       /foods?page=1 -> /foods
            {
                cacheKeyWillBeUsed: async ({ request, mode }) => {
                    const url = new URL(request.url);
                    // 移除查詢參數，只保留 origin + pathname
                    const cacheKey = url.origin + url.pathname;
                    console.log(`[Service Worker] cacheKeyWillBeUsed (${mode}): ${request.url} -> ${cacheKey}`);
                    return new Request(cacheKey, { headers: request.headers });
                }
            }
        ],
        networkTimeoutSeconds: 3 // 3 秒內沒回應就用快取
    })
);

// 快取策略：登入頁面
workbox.routing.registerRoute(
    ({ request, url }) => {
        return request.destination === 'document' && url.pathname === '/login';
    },
    new workbox.strategies.NetworkFirst({
        cacheName: HTML_CACHE,
        plugins: [
            new workbox.expiration.ExpirationPlugin({
                maxEntries: 5,
                maxAgeSeconds: 7 * 24 * 60 * 60
            }),
            new workbox.cacheableResponse.CacheableResponsePlugin({
                statuses: [0, 200]
            })
        ],
        networkTimeoutSeconds: 3
    })
);

// 監聽安裝事件
self.addEventListener('install', (event) => {
    console.log('[Service Worker] 安裝中...');
    
    // 預先快取重要的 HTML 頁面
    event.waitUntil(
        caches.open(HTML_CACHE).then(async (cache) => {
            console.log('[Service Worker] 預快取 HTML 頁面');
            // 使用完整 URL 作為快取 key
            const urlsToCache = [
                new URL('/foods', self.location.origin).href,
                new URL('/login', self.location.origin).href
            ];
            for (const url of urlsToCache) {
                try {
                    const response = await fetch(url);
                    if (response.ok) {
                        await cache.put(url, response);
                        console.log('[Service Worker] 已預快取:', url);
                    }
                } catch (err) {
                    console.warn('[Service Worker] 預快取失敗:', url, err);
                }
            }
        })
    );
    
    self.skipWaiting();
});

// 監聽啟動事件
self.addEventListener('activate', (event) => {
    console.log('[Service Worker] 啟動中...');
    
    // 當前有效的快取名稱列表
    const validCaches = [
        STATIC_CACHE,
        HTML_CACHE,
        FONTS_CACHE,
        CDN_CACHE,
        OFFLINE_DATA_CACHE,
        IMAGE_CACHE
    ];
    
    // 遷移舊的 HTML 快取到新版本（如果有的話）
    // 這確保用戶更新 Service Worker 後不會失去已快取的 HTML 頁面
    const migrateHtmlCache = async () => {
        const cacheNames = await caches.keys();
        // 找到舊的 HTML 快取（格式：food-history-html-cXX）
        const oldHtmlCaches = cacheNames.filter(name => 
            name.startsWith(`${CACHE_PREFIX}-html-`) && name !== HTML_CACHE
        );
        
        if (oldHtmlCaches.length > 0) {
            console.log('[Service Worker] 發現舊的 HTML 快取，開始遷移:', oldHtmlCaches);
            const newCache = await caches.open(HTML_CACHE);
            
            for (const oldCacheName of oldHtmlCaches) {
                try {
                    const oldCache = await caches.open(oldCacheName);
                    const requests = await oldCache.keys();
                    
                    for (const request of requests) {
                        // 檢查新快取中是否已有此項目
                        const existingResponse = await newCache.match(request);
                        if (!existingResponse) {
                            const response = await oldCache.match(request);
                            if (response) {
                                await newCache.put(request, response);
                                console.log('[Service Worker] 已遷移 HTML:', request.url);
                            }
                        }
                    }
                } catch (err) {
                    console.warn('[Service Worker] 遷移 HTML 快取失敗:', oldCacheName, err);
                }
            }
        }
    };
    
    // 清理舊版本快取（只清理程式碼快取，保留資料快取）
    const cleanupOldCaches = async () => {
        const cacheNames = await caches.keys();
        const cachesToDelete = cacheNames
            .filter((cacheName) => cacheName.startsWith(CACHE_PREFIX))
            .filter((cacheName) => !validCaches.includes(cacheName));
        
        for (const cacheName of cachesToDelete) {
            console.log('[Service Worker] 刪除舊快取:', cacheName);
            await caches.delete(cacheName);
        }
    };
    
    event.waitUntil(
        migrateHtmlCache()
            .then(() => cleanupOldCaches())
            .then(() => self.clients.claim())
    );
});

// 監聽來自主執行緒的訊息
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
    
    if (event.data && event.data.type === 'PREFETCH_ALL_DATA') {
        prefetchAllData(event);
    }
    
    if (event.data && event.data.type === 'GET_CACHED_DATA') {
        getCachedData(event);
    }
    
    if (event.data && event.data.type === 'SEARCH_OFFLINE') {
        searchOffline(event);
    }
    
    if (event.data && event.data.type === 'CHECK_CACHE_VERSION') {
        checkCacheVersion(event);
    }
    
    if (event.data && event.data.type === 'LIST_HTML_CACHE') {
        listHtmlCache(event);
    }
});

// 預載所有資料
async function prefetchAllData(event) {
    try {
        // 通知開始預載
        notifyClient(event, { type: 'PREFETCH_STARTED' });
        
        // 1. 獲取離線資料
        const dataResponse = await fetch('/api/foods/offline-cache');
        if (!dataResponse.ok) throw new Error('無法獲取離線資料');
        
        const data = await dataResponse.json();
        
        // 快取離線資料
        const dataCache = await caches.open(OFFLINE_DATA_CACHE);
        await dataCache.put('/api/foods/offline-cache', new Response(JSON.stringify(data)));
        
        notifyClient(event, { 
            type: 'PREFETCH_PROGRESS', 
            progress: 20,
            message: '已快取食物資料'
        });
        
        // 2. 預載所有編輯頁面
        const htmlCache = await caches.open(HTML_CACHE);
        let loadedPages = 0;
        const totalPages = data.foods.length;
        
        console.log('[Service Worker] 開始預快取編輯頁面，共', totalPages, '筆食物資料');
        
        for (const food of data.foods) {
            try {
                const editPath = `/foods/${food.id}/edit`;
                // 使用完整 URL 作為快取 key，與 cacheKeyWillBeUsed 保持一致
                const editUrl = new URL(editPath, self.location.origin).href;
                console.log('[Service Worker] 正在快取:', editUrl, '(food.id:', food.id, ')');
                
                const pageResponse = await fetch(editPath);
                console.log('[Service Worker] fetch 結果:', editPath, 'status:', pageResponse.status);
                
                if (pageResponse.ok) {
                    // 使用完整 URL 作為快取 key
                    await htmlCache.put(editUrl, pageResponse);
                    console.log('[Service Worker] 已快取編輯頁面:', editUrl);
                } else {
                    console.warn('[Service Worker] fetch 失敗:', editPath, 'status:', pageResponse.status);
                }
                loadedPages++;
                
                const progress = 20 + Math.floor((loadedPages / totalPages) * 30);
                notifyClient(event, {
                    type: 'PREFETCH_PROGRESS',
                    progress,
                    message: `已快取 ${loadedPages}/${totalPages} 個編輯頁面`
                });
            } catch (err) {
                console.warn('[Service Worker] 編輯頁面快取失敗:', food.id);
            }
        }
        
        // 3. 預載所有圖片
        const imageUrls = data.foods
            .filter(food => food.imagePath)
            .map(food => `/foods/images/${food.imagePath}`);
        
        const imageCache = await caches.open(IMAGE_CACHE);
        let loadedImages = 0;
        
        for (const imageUrl of imageUrls) {
            try {
                const imageResponse = await fetch(imageUrl);
                if (imageResponse.ok) {
                    await imageCache.put(imageUrl, imageResponse);
                }
                loadedImages++;
                
                const progress = 50 + Math.floor((loadedImages / imageUrls.length) * 45);
                notifyClient(event, {
                    type: 'PREFETCH_PROGRESS',
                    progress,
                    message: `已快取 ${loadedImages}/${imageUrls.length} 張圖片`
                });
            } catch (err) {
                console.warn('[Service Worker] 圖片快取失敗:', imageUrl);
            }
        }
        
        // 4. 儲存快取版本資訊
        const versionInfo = {
            version: data.cacheVersion,
            timestamp: data.timestamp,
            totalFoods: data.foods.length,
            cachedAt: new Date().toISOString()
        };
        await dataCache.put('/api/foods/cache-version', new Response(JSON.stringify(versionInfo)));
        
        notifyClient(event, {
            type: 'PREFETCH_COMPLETE',
            progress: 100,
            message: '所有資料已快取完成',
            totalFoods: data.foods.length,
            totalImages: loadedImages,
            totalPages: loadedPages
        });
        
    } catch (error) {
        console.error('[Service Worker] 預載失敗:', error);
        notifyClient(event, {
            type: 'PREFETCH_ERROR',
            error: error.message
        });
    }
}

// 獲取快取的資料
async function getCachedData(event) {
    try {
        const cache = await caches.open(OFFLINE_DATA_CACHE);
        const response = await cache.match('/api/foods/offline-cache');
        
        if (response) {
            const data = await response.json();
            notifyClient(event, {
                type: 'CACHED_DATA',
                data: data
            });
        } else {
            notifyClient(event, {
                type: 'CACHED_DATA',
                data: null
            });
        }
    } catch (error) {
        notifyClient(event, {
            type: 'CACHED_DATA_ERROR',
            error: error.message
        });
    }
}

// 離線搜尋
async function searchOffline(event) {
    try {
        const keyword = event.data.keyword || '';
        const cache = await caches.open(OFFLINE_DATA_CACHE);
        const response = await cache.match('/api/foods/offline-cache');
        
        if (!response) {
            notifyClient(event, {
                type: 'SEARCH_RESULT',
                results: [],
                keyword,
                error: '無快取資料'
            });
            return;
        }
        
        const data = await response.json();
        const foods = data.foods || [];
        
        // 執行本地搜尋
        const results = keyword.trim() === '' 
            ? foods 
            : foods.filter(food => {
                const searchTerm = keyword.toLowerCase();
                return (
                    (food.name && food.name.toLowerCase().includes(searchTerm)) ||
                    (food.notes && food.notes.toLowerCase().includes(searchTerm))
                );
            });
        
        notifyClient(event, {
            type: 'SEARCH_RESULT',
            results,
            keyword,
            totalCached: foods.length
        });
        
    } catch (error) {
        notifyClient(event, {
            type: 'SEARCH_RESULT',
            results: [],
            error: error.message
        });
    }
}

// 檢查快取版本
async function checkCacheVersion(event) {
    try {
        // 獲取本地版本
        const cache = await caches.open(OFFLINE_DATA_CACHE);
        const localVersionResponse = await cache.match('/api/foods/cache-version');
        const localVersion = localVersionResponse ? await localVersionResponse.json() : null;
        
        // 獲取伺服器版本
        let serverVersion = null;
        let fetchError = null;
        try {
            const serverResponse = await fetch('/api/foods/cache-version');
            if (serverResponse.ok) {
                serverVersion = await serverResponse.json();
            }
        } catch (err) {
            // 離線時無法獲取伺服器版本
            fetchError = err.message;
        }
        
        const needsUpdate = serverVersion && localVersion && 
            serverVersion.version !== localVersion.version;
        
        notifyClient(event, {
            type: 'CACHE_VERSION_INFO',
            localVersion,
            serverVersion,
            needsUpdate,
            // isOnline 只表示 Service Worker 能否成功 fetch，不代表真正的連線狀態
            // 真正的連線狀態由頁面上的 ConnectionManager 管理
            isOnline: !!serverVersion,
            fetchError
        });
        
    } catch (error) {
        notifyClient(event, {
            type: 'CACHE_VERSION_INFO',
            error: error.message
        });
    }
}

// 通知客戶端
function notifyClient(event, message) {
    if (event.source) {
        event.source.postMessage(message);
    }
}

// 背景同步：當網路恢復時更新快取
self.addEventListener('sync', (event) => {
    if (event.tag === 'sync-food-data') {
        event.waitUntil(syncFoodData());
    }
});

async function syncFoodData() {
    try {
        console.log('[Service Worker] 背景同步開始...');
        
        const response = await fetch('/api/foods/offline-cache');
        if (response.ok) {
            const data = await response.json();
            const cache = await caches.open(OFFLINE_DATA_CACHE);
            await cache.put('/api/foods/offline-cache', new Response(JSON.stringify(data)));
            
            // 更新版本資訊
            const versionInfo = {
                version: data.cacheVersion,
                timestamp: data.timestamp,
                totalFoods: data.foods.length,
                cachedAt: new Date().toISOString()
            };
            await cache.put('/api/foods/cache-version', new Response(JSON.stringify(versionInfo)));
            
            // 通知所有客戶端
            const clients = await self.clients.matchAll();
            clients.forEach(client => {
                client.postMessage({
                    type: 'BACKGROUND_SYNC_COMPLETE',
                    totalFoods: data.foods.length
                });
            });
            
            console.log('[Service Worker] 背景同步完成');
        }
    } catch (error) {
        console.error('[Service Worker] 背景同步失敗:', error);
    }
}

// 列出 HTML 快取內容（用於調試）
async function listHtmlCache(event) {
    try {
        const cache = await caches.open(HTML_CACHE);
        const keys = await cache.keys();
        const urls = keys.map(request => request.url);
        
        console.log('[Service Worker] HTML 快取內容:', urls);
        
        notifyClient(event, {
            type: 'HTML_CACHE_LIST',
            cacheName: HTML_CACHE,
            urls: urls,
            count: urls.length
        });
    } catch (error) {
        notifyClient(event, {
            type: 'HTML_CACHE_LIST',
            error: error.message
        });
    }
}
