package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 본인 커뮤니티 나가기 — 소유자는 못 나가고, 멤버는 강퇴와 같은 정리를 거친다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceLeaveTest {

    private static final Long COMMUNITY_ID = 9L;
    private static final Long OWNER_ID = 15L;
    private static final Long MEMBER_ID = 16L;

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private RoomService roomService;
    @Mock private MatchParticipantRepository matchParticipantRepository;
    @Mock private SeasonRankSnapshotRepository seasonRankSnapshotRepository;

    @InjectMocks private CommunityService communityService;

    @BeforeEach
    void setUp() {
        Community community = new Community("금요 보드게임", "South Korea", null, OWNER_ID);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of());
        when(roomRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of());
    }

    @Test
    void 멤버는_나가면_멤버십이_지워진다() {
        CommunityMember cm = Mockito.mock(CommunityMember.class);
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(Optional.of(cm));

        communityService.leaveCommunity(COMMUNITY_ID, MEMBER_ID);

        verify(communityMemberRepository).delete(cm);
    }

    @Test
    void 경기_기록이_없으면_나간_멤버_계정을_만들지_않는다() {
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(true);

        communityService.leaveCommunity(COMMUNITY_ID, MEMBER_ID);

        verify(roomService, never()).createLeftPlaceholder();
    }

    @Test
    void 소유자는_나갈_수_없다() {
        assertThatThrownBy(() -> communityService.leaveCommunity(COMMUNITY_ID, OWNER_ID))
            .isInstanceOf(IllegalStateException.class);
        verify(communityMemberRepository, never()).delete(any());
    }

    @Test
    void 멤버가_아니면_거절() {
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> communityService.leaveCommunity(COMMUNITY_ID, MEMBER_ID))
            .isInstanceOf(IllegalArgumentException.class);
        verify(communityMemberRepository, never()).delete(any());
    }
}
