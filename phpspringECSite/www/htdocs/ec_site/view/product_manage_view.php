<?php include 'header.php'; ?>

<h2>商品管理</h2>
<div id="msg" class="alert" style="display:none;"></div>

<!-- カテゴリ管理 -->
<h3>カテゴリ管理</h3>
<div class="card">
    <div style="display:flex;gap:24px;flex-wrap:wrap;align-items:flex-start;">
        <div style="flex:1;min-width:220px;">
            <p style="font-size:0.88rem;color:var(--muted);margin-bottom:8px;">登録カテゴリ</p>
            <table style="width:100%;border-collapse:collapse;font-size:0.9rem;" id="cat-table">
                <thead><tr>
                    <th style="text-align:left;padding:6px 8px;border-bottom:1px solid var(--border);">カテゴリ名</th>
                    <th style="width:120px;padding:6px 8px;border-bottom:1px solid var(--border);">操作</th>
                </tr></thead>
                <tbody id="cat-tbody"></tbody>
            </table>
        </div>
        <div style="min-width:200px;">
            <p style="font-size:0.88rem;color:var(--muted);margin-bottom:8px;">新規カテゴリ追加</p>
            <form id="cat-add-form" style="display:flex;gap:8px;align-items:center;">
                <input type="text" id="cat-add-name" placeholder="カテゴリ名" maxlength="100" required
                       style="flex:1;padding:8px 10px;border:1px solid var(--border);border-radius:6px;font-size:0.9rem;">
                <button type="submit" style="white-space:nowrap;">追加</button>
            </form>
        </div>
    </div>
</div>

<h3>商品追加</h3>
<div class="card">
<form id="add-form">
    <div class="form-row">
        <label>商品名 <span style="font-size:0.75rem;color:var(--muted);font-weight:400;">（ランダム自動生成）</span></label>
        <div style="display:flex;gap:8px;align-items:center;">
            <input type="text" id="add-name" readonly required
                   style="background:#eff6ff;color:var(--primary);font-weight:700;letter-spacing:2px;max-width:160px;cursor:default;">
            <button type="button" id="btn-regen"
                    style="padding:6px 12px;font-size:0.82rem;background:var(--muted);white-space:nowrap;"
                    title="商品名を再生成">↺ 再生成</button>
        </div>
    </div>
    <div class="form-row">
        <label>説明文 <span style="font-size:0.75rem;color:var(--muted);font-weight:400;">（画像選択で自動入力）</span></label>
        <textarea id="add-desc" rows="3" readonly
                  style="background:#eff6ff;color:var(--primary);cursor:default;resize:none;"
                  placeholder="画像を選択してください"></textarea>
    </div>
    <div class="form-row">
        <label>カテゴリ</label>
        <select id="add-category" style="max-width:200px;padding:9px 12px;border:1px solid var(--border);border-radius:6px;font-size:0.95rem;background:#fff;">
            <option value="">未設定</option>
        </select>
    </div>
    <div class="form-row">
        <label>価格</label>
        <input type="number" id="add-price" min="0" step="1" value="100" required>
    </div>
    <div class="form-row">
        <label>在庫数</label>
        <input type="number" id="add-stock" min="0" value="1" required>
    </div>
    <div class="form-row">
        <label>公開</label>
        <input type="checkbox" id="add-public" checked>
    </div>
    <div class="form-row">
        <label>画像</label>
        <div class="preset-selector" id="preset-selector">
            <?php foreach([
                ['apple', 'リンゴ', 'リンゴです。'],
                ['banana','バナナ', 'バナナです。'],
                ['orange','オレンジ','オレンジです。'],
                ['melon', 'メロン', 'メロンです。'],
            ] as [$key,$label,$desc]): ?>
            <label class="preset-label">
                <input type="radio" name="preset_image" value="<?= $key ?>.jpg" required style="display:none;" class="preset-radio" data-desc="<?= htmlspecialchars($desc, ENT_QUOTES) ?>">
                <img src="/uploads/<?= $key ?>.jpg" width="80" height="80" alt="<?= $label ?>">
                <?= $label ?>
            </label>
            <?php endforeach; ?>
        </div>
    </div>
    <button type="submit">商品追加</button>
</form>
</div>

<h3>商品一覧</h3>
<table id="product-table">
<thead><tr><th>画像</th><th>商品名</th><th>カテゴリ</th><th>価格</th><th>在庫数</th><th>公開</th><th style="min-width:140px;">操作</th></tr></thead>
<tbody id="product-tbody"></tbody>
</table>

<div id="confirm-modal" style="display:none;position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,0.45);align-items:center;justify-content:center;">
    <div style="background:#fff;border-radius:12px;padding:36px 32px;max-width:360px;width:90%;box-shadow:0 8px 32px rgba(0,0,0,0.2);text-align:center;">
        <p style="font-size:1.05rem;font-weight:700;margin-bottom:8px;">商品を削除しますか？</p>
        <p style="color:var(--muted);font-size:0.9rem;margin-bottom:28px;">この操作は取り消せません。</p>
        <div style="display:flex;gap:12px;justify-content:center;">
            <button id="modal-cancel"  style="background:var(--muted);min-width:100px;">キャンセル</button>
            <button id="modal-confirm" style="background:var(--danger);min-width:100px;">削除する</button>
        </div>
    </div>
