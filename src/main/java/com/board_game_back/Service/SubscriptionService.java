package com.board_game_back.Service;

import com.board_game_back.DTO.SubscriptionDto;
import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 모임장 Pro 구독. **결제 연동은 없다 — 만료 시각을 심는 프로토타입이다.**
 *
 * <p>이게 서버에 있는 이유는 매출 보호가 아니라, 커뮤니티에 들어오려는 사람의 앱이
 * 그 모임 운영자가 Pro인지 알 방법이 없기 때문이다. 인원 한도를 join에서 막으려면
 * 서버가 구독을 알아야 한다. — plan-monetization.md
 *
 * <p>실제 결제를 붙일 땐 {@link #subscribe}가 영수증 검증을 거치도록 바꾸면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String MONTHLY = "MONTHLY";
    private static final String YEARLY = "YEARLY";

    private final MemberRepository memberRepository;
    private final CommunityAdminRepository communityAdminRepository;

    public SubscriptionDto.Response getMine(Long memberId) {
        return memberRepository.findById(memberId)
            .map(SubscriptionService::toResponse)
            .orElseGet(SubscriptionDto.Response::free);
    }

    @Transactional
    public SubscriptionDto.Response subscribe(Long memberId, String billing) {
        String plan = YEARLY.equals(billing) ? YEARLY : MONTHLY;
        Member member = findMember(memberId);
        LocalDateTime until = LocalDateTime.now(KST).plusMonths(YEARLY.equals(plan) ? 12 : 1);
        member.grantPro(plan, until);
        memberRepository.save(member);
        return toResponse(member);
    }

    /** 해지 예약. 남은 기간은 그대로 둔다 — 인질 모델을 쓰지 않는다. */
    @Transactional
    public SubscriptionDto.Response cancel(Long memberId) {
        Member member = findMember(memberId);
        member.cancelPro();
        memberRepository.save(member);
        return toResponse(member);
    }

    @Transactional
    public SubscriptionDto.Response resume(Long memberId) {
        Member member = findMember(memberId);
        member.resumePro();
        memberRepository.save(member);
        return toResponse(member);
    }

    public boolean isPro(Long memberId) {
        if (memberId == null) return false;
        return memberRepository.findById(memberId).map(Member::isPro).orElse(false);
    }

    /**
     * 커뮤니티의 한도가 풀렸는지. **어드민 중 한 명이라도 Pro면 풀린다.**
     *
     * <p>생성자(createdBy)만 보면 안 된다 — 실제 운영자가 생성자가 아닌 경우가 흔하고
     * (운영 인수인계, 공동 관리), 계정 단위 구독의 약속이 "내가 운영하는 커뮤니티는
     * 전부 무제한"이기 때문이다. 여기서 '운영한다'는 어드민이라는 뜻이다.
     */
    public boolean isCommunityPro(Long communityId, Long createdBy) {
        if (isPro(createdBy)) return true;
        return communityAdminRepository.findByCommunityId(communityId).stream()
            .map(admin -> admin.getMember() == null ? null : admin.getMember().getId())
            .anyMatch(this::isPro);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }

    private static SubscriptionDto.Response toResponse(Member m) {
        return new SubscriptionDto.Response(m.isPro(), m.getProBilling(), m.getProUntil(), m.isProCanceled());
    }
}
