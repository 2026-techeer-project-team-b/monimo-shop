-- 재고 표. 앱이 뜰 때마다 실행되므로 IF NOT EXISTS · INSERT IGNORE 필수 (spring.sql.init.mode=always)
CREATE TABLE IF NOT EXISTS stock (
    product_id  VARCHAR(64) NOT NULL PRIMARY KEY,
    qty         INT         NOT NULL,
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

-- 주문별 차감 기록. order_id 가 PK 라 같은 주문이 두 번 차감되지 않고, restored 로 두 번 복원되지 않는다
CREATE TABLE IF NOT EXISTS stock_deductions (
    order_id    BIGINT      NOT NULL PRIMARY KEY,
    product_id  VARCHAR(64) NOT NULL,
    quantity    INT         NOT NULL,
    restored    TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

-- 초기 재고. 이미 있으면 건드리지 않는다 (재시작해도 줄어든 재고가 되돌아가지 않게). 처음부터 다시 하려면 compose down -v
-- P-100 은 k6 가 주문하는 상품, P-SOLDOUT 은 품절(409) 시험용
INSERT IGNORE INTO stock (product_id, qty) VALUES ('P-100', 1000000), ('P-SOLDOUT', 0);
