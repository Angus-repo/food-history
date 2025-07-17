// 食物歷史應用程式 - JavaScript 功能

// 全域變數
let isLoading = false;
let searchTimeout = null;

// 初始化功能
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    // 初始化搜尋功能
    initializeSearch();
    
    // 初始化圖片上傳功能
    initializeImageUpload();
    
    // 初始化動畫效果
    initializeAnimations();
    
    // 初始化工具提示
    initializeTooltips();
    
    // 初始化鍵盤快捷鍵
    initializeKeyboardShortcuts();
}

// 搜尋功能
function initializeSearch() {
    const searchInput = document.getElementById('searchKeyword');
    if (searchInput) {
        // 即時搜尋（防抖動）
        searchInput.addEventListener('input', function() {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                performSearch(this.value);
            }, 500);
        });
        
        // Enter 鍵搜尋
        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                performSearch(this.value);
            }
        });
        
        // 搜尋建議功能
        searchInput.addEventListener('focus', function() {
            showSearchSuggestions();
        });
    }
}

function performSearch(keyword) {
    if (isLoading) return;
    
    isLoading = true;
    showLoadingState();
    
    const url = new URL(window.location);
    if (keyword.trim()) {
        url.searchParams.set('keyword', keyword);
    } else {
        url.searchParams.delete('keyword');
    }
    
    // 使用 History API 避免頁面重新載入
    history.pushState(null, '', url.toString());
    
    // 這裡可以使用 AJAX 來更新內容，而不是重新載入頁面
    setTimeout(() => {
        window.location.href = url.toString();
    }, 300);
}

function showSearchSuggestions() {
    // 這裡可以實現搜尋建議功能
    // 例如：顯示最近搜尋的關鍵字或熱門食物
}

// 圖片上傳功能
function initializeImageUpload() {
    const imageInput = document.getElementById('imageFile');
    if (imageInput) {
        // 圖片格式驗證
        imageInput.addEventListener('change', function() {
            validateImageFile(this);
        });
    }
}

function validateImageFile(input) {
    if (input.files && input.files[0]) {
        const file = input.files[0];
        const maxSize = 5 * 1024 * 1024; // 5MB
        const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
        
        // 檢查檔案大小
        if (file.size > maxSize) {
            showNotification('圖片檔案太大，請選擇小於 5MB 的圖片', 'error');
            input.value = '';
            return false;
        }
        
        // 檢查檔案類型
        if (!allowedTypes.includes(file.type)) {
            showNotification('不支援的圖片格式，請選擇 JPEG、PNG、GIF 或 WebP 格式', 'error');
            input.value = '';
            return false;
        }
        
        // 預覽圖片
        previewImage(input);
        return true;
    }
}

// 改進的圖片預覽功能
function previewImage(input) {
    if (input.files && input.files[0]) {
        const reader = new FileReader();
        const file = input.files[0];
        
        reader.onloadstart = function() {
            showImageLoadingState();
        };
        
        reader.onprogress = function(e) {
            if (e.lengthComputable) {
                updateImageLoadingProgress((e.loaded / e.total) * 100);
            }
        };
        
        reader.onload = function(e) {
            displayImagePreview(e.target.result);
            hideImageLoadingState();
        };
        
        reader.onerror = function() {
            showNotification('圖片載入失敗，請重試', 'error');
            hideImageLoadingState();
        };
        
        reader.readAsDataURL(file);
    }
}

function displayImagePreview(imageSrc) {
    const uploadArea = document.querySelector('.image-upload-area');
    const uploadPlaceholder = document.getElementById('uploadPlaceholder');
    let imagePreview = document.getElementById('imagePreview');
    
    if (uploadPlaceholder) {
        uploadPlaceholder.style.display = 'none';
    }
    
    if (!imagePreview) {
        imagePreview = document.createElement('img');
        imagePreview.id = 'imagePreview';
        imagePreview.className = 'image-preview';
        uploadArea.appendChild(imagePreview);
    }
    
    imagePreview.src = imageSrc;
    imagePreview.style.display = 'block';
    
    // 添加移除按鈕
    addRemoveImageButton();
}

function addRemoveImageButton() {
    const uploadArea = document.querySelector('.image-upload-area');
    let removeBtn = document.getElementById('removeImageBtn');
    
    if (!removeBtn) {
        removeBtn = document.createElement('button');
        removeBtn.id = 'removeImageBtn';
        removeBtn.type = 'button';
        removeBtn.className = 'btn-danger-modern btn-modern';
        removeBtn.style.position = 'absolute';
        removeBtn.style.top = '10px';
        removeBtn.style.right = '10px';
        removeBtn.innerHTML = '<i class="bi bi-x-lg"></i>';
        removeBtn.onclick = removeImage;
        
        uploadArea.style.position = 'relative';
        uploadArea.appendChild(removeBtn);
    }
}

function removeImage() {
    const imageInput = document.getElementById('imageFile');
    const imagePreview = document.getElementById('imagePreview');
    const uploadPlaceholder = document.getElementById('uploadPlaceholder');
    const removeBtn = document.getElementById('removeImageBtn');
    
    if (imageInput) imageInput.value = '';
    if (imagePreview) imagePreview.style.display = 'none';
    if (uploadPlaceholder) uploadPlaceholder.style.display = 'block';
    if (removeBtn) removeBtn.remove();
}

function showImageLoadingState() {
    const progressBar = document.getElementById('uploadProgress');
    if (progressBar) {
        progressBar.classList.remove('d-none');
    }
}

function updateImageLoadingProgress(percent) {
    const progressFill = document.querySelector('.progress-fill');
    if (progressFill) {
        progressFill.style.width = percent + '%';
    }
}

