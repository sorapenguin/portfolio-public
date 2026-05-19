<?php
session_start();

// 🔐 ログインチェック
if (!isset($_SESSION['user_name'])) {
    http_response_code(403);
    exit('ログインが必要です');
}

// ファイル名取得
$file = $_GET['file'] ?? '';
$file = basename($file);

// 空文字チェック
if (empty($file)) {
    http_response_code(400);
    exit('ファイル名が指定されていません');
}

// 許可ファイル拡張子リスト
$allowed_ext = ['jpg', 'jpeg', 'png', 'gif'];
$ext = strtolower(pathinfo($file, PATHINFO_EXTENSION));
if (!in_array($ext, $allowed_ext)) {
    http_response_code(400);
    exit('不正なファイルです');
}

$path = __DIR__ . '/../uploads/' . $file;

// ファイル存在チェック
if (!file_exists($path)) {
    http_response_code(404);
    exit('画像が見つかりません');
}

// MIMEタイプチェック
$finfo = finfo_open(FILEINFO_MIME_TYPE);
$mime = finfo_file($finfo, $path);
finfo_close($finfo);

$allowed_mime = ['image/jpeg','image/png','image/gif'];
if (!in_array($mime, $allowed_mime)) {
    http_response_code(403);
    exit('不正なファイルです');
}

// 出力
header('Content-Type: ' . $mime);
header('Content-Length: ' . filesize($path));
readfile($path);
exit;