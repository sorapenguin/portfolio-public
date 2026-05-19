<?php
require_once __DIR__ . '/../db.php';

/**
 * 指定商品の在庫情報を取得
 *
 * @param int $product_id
 * @return array|false 在庫情報の連想配列、存在しない場合は false
 */
function getStockByProductId(int $product_id)
{
    $dbh = getDB();
    $stmt = $dbh->prepare("SELECT * FROM stock_table WHERE product_id = :product_id");
    $stmt->execute([':product_id' => $product_id]);
    return $stmt->fetch(PDO::FETCH_ASSOC);
}

/**
 * 在庫数を更新する
 *
 * @param int $product_id
 * @param int $new_qty
 * @return bool 成功したら true
 */
function updateStock(int $product_id, int $new_qty): bool
{
    $dbh = getDB();
    $stmt = $dbh->prepare("
        UPDATE stock_table SET stock_qty = :qty, update_date = NOW()
        WHERE product_id = :product_id
    ");
    return $stmt->execute([
        ':qty' => $new_qty,
        ':product_id' => $product_id
    ]);
}

/**
 * 在庫を減らす（購入完了時など）
 *
 * @param int $product_id
 * @param int $qty
 * @return bool 成功したら true、在庫不足なら false
 */
function decreaseStock(int $product_id, int $qty): bool
{
    $stock = getStockByProductId($product_id);
    if (!$stock || $stock['stock_qty'] < $qty) {
        return false; // 在庫不足
    }
    $new_qty = $stock['stock_qty'] - $qty;
    return updateStock($product_id, $new_qty);
}

/**
 * 在庫を増やす（必要なら）
 *
 * @param int $product_id
 * @param int $qty
 * @return bool 成功したら true
 */
function increaseStock(int $product_id, int $qty): bool
{
    $stock = getStockByProductId($product_id);
    if (!$stock) return false;
    $new_qty = $stock['stock_qty'] + $qty;
    return updateStock($product_id, $new_qty);
}