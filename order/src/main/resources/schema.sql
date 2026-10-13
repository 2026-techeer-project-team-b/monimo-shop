-- 주문 표. 앱이 뜰 때마다 실행되므로 IF NOT EXISTS 필수 (spring.sql.init.mode=always)
CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(64)  NOT NULL,
    quantity    INT          NOT NULL,
    amount      INT          NOT NULL,
    status      VARCHAR(16)  NOT NULL,   -- PENDING · PAID · FAILED · SOLD_OUT
    payment_id  VARCHAR(64)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
