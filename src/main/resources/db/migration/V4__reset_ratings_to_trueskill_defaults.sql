-- Glicko-2 → TrueSkill 전환: 기존 레이팅 초기화 (매치 히스토리는 유지)
-- 배포 후 POST /api/matches/admin/recalculate-all 을 호출하면 히스토리 기반으로 재계산됨

UPDATE player_game_rating
SET rating           = 25.0,
    rating_deviation = 8.3333,
    volatility       = 0.0,
    play_count       = 0,
    win_count        = 0,
    lose_count       = 0;

UPDATE member
SET overall_rating     = 25.0,
    overall_rd         = 8.3333,
    overall_volatility = 0.0;
