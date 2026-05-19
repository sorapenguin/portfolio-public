<?php
require_once __DIR__ . '/../db.php';

/**
 * 管理者情報をユーザー名で取得
 *
 * @param string $user_name
 * @return array|false 管理者情報の連想配列、存在しない場合は false
 */
function getAdminByName(string $user_name)
{
    $dbh = getDB();

    $stmt = $dbh->prepare("SELECT * FROM admin_user_table WHERE user_name = :user_name");
    $stmt->execute([':user_name' => $user_name]);

    return $stmt->fetch(PDO::FETCH_ASSOC);
}