-- seed_store_bulk.sql로 넣은 데이터를 지우고 원래 매장 점수·게임 기록 시간을 되돌린다 (로컬 전용)
--   docker exec -i supabase_db_Board_Game_Ranking_B psql -U postgres < seed_store_bulk_cleanup.sql
BEGIN;
CREATE TEMP TABLE seed_ids AS SELECT member_id FROM member WHERE social_id LIKE 'LOCAL_seed%';
CREATE TEMP TABLE seed_matches AS SELECT id, party_id FROM store_match WHERE recorder_member_id IN (SELECT member_id FROM seed_ids);
DELETE FROM store_match_participant WHERE store_match_id IN (SELECT id FROM seed_matches);
DELETE FROM store_match WHERE id IN (SELECT id FROM seed_matches);
DELETE FROM store_party WHERE id IN (SELECT party_id FROM seed_matches);
DELETE FROM store_contest_entry WHERE contest_id IN (SELECT id FROM store_contest WHERE store_id IN (SELECT store_id FROM seed_stores) AND title = '11월 카탄 정기전');
DELETE FROM store_contest WHERE store_id IN (SELECT store_id FROM seed_stores) AND title = '11월 카탄 정기전';
DELETE FROM store_event WHERE store_id IN (SELECT store_id FROM seed_stores) AND title IN ('신규 가입 첫 판 무료', '주말 카탄 챌린지');
DELETE FROM store_rating WHERE store_id IN (SELECT store_id FROM seed_stores);
INSERT INTO store_rating SELECT * FROM seed_backup_store_rating;
UPDATE board_game SET min_minutes = NULL, max_minutes = NULL WHERE board_game_id IN (SELECT board_game_id FROM seed_backup_board_game);
DELETE FROM store_member WHERE member_id IN (SELECT member_id FROM seed_ids);
DELETE FROM user_event WHERE member_id IN (SELECT member_id FROM seed_ids);
DELETE FROM member WHERE member_id IN (SELECT member_id FROM seed_ids);
DROP TABLE seed_backup_store_rating, seed_backup_board_game, seed_stores;
COMMIT;
