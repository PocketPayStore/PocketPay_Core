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

INSERT INTO point_balance (member_id, balance, created_at, updated_at, is_deleted)
VALUES (1, 100000, NOW(6), NOW(6), FALSE),
       (2, 100000, NOW(6), NOW(6), FALSE),
       (3, 100000, NOW(6), NOW(6), FALSE),
       (4, 100000, NOW(6), NOW(6), FALSE),
       (5, 100000, NOW(6), NOW(6), FALSE),
       (6, 100000, NOW(6), NOW(6), FALSE),
       (7, 100000, NOW(6), NOW(6), FALSE),
       (8, 100000, NOW(6), NOW(6), FALSE),
       (9, 100000, NOW(6), NOW(6), FALSE),
       (10, 100000, NOW(6), NOW(6), FALSE);

INSERT INTO vendor (id, name, created_at, updated_at, is_deleted)
VALUES (1, '포켓카드 유통', NOW(6), NOW(6), FALSE),
       (2, '트레이너즈 카드샵', NOW(6), NOW(6), FALSE),
       (3, '레어카드 컬렉션', NOW(6), NOW(6), FALSE);

INSERT INTO product (id, vendor_id, name, price, created_at, updated_at, is_deleted)
VALUES (1, 1, '이상해씨 커먼카드', 1000, NOW(6), NOW(6), FALSE),
       (2, 1, '리자몽 GX 부스터팩', 5000, NOW(6), NOW(6), FALSE),
       (3, 1, '스타터 덱 세트', 12000, NOW(6), NOW(6), FALSE),
       (4, 2, '피카츄 SR 싱글카드', 15000, NOW(6), NOW(6), FALSE),
       (5, 2, '갸라도스 레어카드', 18000, NOW(6), NOW(6), FALSE),
       (6, 2, '나인테일 프로모카드', 22000, NOW(6), NOW(6), FALSE),
       (7, 2, '한정판 컬렉션 박스', 30000, NOW(6), NOW(6), FALSE),
       (8, 3, '부스터박스(10팩입)', 45000, NOW(6), NOW(6), FALSE),
       (9, 3, '뮤츠 EX 싱글카드', 45000, NOW(6), NOW(6), FALSE),
       (10, 3, '골드 스타 레어카드', 80000, NOW(6), NOW(6), FALSE);

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
