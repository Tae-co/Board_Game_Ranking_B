package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_record_id")
    private MatchRecord matchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private int placement;  // 등수 (1등, 2등...) -> 랭킹 계산의 핵심 지표

    private double ratingChange; // 이 판으로 인해 레이팅이 얼마나 올랐/떨어졌는지 (+15.2, -8.4 등 UI 표시용)

    @Builder
    public MatchParticipant(MatchRecord matchRecord, Member member, int placement) {
        this.matchRecord = matchRecord;
        this.member = member;
        this.placement = placement;
        matchRecord.getParticipants().add(this);
    }

    public void updateRatingChange(double change) {
        this.ratingChange = change;
    }

    public void updatePlacement(int placement) {
        this.placement = placement;
    }

}
