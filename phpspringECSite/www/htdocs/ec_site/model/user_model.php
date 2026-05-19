<?php
require_once __DIR__ . '/../db.php';

/**
 * ユーザー名で情報取得
 *
 * @param string $user_name
 * @return array|false ユーザー情報の連想配列、存在しなければ false
 */
function getUserByName(string $user_name)
{
    $dbh = getDB();
    $stmt = $dbh->prepare("SELECT * FROM user_table WHERE user_name = :user_name");
    $stmt->execute([':user_name' => $user_name]);
    return $stmt->fetch(PDO::FETCH_ASSOC);
}

/**
 * ユーザーIDで情報取得
 *
 * @param int $user_id
 * @return array|false ユーザー情報の連想配列、存在しなければ false
 */
function getUserById(int $user_id)
{
    $dbh = getDB();
    $stmt = $dbh->prepare("SELECT * FROM user_table WHERE user_id = :user_id");
    $stmt->execute([':user_id' => $user_id]);
    return $stmt->fetch(PDO::FETCH_ASSOC);
}

/**
 * セッションによるログインチェック
 *
 * @return bool
 */
function checkLogin(): bool
{
    return isset($_SESSION['user_name']);
}

/**
 * 管理者判定
 *
 * @return bool
 */
function isAdmin(): bool
{
    return isset($_SESSION['user_name']) && $_SESSION['user_name'] === 'ec_admin';
}

/**
 * ユーザー登録
 *
 * @param string $user_name
 * @param string $hashed_password ハッシュ済みパスワード
 * @return bool 登録成功で true、重複などで false
 */
function registerUser(string $user_name, string $hashed_password): bool
{
    $dbh = getDB();
    
    // 既存チェック
    $stmt = $dbh->prepare("SELECT * FROM user_table WHERE user_name = :user_name");
    $stmt->execute([':user_name' => $user_name]);
    if ($stmt->fetch(PDO::FETCH_ASSOC)) {
        return false; // ユーザー名重複
    }

    // 登録
    $stmt2 = $dbh->prepare("
        INSERT INTO user_table (user_name, password, create_date, update_date)
        VALUES (:user_name, :password, NOW(), NOW())
    ");
    $stmt2->execute([
        ':user_name' => $user_name,
        ':password' => $hashed_password
    ]);

    return true;
}

/**
 * パスワードチェック
 *
 * @param string $user_name
 * @param string $password 生パスワード
 * @return bool
 */
function checkPassword(string $user_name, string $password): bool
{
    $user = getUserByName($user_name);
    if ($user && password_verify($password, $user['password'])) {
        return true;
    }
    return false;
}