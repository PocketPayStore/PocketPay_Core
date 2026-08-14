-- ============================================================================
-- V2: 시드 데이터
-- 테스트용 회원 10명(id 1~10, email/password는 test{id}@test.com / test{id})을 심고,
-- 상품/재고 조회용 API를 따로 만들지 않는 대신 상품도 고정 ID(1~10)로 심어서 사용한다.
-- created_at/updated_at은 JPA Auditing이 아니라 이 마이그레이션이 직접 채운다.
-- ============================================================================

INSERT INTO member (id, email, password, name, role, created_at, updated_at, is_deleted)
VALUES (1, 'test1@test.com', 'test1', '테스트유저1', 'USER', NOW(6), NOW(6), FALSE),
       (2, 'test2@test.com', 'test2', '테스트유저2', 'USER', NOW(6), NOW(6), FALSE),
       (3, 'test3@test.com', 'test3', '테스트유저3', 'USER', NOW(6), NOW(6), FALSE),
       (4, 'test4@test.com', 'test4', '테스트유저4', 'USER', NOW(6), NOW(6), FALSE),
       (5, 'test5@test.com', 'test5', '테스트유저5', 'USER', NOW(6), NOW(6), FALSE),
       (6, 'test6@test.com', 'test6', '테스트유저6', 'USER', NOW(6), NOW(6), FALSE),
       (7, 'test7@test.com', 'test7', '테스트유저7', 'USER', NOW(6), NOW(6), FALSE),
       (8, 'test8@test.com', 'test8', '테스트유저8', 'USER', NOW(6), NOW(6), FALSE),
       (9, 'test9@test.com', 'test9', '테스트유저9', 'USER', NOW(6), NOW(6), FALSE),
       (10, 'test10@test.com', 'test10', '테스트유저10', 'USER', NOW(6), NOW(6), FALSE);

-- 위탁판매 구조라 product.seller_id가 필수라, 회원 1~7을 판매자로 분산해서 지정한다.
-- 가격대(1,000 ~ 80,000원)와 재고 수준(1 ~ 500)을 다양하게 잡아서 k6 시나리오 A(정상, 재고 넉넉한 상품)
-- / B(충돌, 재고 희박한 상품 집중)를 따로 겨냥할 수 있게 한다.
INSERT INTO product (id, seller_id, name, price, created_at, updated_at, is_deleted)
VALUES (1, 1, '이상해씨 커먼카드', 1000, NOW(6), NOW(6), FALSE),
       (2, 1, '리자몽 GX 부스터팩', 5000, NOW(6), NOW(6), FALSE),
       (3, 2, '스타터 덱 세트', 12000, NOW(6), NOW(6), FALSE),
       (4, 2, '피카츄 SR 싱글카드', 15000, NOW(6), NOW(6), FALSE),
       (5, 3, '갸라도스 레어카드', 18000, NOW(6), NOW(6), FALSE),
       (6, 3, '나인테일 프로모카드', 22000, NOW(6), NOW(6), FALSE),
       (7, 4, '한정판 컬렉션 박스', 30000, NOW(6), NOW(6), FALSE),
       (8, 5, '부스터박스(10팩입)', 45000, NOW(6), NOW(6), FALSE),
       (9, 6, '뮤츠 EX 싱글카드', 45000, NOW(6), NOW(6), FALSE),
       (10, 7, '골드 스타 레어카드', 80000, NOW(6), NOW(6), FALSE);

-- stock의 PK는 별도 auto_increment id라 여기선 지정하지 않고 product_id만 채운다.
-- 9/10은 재고를 극단적으로 적게 잡아서 단계 5(락 전략) 부하테스트의
-- "동일 상품 집중" 시나리오(B) 대상으로 쓴다. 1/2는 재고가 넉넉해 시나리오 A(정상) 대상이다.
INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at, is_deleted)
VALUES (1, 500, 0, 0, NOW(6), NOW(6), FALSE),
       (2, 100, 0, 0, NOW(6), NOW(6), FALSE),
       (3, 80, 0, 0, NOW(6), NOW(6), FALSE),
       (4, 10, 0, 0, NOW(6), NOW(6), FALSE),
       (5, 30, 0, 0, NOW(6), NOW(6), FALSE),
       (6, 20, 0, 0, NOW(6), NOW(6), FALSE),
       (7, 50, 0, 0, NOW(6), NOW(6), FALSE),
       (8, 15, 0, 0, NOW(6), NOW(6), FALSE),
       (9, 3, 0, 0, NOW(6), NOW(6), FALSE),
       (10, 1, 0, 0, NOW(6), NOW(6), FALSE);
