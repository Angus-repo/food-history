// 圖片預覽功能
function previewImage(input) {
    if (input.files && input.files[0]) {
        var reader = new FileReader();
        reader.onload = function(e) {
            var preview = document.getElementById('imagePreview');
            if (!preview) {
                preview = document.createElement('img');
                preview.id = 'imagePreview';
                preview.className = 'mt-2';
                preview.style.maxWidth = '200px';
                input.parentElement.appendChild(preview);
            }
            preview.src = e.target.result;
        }
        reader.readAsDataURL(input.files[0]);
    }
}

// 刪除食物功能
function deleteFood(id) {
    if (confirm('確定要刪除這筆食物記錄嗎？')) {
        fetch('/foods/' + id, {
            method: 'DELETE'
        }).then(response => {
            if (response.ok) {
                window.location.href = '/foods';
            } else {
                alert('刪除失敗，請稍後再試。');
            }
        }).catch(error => {
            console.error('Error:', error);
            alert('刪除失敗，請稍後再試。');
        });
    }
}

// 食物搜尋功能
function searchFoods() {
    const keyword = document.getElementById('searchKeyword').value;
    window.location.href = '/foods?keyword=' + encodeURIComponent(keyword);
}