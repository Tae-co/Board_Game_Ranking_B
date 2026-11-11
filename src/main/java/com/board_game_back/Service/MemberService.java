package com.board_game_back.Service;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.DTO.MemberDto;
import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 회원 조회·프로필 수정·탈퇴 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final int SEARCH_LIMIT = 100;

    private final MemberRepository memberRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final RoomService roomService;
    private final UserEventService userEventService;

    /**
     * 닉네임으로 회원 찾기. 빈 검색어로 전체 회원 명부(memberId+nickname)를 덤프하지 못하게
     * 검색어가 있어야만 결과를 준다. '나간 멤버' 자리 계정은 뺀다.
     */
    public List<MemberDto.SearchItem> searchByNickname(String nickname, Long excludeId) {
        if (nickname.isBlank()) {
            return List.of();
        }
        return memberRepository.findByNicknameContainingIgnoreCase(nickname).stream()
            .filter(m -> excludeId == null || !m.getId().equals(excludeId))
            .filter(m -> !m.isLeftPlaceholder())
            .limit(SEARCH_LIMIT)
            .map(m -> new MemberDto.SearchItem(m.getId(), m.getNickname()))
            .toList();
    }

    public MemberDto.ProfileResponse getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        return new MemberDto.ProfileResponse(
            member.getId(),
            member.getNickname(),
            member.getBestDisplayScore(),
            member.getOverallStats().getRatingDeviation(),
            member.getProfileImage()
        );
    }

    /** 게임별 판수·승·패와 그 합계 (한 판 이상 한 게임만) */
    public MemberDto.StatsResponse getStats(Long memberId) {
        List<PlayerGameRating> ratings = playerGameRatingRepository.findPlayedByMemberId(memberId);
        int totalPlay = ratings.stream().mapToInt(PlayerGameRating::getPlayCount).sum();
        int totalWin = ratings.stream().mapToInt(PlayerGameRating::getWinCount).sum();
        int totalLose = ratings.stream().mapToInt(PlayerGameRating::getLoseCount).sum();
        List<MemberDto.GameStatItem> games = ratings.stream()
            .map(r -> new MemberDto.GameStatItem(
                r.getBoardGame().getName(), r.getPlayCount(), r.getWinCount(), r.getLoseCount()))
            .toList();
        return new MemberDto.StatsResponse(totalPlay, totalWin, totalLose, games);
    }

    public boolean exists(Long memberId) {
        return memberRepository.existsById(memberId);
    }

    /**
     * 회원 탈퇴. member 행이 통째로 사라지므로 user_event가 이탈의 유일한 기록이다.
     * 클래스의 readOnly 트랜잭션에 묶이면 삭제가 반영되지 않으므로 트랜잭션 밖에서 돌린다 —
     * 삭제(RoomService)와 이벤트 기록은 전처럼 각자 트랜잭션으로.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void withdraw(Long memberId) {
        roomService.deleteMember(memberId);
        userEventService.recordServerSide(EventName.MEMBER_DELETED, memberId);
    }

    @Transactional
    public void updateProfileImage(Long memberId, String profileImage) {
        findMember(memberId).updateProfileImage(profileImage);
    }

    /** 닉네임 규칙(NicknameRules) 검사는 호출하는 쪽에서 끝낸 뒤 부른다 */
    @Transactional
    public void updateNickname(Long memberId, String nickname) {
        findMember(memberId).updateNickname(nickname);
    }

    /** 관리자 회원 목록 */
    public List<MemberDto.AdminSummary> getMembersForAdmin() {
        return memberRepository.findAll().stream().map(MemberDto.AdminSummary::from).toList();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
