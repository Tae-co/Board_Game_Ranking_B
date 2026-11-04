package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 점수판 요청 묶음 (2g·6c). 같은 게임 이름(공백·대소문자 무시)은 한 줄 */
@Entity
@Table(name = "score_sheet_game_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreSheetGameRequest {

    public enum Status { REVIEW, MAKING, ADDED, HOLD }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 60, unique = true)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.REVIEW;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastRequestedAt;

    public ScoreSheetGameRequest(String name, String normalizedName, LocalDateTime now) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.createdAt = now;
        this.lastRequestedAt = now;
    }

    public void touch(LocalDateTime now) {
        this.lastRequestedAt = now;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public static String normalize(String name) {
        return name.replaceAll("\\s+", "").toLowerCase();
    }
}
