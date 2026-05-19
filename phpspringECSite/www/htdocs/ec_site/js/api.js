const API_BASE = '/api';

function getToken() {
    return localStorage.getItem('ec_token');
}

function setToken(token) {
    localStorage.setItem('ec_token', token);
}

function clearToken() {
    localStorage.removeItem('ec_token');
}

function decodeToken(token) {
    if (!token) return null;
    try {
        const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return JSON.parse(decodeURIComponent(atob(b64).split('').map(c =>
            '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
        ).join('')));
    } catch { return null; }
}

function getUser() { return decodeToken(getToken()); }

function isLoggedIn() {
    const u = getUser();
    return u ? u.exp * 1000 > Date.now() : false;
}

function isAdmin() {
    const u = getUser();
    return u ? u.role === 'ADMIN' : false;
}

function requireLogin(adminOnly = false) {
    if (!isLoggedIn()) { location.href = '/login.php'; return false; }
    if (adminOnly && !isAdmin()) { location.href = '/product_list.php'; return false; }
    return true;
}

async function apiRequest(method, path, body = null) {
    const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const opts = { method, headers };
    if (body !== null) opts.body = JSON.stringify(body);

    let res;
    try {
        res = await fetch(API_BASE + path, opts);
    } catch (networkErr) {
        throw new Error('サーバーに接続できません。時間をおいて再度お試しください。');
    }

    if (res.status === 401) {
        clearToken();
        location.href = '/login.php';
        return;
    }

    const text = await res.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        // レスポンスがJSONでない場合はそのままHTTPステータスをエラーにする
    }

    if (!res.ok) {
        const msg = (data && data.message) ? data.message
                  : (data && data.error)   ? `${data.error} (${res.status})`
                  : `HTTP ${res.status}`;
        throw new Error(msg);
    }
    return data;
}

function showAlert(el, msg, type = 'error') {
    el.textContent = msg;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
}

function renderHeader() {
    const nav = document.getElementById('header-nav');
    if (!nav) return;
    const u = getUser();
    if (u && isLoggedIn()) {
        nav.innerHTML = `
            <span>${u.sub}さん</span>
            ${u.role === 'ADMIN' ? '<a href="/admin_dashboard.php">ダッシュボード</a>' : ''}
            ${u.role === 'ADMIN' ? '<a href="/product_manage.php">商品管理</a>' : ''}
            ${u.role === 'ADMIN' ? '<a href="/admin_orders.php">注文管理</a>' : ''}
            ${u.role === 'ADMIN' ? '<a href="/admin_coupons.php">クーポン管理</a>' : ''}
            ${u.role !== 'ADMIN' ? '<a href="/order_history.php">注文履歴</a>' : ''}
            ${u.role !== 'ADMIN' ? '<a href="/favorites.php">&#x2665; お気に入り</a>' : ''}
            ${u.role !== 'ADMIN' ? '<a href="/cart_page.php">カート</a>' : ''}
            <a href="#" id="logout-link" class="btn-logout">ログアウト</a>
        `;
        document.getElementById('logout-link').addEventListener('click', e => {
            e.preventDefault();
            clearToken();
            location.href = '/login.php';
        });
    } else {
        nav.innerHTML = `
            <a href="/login.php">ログイン</a>
            <a href="/register.php">会員登録</a>
        `;
    }
}
