-- 커뮤니티 나가기 테스트용 로컬 데이터 (로컬 DB 전용, 여러 번 돌려도 됨 — 돌릴 때마다 새로 만든다)
--   "나가기 테스트 모임" 주인: 앨리스(2) · 멤버: 캐롤(4, 어드민) · 현우(14) · 태엽(8)
--   카탄 방(방장 캐롤, 4명) 4판 · 스플렌더 방(방장 앨리스, 3명) 3판. 캐롤이 1등을 많이 했다
-- 실행 후 점수는 관리자 재계산(POST /api/matches/admin/recalculate-all)으로 채운다

BEGIN;

-- 이전에 만든 테스트 모임 정리
DELETE FROM match_participant WHERE match_record_id IN (
  SELECT mr.match_record_id FROM match_record mr JOIN room r ON r.id = mr.room_id
  JOIN community c ON c.id = r.community_id WHERE c.name = '나가기 테스트 모임');
DELETE FROM match_record WHERE room_id IN (
  SELECT r.id FROM room r JOIN community c ON c.id = r.community_id WHERE c.name = '나가기 테스트 모임');
DELETE FROM player_game_rating WHERE room_id IN (
  SELECT r.id FROM room r JOIN community c ON c.id = r.community_id WHERE c.name = '나가기 테스트 모임');
DELETE FROM room_member WHERE room_id IN (
  SELECT r.id FROM room r JOIN community c ON c.id = r.community_id WHERE c.name = '나가기 테스트 모임');
DELETE FROM room_season WHERE room_id IN (
  SELECT r.id FROM room r JOIN community c ON c.id = r.community_id WHERE c.name = '나가기 테스트 모임');
DELETE FROM room WHERE community_id IN (SELECT id FROM community WHERE name = '나가기 테스트 모임');
DELETE FROM community_admin WHERE community_id IN (SELECT id FROM community WHERE name = '나가기 테스트 모임');
DELETE FROM community_member WHERE community_id IN (SELECT id FROM community WHERE name = '나가기 테스트 모임');
DELETE FROM community WHERE name = '나가기 테스트 모임';

DO $$
DECLARE
  cid bigint; catan_room bigint; spl_room bigint; mid bigint;
  catan bigint := (SELECT board_game_id FROM board_game WHERE name = 'CATAN' AND community_id IS NULL ORDER BY 1 LIMIT 1);
  splendor bigint := (SELECT board_game_id FROM board_game WHERE name = 'SPLENDOR' AND community_id IS NULL ORDER BY 1 LIMIT 1);
  -- 카탄 4판: {앨리스, 캐롤, 현우, 태엽} 순위
  catan_games int[][] := ARRAY[[2,1,3,4],[3,1,2,4],[1,2,4,3],[4,1,3,2]];
  -- 스플렌더 3판: {앨리스, 캐롤, 현우}
  spl_games int[][] := ARRAY[[2,1,3],[1,3,2],[3,1,2]];
  catan_players bigint[] := ARRAY[2,4,14,8];
  spl_players bigint[] := ARRAY[2,4,14];
  i int; j int;
BEGIN
  INSERT INTO community (name, region, status, created_by, created_at, invite_code)
  VALUES ('나가기 테스트 모임', '서울', 'ACTIVE', 2, now(), 'LVTEST') RETURNING id INTO cid;

  INSERT INTO community_member (community_id, member_id, joined_at)
  SELECT cid, m, now() FROM unnest(ARRAY[2,4,14,8]::bigint[]) m;
  INSERT INTO community_admin (community_id, member_id, created_at)
  SELECT cid, m, now() FROM unnest(ARRAY[2,4]::bigint[]) m;

  INSERT INTO room (name, invite_code, board_game_id, community_id, session_active)
  VALUES ('카탄 방', 'LVCAT1', catan, cid, false) RETURNING id INTO catan_room;
  INSERT INTO room (name, invite_code, board_game_id, community_id, session_active)
  VALUES ('스플렌더 방', 'LVSPL1', splendor, cid, false) RETURNING id INTO spl_room;

  INSERT INTO room_member (room_id, member_id, role) VALUES
    (catan_room, 4, 'HOST'), (catan_room, 2, 'MEMBER'), (catan_room, 14, 'MEMBER'), (catan_room, 8, 'MEMBER'),
    (spl_room, 2, 'HOST'), (spl_room, 4, 'MEMBER'), (spl_room, 14, 'MEMBER');

  FOR i IN 1..array_length(catan_games, 1) LOOP
    INSERT INTO match_record (board_game_id, room_id, played_at, recorded_by_member_id)
    VALUES (catan, catan_room, now() - make_interval(hours => 10 - i), 4) RETURNING match_record_id INTO mid;
    FOR j IN 1..4 LOOP
      INSERT INTO match_participant (match_record_id, member_id, placement, rating_change)
      VALUES (mid, catan_players[j], catan_games[i][j], 0);
    END LOOP;
  END LOOP;

  FOR i IN 1..array_length(spl_games, 1) LOOP
    INSERT INTO match_record (board_game_id, room_id, played_at, recorded_by_member_id)
    VALUES (splendor, spl_room, now() - make_interval(hours => 5 - i), 2) RETURNING match_record_id INTO mid;
    FOR j IN 1..3 LOOP
      INSERT INTO match_participant (match_record_id, member_id, placement, rating_change)
      VALUES (mid, spl_players[j], spl_games[i][j], 0);
    END LOOP;
  END LOOP;
END $$;

COMMIT;
