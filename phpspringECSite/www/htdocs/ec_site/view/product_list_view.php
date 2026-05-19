<?php include 'header.php'; ?>

<h2>商品一覧</h2>
<div id="msg" class="alert" style="display:none;"></div>

<div class="filter-panel">
    <div class="filter-main-row">
        <input type="text" id="search-input" placeholder="商品名で検索..." autocomplete="off">
        <button type="button" id="search-btn">検索</button>
        <button type="button" id="reset-btn" class="btn-reset">リセット</button>
    </div>
    <div class="filter-sub-row">
        <div class="filter-price-group">
            <span class="filter-label-text">価格</span>
            <input type="number" id="price-min" placeholder="下限" min="0">
            <span class="filter-separator">〜</span>
            <input type="number" id="price-max" placeholder="上限" min="0">
            <span class="filter-label-text">円</span>
        </div>
        <label class="filter-stock-label">
            <input type="checkbox" id="in-stock-only">
            在庫ありのみ
        </label>
    </div>
</div>

<div class="category-tabs" id="category-tabs">
    <button class="cat-tab active" data-cat-id="">すべて</button>
</div>

<div id="product-grid" class="product-grid"></div>

<div class="pagination" id="pagination" style="display:none;"></div>

<p><a href="/cart_page.php">カートを見る &rarr;</a></p>

<script>
(function () {
    if (!requireLogin()) return;

    const grid       = document.getElementById('product-grid');
    const msg        = document.getElementById('msg');
    const pager      = document.getElementById('pagination');
    const searchInput = document.getElementById('search-input');
    const tabsEl     = document.getElementById('category-tabs');

    let state = { keyword: '', categoryId: null, priceMin: null, priceMax: null, inStockOnly: false, page: 0, size: 12 };
    let favIds = new Set();

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    async function loadCategories() {
        try {
            const cats = await apiRequest('GET', '/categories');
            (cats || []).forEach(cat => {
                const btn = document.createElement('button');
                btn.className = 'cat-tab';
                btn.dataset.catId = cat.categoryId;
                btn.textContent = cat.categoryName;
                tabsEl.appendChild(btn);
            });
        } catch (_) {}
    }

    tabsEl.addEventListener('click', e => {
        const btn = e.target.closest('.cat-tab');
        if (!btn) return;
        tabsEl.querySelectorAll('.cat-tab').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.categoryId = btn.dataset.catId || null;
        state.page = 0;
        load();
    });

    function applyFilters() {
        state.keyword     = searchInput.value.trim();
        const minVal      = document.getElementById('price-min').value;
        const maxVal      = document.getElementById('price-max').value;
        state.priceMin    = minVal !== '' ? parseInt(minVal, 10) : null;
        state.priceMax    = maxVal !== '' ? parseInt(maxVal, 10) : null;
        state.inStockOnly = document.getElementById('in-stock-only').checked;
        state.page = 0;
        load();
    }

    document.getElementById('search-btn').addEventListener('click', applyFilters);

    searchInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') applyFilters();
    });

    document.getElementById('reset-btn').addEventListener('click', () => {
        searchInput.value = '';
        document.getElementById('price-min').value = '';
        document.getElementById('price-max').value = '';
        document.getElementById('in-stock-only').checked = false;
        tabsEl.querySelectorAll('.cat-tab').forEach(b => b.classList.remove('active'));
        tabsEl.querySelector('[data-cat-id=""]').classList.add('active');
        state.keyword = '';
        state.categoryId = null;
        state.priceMin = null;
        state.priceMax = null;
        state.inStockOnly = false;
        state.page = 0;
        load();
    });

    async function loadFavIds() {
        try {
            const ids = await apiRequest('GET', '/favorites/ids');
            favIds = new Set(ids || []);
        } catch (_) { favIds = new Set(); }
    }

    async function load() {
        msg.style.display = 'none';
        grid.innerHTML = '<p style="color:var(--muted)">読み込み中...</p>';
        pager.style.display = 'none';
        try {
            const params = new URLSearchParams({ page: state.page, size: state.size });
            if (state.keyword)           params.set('keyword',     state.keyword);
            if (state.categoryId)        params.set('categoryId',  state.categoryId);
            if (state.priceMin !== null) params.set('priceMin',    state.priceMin);
            if (state.priceMax !== null) params.set('priceMax',    state.priceMax);
            if (state.inStockOnly)       params.set('inStockOnly', '1');

            const data = await apiRequest('GET', '/products?' + params.toString());
            const products = data.products || [];

            if (!products.length) {
                grid.innerHTML = '<p>該当する商品はありません。</p>';
                return;
            }

            grid.innerHTML = products.map(p => `
                <div class="product-card">
                    <button class="btn-fav ${favIds.has(p.productId) ? 'active' : ''}"
                            onclick="toggleFav(${p.productId}, this)"
                            title="${favIds.has(p.productId) ? 'お気に入り解除' : 'お気に入り追加'}">&#x2665;</button>
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
                    ${p.stockQty > 0 ? `
                    <div class="product-card-footer">
                        <button onclick="addToCart(${p.productId})">カートに入れる</button>
                    </div>` : ''}
                </div>
            `).join('');

            renderPagination(data.totalPages, data.page);
        } catch (err) {
            grid.innerHTML = '';
            showAlert(msg, err.message);
        }
    }

    function renderPagination(totalPages, currentPage) {
        if (totalPages <= 1) { pager.style.display = 'none'; return; }

        pager.style.display = 'flex';
        const items = [];

        items.push(`<button class="page-btn" ${currentPage === 0 ? 'disabled' : ''} data-page="${currentPage - 1}">&lsaquo;</button>`);

        for (let i = 0; i < totalPages; i++) {
            if (totalPages > 7 && Math.abs(i - currentPage) > 2 && i !== 0 && i !== totalPages - 1) {
                if (i === 1 || i === totalPages - 2) items.push('<span class="page-ellipsis">…</span>');
                continue;
            }
            items.push(`<button class="page-btn ${i === currentPage ? 'active' : ''}" data-page="${i}">${i + 1}</button>`);
        }

        items.push(`<button class="page-btn" ${currentPage === totalPages - 1 ? 'disabled' : ''} data-page="${currentPage + 1}">&rsaquo;</button>`);

        pager.innerHTML = items.join('');
        pager.querySelectorAll('.page-btn:not([disabled])').forEach(btn => {
            btn.addEventListener('click', () => {
                state.page = parseInt(btn.dataset.page);
                load();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        });
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

    window.toggleFav = async function (productId, btn) {
        btn.disabled = true;
        try {
            const res = await apiRequest('POST', `/favorites/${productId}`);
            if (res.favorited) {
                favIds.add(productId);
                btn.classList.add('active');
                btn.title = 'お気に入り解除';
            } else {
                favIds.delete(productId);
                btn.classList.remove('active');
                btn.title = 'お気に入り追加';
            }
        } catch (err) {
            showAlert(msg, err.message);
        } finally {
            btn.disabled = false;
        }
    };

    loadCategories().then(() => Promise.all([loadFavIds(), load()]));
})();
</script>

<?php include 'footer.php'; ?>
