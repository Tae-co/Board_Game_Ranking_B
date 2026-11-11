package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.BoardGameDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.RatingType;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 커스텀 점수판 생성·삭제 규칙 — BoardGameController에서 서비스로 옮기며 동작을 고정한다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardGameServiceTest {

    private static final Long COMMUNITY_ID = 31L;
    private static final Long ADMIN_ID = 4L;
    private static final Long GAME_ID = 70L;

    @Mock private BoardGameRepository boardGameRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private MatchRecordRepository matchRecordRepository;

    @InjectMocks private BoardGameService boardGameService;

    private BoardGame customGame() {
        BoardGame game = BoardGame.builder().name("커스텀").imageUrl("").minPlayers(2).maxPlayers(8)
            .schemaJson("{\"type\":\"simple\"}").communityId(COMMUNITY_ID).createdByMemberId(ADMIN_ID).build();
        ReflectionTestUtils.setField(game, "id", GAME_ID);
        return game;
    }

    private void asAdmin() {
        when(communityAdminRepository.existsByCommunityIdAndMemberId(COMMUNITY_ID, ADMIN_ID)).thenReturn(true);
    }

    private static void assertStatus(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                ResponseStatusException ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(status);
                assertThat(ex.getReason()).isEqualTo(message);
            });
    }

    @Test
    void 인원수를_안_주면_2에서_8명_이름은_앞뒤_공백을_뺀다() {
        asAdmin();
        when(boardGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardGameDto.Response res = boardGameService.createCustomGame(ADMIN_ID,
            new BoardGameDto.CreateRequest("  내 게임 ", COMMUNITY_ID, "{\"type\":\"simple\"}", null, null, null));

        assertThat(res.name()).isEqualTo("내 게임");
        assertThat(res.minPlayers()).isEqualTo(2);
        assertThat(res.maxPlayers()).isEqualTo(8);
        assertThat(res.imageUrl()).isEmpty();
        assertThat(res.ratingType()).isEqualTo(RatingType.ELO);
        assertThat(res.chipGame()).isFalse();
        assertThat(res.createdByMemberId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void chip_점수판이면_칩_랭킹으로_만든다() {
        asAdmin();
        when(boardGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardGameDto.Response res = boardGameService.createCustomGame(ADMIN_ID,
            new BoardGameDto.CreateRequest("포커", COMMUNITY_ID, "{\"type\":\"chip\"}", null, null, null));

        assertThat(res.ratingType()).isEqualTo(RatingType.CHIP);
        assertThat(res.chipGame()).isTrue();
    }

    @Test
    void 어드민이_아니면_만들지_못하고_저장하지_않는다() {
        assertStatus(() -> boardGameService.createCustomGame(ADMIN_ID,
                new BoardGameDto.CreateRequest("x", COMMUNITY_ID, "{\"type\":\"simple\"}", null, null, null)),
            HttpStatus.FORBIDDEN, "커뮤니티 어드민만 점수판을 만들 수 있습니다.");
        verify(boardGameRepository, never()).save(any());
    }

    @Test
    void 기본_제공_게임은_지울_수_없다() {
        BoardGame official = BoardGame.builder().name("CATAN").minPlayers(3).maxPlayers(4).build();
        when(boardGameRepository.findById(1L)).thenReturn(Optional.of(official));

        assertStatus(() -> boardGameService.deleteCustomGame(ADMIN_ID, 1L),
            HttpStatus.FORBIDDEN, "기본 제공 게임은 삭제할 수 없습니다.");
        verify(boardGameRepository, never()).delete(any());
    }

    @Test
    void 쓰는_그룹이_있으면_세_개까지_이름을_알려주고_나머지는_개수로() {
        asAdmin();
        when(boardGameRepository.findById(GAME_ID)).thenReturn(Optional.of(customGame()));
        when(roomRepository.findByBoardGameId(GAME_ID)).thenReturn(List.of(
            new Room("가", "A1", GAME_ID), new Room("나", "A2", GAME_ID),
            new Room("다", "A3", GAME_ID), new Room("라", "A4", GAME_ID), new Room("마", "A5", GAME_ID)));

        assertStatus(() -> boardGameService.deleteCustomGame(ADMIN_ID, GAME_ID), HttpStatus.CONFLICT,
            "'가', '나', '다' 외 2개 그룹에서 사용 중이에요.\n그룹을 먼저 삭제한 뒤 점수판을 삭제해주세요.");
        verify(boardGameRepository, never()).delete(any());
    }

    @Test
    void 플레이_기록이_남아_있으면_지울_수_없다() {
        asAdmin();
        when(boardGameRepository.findById(GAME_ID)).thenReturn(Optional.of(customGame()));
        when(roomRepository.findByBoardGameId(GAME_ID)).thenReturn(List.of());
        when(matchRecordRepository.existsByBoardGameId(GAME_ID)).thenReturn(true);

        assertStatus(() -> boardGameService.deleteCustomGame(ADMIN_ID, GAME_ID),
            HttpStatus.CONFLICT, "플레이 기록이 남아 있어 삭제할 수 없어요.");
        verify(boardGameRepository, never()).delete(any());
    }

    @Test
    void 쓰는_곳이_없으면_지운다() {
        asAdmin();
        BoardGame game = customGame();
        when(boardGameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(roomRepository.findByBoardGameId(GAME_ID)).thenReturn(List.of());
        when(matchRecordRepository.existsByBoardGameId(GAME_ID)).thenReturn(false);

        boardGameService.deleteCustomGame(ADMIN_ID, GAME_ID);

        verify(boardGameRepository).delete(game);
    }
}
