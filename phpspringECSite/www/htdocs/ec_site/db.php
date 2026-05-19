<?php
$config = require __DIR__ . '/config/const.php';

function getDB() {
    static $dbh = null;
    global $config;

    if ($dbh === null) {
        $dsn = "pgsql:host=" . $config['DB_HOST'] . ";port=" . $config['DB_PORT'] . ";dbname=" . $config['DB_NAME'];
        try {
            $dbh = new PDO($dsn, $config['DB_USER'], $config['DB_PASS'], [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            ]);
        } catch (PDOException $e) {
            error_log("DB接続エラー: " . $e->getMessage());
            echo "データベース接続に失敗しました。";
            exit;
        }
    }

    return $dbh;
}
