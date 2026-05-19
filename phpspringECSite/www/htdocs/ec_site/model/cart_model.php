<?php
require_once __DIR__ . '/../db.php';

/**
 * カートに商品追加
 * すでに存在する場合は数量+1
 *
 * @param int $user_id
 * @param int $product_id
 * @return void
 */
function addToCart(int $user_id, int $product_id): void
{
    $dbh = getDB();

    // すでにカートにあれば数量+1
    $stmt = $dbh->prepare("SELECT * FROM cart_table WHERE user_id = :user_id AND product_id = :product_id");
    $stmt->execute([
        ':user_id' => $user_id,
        ':product_id' => $product_id
    ]);
    $item = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($item) {
        $stmt = $dbh->prepare("UPDATE cart_table SET product_qty = product_qty + 1, update_date = NOW() WHERE cart_id = :cart_id");
        $stmt->execute([':cart_id' => $item['cart_id']]);
    } else {
        $stmt = $dbh->prepare("
            INSERT INTO cart_table (user_id, product_id, product_qty, create_date, update_date)
            VALUES (:user_id, :product_id, 1, NOW(), NOW())
        ");
        $stmt->execute([
            ':user_id' => $user_id,
            ':product_id' => $product_id
        ]);
    }
}

/**
 * ユーザーのカート情報取得
 *
 * @param int $user_id
 * @return array
 */
function getCartItems(int $user_id): array
{
    $dbh = getDB();
    $stmt = $dbh->prepare("
        SELECT c.*, p.product_name, p.price, i.image_name, s.stock_qty
        FROM cart_table c
        JOIN product_table p ON c.product_id = p.product_id
        LEFT JOIN image_table i ON p.product_id = i.product_id
        LEFT JOIN stock_table s ON p.product_id = s.product_id
        WHERE c.user_id = :user_id
    ");
    $stmt->execute([':user_id' => $user_id]);
    return $stmt->fetchAll(PDO::FETCH_ASSOC);
}

/**
 * カート内商品削除
 *
 * @param int $cart_id
 * @return void
 */
function removeCartItem(int $cart_id): void
{
    $dbh = getDB();
    $stmt = $dbh->prepare("DELETE FROM cart_table WHERE cart_id = :cart_id");
    $stmt->execute([':cart_id' => $cart_id]);
}

/**
 * カート数量変更
 *
 * @param int $cart_id
 * @param int $qty
 * @return void
 */
function updateCartQty(int $cart_id, int $qty): void
{
    $dbh = getDB();
    $stmt = $dbh->prepare("UPDATE cart_table SET product_qty = :qty, update_date = NOW() WHERE cart_id = :cart_id");
    $stmt->execute([
        ':qty' => $qty,
        ':cart_id' => $cart_id
    ]);
}