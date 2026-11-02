package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.StoreMatch;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreParty;
import com.board_game_back.Entity.StorePartyMember;
import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMatchParticipantRepository;
import com.board_game_back.Repository.StoreMatchRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StorePartyMemberRepository;
import com.board_game_back.Repository.StorePartyRepository;
import com.board_game_back.Repository.StoreRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 기록자 승계·판 사이에만·체크인 만료 (plan-store 2i, §1 매장 체크인) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePartyServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long PARTY_ID = 9L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 5, 0);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreMemberRepository storeMemberRepository;
    @Mock private StorePartyRepository storePartyRepository;
    @Mock private StorePartyMemberRepository storePartyMemberRepository;
    @Mock private StoreMatchRepository storeMatchRepository;
    @Mock private StoreMatchParticipantRepository storeMatchParticipantRepository;
    @Mock private BoardGameRepository boardGameRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StoreAccessService storeAccessService;
    @Mock private StorePartyNotifier notifier;

    private StorePartyService service;
    private StoreParty party;
    private final List<StorePartyMember> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StorePartyService(storeRepository, storeMemberRepository, storePartyRepository,
            storePartyMemberRepository, storeMatchRepository, storeMatchParticipantRepository, boardGameRepository,
            memberRepository, storeAccessService, notifier);
        party = new StoreParty(STORE_ID, "1234", 1L, LocalDate.of(2026, 11, 2), T0);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        for (long id = 1; id <= 3; id++) {
            StorePartyMember pm = new StorePartyMember(PARTY_ID, id, T0.plusMinutes(id));
            ReflectionTestUtils.setField(pm, "id", id * 10);
            members.add(pm);
        }
        when(storePartyRepository.findByIdForUpdate(PARTY_ID)).thenReturn(Optional.of(party));
        when(storePartyMemberRepository.findByPartyIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(PARTY_ID))
            .thenAnswer(inv -> members.stream().filter(StorePartyMember::isActive).toList());
        when(storePartyMemberRepository.findByMemberIdAndLeftAtIsNull(anyLong())).thenAnswer(inv -> members.stream()
            .filter(m -> m.isActive() && m.getMemberId().equals(inv.getArgument(0))).findFirst());
        when(storeMatchRepository.findFirstByPartyIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void 기록자가_나가면_가장_먼저_합류한_사람에게() {
        service.leave(1L);
        assertThat(party.getRecorderMemberId()).isEqualTo(2L);
        assertThat(party.isActive()).isTrue();
    }

    @Test
    void 기록자가_아닌_사람이_나가면_기록자_그대로() {
        service.leave(2L);
        assertThat(party.getRecorderMemberId()).isEqualTo(1L);
    }

    @Test
    void 마지막_사람이_나가면_그룹이_없어진다() {
        service.leave(1L);
        service.leave(2L);
        service.leave(3L);
        assertThat(party.isActive()).isFalse();
    }

    @Test
    void 게임_중엔_내보내기_막힘() {
        when(storeMatchRepository.findFirstByPartyIdAndStatusIn(any(), any())).thenReturn(Optional.of(mock(StoreMatch.class)));
        assertThatThrownBy(() -> service.kick(1L, 2L))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.PARTY_PLAYING);
    }

    @Test
    void 기록자_넘기기는_기록자만() {
        assertThatThrownBy(() -> service.handOver(2L, 3L))
            .extracting(e -> ((StoreException) e).code()).isEqualTo(StoreErrorCode.NOT_RECORDER);
    }

    @Test
    void 체크인이_끝난_기록자는_빠지고_승계() {
        StoreMember expired = new StoreMember(STORE_ID, 1L, 1, T0);
        expired.checkIn(T0.minusHours(4));
        StoreMember active = new StoreMember(STORE_ID, 2L, 1, T0);
        active.checkIn(T0);
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, 1L)).thenReturn(Optional.of(expired));
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, 2L)).thenReturn(Optional.of(active));
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, 3L)).thenReturn(Optional.of(active));

        assertThat(service.expireCheckins(PARTY_ID, T0.plusMinutes(10))).isEqualTo(1);
        assertThat(members.get(0).getLeftReason()).isEqualTo("EXPIRED");
        assertThat(party.getRecorderMemberId()).isEqualTo(2L);
    }

    @Test
    void 판이_열려_있으면_체크인_만료로_안_빠진다() {
        when(storeMatchRepository.findFirstByPartyIdAndStatusIn(any(), any())).thenReturn(Optional.of(mock(StoreMatch.class)));
        assertThat(service.expireCheckins(PARTY_ID, T0.plusHours(10))).isZero();
    }
}
