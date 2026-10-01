package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방 하나의 시즌. 호스트가 이름·종료일을 정하고, 종료일이 지나면 같은 길이로 다음 시즌이 열린다.
 * 기획: {@code docs/plans/plan-season-reset.md} §22.
 *
 * <p><b>시각은 전부 UTC 벽시계다</b> ({@code MatchRecord.playedAt}과 직접 비교하려고).
 * 날짜(시작일·종료일)는 커뮤니티 region 타임존에서의 의미이고, 변환은 이 클래스가 한다.
 *
 * <p><b>{@code endAt}은 배타적이다</b> — 종료일 다음 날 00:00. 종료일 당일 경기까지 그 시즌에 들어간다.
 */
@Entity
@Table(name = "room_season")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomSeason {

    public static final int MAX_NAME_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "room_season_id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "season_number", nullable = false)
    private int seasonNumber;

    /** null이면 화면이 "시즌 N"으로 표시한다. */
    @Column(name = "name", length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    public static RoomSeason open(Long roomId, int seasonNumber, String name,
                                  LocalDateTime startAt, LocalDate endDate, ZoneId zone) {
        RoomSeason season = new RoomSeason();
        season.roomId = roomId;
        season.seasonNumber = seasonNumber;
        season.name = normalizeName(name);
        season.startAt = startAt;
        season.endAt = endExclusiveUtc(endDate, zone);
        return season;
    }

    /**
     * 같은 길이로 이어지는 다음 시즌. 시작은 이 시즌의 끝이다 — 빈틈이 있으면 그 사이 경기가
     * 어느 시즌에도 속하지 않고, 재계산(현재 시즌 시작 이후만 리플레이)에서도 빠진다.
     *
     * @param now 다음 종료일까지 이미 지났으면(서버가 오래 죽어 있었다) 빈 시즌을 여러 개 만들지 않고
     *            종료일만 길이 단위로 민다
     */
    public RoomSeason next(ZoneId zone, LocalDateTime now) {
        long lengthDays = Math.max(1, ChronoUnit.DAYS.between(startDate(zone), endDate(zone)) + 1);
        LocalDate nextEnd = endDate(zone).plusDays(lengthDays);
        while (!endExclusiveUtc(nextEnd, zone).isAfter(now)) nextEnd = nextEnd.plusDays(lengthDays);
        return open(roomId, seasonNumber + 1, null, endAt, nextEnd, zone);
    }

    public void close(LocalDateTime now) {
        this.closedAt = now;
    }

    public void rename(String newName) {
        this.name = normalizeName(newName);
    }

    public void changeEndDate(LocalDate endDate, ZoneId zone) {
        this.endAt = endExclusiveUtc(endDate, zone);
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public boolean isDue(LocalDateTime now) {
        return !isClosed() && !endAt.isAfter(now);
    }

    /** 커뮤니티 타임존에서의 종료일 (포함). */
    public LocalDate endDate(ZoneId zone) {
        return toZoned(endAt, zone).toLocalDate().minusDays(1);
    }

    public LocalDate startDate(ZoneId zone) {
        return toZoned(startAt, zone).toLocalDate();
    }

    /** 종료일 다음 날 00:00(zone)을 UTC 벽시계로. */
    public static LocalDateTime endExclusiveUtc(LocalDate endDate, ZoneId zone) {
        return LocalDateTime.ofInstant(endDate.plusDays(1).atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

    private static LocalDateTime toZoned(LocalDateTime utc, ZoneId zone) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("시즌 이름은 " + MAX_NAME_LENGTH + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }
}
