<?php include 'header.php'; ?>

<h2>注文履歴</h2>
<div id="msg" class="alert" style="display:none;"></div>
<div id="order-list"></div>
<p style="margin-top:24px;"><a href="/product_list.php">&larr; 商品一覧に戻る</a></p>

<script>
(function () {
    if (!requireLogin()) return;

    const msg  = document.getElementById('msg');
    const list = document.getElementById('order-list');

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function formatDate(str) {
        if (!str) return '—';
        const d = new Date(str + 'T00:00:00');
        return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
    }

    async function load() {
        try {
            const orders = await apiRequest('GET', '/order');
            if (!orders || orders.length === 0) {
                list.innerHTML = '<p class="order-empty">まだ注文履歴がありません。</p>';
                return;
            }
            list.innerHTML = orders.map(o => `
                <div class="card order-card">
                    <div class="order-card-header">
                        <span class="order-id">注文番号 #${o.orderId}</span>
                        <span class="order-status">${esc(o.status)}</span>
                        ${o.createDate ? `<span style="font-size:0.85rem;color:var(--muted);">${formatDate(o.createDate)}</span>` : ''}
                        <span class="order-total">&yen;${o.totalPrice.toLocaleString()}</span>
                    </div>
                    <table class="order-items-table">
                        <thead>
                            <tr><th>画像</th><th>商品名</th><th>単価</th><th>数量</th><th>小計</th></tr>
                        </thead>
                        <tbody>
                            ${o.items.map(i => `
                            <tr>
                                <td>${i.imageName ? `<img src="/uploads/${encodeURIComponent(i.imageName)}" width="48" height="48" style="border-radius:6px;">` : '<div class="order-no-img"></div>'}</td>
                                <td>${esc(i.productName)}</td>
                                <td>&yen;${i.price.toLocaleString()}</td>
                                <td>${i.qty}</td>
                                <td>&yen;${(i.price * i.qty).toLocaleString()}</td>
                            </tr>`).join('')}
                        </tbody>
                        <tfoot>
                            <tr class="total-row">
                                <td colspan="4" style="text-align:right;padding-right:24px;">合計</td>
                                <td>&yen;${o.totalPrice.toLocaleString()}</td>
                            </tr>
                        </tfoot>
                    </table>
                </div>
            `).join('');
        } catch (err) {
            showAlert(msg, err.message || '注文履歴の取得に失敗しました');
        }
    }

    load();
})();
</script>

<?php include 'footer.php'; ?>
