package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.MemberDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.EventName;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** MemberController에서 옮겨 온 회원 검색·통계·탈퇴 규칙 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PlayerGameRatingRepository playerGameRatingRepository;
    @Mock private RoomService roomService;
    @Mock private UserEventService userEventService;

    @InjectMocks private MemberService memberService;

    private static Member member(long id, String nickname, String role) {
        Member m = Member.builder().nickname(nickname).role(role).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void 빈_검색어면_회원_명부를_조회하지_않는다() {
        assertThat(memberService.searchByNickname("  ", null)).isEmpty();
        verify(memberRepository, never()).findByNicknameContainingIgnoreCase(anyString());
    }

    @Test
    void 검색에서_본인과_나간_멤버_자리는_빠진다() {
        when(memberRepository.findByNicknameContainingIgnoreCase("앨")).thenReturn(List.of(
            member(2, "앨리스", "USER"), member(3, "앨버트", "USER"), member(9, "나간 멤버 #9", Member.ROLE_LEFT)));

        List<MemberDto.SearchItem> result = memberService.searchByNickname("앨", 3L);

        assertThat(result).containsExactly(new MemberDto.SearchItem(2L, "앨리스"));
    }

    @Test
    void 검색은_최대_100명() {
        when(memberRepository.findByNicknameContainingIgnoreCase("a")).thenReturn(
            LongStream.rangeClosed(1, 150).mapToObj(i -> member(i, "a" + i, "USER")).toList());

        assertThat(memberService.searchByNickname("a", null)).hasSize(100);
    }

    @Test
    void 통계는_게임별_값과_그_합계() {
        BoardGame catan = BoardGame.builder().name("CATAN").minPlayers(3).maxPlayers(4).build();
        BoardGame uno = BoardGame.builder().name("UNO").minPlayers(2).maxPlayers(10).build();
        PlayerGameRating r1 = new PlayerGameRating(null, catan, null);
        r1.addPlayCount(); r1.addPlayCount(); r1.addWinCount(); r1.addLoseCount();
        PlayerGameRating r2 = new PlayerGameRating(null, uno, null);
        r2.addPlayCount(); r2.addWinCount();
        when(playerGameRatingRepository.findPlayedByMemberId(4L)).thenReturn(List.of(r1, r2));

        MemberDto.StatsResponse stats = memberService.getStats(4L);

        assertThat(stats.totalPlay()).isEqualTo(3);
        assertThat(stats.totalWin()).isEqualTo(2);
        assertThat(stats.totalLose()).isEqualTo(1);
        assertThat(stats.games()).containsExactly(
            new MemberDto.GameStatItem("CATAN", 2, 1, 1), new MemberDto.GameStatItem("UNO", 1, 1, 0));
    }

    @Test
    void 탈퇴하면_기록을_정리하고_이탈_이벤트를_남긴다() {
        memberService.withdraw(4L);

        verify(roomService).deleteMember(4L);
        verify(userEventService).recordServerSide(EventName.MEMBER_DELETED, 4L);
    }
}
