<?php include 'header.php'; ?>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>

<h2>ダッシュボード</h2>
<div id="msg" class="alert" style="display:none;"></div>

<!-- サマリーカード -->
<div class="dash-summary" id="dash-summary"></div>

<!-- 月別売上 / 人気商品 (2カラム) -->
<div class="dash-2col">
    <div class="dash-widget">
        <h3>月別売上（直近12ヶ月）</h3>
        <div id="monthly-chart"></div>
    </div>
    <div class="dash-widget">
        <h3>人気商品 TOP10</h3>
        <div id="top-products"></div>
    </div>
</div>

<!-- 在庫切れ商品 -->
<div class="dash-widget">
    <h3>在庫切れ商品</h3>
    <div id="out-of-stock"></div>
</div>

<script>
(function () {
    if (!requireLogin(true)) return;

    const msg = document.getElementById('msg');

    function esc(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function fmtYen(n) { return '¥' + Number(n).toLocaleString(); }

    async function load() {
        try {
            const d = await apiRequest('GET', '/admin/dashboard');
            renderSummary(d);
            renderMonthlyChart(d.monthlySales || []);
            renderTopProducts(d.topProducts || []);
            renderOutOfStock(d.outOfStock || []);
        } catch (err) {
            showAlert(msg, err.message || 'データの取得に失敗しました');
        }
    }

    function renderSummary(d) {
        const monthly = d.monthlySales || [];
        const today   = new Date();
        const cur     = monthly.find(m => m.year === today.getFullYear() && m.month === today.getMonth() + 1);
        const totalSales    = cur ? cur.totalSales   : 0;
        const totalOrders   = cur ? cur.orderCount   : 0;
        const outOfStockCnt = (d.outOfStock || []).length;

        document.getElementById('dash-summary').innerHTML = `
            <div class="dash-card">
                <div class="dash-card-label">今月売上</div>
                <div class="dash-card-value">${fmtYen(totalSales)}</div>
            </div>
            <div class="dash-card">
                <div class="dash-card-label">今月注文数</div>
                <div class="dash-card-value">${totalOrders}<span class="dash-card-unit">件</span></div>
            </div>
            <div class="dash-card dash-card-warn">
                <div class="dash-card-label">在庫切れ商品</div>
                <div class="dash-card-value">${outOfStockCnt}<span class="dash-card-unit">品</span></div>
            </div>
        `;
    }

    function renderMonthlyChart(rows) {
        const el = document.getElementById('monthly-chart');
        if (!rows.length) {
            el.innerHTML = '<p class="dash-empty">注文データがありません</p>';
            return;
        }

        el.innerHTML = '<div class="chart-canvas-wrap"><canvas id="salesChart"></canvas></div>';

        const labels = rows.map(r => `${r.year}/${String(r.month).padStart(2, '0')}`);
        const sales  = rows.map(r => r.totalSales);
        const orders = rows.map(r => r.orderCount);

        new Chart(document.getElementById('salesChart'), {
            data: {
                labels,
                datasets: [
                    {
                        type: 'line',
                        label: '注文数',
                        data: orders,
                        borderColor: '#f59e0b',
                        backgroundColor: 'rgba(245,158,11,0.12)',
                        pointBackgroundColor: '#f59e0b',
                        pointRadius: 5,
                        pointHoverRadius: 7,
                        tension: 0.35,
                        fill: false,
                        yAxisID: 'yRight',
                        order: 1,
                    },
                    {
                        type: 'bar',
                        label: '売上金額',
                        data: sales,
                        backgroundColor: 'rgba(37,99,235,0.72)',
                        borderColor: '#2563eb',
                        borderWidth: 0,
                        borderRadius: 5,
                        borderSkipped: false,
                        yAxisID: 'yLeft',
                        order: 2,
                    }
                ]
            },
            options: {
                responsive: true,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: {
                        position: 'top',
                        labels: { font: { size: 12 }, padding: 16 }
                    },
                    tooltip: {
                        callbacks: {
                            label: ctx => {
                                if (ctx.dataset.yAxisID === 'yLeft') {
                                    return `  売上: ${fmtYen(ctx.raw)}`;
                                }
                                return `  注文数: ${ctx.raw}件`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(0,0,0,0.04)' },
                        ticks: { font: { size: 11 } }
                    },
                    yLeft: {
                        type: 'linear',
                        position: 'left',
                        ticks: {
                            callback: v => '¥' + Number(v).toLocaleString(),
                            font: { size: 11 }
                        },
                        grid: { color: 'rgba(0,0,0,0.05)' }
                    },
                    yRight: {
                        type: 'linear',
                        position: 'right',
                        ticks: {
                            callback: v => v + '件',
                            font: { size: 11 }
                        },
                        grid: { drawOnChartArea: false }
                    }
                }
            }
        });
    }

    const CHART_COLORS = [
        '#2563eb','#f59e0b','#10b981','#ef4444','#8b5cf6',
        '#06b6d4','#f97316','#ec4899','#84cc16','#6366f1'
    ];

    function renderTopProducts(rows) {
        const el = document.getElementById('top-products');
        if (!rows.length) {
            el.innerHTML = '<p class="dash-empty">販売データがありません</p>';
            return;
        }

        const rowsHtml = rows.map((p, i) => `
            <tr>
                <td class="rank-cell">${i + 1}</td>
                <td><a href="/product_detail.php?id=${p.productId}">${esc(p.productName)}</a></td>
                <td class="num-cell">${Number(p.totalQty).toLocaleString()}</td>
                <td class="num-cell">${fmtYen(p.totalSales)}</td>
            </tr>`).join('');

        el.innerHTML = `
            <div class="chart-canvas-wrap chart-canvas-wrap--donut">
                <canvas id="topProductsChart"></canvas>
            </div>
            <table class="dash-table" style="margin-top:16px;">
                <thead>
                    <tr><th>#</th><th>商品名</th><th>販売数</th><th>売上金額</th></tr>
                </thead>
                <tbody>${rowsHtml}</tbody>
            </table>`;

        new Chart(document.getElementById('topProductsChart'), {
            type: 'doughnut',
            data: {
                labels: rows.map(r => r.productName),
                datasets: [{
                    data: rows.map(r => r.totalSales),
                    backgroundColor: CHART_COLORS,
                    borderColor: '#fff',
                    borderWidth: 3,
                    hoverOffset: 8,
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'right',
                        labels: {
                            font: { size: 11 },
                            padding: 10,
                            boxWidth: 14,
                            generateLabels: chart => {
                                const ds = chart.data.datasets[0];
                                const total = ds.data.reduce((a, b) => a + b, 0);
                                return chart.data.labels.map((label, i) => ({
                                    text: label.length > 12 ? label.slice(0, 12) + '…' : label,
                                    fillStyle: ds.backgroundColor[i],
                                    strokeStyle: '#fff',
                                    lineWidth: 1,
                                    index: i,
                                    hidden: false,
                                }));
                            }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.label}: ${fmtYen(ctx.raw)}`
                        }
                    }
                },
                cutout: '62%',
            }
        });
    }

    function renderOutOfStock(rows) {
        const el = document.getElementById('out-of-stock');
        if (!rows.length) {
            el.innerHTML = '<p class="dash-empty" style="color:var(--primary);">在庫切れ商品はありません ✓</p>';
            return;
        }
        const rowsHtml = rows.map(p => `
            <tr>
                <td>${p.imageName
                    ? `<img src="/uploads/${encodeURIComponent(p.imageName)}" alt="${esc(p.productName)}" class="dash-thumb">`
                    : '<div class="dash-thumb-empty"></div>'}</td>
                <td><a href="/product_detail.php?id=${p.productId}">${esc(p.productName)}</a></td>
                <td><span class="badge-sold-out">在庫切れ</span></td>
                <td><a href="/product_manage.php" class="dash-link-edit">在庫追加 &rarr;</a></td>
            </tr>`).join('');

        el.innerHTML = `
            <table class="dash-table">
                <thead>
                    <tr><th></th><th>商品名</th><th>状態</th><th>操作</th></tr>
                </thead>
                <tbody>${rowsHtml}</tbody>
            </table>`;
    }

    load();
})();
</script>

<?php include 'footer.php'; ?>
