<?php include 'header.php'; ?>

<div class="auth-wrap"><div class="auth-card">
    <h2>会員登録</h2>
    <div id="msg" class="alert" style="display:none;"></div>
    <form id="reg-form">
        <div class="form-row">
            <label>ユーザー名 <small>(3〜50文字)</small></label>
            <input type="text" id="user_name" minlength="3" maxlength="50" required autofocus>
        </div>
        <div class="form-row">
            <label>パスワード <small>(6文字以上)</small></label>
            <input type="password" id="password" minlength="6" required>
        </div>
        <button type="submit">登録する</button>
    </form>
    <p style="margin-top:16px;"><a href="/login.php">ログインはこちら</a></p>
</div></div>

<script>
(function () {
    if (isLoggedIn()) { location.href = '/product_list.php'; return; }
    const msg = document.getElementById('msg');
    document.getElementById('reg-form').addEventListener('submit', async e => {
        e.preventDefault();
        msg.style.display = 'none';
        try {
            await apiRequest('POST', '/auth/register', {
                userName: document.getElementById('user_name').value,
                password: document.getElementById('password').value
            });
            showAlert(msg, '登録が完了しました。ログインしてください。', 'success');
            setTimeout(() => location.href = '/login.php', 1500);
        } catch (err) {
            showAlert(msg, err.message);
        }
    });
})();
</script>

<?php include 'footer.php'; ?>
