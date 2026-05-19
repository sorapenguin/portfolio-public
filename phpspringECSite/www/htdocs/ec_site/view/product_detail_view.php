<?php include 'header.php'; ?>

<div id="msg" class="alert" style="display:none;"></div>
<div id="detail-content"></div>
<p style="margin-top:24px;"><a href="/product_list.php">&larr; 商品一覧に戻る</a></p>

<script>
(function () {
    if (!requireLogin()) return;

    const content = document.getElementById('detail-content');
    const msg     = document.getElementById('msg');
    const id      = new URLSearchParams(location.search).get('id');

    if (!id) {
        content.innerHTML = '<p>商品IDが指定されていません。</p>';
        return;
    }

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    async function load() {
        try {
            const [p, favIds] = await Promise.all([
                apiRequest('GET', `/products/${id}`),
                apiRequest('GET', '/favorites/ids').catch(() => [])
            ]);
            if (!p) return;
            const isFav = (favIds || []).includes(Number(id));
            document.title = `${p.productName} - ECサイト`;
            content.innerHTML = `
                <div class="product-detail-wrap">
                    <div class="product-detail-img">
                        ${p.imageName
                            ? `<img src="/uploads/${encodeURIComponent(p.imageName)}" alt="${esc(p.productName)}">`
                            : '<div class="product-detail-no-img"></div>'}
                    </div>
                    <div class="product-detail-info">
                        <h2 style="border:none;padding:0;margin-bottom:16px;">${esc(p.productName)}</h2>
                        <div class="product-detail-price">&yen;${p.price.toLocaleString()}</div>
                        <div class="product-detail-stock ${p.stockQty > 0 ? '' : 'sold-out'}">
                            ${p.stockQty > 0 ? `在庫: ${p.stockQty}点` : '売り切れ'}
                        </div>
                        ${p.description
                            ? `<div class="product-detail-desc">${esc(p.description)}</div>`
                            : ''}
                        <div class="product-detail-actions">
                            ${p.stockQty > 0
                                ? `<button id="add-btn" class="btn-add-cart">カートに入れる</button>`
                                : ''}
                            <button id="fav-btn" class="btn-fav btn-fav-detail ${isFav ? 'active' : ''}"
                                    title="${isFav ? 'お気に入り解除' : 'お気に入り追加'}">
                                &#x2665; ${isFav ? 'お気に入り済み' : 'お気に入り'}
                            </button>
                        </div>
                    </div>
                </div>
            `;
            if (p.stockQty > 0) {
                document.getElementById('add-btn').addEventListener('click', () => addToCart(p.productId));
            }
            document.getElementById('fav-btn').addEventListener('click', () => toggleFav(p.productId));
        } catch (err) {
            showAlert(msg, err.message || '商品の取得に失敗しました');
        }
    }

    async function toggleFav(productId) {
        const btn = document.getElementById('fav-btn');
        btn.disabled = true;
        try {
            const res = await apiRequest('POST', `/favorites/${productId}`);
            if (res.favorited) {
                btn.classList.add('active');
                btn.title = 'お気に入り解除';
                btn.innerHTML = '&#x2665; お気に入り済み';
            } else {
                btn.classList.remove('active');
                btn.title = 'お気に入り追加';
                btn.innerHTML = '&#x2665; お気に入り';
            }
        } catch (err) {
            showAlert(msg, err.message);
        } finally {
            btn.disabled = false;
        }
    }

    async function addToCart(productId) {
        msg.style.display = 'none';
        const btn = document.getElementById('add-btn');
        btn.disabled = true;
        btn.textContent = '追加中…';
        try {
            await apiRequest('POST', '/cart/add', { productId });
            showAlert(msg, 'カートに追加しました', 'success');
        } catch (err) {
            showAlert(msg, err.message);
        } finally {
            btn.disabled = false;
            btn.textContent = 'カートに入れる';
        }
    }

    load();
})();
</script>

<?php include 'footer.php'; ?>
