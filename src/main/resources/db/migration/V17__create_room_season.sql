-- 방별 시즌. 월간 전역 시즌(V16의 season_key)을 대체한다.
-- 기획: docs/plans/plan-season-reset.md §22
--
-- 호스트가 방마다 시즌 이름·종료일을 정하고, 종료일이 지나면 같은 길이로 다음 시즌이 자동 시작된다.
-- end_at은 배타적이다 — 종료일 다음 날 00:00(커뮤니티 region 타임존)을 UTC로 저장한다.
-- name이 NULL이면 화면이 "시즌 N"으로 표시한다 (자동 생성 시즌 이름을 서버 언어로 박지 않으려는 것).

CREATE TABLE IF NOT EXISTS room_season (
    room_season_id BIGSERIAL PRIMARY KEY,
    room_id        BIGINT      NOT NULL,
    season_number  INTEGER     NOT NULL,
    name           VARCHAR(40),
    start_at       TIMESTAMP   NOT NULL,
    end_at         TIMESTAMP   NOT NULL,
    closed_at      TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_room_season_number
    ON room_season (room_id, season_number);

-- 방당 진행 중인 시즌은 하나. 롤오버가 겹쳐 돌아도 두 개가 열리지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_room_season_open
    ON room_season (room_id) WHERE closed_at IS NULL;

-- 스케줄러: 마감할 시즌 찾기
CREATE INDEX IF NOT EXISTS idx_room_season_due
    ON room_season (end_at) WHERE closed_at IS NULL;

ALTER TABLE room_season ENABLE ROW LEVEL SECURITY;

-- ── 기존 월간 스냅샷을 방별 닫힌 시즌으로 옮긴다 ──
-- 운영에는 2026-09 한 시즌(방 16개)이 있다. 월 경계는 KST 1일 00:00이었다 (§22: 경기 있는 방은 전부 한국).

INSERT INTO room_season (room_id, season_number, name, start_at, end_at, closed_at)
SELECT s.room_id,
       ROW_NUMBER() OVER (PARTITION BY s.room_id ORDER BY s.season_key),
       NULL,
       -- 첫 시즌은 도입 전 기간 전체를 덮는다 (소급 마감 금지, §5). 시작은 그 방의 첫 경기.
       COALESCE(
           (SELECT MIN(m.played_at) FROM match_record m WHERE m.room_id = s.room_id),
           ((to_date(s.season_key, 'YYYY-MM')::timestamp) AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC'),
       (((to_date(s.season_key, 'YYYY-MM') + INTERVAL '1 month')::timestamp) AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC',
       MAX(s.created_at)
FROM season_rank_snapshot s
GROUP BY s.room_id, s.season_key;

-- 두 번째 이후 닫힌 시즌의 시작은 직전 시즌의 끝이다.
UPDATE room_season rs
SET start_at = prev.end_at
FROM room_season prev
WHERE prev.room_id = rs.room_id AND prev.season_number = rs.season_number - 1;

ALTER TABLE season_rank_snapshot ADD COLUMN IF NOT EXISTS room_season_id BIGINT;

UPDATE season_rank_snapshot s
SET room_season_id = rs.room_season_id
FROM room_season rs
WHERE rs.room_id = s.room_id
  AND rs.end_at = (((to_date(s.season_key, 'YYYY-MM') + INTERVAL '1 month')::timestamp) AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'UTC';

ALTER TABLE season_rank_snapshot ALTER COLUMN room_season_id SET NOT NULL;
ALTER TABLE season_rank_snapshot ALTER COLUMN season_key DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_season_rank_snapshot_room_season
    ON season_rank_snapshot (room_season_id, board_game_id, member_id);
CREATE INDEX IF NOT EXISTS idx_season_rank_snapshot_room_season
    ON season_rank_snapshot (room_season_id, board_game_id, rank);

-- ── 모든 방에 진행 중 시즌: 2026-10-01부터 4주 (종료일 10/28 → 10/29 00:00 KST 마감) ──
-- 닫힌 시즌이 있는 방은 그 끝에서 이어 시작한다 (재계산이 시즌 시작 이후 경기만 리플레이하므로 빈틈이 없어야 한다).

INSERT INTO room_season (room_id, season_number, name, start_at, end_at)
SELECT r.id,
       COALESCE((SELECT MAX(rs.season_number) FROM room_season rs WHERE rs.room_id = r.id), 0) + 1,
       NULL,
       COALESCE((SELECT MAX(rs.end_at) FROM room_season rs WHERE rs.room_id = r.id),
                TIMESTAMP '2026-09-30 15:00:00'),
       TIMESTAMP '2026-10-28 15:00:00'
FROM room r
WHERE NOT EXISTS (SELECT 1 FROM room_season rs WHERE rs.room_id = r.id AND rs.closed_at IS NULL);
