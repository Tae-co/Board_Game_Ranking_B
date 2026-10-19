-- 경기를 점수판에 입력해 저장한 사람. 모임 현황의 "The Scorekeeper"가 센다.
-- 회원 탈퇴는 member 행을 하드 삭제하므로 FK를 두지 않는다 (user_event와 같다).
ALTER TABLE match_record ADD COLUMN IF NOT EXISTS recorded_by_member_id BIGINT;

-- 과거분: 이 컬럼 전에는 기록자를 저장하지 않았다. 행동 로그 MATCH_SUBMITTED(2026-10-02~)가
-- 저장 직후 찍히므로 같은 방·게임에서 played_at 뒤 3분 안의 가장 가까운 경기에 붙인다.
-- 토큰 만료로 member_id 없이 찍힌 로그는 같은 기기(anon_id)의 회원으로 본다 (여러 회원이 쓴 기기는 제외).
-- 그 이전 경기는 알 수 없어 NULL로 남는다.
WITH device AS (
    SELECT anon_id, MIN(member_id) AS member_id FROM user_event
    WHERE member_id IS NOT NULL AND anon_id IS NOT NULL
    GROUP BY anon_id HAVING COUNT(DISTINCT member_id) = 1
),
ev AS (
    SELECT e.id, COALESCE(e.member_id, d.member_id) AS member_id, e.room_id, e.board_game_id, e.created_at
    FROM user_event e
    LEFT JOIN device d ON e.member_id IS NULL AND d.anon_id = e.anon_id
    WHERE e.event_name = 'MATCH_SUBMITTED'
),
nearest AS (
    SELECT DISTINCT ON (ev.id) ev.id, ev.member_id, m.match_record_id, ev.created_at - m.played_at AS gap
    FROM ev
    JOIN match_record m ON m.room_id = ev.room_id AND m.board_game_id = ev.board_game_id
        AND ev.created_at >= m.played_at AND ev.created_at < m.played_at + INTERVAL '3 minutes'
    WHERE ev.member_id IS NOT NULL
    ORDER BY ev.id, gap
),
claimed AS (
    SELECT DISTINCT ON (match_record_id) match_record_id, member_id
    FROM nearest
    ORDER BY match_record_id, gap
)
UPDATE match_record m SET recorded_by_member_id = c.member_id
FROM claimed c
WHERE m.match_record_id = c.match_record_id AND m.recorded_by_member_id IS NULL;