function hideImageLoadingState() {
    const progressBar = document.getElementById('uploadProgress');
    if (progressBar) {
        setTimeout(() => {
            progressBar.classList.add('d-none');
        }, 500);
    }
}

// 刪除食物功能
function deleteFood(id) {
    showConfirmDialog(
        '確認刪除',
        '確定要刪除這筆食物記錄嗎？此操作無法復原。',
        function() {
            performDeleteFood(id);
        }
    );
}

function performDeleteFood(id) {
    if (isLoading) return;
    
    isLoading = true;
    showLoadingState();
    
    const token = getCSRFToken();
    
    fetch('/foods/' + id, {
        method: 'DELETE',
        headers: {
            'X-CSRF-TOKEN': token,
            'Content-Type': 'application/json'
        }
    })
    .then(response => {
        if (response.ok) {
            showNotification('食物記錄已成功刪除', 'success');
            setTimeout(() => {
                window.location.href = '/foods';
            }, 1000);
        } else {
            throw new Error('刪除失敗');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showNotification('刪除失敗，請稍後再試', 'error');
    })
    .finally(() => {
        isLoading = false;
        hideLoadingState();
    });
}

// 工具函數
function getCSRFToken() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    return tokenMeta ? tokenMeta.getAttribute('content') : '';
}

function showLoadingState() {
    const loadingOverlay = document.createElement('div');
    loadingOverlay.id = 'loadingOverlay';
    loadingOverlay.innerHTML = `
        <div style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
                    background: rgba(0,0,0,0.5); display: flex; align-items: center; 
                    justify-content: center; z-index: 9999;">
            <div style="background: white; padding: 2rem; border-radius: 1rem; text-align: center;">
                <div class="loading-spinner" style="margin-bottom: 1rem;"></div>
                <p>處理中...</p>
            </div>
        </div>
    `;
    document.body.appendChild(loadingOverlay);
}

function hideLoadingState() {
    const loadingOverlay = document.getElementById('loadingOverlay');
    if (loadingOverlay) {
        loadingOverlay.remove();
    }
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `alert-${type === 'error' ? 'danger' : type}-modern alert-modern`;
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 10000;
        min-width: 300px;
        animation: slideInRight 0.3s ease;
    `;
    
    const icon = type === 'success' ? 'check-circle' : 
                 type === 'error' ? 'exclamation-triangle' : 'info-circle';
    
    notification.innerHTML = `
        <i class="bi bi-${icon} me-2"></i>
        ${message}
        <button type="button" class="btn-close" onclick="this.parentElement.remove()"></button>
    `;
    
    document.body.appendChild(notification);
    
    // 自動移除
    setTimeout(() => {
        if (notification.parentElement) {
            notification.remove();
        }
    }, 5000);
}

function showConfirmDialog(title, message, onConfirm, onCancel = null) {
    const dialog = document.createElement('div');
    dialog.innerHTML = `
        <div style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
                    background: rgba(0,0,0,0.5); display: flex; align-items: center; 
                    justify-content: center; z-index: 10000;">
            <div class="modern-card" style="max-width: 400px; width: 90%;">
                <div class="card-body-modern">
                    <h4 style="margin-bottom: 1rem; color: var(--gray-800);">${title}</h4>
                    <p style="margin-bottom: 2rem; color: var(--gray-600);">${message}</p>
                    <div style="display: flex; gap: 1rem; justify-content: flex-end;">
                        <button class="btn-secondary-modern btn-modern" onclick="this.closest('div').parentElement.remove(); ${onCancel ? onCancel.toString() + '()' : ''}">
                            取消
                        </button>
                        <button class="btn-danger-modern btn-modern" onclick="this.closest('div').parentElement.remove(); (${onConfirm.toString()})()">
                            確認
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    document.body.appendChild(dialog);
}

// 動畫效果
function initializeAnimations() {
    // 觀察器用於觸發進入動畫
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('fade-in');
            }
        });
    });
    
    // 觀察所有需要動畫的元素
    document.querySelectorAll('.modern-card, .form-section').forEach(el => {
        observer.observe(el);
    });
}

// 工具提示
function initializeTooltips() {
    // 這裡可以初始化 Bootstrap 工具提示或自定義工具提示
    const tooltipElements = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    if (tooltipElements.length > 0 && typeof bootstrap !== 'undefined') {
        tooltipElements.forEach(el => new bootstrap.Tooltip(el));
    }
}

// 鍵盤快捷鍵
function initializeKeyboardShortcuts() {
    document.addEventListener('keydown', function(e) {
        // Ctrl/Cmd + K: 聚焦搜尋框
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            e.preventDefault();
            const searchInput = document.getElementById('searchKeyword');
            if (searchInput) {
                searchInput.focus();
                searchInput.select();
            }
        }
        
        // Ctrl/Cmd + N: 新增食物
        if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
            e.preventDefault();
            window.location.href = '/foods/new';
        }
        
        // ESC: 關閉對話框或清除搜尋
        if (e.key === 'Escape') {
            const dialogs = document.querySelectorAll('[style*="position: fixed"]');
            if (dialogs.length > 0) {
                dialogs[dialogs.length - 1].remove();
            } else {
                const searchInput = document.getElementById('searchKeyword');
                if (searchInput && searchInput === document.activeElement) {
                    searchInput.value = '';
                    searchInput.blur();
                }
            }
        }
    });
}

// CSS 動畫樣式
const animationStyles = `
    @keyframes slideInRight {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    .btn-close {
        background: none;
        border: none;
        font-size: 1.2rem;
        cursor: pointer;
        padding: 0.25rem;
        margin-left: auto;
    }
`;

// 注入動畫樣式
const styleSheet = document.createElement('style');
styleSheet.textContent = animationStyles;
document.head.appendChild(styleSheet);