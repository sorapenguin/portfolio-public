<?php include 'header.php'; ?>

<div id="page-content"></div>

<script>
(function () {
    if (!requireLogin()) return;

    const page  = document.getElementById('page-content');
    const order = JSON.parse(sessionStorage.getItem('last_order') || 'null');

    if (!order) {
        page.innerHTML = `
            <h2>注文情報が見つかりません</h2>
            <p>すでに購入完了済み、またはセッションが切れた可能性があります。</p>
            <div style="display:flex;gap:16px;margin-top:24px;">
                <a href="/order_history.php" class="btn-link-primary">注文履歴を確認する</a>
                <a href="/product_list.php">&larr; 商品一覧に戻る</a>
            </div>`;
        return;
    }

    sessionStorage.removeItem('last_order');

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function formatDate(str) {
        if (!str) return '—';
        const d = new Date(str + 'T00:00:00');
        return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
    }

    page.innerHTML = `
        <div class="complete-banner">
            <div style="font-size:2.8rem;margin-bottom:8px;">✓</div>
            <h2>ご購入ありがとうございました</h2>
            <p style="color:#1e40af;margin:0;">ご注文が確定しました</p>
        </div>

        <div class="card" style="max-width:640px;">
            <p style="margin-bottom:6px;"><strong>注文番号：</strong>&nbsp;#${order.orderId}</p>
            <p style="margin-bottom:6px;"><strong>注文日時：</strong>&nbsp;${formatDate(order.createDate)}</p>
            ${order.discountAmount ? `
            <p style="margin-bottom:6px;color:#16a34a;">
                <strong>クーポン割引：</strong>&nbsp;-&yen;${order.discountAmount.toLocaleString()}（${order.discountRate}% OFF）
            </p>` : ''}
            <p style="margin-bottom:20px;"><strong>合計金額：</strong>&nbsp;&yen;${order.totalPrice.toLocaleString()}</p>

            <table>
            <thead><tr><th>画像</th><th>商品名</th><th>単価</th><th>数量</th><th>小計</th></tr></thead>
            <tbody>
            ${order.items.map(i => `
                <tr>
                    <td>${i.imageName
                        ? `<img src="/uploads/${encodeURIComponent(i.imageName)}" width="52" height="52">`
                        : '<div class="order-no-img"></div>'}</td>
                    <td>${esc(i.productName)}</td>
                    <td>&yen;${i.price.toLocaleString()}</td>
                    <td>${i.qty}</td>
                    <td>&yen;${(i.price * i.qty).toLocaleString()}</td>
                </tr>`).join('')}
            </tbody>
            <tfoot><tr class="total-row">
                <td colspan="4" style="text-align:right;padding-right:24px;">合計</td>
                <td>&yen;${order.totalPrice.toLocaleString()}</td>
            </tr></tfoot>
            </table>
        </div>

        <div style="display:flex;gap:16px;margin-top:8px;flex-wrap:wrap;">
            <a href="/order_history.php" style="
                display:inline-block;
                padding:10px 24px;
                background:var(--primary);
                color:white;
                border-radius:6px;
                font-weight:500;
                text-decoration:none;
            ">注文履歴を見る</a>
            <a href="/product_list.php" style="
                display:inline-block;
                padding:10px 24px;
                background:white;
                color:var(--primary);
                border:1px solid var(--border);
                border-radius:6px;
                font-weight:500;
                text-decoration:none;
            ">&larr; 商品一覧に戻る</a>
        </div>
    `;
})();
</script>

<?php include 'footer.php'; ?>
