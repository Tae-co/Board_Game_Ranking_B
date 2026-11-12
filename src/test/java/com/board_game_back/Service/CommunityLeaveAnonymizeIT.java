package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.MatchParticipant;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.RatingType;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 나가기 — 실제 DB(로컬 Supabase)로 확인한다.
 * 나간 사람의 경기는 '나간 멤버'로 남고, 전체 재계산을 돌려도 남은 멤버 점수가 그대로여야 한다.
 * 테스트 트랜잭션은 끝나면 롤백된다.
 */
@SpringBootTest(properties = "jwt.secret=test-only-secret-not-for-production")
@Transactional
class CommunityLeaveAnonymizeIT {

    @Autowired CommunityService communityService;
    @Autowired MatchService matchService;
    @Autowired MemberRepository memberRepository;
    @Autowired CommunityRepository communityRepository;
    @Autowired CommunityMemberRepository communityMemberRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired BoardGameRepository boardGameRepository;
    @Autowired PlayerGameRatingRepository ratingRepository;
    @Autowired MatchRecordRepository matchRecordRepository;

    @Autowired RoomService roomService;
    @Autowired SeasonService seasonService;

    private Member owner, a, leaver;
    private Community community;
    private BoardGame game;
    private Room room;
    private double ownerBefore, aBefore;

    /** 주인·가·나(나갈 사람) 셋이 한 방에서 3판. 나갈 사람이 1등을 많이 해 남은 사람 점수에 영향을 줬다 */
    private void seed() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        owner = memberRepository.save(Member.builder().nickname("주인" + tag).build());
        a = memberRepository.save(Member.builder().nickname("가" + tag).build());
        leaver = memberRepository.save(Member.builder().nickname("나" + tag).build());

        community = communityRepository.save(new Community("테스트" + tag, "South Korea", null, owner.getId()));
        for (Member m : List.of(owner, a, leaver)) communityMemberRepository.save(new CommunityMember(community, m));

        game = boardGameRepository.save(BoardGame.builder()
            .name("테스트게임" + tag).minPlayers(2).maxPlayers(4).ratingType(RatingType.ELO).build());
        Room r = new Room("방" + tag, tag, game.getId());
        r.assignCommunity(community.getId());
        room = roomRepository.save(r);
        roomMemberRepository.save(new RoomMember(room, owner, MemberRole.HOST));
        roomMemberRepository.save(new RoomMember(room, a, MemberRole.MEMBER));
        roomMemberRepository.save(new RoomMember(room, leaver, MemberRole.MEMBER));

