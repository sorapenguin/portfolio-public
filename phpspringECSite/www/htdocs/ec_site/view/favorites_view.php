<?php include 'header.php'; ?>

<h2>&#x2665; お気に入り</h2>
<div id="msg" class="alert" style="display:none;"></div>
<div id="fav-grid" class="product-grid"></div>

<script>
(function () {
    if (!requireLogin()) return;

    const grid = document.getElementById('fav-grid');
    const msg  = document.getElementById('msg');

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    async function load() {
        grid.innerHTML = '<p style="color:var(--muted)">読み込み中...</p>';
        try {
            const items = await apiRequest('GET', '/favorites');
            if (!items || items.length === 0) {
                grid.innerHTML = '<p>お気に入りに登録された商品はありません。</p>';
                return;
            }
            grid.innerHTML = items.map(p => `
                <div class="product-card" id="fav-card-${p.productId}">
                    <a href="/product_detail.php?id=${p.productId}" class="product-card-link">
                        ${p.imageName
                            ? `<img src="/uploads/${encodeURIComponent(p.imageName)}" alt="${esc(p.productName)}">`
                            : '<div class="product-card-no-img"></div>'}
                        <div class="product-card-body">
                            ${p.categoryName ? `<div class="product-card-cat">${esc(p.categoryName)}</div>` : ''}
                            <div class="product-card-name">${esc(p.productName)}</div>
                            <div class="product-card-price">&yen;${p.price.toLocaleString()}</div>
                            <div class="product-card-stock ${p.stockQty > 0 ? '' : 'sold-out'}">
                                ${p.stockQty > 0 ? '在庫: ' + p.stockQty : '売り切れ'}
                            </div>
                        </div>
                    </a>
                    <div class="product-card-footer">
                        ${p.stockQty > 0 ? `<button onclick="addToCart(${p.productId})">カートに入れる</button>` : ''}
                        <button class="btn-fav active" onclick="removeFav(${p.productId}, this)" title="お気に入り解除">&#x2665;</button>
                    </div>
                </div>
            `).join('');
        } catch (err) {
            grid.innerHTML = '';
            showAlert(msg, err.message || 'お気に入りの取得に失敗しました');
        }
    }

    window.addToCart = async function (productId) {
        msg.style.display = 'none';
        try {
            await apiRequest('POST', '/cart/add', { productId });
            showAlert(msg, 'カートに追加しました', 'success');
        } catch (err) {
            showAlert(msg, err.message);
        }
    };

    window.removeFav = async function (productId, btn) {
        btn.disabled = true;
        try {
            await apiRequest('POST', `/favorites/${productId}`);
            const card = document.getElementById(`fav-card-${productId}`);
            card.style.transition = 'opacity 0.3s';
            card.style.opacity = '0';
            setTimeout(() => {
                card.remove();
                if (!grid.querySelector('.product-card')) {
                    grid.innerHTML = '<p>お気に入りに登録された商品はありません。</p>';
                }
            }, 300);
        } catch (err) {
            btn.disabled = false;
            showAlert(msg, err.message);
        }
    };

    load();
})();
</script>

<?php include 'footer.php'; ?>
