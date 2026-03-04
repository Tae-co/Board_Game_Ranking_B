package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Builder
    public BoardGame(String name, String imageUrl, int minPlayers, int maxPlayers) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;

    }
}