        int[][] placements = { {3, 2, 1}, {2, 3, 1}, {1, 3, 2} };
        for (int[] p : placements) {
            matchService.recordMatchResult(new MatchDto.ResultRequest(game.getId(), room.getId(), List.of(
                new MatchDto.ParticipantRequest(owner.getId(), p[0], null, null, null),
                new MatchDto.ParticipantRequest(a.getId(), p[1], null, null, null),
                new MatchDto.ParticipantRequest(leaver.getId(), p[2], null, null, null))), owner.getId());
        }
        ownerBefore = score(owner, game, room);
        aBefore = score(a, game, room);
    }

    /** 나간 사람 줄이 '나간 멤버' 한 명으로 옮겨졌고, 재계산해도 남은 사람 점수가 그대로 */
    private void assertAnonymizedAndScoresKept() {
        Participants after = participants(room.getId(), game.getId());
        assertThat(after.ids).doesNotContain(leaver.getId());
        assertThat(after.leftIds).hasSize(1);
        Member ghost = memberRepository.findById(after.leftIds.get(0)).orElseThrow();
        assertThat(ghost.getNickname()).isEqualTo("나간 멤버 #" + ghost.getId());
        assertThat(ghost.isLeftPlaceholder()).isTrue();

        assertThat(ratingRepository.findByMember_IdAndRoom_Id(leaver.getId(), room.getId())).isEmpty();
        assertThat(ratingRepository.findByMember_IdAndRoom_Id(ghost.getId(), room.getId())).isEmpty();

        matchService.recalculateAllRatings();
        assertThat(score(owner, game, room)).isEqualTo(ownerBefore);
        assertThat(score(a, game, room)).isEqualTo(aBefore);
        assertThat(ratingRepository.findByMember_IdAndRoom_Id(ghost.getId(), room.getId())).isEmpty();
    }

    @Test
    void 커뮤니티를_나가면_경기는_나간_멤버로_남고_재계산해도_남은_멤버_점수는_그대로() {
        seed();
        communityService.leaveCommunity(community.getId(), leaver.getId());
        assertAnonymizedAndScoresKept();
    }

    @Test
    void 방을_나가면_경기는_나간_멤버로_남고_재계산해도_남은_멤버_점수는_그대로() {
        seed();
        roomService.leaveRoom(room.getId(), leaver.getId(), leaver.getId());
        assertAnonymizedAndScoresKept();
    }

    @Test
    void 나간_멤버는_명예의_전당_상을_받지_않는다() {
        seed();  // 나갈 사람이 3판 중 2판 1등 → 나가기 전에는 최다 승리
        assertThat(seasonService.getStatus(community.getId()).awards())
            .anyMatch(aw -> aw.type().equals("MOST_WINS") && aw.memberId().equals(leaver.getId()));

        communityService.leaveCommunity(community.getId(), leaver.getId());

        assertThat(seasonService.getStatus(community.getId()).awards())
            .allMatch(aw -> memberRepository.findById(aw.memberId()).map(m -> !m.isLeftPlaceholder()).orElse(true))
            .noneMatch(aw -> aw.memberId().equals(leaver.getId()));
    }

    @Test
    void 회원_탈퇴하면_회원은_지워지고_경기는_나간_멤버로_남아_점수는_그대로() {
        seed();
        roomService.deleteMember(leaver.getId());

        assertThat(memberRepository.findById(leaver.getId())).isEmpty();
        assertAnonymizedAndScoresKept();
    }

    @Test
    void 예전에_실명으로_남은_기록도_탈퇴하면_나간_멤버로_옮긴다() {
        seed();
        // 익명화 도입 전에 방을 나간 경우: 방 멤버만 빠지고 경기 줄은 실명으로 남아 있다
        roomMemberRepository.deleteByRoomIdAndMemberId(room.getId(), leaver.getId());
        ratingRepository.deleteByMember_IdAndRoom_Id(leaver.getId(), room.getId());

        roomService.deleteMember(leaver.getId());

        assertThat(memberRepository.findById(leaver.getId())).isEmpty();
        assertAnonymizedAndScoresKept();
    }

    @Test
    void 방을_나갔다_다시_들어오면_옛_경기는_돌아오지_않고_새로_시작() {
        seed();
        roomService.leaveRoom(room.getId(), leaver.getId(), leaver.getId());
        roomMemberRepository.save(new RoomMember(roomRepository.findById(room.getId()).orElseThrow(),
            memberRepository.findById(leaver.getId()).orElseThrow(), MemberRole.MEMBER));

        matchService.recalculateAllRatings();

        assertThat(ratingRepository.findByMember_IdAndRoom_Id(leaver.getId(), room.getId())).isEmpty();
        assertThat(score(owner, game, room)).isEqualTo(ownerBefore);
        assertThat(score(a, game, room)).isEqualTo(aBefore);
    }

    private double score(Member m, BoardGame game, Room room) {
        Member fresh = memberRepository.findById(m.getId()).orElseThrow();
        BoardGame g = boardGameRepository.findById(game.getId()).orElseThrow();
        Room r = roomRepository.findById(room.getId()).orElseThrow();
        return ratingRepository.findByMemberAndBoardGameAndRoom(fresh, g, r).orElseThrow()
            .getGameStats().getDisplayScore();
    }

    private record Participants(List<Long> ids, List<Long> leftIds) {}

    private Participants participants(Long roomId, Long boardGameId) {
        List<Member> members = matchRecordRepository.findByRoomIdAndBoardGameIdWithParticipantsAsc(roomId, boardGameId)
            .stream().flatMap(mr -> mr.getParticipants().stream()).map(MatchParticipant::getMember)
            .collect(Collectors.toList());
        return new Participants(
            members.stream().map(Member::getId).distinct().toList(),
            members.stream().filter(Member::isLeftPlaceholder).map(Member::getId).distinct().toList());
    }
}
