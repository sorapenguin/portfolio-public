-- ============================================================
-- V1__init.sql  –  initial schema for phpspringECSite EC API
-- DDL_AUTO=validate + Flyway 移行後はこのファイルがスキーマの唯一の正とする。
-- ============================================================

-- ── 依存なしテーブル ──────────────────────────────────────────

CREATE TABLE user_table (
    user_id     BIGSERIAL    PRIMARY KEY,
    user_name   VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    create_date DATE,
    update_date DATE
);

CREATE TABLE admin_table (
    admin_id    BIGSERIAL    PRIMARY KEY,
    user_name   VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    create_date DATE,
    update_date DATE
);

CREATE TABLE category_table (
    category_id   BIGSERIAL    PRIMARY KEY,
    category_name VARCHAR(255) NOT NULL UNIQUE,
    create_date   DATE,
    update_date   DATE
);

-- ── product_table（category_table に依存） ────────────────────

CREATE TABLE product_table (
    product_id   BIGSERIAL    PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    price        INTEGER      NOT NULL,
    public_flg   INTEGER      NOT NULL,
    deleted_flg  INTEGER      DEFAULT 0,
    description  TEXT,
    category_id  BIGINT       REFERENCES category_table(category_id),
    create_date  DATE,
    update_date  DATE
);

CREATE INDEX idx_product_category_id    ON product_table (category_id);
CREATE INDEX idx_product_public_deleted ON product_table (public_flg, deleted_flg);

-- ── product_table に依存するテーブル ─────────────────────────

CREATE TABLE image_table (
    image_id    BIGSERIAL    PRIMARY KEY,
    product_id  BIGINT       UNIQUE REFERENCES product_table(product_id),
    image_name  VARCHAR(255),
    create_date DATE,
    update_date DATE
);

CREATE TABLE stock_table (
    stock_id    BIGSERIAL PRIMARY KEY,
    product_id  BIGINT    UNIQUE REFERENCES product_table(product_id),
    -- @Version による楽観的ロック。Hibernate が INSERT 時に 0 をセットするため DEFAULT 0 を付与。
    version     BIGINT    NOT NULL DEFAULT 0,
    stock_qty   INTEGER   NOT NULL,
    create_date DATE,
    update_date DATE
);

-- ── 注文・カート系（plain FK カラム、JPA レベルの関連なし） ──

CREATE TABLE cart_table (
    cart_id     BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    product_id  BIGINT    NOT NULL,
    product_qty INTEGER   NOT NULL,
    create_date DATE,
    update_date DATE
);

CREATE INDEX idx_cart_user_id      ON cart_table (user_id);
CREATE INDEX idx_cart_user_product ON cart_table (user_id, product_id);

CREATE TABLE order_table (
    order_id    BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    total_price INTEGER      NOT NULL,
    status      VARCHAR(255) NOT NULL,
    create_date DATE,
    update_date DATE
);

CREATE INDEX idx_order_user_id ON order_table (user_id);

CREATE TABLE order_item_table (
    order_item_id BIGSERIAL    PRIMARY KEY,
    order_id      BIGINT       NOT NULL,
    product_id    BIGINT       NOT NULL,
    product_name  VARCHAR(255) NOT NULL,
    price         INTEGER      NOT NULL,
    qty           INTEGER      NOT NULL,
    image_name    VARCHAR(255),
    create_date   DATE,
    update_date   DATE
);

CREATE INDEX idx_order_item_order_id ON order_item_table (order_id);

-- ── お気に入り ───────────────────────────────────────────────

CREATE TABLE favorite_table (
    favorite_id BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    product_id  BIGINT    NOT NULL,
    create_date DATE,
    CONSTRAINT uq_favorite_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_favorite_user_id ON favorite_table (user_id);

-- ── クーポン ─────────────────────────────────────────────────

CREATE TABLE coupon_table (
    coupon_id     BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(255) NOT NULL UNIQUE,
    discount_rate INTEGER      NOT NULL,
    expires_at    DATE         NOT NULL,
    is_used       BOOLEAN      NOT NULL DEFAULT FALSE,
    create_date   DATE,
    update_date   DATE
);

-- ── 商品変更履歴 ──────────────────────────────────────────────

CREATE TABLE product_history (
    history_id   BIGSERIAL    PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    operation    VARCHAR(255) NOT NULL,
    changed_at   TIMESTAMP    NOT NULL,
    product_name VARCHAR(255),
    price        INTEGER,
    description  TEXT,
    category     VARCHAR(255),
    stock_qty    INTEGER,
    public_flg   INTEGER,
    deleted_flg  INTEGER
);
