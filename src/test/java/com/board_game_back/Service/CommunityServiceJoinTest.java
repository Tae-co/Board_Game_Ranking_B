package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.CommunityDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 초대코드 참가 — 이미 멤버인지를 응답에 실어야 프론트가 환영 토스트와 참가 이벤트를 거른다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceJoinTest {

    private static final Long COMMUNITY_ID = 9L;
    private static final Long MEMBER_ID = 16L;
    private static final String CODE = "8C3285";

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private RoomService roomService;

    @InjectMocks private CommunityService communityService;

    @BeforeEach
    void setUp() {
        Community community = new Community("금요 보드게임", "South Korea", null, 15L);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        Member member = Member.builder().nickname("친구").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        when(communityRepository.findByInviteCode(CODE)).thenReturn(Optional.of(community));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    void 처음_참가하면_멤버로_저장하고_alreadyMember는_false() {
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(false);

        CommunityDto.JoinResponse res = communityService.joinCommunity(CODE, MEMBER_ID);

        assertThat(res.alreadyMember()).isFalse();
        assertThat(res.communityId()).isEqualTo(COMMUNITY_ID);
        verify(communityMemberRepository).save(any());
    }

    @Test
    void 이미_멤버면_다시_저장하지_않고_alreadyMember는_true() {
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(true);

        CommunityDto.JoinResponse res = communityService.joinCommunity(CODE, MEMBER_ID);

        assertThat(res.alreadyMember()).isTrue();
        assertThat(res.name()).isEqualTo("금요 보드게임");
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void 소문자와_공백이_섞인_코드도_찾는다() {
        when(communityMemberRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, MEMBER_ID)).thenReturn(false);

        CommunityDto.JoinResponse res = communityService.joinCommunity(" 8c3285 ", MEMBER_ID);

        assertThat(res.communityId()).isEqualTo(COMMUNITY_ID);
    }
}
