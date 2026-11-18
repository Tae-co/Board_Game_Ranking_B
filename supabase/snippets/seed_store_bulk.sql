-- 로컬 전용: 매장 하나에 화면 확인용 데이터를 많이 넣는다.
--   docker exec -i supabase_db_Board_Game_Ranking_B psql -U postgres -v store=1 < seed_store_bulk.sql
-- 회원 30명(social_id LOCAL_seed*, 매장끼리 같이 씀) + 11월 1~4일 기록된 경기 ~140판 + 이벤트 2 + 모집 중 대회 1.
-- 카탄 말고는 기록 시간이 비어 있어 매장에 안 보이므로 기록 시간도 채운다.
-- 점수는 실제 Elo가 아니라 순위별 단순 가감(1등 +, 꼴등 -, 하한 500)이다. 같은 매장은 두 번 넣지 않는다.
-- 지우기: seed_store_bulk_cleanup.sql
BEGIN;

SELECT set_config('seed.store_id', :'store', false);

-- 넣은 매장과 원래 점수 (cleanup에서 복원)
CREATE TABLE IF NOT EXISTS seed_stores (store_id bigint PRIMARY KEY);
CREATE TABLE IF NOT EXISTS seed_backup_store_rating AS SELECT * FROM store_rating WHERE false;
CREATE TABLE IF NOT EXISTS seed_backup_board_game AS SELECT board_game_id FROM board_game WHERE false;

INSERT INTO seed_backup_board_game
SELECT board_game_id FROM board_game WHERE board_game_id IN (2, 3, 12, 13, 20, 21) AND min_minutes IS NULL;
UPDATE board_game SET min_minutes = 15, max_minutes = 90 WHERE board_game_id IN (2, 3, 12, 13, 20, 21) AND min_minutes IS NULL;

DO $$
DECLARE
  sid      CONSTANT bigint := current_setting('seed.store_id')::bigint;
  names    text[] := ARRAY['민지','서준','하은','도윤','지우','예준','수아','시우','Mina','Jake',
                           '보드게임하는직장인','카탄장인','주사위요정','윤서','지호','채원','건우','다은','Noah','소율',
                           '스플렌더매니아','유진','현준','나연','태민','Sora','은호','라온','밤샘보드러','하린'];
  pool     bigint[];
  seeds    bigint[];
  rooms    bigint[];
  gms      bigint[];
  mins     int[];
  i int; n int; k int; d int; ri int;
  mid bigint; pid bigint; gid bigint; rid bigint; mem bigint;
  players  bigint[];
  bdate date; st timestamp; en timestamp;
  rb float8; ra float8; delta float8;
