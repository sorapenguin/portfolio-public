<?php
return [
    'DB_HOST' => getenv('DB_HOST') ?: 'db',
    'DB_PORT' => getenv('DB_PORT') ?: '5432',
    'DB_NAME' => getenv('DB_NAME') ?: 'ecsitephp',
    'DB_USER' => getenv('DB_USER') ?: 'ec_user',
    'DB_PASS' => getenv('DB_PASS') ?: 'ec_pass',
];
