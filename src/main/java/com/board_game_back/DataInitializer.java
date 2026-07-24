package com.board_game_back;

import com.board_game_back.Entity.Community;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Service.MatchService;
import com.board_game_back.Utils.InviteCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final CommunityRepository communityRepository;
    private final PlayerGameRatingRepository ratingRepository;
    private final MatchService matchService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // inviteCode가 없는 커뮤니티에 코드 자동 생성
        List<Community> communities = communityRepository.findAll();
        for (Community community : communities) {
            if (community.getInviteCode() == null || community.getInviteCode().isBlank()) {
                String code;
                do {
                    code = InviteCodeUtil.generate();
                } while (communityRepository.findByInviteCode(code).isPresent());
                community.assignInviteCode(code);
                communityRepository.save(community);
                System.out.println("[DataInitializer] 커뮤니티 '" + community.getName() + "' 초대 코드 생성: " + code);
            }
        }

        // play_count > 0인데 last_played_at이 없는 레코드 → 이전 버그로 미반영된 기록 재계산
        if (ratingRepository.existsByLastPlayedAtIsNullAndPlayCountGreaterThan(0)) {
            System.out.println("[DataInitializer] 미반영 레이팅 감지 → 전체 재계산 시작");
            matchService.recalculateAllRatings();
            System.out.println("[DataInitializer] 레이팅 재계산 완료");
        }
    }
}
