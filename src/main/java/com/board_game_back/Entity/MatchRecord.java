package com.board_game_back.Entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_game_id")
    private BoardGame boardGame;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    private LocalDateTime playedAt; // 게임 종료 및 랭킹 반영 시간

    @OneToMany(mappedBy = "matchRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchParticipant> participants = new ArrayList<>();

    @Builder
    public MatchRecord(BoardGame boardGame, Room room) {
        this.boardGame = boardGame;
        this.room = room;
        // UTC로 저장한다. MatchService가 응답에 "Z"를 붙여 절대 시각으로 내보내고,
        // 프론트가 커뮤니티 region(REGION_TIMEZONE)에 맞춰 현지 시각으로 변환한다.
        // KST 벽시계로 바꾸면 모든 국가에서 9시간 어긋난다.
        this.playedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }
}
