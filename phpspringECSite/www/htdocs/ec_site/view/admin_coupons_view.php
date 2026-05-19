<?php include 'header.php'; ?>

<h2>クーポン管理</h2>
<div id="msg" class="alert" style="display:none;"></div>

<div class="card" style="max-width:480px;margin-bottom:24px;">
    <h3 style="margin-bottom:16px;">新規クーポン作成</h3>
    <div style="display:flex;flex-direction:column;gap:12px;">
        <div>
            <label for="new-code">クーポンコード</label>
            <input type="text" id="new-code" placeholder="例: SUMMER10" maxlength="32"
                   style="text-transform:uppercase;" oninput="this.value=this.value.toUpperCase()">
        </div>
        <div>
            <label for="new-rate">割引率（%）</label>
            <input type="number" id="new-rate" placeholder="例: 10" min="1" max="100" style="width:120px;">
        </div>
        <div>
            <label for="new-expires">有効期限</label>
            <input type="date" id="new-expires">
        </div>
        <button id="create-btn" type="button">作成</button>
    </div>
</div>

<div id="list-content"></div>

<script>
(function () {
    if (!requireLogin(true)) return;

    const msg  = document.getElementById('msg');
    const list = document.getElementById('list-content');

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    async function loadCoupons() {
        try {
            const coupons = await apiRequest('GET', '/admin/coupons');
            if (!coupons || !coupons.length) {
                list.innerHTML = '<p style="color:var(--muted);">クーポンがまだありません。</p>';
                return;
            }
            list.innerHTML = `
                <table>
                <thead><tr>
                    <th>ID</th><th>コード</th><th>割引率</th><th>有効期限</th><th>状態</th><th>作成日</th>
                </tr></thead>
                <tbody>
                ${coupons.map(c => {
                    const expired = c.expiresAt && new Date(c.expiresAt) < new Date();
                    const statusLabel = c.isUsed ? '<span style="color:#dc2626;">使用済</span>'
                                     : expired   ? '<span style="color:#f59e0b;">期限切れ</span>'
                                     :             '<span style="color:#16a34a;">有効</span>';
                    return `<tr>
                        <td>${c.couponId}</td>
                        <td><strong>${esc(c.code)}</strong></td>
                        <td>${c.discountRate}%</td>
                        <td>${c.expiresAt || '—'}</td>
                        <td>${statusLabel}</td>
                        <td>${c.createDate || '—'}</td>
                    </tr>`;
                }).join('')}
                </tbody>
                </table>
            `;
        } catch (err) {
            showAlert(msg, err.message);
        }
    }

    document.getElementById('create-btn').addEventListener('click', async () => {
        const code     = document.getElementById('new-code').value.trim().toUpperCase();
        const rate     = parseInt(document.getElementById('new-rate').value, 10);
        const expires  = document.getElementById('new-expires').value;

        if (!code)        { showAlert(msg, 'コードを入力してください'); return; }
        if (!rate || rate < 1 || rate > 100) { showAlert(msg, '割引率は1〜100で入力してください'); return; }
        if (!expires)     { showAlert(msg, '有効期限を選択してください'); return; }

        try {
            await apiRequest('POST', '/admin/coupons', { code, discountRate: rate, expiresAt: expires });
            showAlert(msg, `クーポン「${code}」を作成しました`, 'success');
            document.getElementById('new-code').value    = '';
            document.getElementById('new-rate').value    = '';
            document.getElementById('new-expires').value = '';
            loadCoupons();
        } catch (err) {
            showAlert(msg, err.message);
        }
    });

    loadCoupons();
})();
</script>

<?php include 'footer.php'; ?>
