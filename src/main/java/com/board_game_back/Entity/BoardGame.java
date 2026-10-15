package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_game_id")
    private Long id;

    private String name;        // 예: 카탄, 스플렌더
    private String imageUrl;    // 썸네일 이미지
    private int minPlayers;
    private int maxPlayers;

    @Column(columnDefinition = "TEXT")
    private String schemaJson;  // flat/sectioned 타입 점수판 스키마 (JSON)

    private Long communityId;        // null = 공식 게임(모두에게 보임), 값 있으면 해당 커뮤니티 전용
    private Long createdByMemberId;  // null = 관리자가 만든 게임

    @Enumerated(EnumType.STRING)
    private RatingType ratingType = RatingType.ELO;  // 만들 때 정하고 바꾸지 않는다

    private Integer startingChips;  // 칩 게임만. 이 게임으로 만드는 그룹의 시작 칩 기본값

    @Builder
    public BoardGame(String name, String imageUrl, int minPlayers, int maxPlayers, String schemaJson,
                     Long communityId, Long createdByMemberId, RatingType ratingType, Integer startingChips) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.schemaJson = schemaJson;
        this.communityId = communityId;
        this.createdByMemberId = createdByMemberId;
        if (ratingType != null) this.ratingType = ratingType;
        this.startingChips = startingChips;
    }

    public boolean isChipGame() {
        return ratingType == RatingType.CHIP;
    }

    public void update(String name, String imageUrl, int minPlayers, int maxPlayers, String schemaJson) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.schemaJson = schemaJson;
    }
}
