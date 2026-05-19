<?php include 'header.php'; ?>

<h2>注文管理</h2>
<div id="msg" class="alert" style="display:none;"></div>

<table id="orders-table">
    <thead>
        <tr>
            <th>注文番号</th>
            <th>ユーザー</th>
            <th>注文日</th>
            <th>商品</th>
            <th>合計金額</th>
            <th>ステータス</th>
            <th style="min-width:80px;">操作</th>
        </tr>
    </thead>
    <tbody id="orders-tbody"></tbody>
</table>

<script>
(function () {
    if (!requireLogin(true)) return;

    const msg   = document.getElementById('msg');
    const tbody = document.getElementById('orders-tbody');

    const STATUSES = ['未処理', '処理中', '発送済み', '完了'];

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function statusOptions(current) {
        return STATUSES.map(s =>
            `<option value="${esc(s)}" ${s === current ? 'selected' : ''}>${esc(s)}</option>`
        ).join('');
    }

    function formatDate(str) {
        if (!str) return '—';
        const d = new Date(str + 'T00:00:00');
        return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
    }

    function itemsSummary(items) {
        if (!items || items.length === 0) return '—';
        return items.map(i => `${esc(i.productName)} x${i.qty}`).join('、');
    }

    async function load() {
        try {
            const orders = await apiRequest('GET', '/admin/orders');
            if (!orders || orders.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--muted);padding:32px;">注文はありません</td></tr>';
                return;
            }
            tbody.innerHTML = orders.map(o => `
                <tr>
                    <td style="font-weight:700;color:var(--primary);">#${o.orderId}</td>
                    <td>${esc(o.userName || '—')}</td>
                    <td style="white-space:nowrap;">${formatDate(o.createDate)}</td>
                    <td style="font-size:0.85rem;color:var(--muted);max-width:220px;">${itemsSummary(o.items)}</td>
                    <td style="font-weight:600;">¥${o.totalPrice.toLocaleString()}</td>
                    <td>
                        <select id="status-${o.orderId}" class="status-select status-${statusClass(o.status)}">
                            ${statusOptions(o.status)}
                        </select>
                    </td>
                    <td>
                        <button onclick="updateStatus(${o.orderId})" style="padding:5px 14px;font-size:0.83rem;">更新</button>
                    </td>
                </tr>`).join('');

            // セレクト変更時に色を同期
            orders.forEach(o => {
                const sel = document.getElementById('status-' + o.orderId);
                sel.addEventListener('change', () => syncStatusColor(sel));
            });
        } catch (err) {
            showAlert(msg, err.message);
        }
    }

    function statusClass(status) {
        const map = { '未処理': 'pending', '処理中': 'processing', '発送済み': 'shipped', '完了': 'done' };
        return map[status] || 'pending';
    }

    function syncStatusColor(sel) {
        sel.className = 'status-select status-' + statusClass(sel.value);
    }

    window.updateStatus = async function (orderId) {
        const sel    = document.getElementById('status-' + orderId);
        const status = sel.value;
        msg.style.display = 'none';
        try {
            await apiRequest('PUT', `/admin/orders/${orderId}/status?status=${encodeURIComponent(status)}`);
            showAlert(msg, `#${orderId} のステータスを「${status}」に更新しました`, 'success');
        } catch (err) {
            showAlert(msg, err.message);
        }
    };

    load();
})();
</script>

<?php include 'footer.php'; ?>
