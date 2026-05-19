<?php
require_once __DIR__ . '/../db.php';

/**
 * 公開商品一覧取得
 *
 * @return array
 */
function getPublicProducts(): array
{
    $dbh = getDB();
    $stmt = $dbh->query("
        SELECT p.*, s.stock_qty, i.image_name
        FROM product_table p
        LEFT JOIN stock_table s ON p.product_id = s.product_id
        LEFT JOIN image_table i ON p.product_id = i.product_id
        WHERE p.public_flg = 1
    ");
    return $stmt->fetchAll(PDO::FETCH_ASSOC);
}

/**
 * 管理者用商品一覧取得
 *
 * @return array
 */
function getAllProducts(): array
{
    $dbh = getDB();
    $stmt = $dbh->query("
        SELECT p.*, s.stock_qty, i.image_name
        FROM product_table p
        LEFT JOIN stock_table s ON p.product_id = s.product_id
        LEFT JOIN image_table i ON p.product_id = i.product_id
    ");
    return $stmt->fetchAll(PDO::FETCH_ASSOC);
}

/**
 * 商品追加
 *
 * @param string $name
 * @param float|int $price
 * @param int $stock_qty
 * @param int $public_flg
 * @param string $image_name
 * @return void
 * @throws Exception
 */
function addProduct(string $name, int $price, int $stock_qty, int $public_flg, string $image_name): void
{
    $dbh = getDB();
    $dbh->beginTransaction();
    try {
        $stmt = $dbh->prepare("
            INSERT INTO product_table (product_name, price, public_flg, create_date, update_date)
            VALUES (:name, :price, :public_flg, NOW(), NOW())
            RETURNING product_id
        ");
        $stmt->execute([
            ':name' => $name,
            ':price' => $price,
            ':public_flg' => $public_flg
        ]);

        $product_id = (int)$stmt->fetchColumn();

        $stmt2 = $dbh->prepare("
            INSERT INTO stock_table (product_id, stock_qty, create_date, update_date)
            VALUES (:product_id, :stock_qty, NOW(), NOW())
        ");
        $stmt2->execute([
            ':product_id' => $product_id,
            ':stock_qty' => $stock_qty
        ]);

        $stmt3 = $dbh->prepare("
            INSERT INTO image_table (product_id, image_name, create_date, update_date)
            VALUES (:product_id, :image_name, NOW(), NOW())
        ");
        $stmt3->execute([
            ':product_id' => $product_id,
            ':image_name' => $image_name
        ]);

        $dbh->commit();
    } catch (Exception $e) {
        $dbh->rollBack();
        throw $e;
    }
}

/**
 * 公開フラグ更新
 *
 * @param int $product_id
 * @param int $public_flg
 * @return void
 */
function updatePublicFlg(int $product_id, int $public_flg): void
{
    $dbh = getDB();
    $stmt = $dbh->prepare("
        UPDATE product_table SET public_flg = :public_flg, update_date = NOW()
        WHERE product_id = :product_id
    ");
    $stmt->execute([
        ':public_flg' => $public_flg,
        ':product_id' => $product_id
    ]);
}

/**
 * 商品削除
 *
 * @param int $product_id
 * @return void
 * @throws Exception
 */
function deleteProduct(int $product_id): void
{
    $dbh = getDB();
    $dbh->beginTransaction();
    try {
        $stmt1 = $dbh->prepare("DELETE FROM image_table WHERE product_id = :product_id");
        $stmt1->execute([':product_id' => $product_id]);

        $stmt2 = $dbh->prepare("DELETE FROM stock_table WHERE product_id = :product_id");
        $stmt2->execute([':product_id' => $product_id]);

        $stmt3 = $dbh->prepare("DELETE FROM product_table WHERE product_id = :product_id");
        $stmt3->execute([':product_id' => $product_id]);

        $dbh->commit();
    } catch (Exception $e) {
        $dbh->rollBack();
        throw $e;
    }
}