package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.board_game_back.DTO.SubscriptionDto;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.MemberRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 모임장 Pro 구독. 결제는 없고 만료 시각 플래그만 다룬다.
 *
 * <p>여기서 지키려는 건 두 가지다: 해지해도 남은 기간은 살아있어야 하고,
 * 커뮤니티 Pro 판정이 생성자가 아니라 <b>어드민</b> 기준이어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock private MemberRepository memberRepository;
    @Mock private CommunityAdminRepository communityAdminRepository;

    @InjectMocks private SubscriptionService subscriptionService;

    @Test
    void subscribe_월간이면_한_달_뒤가_만료다() {
        Member 태엽 = member(8L);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(태엽));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto.Response res = subscriptionService.subscribe(8L, "MONTHLY");

        assertThat(res.active()).isTrue();
        assertThat(res.billing()).isEqualTo("MONTHLY");
        assertThat(res.proUntil()).isBetween(
            LocalDateTime.now(KST).plusMonths(1).minusMinutes(1),
            LocalDateTime.now(KST).plusMonths(1).plusMinutes(1));
    }

    @Test
    void subscribe_연간이면_열두_달_뒤가_만료다() {
        Member 태엽 = member(8L);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(태엽));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto.Response res = subscriptionService.subscribe(8L, "YEARLY");

        assertThat(res.billing()).isEqualTo("YEARLY");
        assertThat(res.proUntil()).isAfter(LocalDateTime.now(KST).plusMonths(11));
    }

    @Test
    void subscribe_모르는_플랜은_월간으로_떨어진다() {
        Member 태엽 = member(8L);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(태엽));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(subscriptionService.subscribe(8L, "LIFETIME").billing()).isEqualTo("MONTHLY");
    }

    /** 인질 모델을 쓰지 않는다 — 낸 기간은 그대로 쓴다. */
    @Test
    void cancel_해지해도_남은_기간은_계속_이용한다() {
        Member 태엽 = member(8L);
        태엽.grantPro("MONTHLY", LocalDateTime.now(KST).plusDays(20));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(태엽));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto.Response res = subscriptionService.cancel(8L);

        assertThat(res.canceled()).isTrue();
        assertThat(res.active()).isTrue();   // 아직 만료 전이므로 Pro다
    }

    @Test
    void isPro_만료된_구독은_Pro가_아니다() {
        Member 태엽 = member(8L);
        태엽.grantPro("MONTHLY", LocalDateTime.now(KST).minusDays(1));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(태엽));

        assertThat(subscriptionService.isPro(8L)).isFalse();
    }

    @Test
    void isPro_구독한_적_없으면_Pro가_아니다() {
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L)));

        assertThat(subscriptionService.isPro(8L)).isFalse();
    }

    /**
     * 생성자만 보면 안 되는 이유: 운영을 넘겨받았거나 공동 관리 중이면
     * 실제로 굴리는 사람이 생성자가 아니다. 계정 단위 구독의 약속이
     * "내가 운영하는 커뮤니티는 전부 무제한"이라 '운영'을 어드민으로 읽는다.
     */
    @Test
    void isCommunityPro_생성자가_아니어도_어드민이_Pro면_풀린다() {
        Member 생성자 = member(2L);                    // 구독 없음
        Member 운영자 = member(8L);                    // 실제로 굴리는 사람
        운영자.grantPro("MONTHLY", LocalDateTime.now(KST).plusDays(20));

        when(memberRepository.findById(2L)).thenReturn(Optional.of(생성자));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(운영자));
        when(communityAdminRepository.findByCommunityId(1L))
            .thenReturn(List.of(admin(1L, 운영자)));

        assertThat(subscriptionService.isCommunityPro(1L, 2L)).isTrue();
    }

    @Test
    void isCommunityPro_아무도_Pro가_아니면_잠긴다() {
        Member 생성자 = member(2L);
        Member 어드민 = member(8L);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(생성자));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(어드민));
        when(communityAdminRepository.findByCommunityId(1L))
            .thenReturn(List.of(admin(1L, 어드민)));

        assertThat(subscriptionService.isCommunityPro(1L, 2L)).isFalse();
    }

    @Test
    void isCommunityPro_생성자가_Pro면_어드민을_보지도_않는다() {
        Member 생성자 = member(2L);
        생성자.grantPro("YEARLY", LocalDateTime.now(KST).plusMonths(6));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(생성자));

        assertThat(subscriptionService.isCommunityPro(1L, 2L)).isTrue();
    }

    private Member member(Long id) {
        Member member = Member.builder().nickname("m" + id).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private CommunityAdmin admin(Long communityId, Member member) {
        Community community = new Community("모임", "서울", null, 2L);
        ReflectionTestUtils.setField(community, "id", communityId);
        return new CommunityAdmin(community, member);
    }
}
