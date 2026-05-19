<?php include 'header.php'; ?>

<h2>ショッピングカート</h2>
<div id="msg" class="alert" style="display:none;"></div>
<div id="cart-content"></div>
<p style="margin-top:20px;"><a href="/product_list.php">&larr; 商品一覧に戻る</a></p>

<script>
(function () {
    if (!requireLogin()) return;

    const content = document.getElementById('cart-content');
    const msg     = document.getElementById('msg');
    let appliedCouponCode = null;
    let appliedDiscountRate = null;

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    async function load() {
        msg.style.display = 'none';
        try {
            const items = await apiRequest('GET', '/cart');
            if (!items || !items.length) {
                content.innerHTML = `
                    <div class="card" style="text-align:center;padding:48px;">
                        <p style="color:var(--muted);margin-bottom:20px;">カートに商品はありません</p>
                        <a href="/product_list.php">商品一覧を見る</a>
                    </div>`;
                return;
            }
            const subtotal = items.reduce((s, i) => s + i.price * i.productQty, 0);
            content.innerHTML = `
                <table>
                <thead><tr>
                    <th>画像</th><th>商品名</th><th>価格</th><th>数量</th><th>小計</th><th>操作</th>
                </tr></thead>
                <tbody>
                ${items.map(i => `
                    <tr>
                        <td>${i.imageName ? `<img src="/uploads/${encodeURIComponent(i.imageName)}" width="56" height="56" alt="${esc(i.productName)}">` : ''}</td>
                        <td>${esc(i.productName)}</td>
                        <td>&yen;${i.price.toLocaleString()}</td>
                        <td style="display:flex;gap:6px;align-items:center;">
                            <input type="number" id="qty-${i.cartId}" value="${i.productQty}" min="1" style="width:70px;padding:4px 8px;">
                            <button onclick="updateQty(${i.cartId})">変更</button>
                        </td>
                        <td>&yen;${(i.price * i.productQty).toLocaleString()}</td>
                        <td><button onclick="removeItem(${i.cartId})">削除</button></td>
                    </tr>`).join('')}
                </tbody>
                <tfoot>
                    <tr class="total-row">
                        <td colspan="4" style="text-align:right;padding-right:24px;">小計</td>
                        <td colspan="2" id="subtotal-cell">&yen;${subtotal.toLocaleString()}</td>
                    </tr>
                    <tr id="discount-row" style="display:none;color:#16a34a;">
                        <td colspan="4" style="text-align:right;padding-right:24px;">クーポン割引</td>
                        <td colspan="2" id="discount-cell"></td>
                    </tr>
                    <tr class="total-row">
                        <td colspan="4" style="text-align:right;padding-right:24px;">合計</td>
                        <td colspan="2" id="total-cell">&yen;${subtotal.toLocaleString()}</td>
                    </tr>
                </tfoot>
                </table>

                <div class="card" style="margin-top:16px;padding:16px;">
                    <p style="margin-bottom:8px;font-weight:500;">クーポンコード</p>
                    <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
                        <input type="text" id="coupon-input" placeholder="コードを入力"
                               style="width:200px;text-transform:uppercase;" maxlength="32">
                        <button id="coupon-apply-btn" type="button">適用</button>
                        <button id="coupon-remove-btn" type="button" style="display:none;background:var(--muted);">取消</button>
                    </div>
                    <div id="coupon-msg" style="margin-top:6px;font-size:.875rem;"></div>
                </div>

                <button id="purchase-btn" name="purchase" style="margin-top:16px;">購入する</button>
            `;
            document.getElementById('purchase-btn').addEventListener('click', purchase);
            document.getElementById('coupon-apply-btn').addEventListener('click', applyCoupon);
            document.getElementById('coupon-remove-btn').addEventListener('click', removeCoupon);

            if (appliedCouponCode) {
                renderCouponApplied(appliedCouponCode, appliedDiscountRate, subtotal);
            }
        } catch (err) {
            showAlert(msg, err.message);
        }
    }

    function renderCouponApplied(code, rate, subtotal) {
        const discountAmount = Math.floor(subtotal * rate / 100);
        const total = subtotal - discountAmount;
        document.getElementById('discount-row').style.display = '';
        document.getElementById('discount-cell').textContent = `-¥${discountAmount.toLocaleString()} (${rate}% OFF)`;
        document.getElementById('total-cell').textContent = `¥${total.toLocaleString()}`;
        const couponMsg = document.getElementById('coupon-msg');
        if (couponMsg) {
            couponMsg.style.color = '#16a34a';
            couponMsg.textContent = `「${code}」が適用されています（${rate}% 割引）`;
        }
        const applyBtn = document.getElementById('coupon-apply-btn');
        const removeBtn = document.getElementById('coupon-remove-btn');
        const input = document.getElementById('coupon-input');
        if (applyBtn) applyBtn.style.display = 'none';
        if (removeBtn) removeBtn.style.display = '';
        if (input) { input.value = code; input.disabled = true; }
    }

    async function applyCoupon() {
        const input = document.getElementById('coupon-input');
        const couponMsg = document.getElementById('coupon-msg');
        const code = input.value.trim().toUpperCase();
        if (!code) { couponMsg.style.color = '#dc2626'; couponMsg.textContent = 'コードを入力してください'; return; }
        try {
            const res = await apiRequest('GET', `/coupon/validate?code=${encodeURIComponent(code)}`);
            appliedCouponCode = res.code;
            appliedDiscountRate = res.discountRate;
            const subtotal = getCurrentSubtotal();
            renderCouponApplied(res.code, res.discountRate, subtotal);
        } catch (err) {
            couponMsg.style.color = '#dc2626';
            couponMsg.textContent = err.message;
        }
    }

    function removeCoupon() {
        appliedCouponCode = null;
        appliedDiscountRate = null;
        const input = document.getElementById('coupon-input');
        if (input) { input.value = ''; input.disabled = false; }
        const applyBtn = document.getElementById('coupon-apply-btn');
        const removeBtn = document.getElementById('coupon-remove-btn');
        if (applyBtn) applyBtn.style.display = '';
        if (removeBtn) removeBtn.style.display = 'none';
        const couponMsg = document.getElementById('coupon-msg');
        if (couponMsg) couponMsg.textContent = '';
        document.getElementById('discount-row').style.display = 'none';
        const subtotal = getCurrentSubtotal();
        document.getElementById('total-cell').textContent = `¥${subtotal.toLocaleString()}`;
    }

    function getCurrentSubtotal() {
        const cell = document.getElementById('subtotal-cell');
        return cell ? parseInt(cell.textContent.replace(/[¥,]/g, ''), 10) : 0;
    }

    window.updateQty = async function (cartId) {
        const qty = parseInt(document.getElementById('qty-' + cartId).value);
        if (qty < 1) return;
        try {
            await apiRequest('PUT', `/cart/${cartId}?qty=${qty}`);
            load();
        } catch (err) { showAlert(msg, err.message); }
    };

    window.removeItem = async function (cartId) {
        if (!confirm('削除しますか？')) return;
        try {
            await apiRequest('DELETE', `/cart/${cartId}`);
            load();
        } catch (err) { showAlert(msg, err.message); }
    };

    async function purchase() {
        if (!confirm('購入を確定しますか？')) return;

        const btn = document.getElementById('purchase-btn');
        btn.disabled = true;
        btn.classList.add('btn-loading');
        btn.textContent = '購入処理中…';
        msg.style.display = 'none';

        try {
            const url = appliedCouponCode ? `/order?couponCode=${encodeURIComponent(appliedCouponCode)}` : '/order';
            const order = await apiRequest('POST', url);
            sessionStorage.setItem('last_order', JSON.stringify(order));
            location.href = '/purchase_complete.php';
        } catch (err) {
            showAlert(msg, err.message);
            btn.disabled = false;
            btn.classList.remove('btn-loading');
            btn.textContent = '購入する';
        }
    }

    load();
})();
</script>

<?php include 'footer.php'; ?>
