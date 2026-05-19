<?php include 'header.php'; ?>

<div class="auth-wrap"><div class="auth-card">
    <h2>管理者認証</h2>
    <p>6桁の認証コードを入力してください。</p>

    <div id="dev-code-box" class="code-box" style="display:none;">
        <span style="font-size:0.75rem;color:var(--muted);letter-spacing:0;display:block;margin-bottom:8px;">🔧 開発用 認証コード</span>
        <span id="dev-code-digits" class="code-digits"></span>
        <span class="code-expiry">（自動入力済み）</span>
    </div>

    <div id="msg" class="alert" style="display:none;"></div>
    <form id="verify-form">
        <div class="form-row">
            <label>認証コード</label>
            <input type="text" id="auth_code" maxlength="6" pattern="\d{6}" placeholder="000000" required autofocus
                   style="letter-spacing:8px;font-size:1.4rem;text-align:center;max-width:200px;">
        </div>
        <button type="submit">認証する</button>
    </form>
</div></div>

<script>
(function () {
    const sessionKey = sessionStorage.getItem('2fa_session');
    if (!sessionKey) { location.href = '/login.php'; return; }

    const devCode = sessionStorage.getItem('2fa_dev_code');
    if (devCode) {
        document.getElementById('auth_code').value = devCode;
        document.getElementById('dev-code-digits').textContent = devCode;
        document.getElementById('dev-code-box').style.display = '';
        sessionStorage.removeItem('2fa_dev_code');
    }

    const msg = document.getElementById('msg');
    document.getElementById('verify-form').addEventListener('submit', async e => {
        e.preventDefault();
        msg.style.display = 'none';
        try {
            const res = await apiRequest('POST', '/auth/verify-2fa', {
                sessionKey,
                code: document.getElementById('auth_code').value
            });
            sessionStorage.removeItem('2fa_session');
            setToken(res.token);
            location.href = '/product_manage.php';
        } catch (err) {
            showAlert(msg, err.message);
        }
    });
})();
</script>

<?php include 'footer.php'; ?>