</div>

<script>
(function () {
    if (!requireLogin(true)) return;

    const msg    = document.getElementById('msg');
    const tbody  = document.getElementById('product-tbody');
    const catTbody = document.getElementById('cat-tbody');
    const addCatSelect = document.getElementById('add-category');
    let categories = [];

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    // カテゴリ一覧を読み込み、select とカテゴリ表を更新する
    async function loadCategories() {
        try {
            categories = await apiRequest('GET', '/categories') || [];
        } catch (_) {
            categories = [];
        }

        // 商品追加フォームの select を再構築
        addCatSelect.innerHTML = '<option value="">未設定</option>';
        categories.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.categoryId;
            opt.textContent = c.categoryName;
            addCatSelect.appendChild(opt);
        });

        // カテゴリ管理テーブルを再構築
        catTbody.innerHTML = categories.length === 0
            ? '<tr><td colspan="2" style="color:var(--muted);padding:8px;">カテゴリがありません</td></tr>'
            : categories.map(c => `
                <tr id="cat-row-${c.categoryId}">
                    <td style="padding:6px 8px;">
                        <span class="cat-name-label" id="cat-label-${c.categoryId}">${esc(c.categoryName)}</span>
                        <input type="text" class="cat-name-input" id="cat-input-${c.categoryId}"
                               value="${esc(c.categoryName)}" maxlength="100"
                               style="display:none;padding:4px 8px;border:1px solid var(--border);border-radius:4px;font-size:0.88rem;">
                    </td>
                    <td style="padding:6px 8px;display:flex;gap:6px;">
                        <button class="btn-cat-edit" data-id="${c.categoryId}" style="font-size:0.8rem;padding:4px 10px;">編集</button>
                        <button class="btn-cat-save" data-id="${c.categoryId}" style="display:none;font-size:0.8rem;padding:4px 10px;">保存</button>
                        <button class="btn-cat-del" data-id="${c.categoryId}" style="font-size:0.8rem;padding:4px 10px;background:var(--danger);">削除</button>
                    </td>
                </tr>`).join('');

        // 編集ボタン
        catTbody.querySelectorAll('.btn-cat-edit').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.dataset.id;
                document.getElementById('cat-label-' + id).style.display = 'none';
                document.getElementById('cat-input-' + id).style.display = '';
                btn.style.display = 'none';
                document.querySelector(`.btn-cat-save[data-id="${id}"]`).style.display = '';
                document.getElementById('cat-input-' + id).focus();
            });
        });

        // 保存ボタン
        catTbody.querySelectorAll('.btn-cat-save').forEach(btn => {
            btn.addEventListener('click', async () => {
                const id   = btn.dataset.id;
                const name = document.getElementById('cat-input-' + id).value.trim();
                if (!name) { showAlert(msg, 'カテゴリ名を入力してください'); return; }
                try {
                    await apiRequest('PUT', `/admin/categories/${id}`, { categoryName: name });
                    showAlert(msg, 'カテゴリを更新しました', 'success');
                    await loadCategories();
                    await load();
                } catch (err) { showAlert(msg, err.message); }
            });
        });

        // 削除ボタン
        catTbody.querySelectorAll('.btn-cat-del').forEach(btn => {
            btn.addEventListener('click', async () => {
                const id = btn.dataset.id;
                const name = document.getElementById('cat-label-' + id)?.textContent || '';
                if (!confirm(`カテゴリ「${name}」を削除しますか？\n※使用中の商品があれば削除できません。`)) return;
                try {
                    await apiRequest('DELETE', `/admin/categories/${id}`);
                    showAlert(msg, 'カテゴリを削除しました', 'success');
                    await loadCategories();
                    await load();
                } catch (err) { showAlert(msg, err.message); }
            });
        });
    }

    // カテゴリ追加フォーム
    document.getElementById('cat-add-form').addEventListener('submit', async e => {
        e.preventDefault();
        const name = document.getElementById('cat-add-name').value.trim();
        if (!name) return;
        try {
            await apiRequest('POST', '/admin/categories', { categoryName: name });
            showAlert(msg, `カテゴリ「${name}」を追加しました`, 'success');
            document.getElementById('cat-add-name').value = '';
            await loadCategories();
        } catch (err) { showAlert(msg, err.message); }
    });

    // 商品一覧で使うカテゴリ select オプション HTML を生成
    function catOptions(selectedId) {
        return '<option value="">未設定</option>' +
            categories.map(c =>
                `<option value="${c.categoryId}" ${String(c.categoryId) === String(selectedId) ? 'selected' : ''}>${esc(c.categoryName)}</option>`
            ).join('');
    }

    function genName() {
        const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        let name = '';
        for (let i = 0; i < 5; i++) name += chars[Math.floor(Math.random() * chars.length)];
        document.getElementById('add-name').value = name;
    }
    genName();
    document.getElementById('btn-regen').addEventListener('click', genName);

    document.querySelectorAll('.preset-radio').forEach(radio => {
        radio.addEventListener('change', () => {
            document.querySelectorAll('.preset-label').forEach(l => l.classList.remove('selected'));
            radio.closest('.preset-label').classList.add('selected');
            document.getElementById('add-desc').value = radio.dataset.desc || '';
        });
    });

    async function load() {
        try {
            const products = await apiRequest('GET', '/admin/products');
            tbody.innerHTML = (products || []).map(p => `
                <tr>
                    <td>${p.imageName ? `<img src="/uploads/${encodeURIComponent(p.imageName)}" width="52" height="52">` : ''}</td>
                    <td>${esc(p.productName)}</td>
                    <td>
                        <select id="cat-${p.productId}" style="padding:4px 8px;border:1px solid var(--border);border-radius:4px;font-size:0.88rem;">
                            ${catOptions(p.categoryId)}
                        </select>
                    </td>
                    <td><input type="number" id="price-${p.productId}" value="${p.price}" min="0" step="1" style="width:90px;padding:4px 8px;"></td>
                    <td><input type="number" id="stock-${p.productId}" value="${p.stockQty}" min="0" style="width:70px;padding:4px 8px;"></td>
                    <td>
                        <input type="checkbox" ${p.publicFlg ? 'checked' : ''} onchange="togglePublic(${p.productId}, this.checked)">
                    </td>
                    <td style="display:flex;gap:6px;align-items:center;flex-wrap:wrap;">
                        <button onclick="updateProduct(${p.productId})">保存</button>
                        <button onclick="deleteProduct(${p.productId})" style="background:var(--danger,#dc3545);">削除</button>
                    </td>
                </tr>`).join('');
        } catch (err) { showAlert(msg, err.message); }
    }

    document.getElementById('add-form').addEventListener('submit', async e => {
        e.preventDefault();
        const selected = document.querySelector('.preset-radio:checked');
        if (!selected) { showAlert(msg, '画像を選択してください'); return; }
        const catId = document.getElementById('add-category').value;
        msg.style.display = 'none';
        try {
            await apiRequest('POST', '/admin/products', {
                productName: document.getElementById('add-name').value,
                description: document.getElementById('add-desc').value || null,
                categoryId: catId ? parseInt(catId) : null,
                price:      parseInt(document.getElementById('add-price').value),
                stockQty:   parseInt(document.getElementById('add-stock').value),
                publicFlg:  document.getElementById('add-public').checked ? 1 : 0,
                imageName:  selected.value
            });
            showAlert(msg, '商品を追加しました', 'success');
            e.target.reset();
            document.querySelectorAll('.preset-label').forEach(l => l.classList.remove('selected'));
            document.getElementById('add-desc').value = '';
            genName();
            load();
        } catch (err) { showAlert(msg, err.message); }
    });

    window.updateProduct = async function (id) {
        const price    = parseInt(document.getElementById('price-' + id).value);
        const stockQty = parseInt(document.getElementById('stock-' + id).value);
        const catVal   = document.getElementById('cat-' + id).value;
        const categoryId = catVal ? parseInt(catVal) : null;
        if (isNaN(price)    || price    < 0) { showAlert(msg, '価格は0以上の整数を入力してください'); return; }
        if (isNaN(stockQty) || stockQty < 0) { showAlert(msg, '在庫数は0以上の整数を入力してください'); return; }
        try {
            await apiRequest('PUT', `/admin/products/${id}`, { price, stockQty, categoryId });
            showAlert(msg, '商品情報を更新しました', 'success');
            load();
        } catch (err) { showAlert(msg, err.message); }
    };

    window.togglePublic = async function (id, checked) {
        try {
            await apiRequest('PUT', `/admin/products/${id}/toggle-public?publicFlg=${checked ? 1 : 0}`);
        } catch (err) { showAlert(msg, err.message); load(); }
    };

    let pendingDeleteId = null;
    const confirmModal  = document.getElementById('confirm-modal');

    document.getElementById('modal-cancel').addEventListener('click', () => {
        confirmModal.style.display = 'none';
        pendingDeleteId = null;
    });
    document.getElementById('modal-confirm').addEventListener('click', async () => {
        confirmModal.style.display = 'none';
        if (pendingDeleteId === null) return;
        const id = pendingDeleteId;
        pendingDeleteId = null;
        try {
            await apiRequest('DELETE', `/admin/products/${id}`);
            showAlert(msg, '商品を削除しました', 'success');
            load();
        } catch (err) { showAlert(msg, err.message); }
    });
    confirmModal.addEventListener('click', e => {
        if (e.target === confirmModal) { confirmModal.style.display = 'none'; pendingDeleteId = null; }
    });

    window.deleteProduct = function (id) {
        pendingDeleteId = id;
        confirmModal.style.display = 'flex';
    };

    loadCategories().then(() => load());
})();
</script>

<?php include 'footer.php'; ?>
