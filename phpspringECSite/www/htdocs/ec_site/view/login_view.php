<?php include 'header.php'; ?>

<div class="auth-wrap"><div class="auth-card">
    <h2>ログイン</h2>

    <div class="portfolio-demo">
        <p class="portfolio-label">ポートフォリオ閲覧用 デモログイン</p>
        <div class="portfolio-btns">
            <button type="button" class="btn-demo-admin" id="demo-admin">
                管理者としてログイン<br><small style="font-weight:400;font-size:0.78rem;">ec_admin / ec_admin</small>
            </button>
            <button type="button" class="btn-demo-user" id="demo-user">
                一般ユーザーとしてログイン<br><small style="font-weight:400;font-size:0.78rem;">ec_user / password123</small>
            </button>
        </div>
    </div>

    <div id="msg" class="alert" style="display:none;"></div>
    <form id="login-form">
        <div class="form-row">
            <label>ユーザー名</label>
            <input type="text" id="user_name" required autofocus>
        </div>
        <div class="form-row">
            <label>パスワード</label>
            <input type="password" id="password" required>
        </div>
        <button type="submit">ログイン</button>
    </form>
    <p style="margin-top:16px;">アカウントをお持ちでない方は<a href="/register.php">こちら</a></p>
</div></div>

<script>
(function () {
    if (isLoggedIn()) {
        location.href = isAdmin() ? '/product_manage.php' : '/product_list.php';
        return;
    }
    const msg = document.getElementById('msg');

    async function doLogin(userName, password) {
        msg.style.display = 'none';
        try {
            const res = await apiRequest('POST', '/auth/login', { userName, password });
            if (res.status === '2FA_REQUIRED') {
                sessionStorage.setItem('2fa_session', res.sessionKey);
                if (res.devCode) sessionStorage.setItem('2fa_dev_code', res.devCode);
                location.href = '/admin_auth.php';
            } else {
                setToken(res.token);
                location.href = res.role === 'ADMIN' ? '/product_manage.php' : '/product_list.php';
            }
        } catch (err) {
            showAlert(msg, err.message);
        }
    }

    document.getElementById('login-form').addEventListener('submit', e => {
        e.preventDefault();
        doLogin(
            document.getElementById('user_name').value,
            document.getElementById('password').value
        );
    });

    document.getElementById('demo-admin').addEventListener('click', () => {
        doLogin('ec_admin', 'ec_admin');
    });

    document.getElementById('demo-user').addEventListener('click', () => {
        doLogin('ec_user', 'password123');
    });
})();
</script>

<?php include 'footer.php'; ?>
