package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.StoreMember;
import com.board_game_back.Entity.StoreStaff;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.StoreMemberRepository;
import com.board_game_back.Repository.StoreStaffRepository;
import com.board_game_back.Service.StoreAccessService.ViewerRole;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 매장 권한은 DB의 역할 + 지정으로 판정한다 (plan-store §6.6) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreAccessServiceTest {

    private static final Long STORE_ID = 3L;
    private static final Long OTHER_STORE_ID = 4L;
    private static final Long MEMBER_ID = 16L;

    @Mock private MemberRepository memberRepository;
    @Mock private StoreStaffRepository storeStaffRepository;
    @Mock private StoreMemberRepository storeMemberRepository;

    @InjectMocks private StoreAccessService access;

    private void role(String role) {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(Member.builder().nickname("n").role(role).build()));
    }

    private void assignedTo(Long storeId) {
        when(storeStaffRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(new StoreStaff(storeId, MEMBER_ID)));
    }

    @Test
    void STAFF_역할에_이_매장_지정이면_STAFF() {
        role("STAFF");
        assignedTo(STORE_ID);

        assertThat(access.viewerRole(STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.STAFF);
        assertThat(access.viewerRole(OTHER_STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.STAFF_OTHER);
        assertThat(access.staffStoreId(MEMBER_ID)).contains(STORE_ID);
    }

    @Test
    void 지정이_없는_STAFF는_STAFF_OTHER() {
        role("STAFF");

        assertThat(access.viewerRole(STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.STAFF_OTHER);
        assertThat(access.staffStoreId(MEMBER_ID)).isEmpty();
    }

    @Test
    void 역할을_빼면_지정_행이_남아도_STAFF가_아니다() {
        role("USER");
        assignedTo(STORE_ID);

        assertThat(access.viewerRole(STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.VISITOR);
        assertThat(access.staffStoreId(MEMBER_ID)).isEmpty();
    }

    @Test
    void 일반_회원은_멤버_여부로_나뉜다() {
        role("USER");
        when(storeMemberRepository.findByStoreIdAndMemberId(STORE_ID, MEMBER_ID))
            .thenReturn(Optional.of(new StoreMember(STORE_ID, MEMBER_ID, 1, LocalDateTime.now())));

        assertThat(access.viewerRole(STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.MEMBER);
        assertThat(access.viewerRole(OTHER_STORE_ID, MEMBER_ID)).isEqualTo(ViewerRole.VISITOR);
    }
}
