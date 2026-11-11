package com.board_game_back.Service;

import com.board_game_back.DTO.MemberDto;
import com.board_game_back.Repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 조회. MemberController의 Repository 직접 사용을 옮겨 올 자리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    /** 관리자 회원 목록 */
    public List<MemberDto.AdminSummary> getMembersForAdmin() {
        return memberRepository.findAll().stream().map(MemberDto.AdminSummary::from).toList();
    }
}
