package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.Member;
import com.board_game_back.Exception.CommunityFullException;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.PlanLimits;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 무료 커뮤니티 인원 한도. **서버가 막는 유일한 한도다.**
 *
 * <p>나머지 한도(게임방·커뮤니티 개수·공동관리자·커스텀 점수판)는 전부 운영자
 * 본인 화면에서만 발동해서 프론트 판정으로 충분하지만, 인원은 <b>다른 사람이
 * 들어오는</b> 동작이라 그 사람 앱을 믿을 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class CommunityJoinLimitTest {

    private static final String CODE = "FULL08";

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private MemberRepository memberRepository;
    // 가입에 성공하면 응답 DTO를 만드느라 이 둘까지 불린다
    @Mock private RoomRepository roomRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private CommunityService communityService;

    private Community 모임;
    private Member 새로운사람;

    @BeforeEach
    void setUp() {
        모임 = new Community("정원 찬 모임", "서울", null, 2L);
        ReflectionTestUtils.setField(모임, "id", 1L);
        모임.assignInviteCode(CODE);

        새로운사람 = Member.builder().nickname("9번째").build();
        ReflectionTestUtils.setField(새로운사람, "id", 99L);
    }

    @Test
    void 정원이_차고_무료면_거절한다() {
        givenJoinAttempt();
        when(communityMemberRepository.countByCommunityId(1L))
            .thenReturn((long) PlanLimits.FREE_MEMBERS);
        when(subscriptionService.isCommunityPro(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> communityService.joinCommunity(CODE, 99L))
            .isInstanceOf(CommunityFullException.class);

        // 거절했으면 아무것도 저장하지 않아야 한다
        verify(communityMemberRepository, never()).save(any(CommunityMember.class));
    }

    /** 프론트가 문구를 만들 수 있도록 코드와 숫자를 실어 보낸다 (기본 언어가 en이라 필요하다). */
    @Test
    void 거절_예외는_인원과_한도를_담는다() {
        givenJoinAttempt();
        when(communityMemberRepository.countByCommunityId(1L)).thenReturn(12L);
        when(subscriptionService.isCommunityPro(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> communityService.joinCommunity(CODE, 99L))
            .isInstanceOfSatisfying(CommunityFullException.class, e -> {
                assertThat(e.getMemberCount()).isEqualTo(12L);
                assertThat(e.getLimit()).isEqualTo(PlanLimits.FREE_MEMBERS);
            });
    }

    @Test
    void 정원이_찼어도_커뮤니티가_Pro면_받아준다() {
        givenJoinAttempt();
        givenResponseBuild();
        when(communityMemberRepository.countByCommunityId(1L))
            .thenReturn((long) PlanLimits.FREE_MEMBERS);
        when(subscriptionService.isCommunityPro(1L, 2L)).thenReturn(true);

        communityService.joinCommunity(CODE, 99L);

        verify(communityMemberRepository).save(any(CommunityMember.class));
    }

    @Test
    void 정원_미만이면_구독을_보지도_않는다() {
        givenJoinAttempt();
        givenResponseBuild();
        when(communityMemberRepository.countByCommunityId(1L))
            .thenReturn((long) PlanLimits.FREE_MEMBERS - 1);

        communityService.joinCommunity(CODE, 99L);

        verify(communityMemberRepository).save(any(CommunityMember.class));
        verify(subscriptionService, never()).isCommunityPro(any(), any());
    }

    /** 이미 멤버면 인원 검사 자체를 하지 않는다 — 재입장이 정원 때문에 막히면 안 된다. */
    @Test
    void 이미_멤버면_정원과_무관하게_통과한다() {
        when(communityRepository.findByInviteCode(CODE)).thenReturn(Optional.of(모임));
        when(memberRepository.findById(99L)).thenReturn(Optional.of(새로운사람));
        when(communityMemberRepository.existsByCommunityIdAndMemberId(1L, 99L)).thenReturn(true);
        givenResponseBuild();

        communityService.joinCommunity(CODE, 99L);

        verify(communityMemberRepository, never()).save(any(CommunityMember.class));
        verify(subscriptionService, never()).isCommunityPro(any(), any());
    }

    /** 성공 경로는 응답 DTO를 만든다 — 집계 조회가 빈 목록이어도 그만이다. */
    private void givenResponseBuild() {
        when(communityMemberRepository.countByCommunityIds(any())).thenReturn(Collections.emptyList());
        when(roomRepository.countByCommunityIds(any())).thenReturn(Collections.emptyList());
        when(communityAdminRepository.findByCommunityIdIn(any())).thenReturn(List.of());
    }

    private void givenJoinAttempt() {
        when(communityRepository.findByInviteCode(CODE)).thenReturn(Optional.of(모임));
        when(memberRepository.findById(99L)).thenReturn(Optional.of(새로운사람));
        when(communityMemberRepository.existsByCommunityIdAndMemberId(1L, 99L)).thenReturn(false);
    }
}
