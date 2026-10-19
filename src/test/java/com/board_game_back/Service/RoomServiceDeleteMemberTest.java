package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 회원 탈퇴 시 소유한 커뮤니티 처리 — 가장 오래된 어드민, 없으면 경기를 가장 많이 한 멤버에게 넘긴다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceDeleteMemberTest {

    private static final Long COMMUNITY_ID = 9L;
    private static final Long OWNER = 52L;
    private static final Long OLD_ADMIN = 11L;
    private static final Long NEW_ADMIN = 20L;

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private MatchParticipantRepository matchParticipantRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private CommunityRepository communityRepository;

    @InjectMocks private RoomService roomService;

    private Community community;

    @BeforeEach
    void setUp() {
        community = new Community("Yada Seoul", "South Korea", null, OWNER);
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        when(communityRepository.findAllByCreatedBy(OWNER)).thenReturn(List.of(community));
    }

    @Test
    void 소유자가_탈퇴하면_가장_오래된_어드민에게_소유권이_넘어간다() {
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            admin(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0)),
            admin(3L, NEW_ADMIN, LocalDateTime.of(2026, 9, 1, 0, 0)),
            admin(2L, OLD_ADMIN, LocalDateTime.of(2026, 6, 1, 0, 0))
        ));

        roomService.deleteMember(OWNER);

        assertThat(community.getCreatedBy()).isEqualTo(OLD_ADMIN);
        verify(communityRepository, never()).delete(community);
        verify(communityAdminRepository, never()).deleteByCommunityId(COMMUNITY_ID);
        verify(communityAdminRepository).deleteByMemberId(OWNER);
    }

    @Test
    void 어드민이_혼자면_경기를_가장_많이_한_멤버가_어드민과_소유권을_받는다() {
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            admin(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0))
        ));
        when(communityMemberRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            member(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0)),
            member(2L, OLD_ADMIN, LocalDateTime.of(2026, 6, 1, 0, 0)),
            member(3L, NEW_ADMIN, LocalDateTime.of(2026, 9, 1, 0, 0))
        ));
        when(matchParticipantRepository.countByMemberInCommunity(COMMUNITY_ID)).thenReturn(List.of(
            new Object[]{OLD_ADMIN, 4L}, new Object[]{NEW_ADMIN, 7L}
        ));

        roomService.deleteMember(OWNER);

        assertThat(community.getCreatedBy()).isEqualTo(NEW_ADMIN);
        ArgumentCaptor<CommunityAdmin> saved = ArgumentCaptor.forClass(CommunityAdmin.class);
        verify(communityAdminRepository).save(saved.capture());
        assertThat(saved.getValue().getMember().getId()).isEqualTo(NEW_ADMIN);
        verify(communityRepository, never()).delete(community);
    }

    @Test
    void 경기_수가_같으면_먼저_가입한_멤버가_받는다() {
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            admin(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0))
        ));
        when(communityMemberRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            member(3L, NEW_ADMIN, LocalDateTime.of(2026, 9, 1, 0, 0)),
            member(2L, OLD_ADMIN, LocalDateTime.of(2026, 6, 1, 0, 0))
        ));

        roomService.deleteMember(OWNER);

        assertThat(community.getCreatedBy()).isEqualTo(OLD_ADMIN);
    }

    @Test
    void 남은_멤버가_없으면_커뮤니티를_지운다() {
        when(communityAdminRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            admin(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0))
        ));
        when(communityMemberRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(
            member(1L, OWNER, LocalDateTime.of(2026, 5, 9, 0, 0))
        ));

        Room room = new Room();
        room.assignCommunity(COMMUNITY_ID);
        when(roomRepository.findByCommunityId(COMMUNITY_ID)).thenReturn(List.of(room));

        roomService.deleteMember(OWNER);

        assertThat(room.getCommunityId()).isNull();
        verify(communityRepository).delete(community);
        verify(communityAdminRepository).deleteByCommunityId(COMMUNITY_ID);
        verify(communityMemberRepository).deleteByCommunityId(COMMUNITY_ID);
    }

    private CommunityMember member(Long id, Long memberId, LocalDateTime joinedAt) {
        Member member = Member.builder().nickname("m" + memberId).build();
        ReflectionTestUtils.setField(member, "id", memberId);
        CommunityMember cm = new CommunityMember(community, member);
        ReflectionTestUtils.setField(cm, "id", id);
        ReflectionTestUtils.setField(cm, "joinedAt", joinedAt);
        return cm;
    }

    private CommunityAdmin admin(Long id, Long memberId, LocalDateTime createdAt) {
        Member member = Member.builder().nickname("m" + memberId).build();
        ReflectionTestUtils.setField(member, "id", memberId);
        CommunityAdmin admin = new CommunityAdmin(community, member);
        admin.setId(id);
        admin.setCreatedAt(createdAt);
        return admin;
    }
}
