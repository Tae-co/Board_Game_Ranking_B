package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.StoreDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.RatingType;
import com.board_game_back.Entity.Store;
import com.board_game_back.Entity.StoreRoom;
import com.board_game_back.Entity.StoreStaff;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreRepository;
import com.board_game_back.Repository.StoreRoomRepository;
import com.board_game_back.Repository.StoreStaffRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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

/** 매장 등록·STAFF 지정·게임방 일괄 생성 (plan-store 6e·6h) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreAdminServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long STAFF_ID = 16L;

    @Mock private StoreRepository storeRepository;
    @Mock private StoreStaffRepository storeStaffRepository;
    @Mock private StoreMemberRepository storeMemberRepository;
    @Mock private StoreRoomRepository storeRoomRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StoreAccessService storeAccessService;

    @InjectMocks private StoreAdminService service;

    @BeforeEach
    void setUp() {
        when(storeRepository.save(any())).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", STORE_ID);
            return s;
        });
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(Store.builder().name("야다").address("서울")
            .region("South Korea").openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(22, 0)).qrSecret("s").build()));
    }

    private static StoreDto.StoreForm form(String region, String open, String close) {
        return new StoreDto.StoreForm(" 야다 ", "홍대점", "서울 마포구", region, null, null, open, close);
    }

    private static BoardGame game(Long id, Long communityId, RatingType type) {
        BoardGame g = BoardGame.builder().name("g" + id).minPlayers(2).maxPlayers(4)
            .communityId(communityId).ratingType(type).build();
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    @Test
    void 등록하면_공식_ELO_게임마다_게임방이_생긴다() {
        when(boardGameRepository.findByCommunityIdIsNull()).thenReturn(List.of(
            game(1L, null, RatingType.ELO), game(2L, null, RatingType.CHIP), game(3L, null, RatingType.ELO)));

        service.create(form("South Korea", "14:00", "02:00"));

        ArgumentCaptor<Store> store = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(store.capture());
        assertThat(store.getValue().getName()).isEqualTo("야다");
        assertThat(store.getValue().getQrSecret()).hasSize(64);
        ArgumentCaptor<StoreRoom> rooms = ArgumentCaptor.forClass(StoreRoom.class);
        verify(storeRoomRepository, times(2)).save(rooms.capture());
        assertThat(rooms.getAllValues()).extracting(StoreRoom::getBoardGameId).containsExactly(1L, 3L);
    }

    @Test
    void 영업시간이_같거나_지역이_없으면_거부() {
        assertCode(() -> service.create(form("South Korea", "14:00", "14:00")), StoreErrorCode.INVALID_HOURS);
        assertCode(() -> service.create(form("South Korea", "25:00", "14:00")), StoreErrorCode.INVALID_HOURS);
        assertCode(() -> service.create(form("서울", "10:00", "22:00")), StoreErrorCode.INVALID_REGION);
        verify(storeRepository, never()).save(any());
    }

    @Test
    void STAFF_역할이_아니면_지정_거부() {
        when(storeAccessService.isStaff(STAFF_ID)).thenReturn(false);

        assertCode(() -> service.assignStaff(STORE_ID, STAFF_ID), StoreErrorCode.NOT_STAFF_ROLE);
    }

    @Test
    void 다른_매장에_지정된_STAFF는_거부() {
        when(storeAccessService.isStaff(STAFF_ID)).thenReturn(true);
        when(storeStaffRepository.findByMemberId(STAFF_ID)).thenReturn(Optional.of(new StoreStaff(99L, STAFF_ID)));

        assertCode(() -> service.assignStaff(STORE_ID, STAFF_ID), StoreErrorCode.STAFF_ALREADY_ASSIGNED);
        verify(storeStaffRepository, never()).save(any());
    }

    @Test
    void 커뮤니티_전용_게임엔_매장_게임방을_만들지_않는다() {
        when(storeRepository.findAll()).thenReturn(List.of(Store.builder().build()));

        service.createRoomsForGame(game(5L, 9L, RatingType.ELO));
        service.createRoomsForGame(game(6L, null, RatingType.CHIP));

        verify(storeRoomRepository, never()).save(any());
    }

    private static void assertCode(Runnable call, StoreErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(StoreException.class)
            .satisfies(e -> assertThat(((StoreException) e).code()).isEqualTo(code));
    }
}
