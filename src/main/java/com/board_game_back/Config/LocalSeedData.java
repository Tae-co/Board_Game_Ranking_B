package com.board_game_back.Config;

import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Utils.InviteCodeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 시드 데이터. {@code @Profile("local")}이라 운영에서는 절대 실행되지 않는다.
 *
 * <p>새로 만든 로컬 DB는 {@link com.board_game_back.DataInitializer}가 만드는 admin 계정만
 * 있고 보드게임·커뮤니티·방이 전부 비어 있어 기능 테스트가 불가능하다. 여기서 최소한의
 * 테스트 데이터를 채운다.
 *
 * <p>멱등하다 — 테스트 회원이 이미 있으면 통째로 건너뛴다. 두 번 띄워도 중복 생성되지 않는다.
 *
 * <p>보드게임 이름은 점수판 스키마 이름(CATAN/UNO/SPLENDOR)과 맞췄다. 프론트 ScoreSheet가
 * boardGameId로 스키마를 못 찾으면 게임 이름으로 매칭하므로, 이 이름이면 점수판이 뜬다.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedData implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final BoardGameRepository boardGameRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 이미 시드가 있으면 건너뛴다 (멱등)
        if (memberRepository.findBySocialId("LOCAL_test1").isPresent()) {
            return;
        }

        // 1. 공식 보드게임 (communityId=null → 모두에게 보임)
        BoardGame catan = boardGameRepository.save(BoardGame.builder()
            .name("CATAN").minPlayers(3).maxPlayers(4).build());
        boardGameRepository.save(BoardGame.builder()
            .name("UNO").minPlayers(2).maxPlayers(10).build());
        boardGameRepository.save(BoardGame.builder()
            .name("SPLENDOR").minPlayers(2).maxPlayers(4).build());

        // 2. 테스트 회원 (소셜 로그인 없이 DB에 직접 존재)
        Member alice = memberRepository.save(Member.builder()
            .socialId("LOCAL_test1").nickname("앨리스").role("USER").build());
        Member bob = memberRepository.save(Member.builder()
            .socialId("LOCAL_test2").nickname("밥").role("USER").build());
        Member carol = memberRepository.save(Member.builder()
            .socialId("LOCAL_test3").nickname("캐롤").role("USER").build());

        // 3. 커뮤니티 + 멤버
        Community community = communityRepository.save(
            new Community("로컬 테스트 모임", "서울", null, alice.getId()));
        community.assignInviteCode(uniqueCommunityCode());
        for (Member m : List.of(alice, bob, carol)) {
            communityMemberRepository.save(new CommunityMember(community, m));
        }

        // 4. 방 + 방 멤버 (앨리스가 방장). 카탄으로 바로 매치 등록 가능
        Room room = new Room("테스트 방", uniqueRoomCode(), catan.getId());
        room.assignCommunity(community.getId());
        room = roomRepository.save(room);
        roomMemberRepository.save(new RoomMember(room, alice, MemberRole.HOST));
        roomMemberRepository.save(new RoomMember(room, bob, MemberRole.MEMBER));
        roomMemberRepository.save(new RoomMember(room, carol, MemberRole.MEMBER));

        System.out.println("[LocalSeedData] 로컬 시드 생성 완료 — 보드게임 3, 회원 3, 커뮤니티 1, 방 1");
    }

    private String uniqueCommunityCode() {
        String code;
        do {
            code = InviteCodeUtil.generate();
        } while (communityRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private String uniqueRoomCode() {
        String code;
        do {
            code = InviteCodeUtil.generate();
        } while (roomRepository.findByInviteCode(code).isPresent());
        return code;
    }
}
