package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.CommunityDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

/** 커뮤니티 설정 저장 시 어드민 목록·소유권 처리 (2026-10-19 공동 어드민 권한 소실 버그) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceAdminTest {

    private static final Long COMMUNITY_ID = 9L;
    private static final Long OWNER = 52L;
    private static final Long OLD_ADMIN = 11L;   // 소유자 다음으로 오래된 어드민
    private static final Long NEW_ADMIN = 20L;   // 나중에 어드민이 된 사람

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private RoomService roomService;

    @InjectMocks private CommunityService communityService;

    private Community community;
    private List<CommunityAdmin> admins;

    @BeforeEach
    void setUp() {
        community = new Community("Yada Seoul", "South Korea", null, OWNER);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);

        admins = new ArrayList<>(List.of(
            admin(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0)),
            admin(3L, NEW_ADMIN, LocalDateTime.of(2026, 9, 1, 0, 0)),
            admin(2L, OLD_ADMIN, LocalDateTime.of(2026, 6, 1, 0, 0))
        ));

        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityAdminRepository.existsByCommunityIdAndMemberId(any(), any())).thenReturn(true);
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(admins);
    }

    @Test
    void 공동_어드민이_그대로_저장하면_아무도_빠지지_않는다() {
        update(OLD_ADMIN, List.of(OLD_ADMIN, NEW_ADMIN), null);

        verify(communityAdminRepository, never()).delete(any());
        assertThat(community.getCreatedBy()).isEqualTo(OWNER);
    }

    @Test
    void 공동_어드민은_자기_체크를_풀면_자기만_빠진다() {
        update(OLD_ADMIN, List.of(NEW_ADMIN), null);

        verify(communityAdminRepository).delete(adminOf(OLD_ADMIN));
        verify(communityAdminRepository, never()).delete(adminOf(OWNER));
    }

    @Test
    void 공동_어드민은_소유자를_해제할_수_없다() {
        update(OLD_ADMIN, List.of(OLD_ADMIN, NEW_ADMIN), true);

        verify(communityAdminRepository, never()).delete(adminOf(OWNER));
        assertThat(community.getCreatedBy()).isEqualTo(OWNER);
    }

    @Test
    void 소유자가_내려놓으면_가장_오래된_어드민에게_소유권이_넘어간다() {
        update(OWNER, List.of(OLD_ADMIN, NEW_ADMIN), true);

        verify(communityAdminRepository).delete(adminOf(OWNER));
        assertThat(community.getCreatedBy()).isEqualTo(OLD_ADMIN);
    }

    @Test
    void 소유자_혼자면_내려놓을_수_없다() {
        assertThatThrownBy(() -> update(OWNER, List.of(), true))
            .isInstanceOf(IllegalArgumentException.class);

        verify(communityAdminRepository, never()).delete(any());
        assertThat(community.getCreatedBy()).isEqualTo(OWNER);
    }

    @Test
    void 구버전_앱의_소유자_저장은_소유자를_유지한다() {
        // 구버전 앱은 목록에서 자기(소유자)를 빼고 ownerLeavesAdmin을 보내지 않는다
        update(OWNER, List.of(OLD_ADMIN, NEW_ADMIN), null);

        verify(communityAdminRepository, never()).delete(any());
        assertThat(community.getCreatedBy()).isEqualTo(OWNER);
    }

    @Test
    void 공동_어드민은_소유자를_내보낼_수_없다() {
        assertThatThrownBy(() -> communityService.removeCommunityMember(COMMUNITY_ID, OWNER, OLD_ADMIN))
            .isInstanceOf(SecurityException.class);

        verify(communityMemberRepository, never()).delete(any());
        verify(communityAdminRepository, never()).delete(any());
    }

    @Test
    void 어드민을_내보내면_어드민_권한도_빠진다() {
        communityService.removeCommunityMember(COMMUNITY_ID, NEW_ADMIN, OLD_ADMIN);

        verify(communityAdminRepository).delete(adminOf(NEW_ADMIN));
        verify(communityAdminRepository, never()).delete(adminOf(OWNER));
        verify(communityAdminRepository, never()).delete(adminOf(OLD_ADMIN));
    }

    @Test
    void 커뮤니티에서_내보내면_방은_방_나가기_경로로_나간다() {
        Room joined = new Room();
        ReflectionTestUtils.setField(joined, "id", 100L);
        Room notJoined = new Room();
        ReflectionTestUtils.setField(notJoined, "id", 200L);
        when(roomRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(joined, notJoined));
        when(roomMemberRepository.findByRoomIdAndMemberId(100L, NEW_ADMIN)).thenReturn(Optional.of(new RoomMember()));
        when(roomMemberRepository.findByRoomIdAndMemberId(200L, NEW_ADMIN)).thenReturn(Optional.empty());

        communityService.removeCommunityMember(COMMUNITY_ID, NEW_ADMIN, OLD_ADMIN);

        // 방장 이어받기·빈 방 정리는 leaveRoom이 한다
        verify(roomService).leaveRoom(100L, NEW_ADMIN, NEW_ADMIN);
        verify(roomService, never()).leaveRoom(200L, NEW_ADMIN, NEW_ADMIN);
        verify(playerGameRatingRepository).deleteByMember_IdAndRoom_Id(NEW_ADMIN, 200L);
    }

    @Test
    void 커뮤니티를_지우면_방도_지운다() {
        communityService.deleteCommunity(COMMUNITY_ID, OWNER);

        verify(roomService).deleteRoomsOfCommunity(COMMUNITY_ID);
        verify(communityRepository).delete(community);
    }

    private void update(Long requesterId, List<Long> adminMemberIds, Boolean ownerLeavesAdmin) {
        communityService.updateCommunity(COMMUNITY_ID,
            new CommunityDto.UpdateRequest(null, null, null, adminMemberIds, ownerLeavesAdmin), requesterId);
    }

    private CommunityAdmin admin(Long id, Long memberId, LocalDateTime createdAt) {
        Member member = Member.builder().nickname("m" + memberId).build();
        ReflectionTestUtils.setField(member, "id", memberId);
        CommunityAdmin admin = new CommunityAdmin(community, member);
        admin.setId(id);
        admin.setCreatedAt(createdAt);
        return admin;
    }

    private CommunityAdmin adminOf(Long memberId) {
        return admins.stream().filter(a -> a.getMember().getId().equals(memberId)).findFirst().orElseThrow();
    }
}
