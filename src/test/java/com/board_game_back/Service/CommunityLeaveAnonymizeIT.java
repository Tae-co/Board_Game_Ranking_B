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

    @Test
    void 나간_사람_경기는_나간_멤버로_남고_재계산해도_남은_멤버_점수는_그대로() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        Member owner = memberRepository.save(Member.builder().nickname("주인" + tag).build());
        Member a = memberRepository.save(Member.builder().nickname("가" + tag).build());
        Member leaver = memberRepository.save(Member.builder().nickname("나" + tag).build());

        Community community = communityRepository.save(new Community("테스트" + tag, "South Korea", null, owner.getId()));
        for (Member m : List.of(owner, a, leaver)) communityMemberRepository.save(new CommunityMember(community, m));

        BoardGame game = boardGameRepository.save(BoardGame.builder()
            .name("테스트게임" + tag).minPlayers(2).maxPlayers(4).ratingType(RatingType.ELO).build());
        Room room = new Room("방" + tag, tag, game.getId());
        room.assignCommunity(community.getId());
        room = roomRepository.save(room);
        roomMemberRepository.save(new RoomMember(room, owner, MemberRole.HOST));
        roomMemberRepository.save(new RoomMember(room, a, MemberRole.MEMBER));
        roomMemberRepository.save(new RoomMember(room, leaver, MemberRole.MEMBER));

        // 나갈 사람이 1등을 많이 해 남은 사람 점수에 영향을 줬다
        int[][] placements = { {3, 2, 1}, {2, 3, 1}, {1, 3, 2} };
        for (int[] p : placements) {
            matchService.recordMatchResult(new MatchDto.ResultRequest(game.getId(), room.getId(), List.of(
                new MatchDto.ParticipantRequest(owner.getId(), p[0], null, null, null),
                new MatchDto.ParticipantRequest(a.getId(), p[1], null, null, null),
                new MatchDto.ParticipantRequest(leaver.getId(), p[2], null, null, null))), owner.getId());
        }
        double ownerBefore = score(owner, game, room);
        double aBefore = score(a, game, room);

        communityService.leaveCommunity(community.getId(), leaver.getId());

        // 경기 기록: 나간 사람 줄이 '나간 멤버' 한 명으로 옮겨졌다
        Participants after = participants(room.getId(), game.getId());
        assertThat(after.ids).doesNotContain(leaver.getId());
        assertThat(after.leftIds).hasSize(1);
        Member ghost = memberRepository.findById(after.leftIds.get(0)).orElseThrow();
        assertThat(ghost.getNickname()).startsWith("나간 멤버 #");
        assertThat(ghost.isLeftPlaceholder()).isTrue();

        // 나간 사람·나간 멤버 모두 랭킹에 없다
        assertThat(ratingRepository.findByMember_IdAndRoom_Id(leaver.getId(), room.getId())).isEmpty();
        assertThat(ratingRepository.findByMember_IdAndRoom_Id(ghost.getId(), room.getId())).isEmpty();

        // 재계산해도 남은 사람 점수가 그대로
        matchService.recalculateAllRatings();
        assertThat(score(owner, game, room)).isEqualTo(ownerBefore);
        assertThat(score(a, game, room)).isEqualTo(aBefore);
        assertThat(ratingRepository.findByMember_IdAndRoom_Id(ghost.getId(), room.getId())).isEmpty();
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