BEGIN
  IF EXISTS (SELECT 1 FROM seed_stores WHERE store_id = sid) THEN
    RAISE NOTICE 'store % already seeded', sid; RETURN;
  END IF;
  INSERT INTO seed_stores VALUES (sid);
  INSERT INTO seed_backup_store_rating SELECT * FROM store_rating WHERE store_id = sid;

  FOR i IN 1..30 LOOP
    SELECT member_id INTO mem FROM member WHERE social_id = 'LOCAL_seed' || lpad(i::text, 2, '0');
    IF mem IS NULL THEN
      INSERT INTO member (social_id, nickname, role, overall_rating, overall_rd, overall_volatility,
                          best_display_score, created_at, pro_canceled)
      VALUES ('LOCAL_seed' || lpad(i::text, 2, '0'), names[i], 'USER', 500, 0, 0, 500, now() - interval '20 days', false)
      RETURNING member_id INTO mem;
    END IF;
    INSERT INTO store_member (store_id, member_id, consent_version, consented_at, joined_at)
    VALUES (sid, mem, 1, now() - interval '5 days', now() - interval '5 days')
    ON CONFLICT DO NOTHING;
    seeds := seeds || mem;
  END LOOP;
  -- 원래 이 매장 회원(직원 제외)도 섞는다
  SELECT seeds || coalesce(array_agg(sm.member_id), '{}') INTO pool
    FROM store_member sm JOIN member m ON m.member_id = sm.member_id
   WHERE sm.store_id = sid AND m.member_id <> ALL(seeds) AND m.role = 'USER';

  SELECT array_agg(sr.id ORDER BY sr.id), array_agg(sr.board_game_id ORDER BY sr.id), array_agg(bg.min_players ORDER BY sr.id)
    INTO rooms, gms, mins
    FROM store_room sr JOIN board_game bg ON bg.board_game_id = sr.board_game_id
   WHERE sr.store_id = sid AND NOT sr.closed;

  FOR d IN 0..3 LOOP
    bdate := DATE '2026-11-01' + d;
    FOR k IN 1..35 LOOP
      -- 앞쪽 방(카탄 등)이 더 자주 잡히게
      ri := 1 + floor(power(random(), 1.6) * array_length(rooms, 1))::int;
      rid := rooms[ri]; gid := gms[ri];
      n := greatest(mins[ri], 3 + floor(random() * 2)::int);
      -- 기록자는 늘 시드 회원 (cleanup이 recorder로 시드 경기를 찾는다)
      players := ARRAY[seeds[1 + floor(random() * 30)::int]];
      SELECT players || array_agg(x) INTO players FROM (SELECT x FROM unnest(pool) x WHERE x <> players[1] ORDER BY random() LIMIT n - 1) s;

      -- KST 11~22시 시작 → UTC 저장
      -- 오늘 경기는 1시간 넘게 지난 것만 (TV '방금 끝난 경기'에 안 뜨게)
      IF bdate = DATE '2026-11-04' THEN
        st := (now() AT TIME ZONE 'UTC') - interval '3 hours' - (random() * interval '2 hours');
      ELSE
        st := bdate + time '02:00' + (random() * interval '11 hours');
      END IF;
      en := st + (25 + floor(random() * 50)::int) * interval '1 minute';

      INSERT INTO store_party (store_id, code, recorder_member_id, business_date, created_at, disbanded_at)
      VALUES (sid, lpad(floor(random() * 10000)::text, 4, '0'), players[1], bdate, st - interval '5 minutes', en)
      RETURNING id INTO pid;

      INSERT INTO store_match (store_id, store_room_id, board_game_id, party_id, recorder_member_id, status, business_date,
                               min_minutes, max_minutes, started_at, save_deadline, closes_at, first_saved_at,
                               draft_version, ended_at, end_reason)
      VALUES (sid, rid, gid, pid, players[1], 'RECORDED', bdate, 20, 90, st, st + interval '100 minutes',
              st + interval '110 minutes', en - interval '2 minutes', 1, en, 'CONFIRMED')
      RETURNING id INTO mid;

      FOR i IN 1..n LOOP
        mem := players[i];
        SELECT rating INTO rb FROM store_rating WHERE store_id = sid AND board_game_id = gid AND member_id = mem;
        IF rb IS NULL THEN
          rb := 500;
          INSERT INTO store_rating (store_id, board_game_id, member_id, rating, games, wins, last_played_at)
          VALUES (sid, gid, mem, 500, 0, 0, en);
        END IF;
        -- 1등 +100 ~ 꼴등 -100, 약간의 흔들림
        delta := 100.0 * (n + 1 - 2 * i) / (n - 1) + (random() * 20 - 10);
        ra := greatest(500, rb + delta);
        UPDATE store_rating SET rating = ra, games = games + 1, wins = wins + (i = 1)::int, last_played_at = en
         WHERE store_id = sid AND board_game_id = gid AND member_id = mem;
        INSERT INTO store_match_participant (store_match_id, member_id, placement, game_score, confirmed_at, rating_before, rating_after)
        VALUES (mid, mem, i, (n - i) * 3 + 4 + floor(random() * 3), en, rb, ra);
      END LOOP;

      UPDATE store_member SET last_recorded_at = greatest(coalesce(last_recorded_at, en), en), checked_in_at = st - interval '10 minutes'
       WHERE store_id = sid AND member_id = ANY(players);
    END LOOP;
  END LOOP;

  INSERT INTO store_event (store_id, title, benefit_text, starts_on, ends_on, created_at) VALUES
    (sid, '신규 가입 첫 판 무료', '처음 오신 분은 첫 게임 1시간 무료', DATE '2026-11-01', DATE '2026-11-30', now()),
    (sid, '주말 카탄 챌린지', '주말에 카탄 3판 이상 기록하면 음료 쿠폰', DATE '2026-11-01', DATE '2026-11-15', now());

  INSERT INTO store_contest (store_id, store_room_id, board_game_id, title, starts_at, business_date, capacity, rounds,
                             prize_text, status, created_at)
  SELECT sid, sr.id, sr.board_game_id, '11월 카탄 정기전', TIMESTAMP '2026-11-07 10:00', DATE '2026-11-07', 16, 2,
         '우승자 1개월 이용권', 'RECRUITING', now()
    FROM store_room sr WHERE sr.store_id = sid AND sr.board_game_id = 1
  RETURNING id INTO mid;
  INSERT INTO store_contest_entry (contest_id, member_id, applied_at, status)
  SELECT mid, x, now() - (random() * interval '2 days'), 'APPLIED'
    FROM (SELECT x FROM unnest(pool) x ORDER BY random() LIMIT 11) s;
END $$;

COMMIT;
