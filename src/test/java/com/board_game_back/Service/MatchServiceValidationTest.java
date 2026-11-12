package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.MatchDto;
import com.board_game_back.DTO.MatchDto.ParticipantRequest;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.MatchRecord;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.MemberRole;
import com.board_game_back.Entity.PlayerGameRating;
import com.board_game_back.Entity.Room;
import com.board_game_back.Entity.RoomMember;
import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MatchRecordRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 경기 기록·수정 요청의 참가자 검증. 화면은 올바른 값만 보내지만 API를 직접 부르면 랭킹이 망가졌다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceValidationTest {

    private static final Long ROOM_ID = 100L;
    private static final Long GAME_ID = 1L;
    private static final Long MATCH_ID = 500L;
    private static final Long HOST_ID = 1L;
    private static final Long OUTSIDER_ID = 9L;

    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private PlayerGameRatingRepository ratingRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MemberRepository memberRepository;
    @Spy private RatingCalculator ratingCalculator = new EloRatingCalculator();
    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private RoomSeasonService roomSeasonService;
    @Mock private SeasonRolloverService seasonRolloverService;

    @InjectMocks private MatchService matchService;

    private Room room;
    private BoardGame catan;

    @BeforeEach
    void setUp() {
        room = new Room("카탄방", "CATAN1", GAME_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        catan = BoardGame.builder().name("카탄").build();
        ReflectionTestUtils.setField(catan, "id", GAME_ID);

        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(boardGameRepository.findById(GAME_ID)).thenReturn(Optional.of(catan));
        for (long id = 1; id <= 3; id++) {
            Member member = Member.builder().nickname("p" + id).build();
            ReflectionTestUtils.setField(member, "id", id);
            when(memberRepository.findById(id)).thenReturn(Optional.of(member));
            MemberRole role = id == HOST_ID ? MemberRole.HOST : MemberRole.MEMBER;
            when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, id))
                .thenReturn(Optional.of(new RoomMember(room, member, role)));
            when(ratingRepository.findByMemberAndBoardGameAndRoom(member, catan, room))
                .thenReturn(Optional.of(PlayerGameRating.builder().member(member).boardGame(catan).room(room).build()));
        }
        Member outsider = Member.builder().nickname("밖").build();
        ReflectionTestUtils.setField(outsider, "id", OUTSIDER_ID);
        when(memberRepository.findById(OUTSIDER_ID)).thenReturn(Optional.of(outsider));
        when(roomMemberRepository.findByRoomIdAndMemberId(ROOM_ID, OUTSIDER_ID)).thenReturn(Optional.empty());

        MatchRecord match = MatchRecord.builder().boardGame(catan).room(room).recordedByMemberId(HOST_ID).build();
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        when(matchRecordRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
    }

    private static ParticipantRequest p(Long memberId, int placement) {
        return new ParticipantRequest(memberId, placement, null, null, null);
    }

    private void assertRecordRejected(List<ParticipantRequest> participants, ErrorCode code) {
        assertThatThrownBy(() -> matchService.recordMatchResult(
            new MatchDto.ResultRequest(GAME_ID, ROOM_ID, participants), HOST_ID))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", code);
        verify(matchRecordRepository, never()).save(any());
    }

    private void assertUpdateRejected(List<ParticipantRequest> participants, ErrorCode code) {
        assertThatThrownBy(() -> matchService.updateMatchResult(
            MATCH_ID, new MatchDto.ResultRequest(GAME_ID, ROOM_ID, participants), HOST_ID))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", code);
        verify(matchRecordRepository, never()).save(any());
    }

    @Test
    void 참가자가_2명_미만이면_기록을_거절한다() {
        assertRecordRejected(List.of(), ErrorCode.MATCH_TOO_FEW_PARTICIPANTS);
        assertRecordRejected(List.of(p(1L, 1)), ErrorCode.MATCH_TOO_FEW_PARTICIPANTS);
    }

    @Test
    void 참가자_목록이_없으면_기록을_거절한다() {
        assertRecordRejected(null, ErrorCode.MATCH_TOO_FEW_PARTICIPANTS);
    }

    @Test
    void 같은_회원이_두_번_들어가면_기록을_거절한다() {
        assertRecordRejected(List.of(p(1L, 1), p(1L, 2)), ErrorCode.MATCH_DUPLICATE_PARTICIPANT);
    }

    @Test
    void 회원_id가_비어_있으면_기록을_거절한다() {
        assertRecordRejected(List.of(p(1L, 1), p(null, 2)), ErrorCode.MATCH_DUPLICATE_PARTICIPANT);
    }

    @Test
    void 순위가_1부터_인원수_사이가_아니면_기록을_거절한다() {
        assertRecordRejected(List.of(p(1L, 0), p(2L, 1)), ErrorCode.MATCH_INVALID_PLACEMENT);
        assertRecordRejected(List.of(p(1L, -1), p(2L, 1)), ErrorCode.MATCH_INVALID_PLACEMENT);
        assertRecordRejected(List.of(p(1L, 1), p(2L, 3)), ErrorCode.MATCH_INVALID_PLACEMENT);
    }

    @Test
    void 게임이나_방이_비어_있으면_기록을_거절한다() {
        assertThatThrownBy(() -> matchService.recordMatchResult(
            new MatchDto.ResultRequest(null, ROOM_ID, List.of(p(1L, 1), p(2L, 2))), HOST_ID))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.MATCH_REQUEST_INCOMPLETE);
        assertThatThrownBy(() -> matchService.recordMatchResult(
            new MatchDto.ResultRequest(GAME_ID, null, List.of(p(1L, 1), p(2L, 2))), HOST_ID))
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.MATCH_REQUEST_INCOMPLETE);
    }

    @Test
    void 동순위와_전원_공동_1등은_기록된다() {
        assertThatCode(() -> matchService.recordMatchResult(
            new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(p(1L, 1), p(2L, 1), p(3L, 3))), HOST_ID))
            .doesNotThrowAnyException();
        assertThatCode(() -> matchService.recordMatchResult(
            new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(p(1L, 1), p(2L, 1), p(3L, 1))), HOST_ID))
            .doesNotThrowAnyException();
    }

    @Test
    void 수정도_같은_참가자_검증을_거친다() {
        assertUpdateRejected(List.of(p(1L, 1)), ErrorCode.MATCH_TOO_FEW_PARTICIPANTS);
        assertUpdateRejected(List.of(p(1L, 1), p(1L, 2)), ErrorCode.MATCH_DUPLICATE_PARTICIPANT);
        assertUpdateRejected(List.of(p(1L, 1), p(2L, 5)), ErrorCode.MATCH_INVALID_PLACEMENT);
    }

    @Test
    void 수정할_때_방_멤버가_아닌_참가자는_거절한다() {
        assertUpdateRejected(List.of(p(1L, 1), p(OUTSIDER_ID, 2)), ErrorCode.PARTICIPANT_NOT_ROOM_MEMBER);
    }

    @Test
    void 수정할_때_없는_회원은_500이_아니라_거절한다() {
        when(memberRepository.findById(777L)).thenReturn(Optional.empty());
        assertUpdateRejected(List.of(p(1L, 1), p(777L, 2)), ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 올바른_수정은_통과한다() {
        assertThatCode(() -> matchService.updateMatchResult(
            MATCH_ID, new MatchDto.ResultRequest(GAME_ID, ROOM_ID, List.of(p(2L, 1), p(1L, 2), p(3L, 2))), HOST_ID))
            .doesNotThrowAnyException();
    }
}
