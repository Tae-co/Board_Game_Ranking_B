package com.board_game_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 TV 화면 설정 (6k). 이 매장의 TV 전부에 같이 적용된다. 줄이 없으면 {@link #defaults}.
 * TOP 10은 뺀 게임만 저장한다 — 잠시 닫았다 다시 연 게임이 켜진 채로 돌아오게 (6k 안내 문구).
 */
@Entity
@Table(name = "store_tv_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreTvSetting {

    /** 돌아가는 순서에 들어갈 수 있는 화면. QR(4h)은 뺄 수 없다 */
    public enum Screen { MVP, TOP10, QR, EVENT, CONTEST, SEASON }

    public static final List<Screen> DEFAULT_ORDER = List.of(Screen.values());
    public static final List<Integer> INTERVALS = List.of(5, 10, 15, 20);
    public static final int DEFAULT_INTERVAL = 10;

    @Id
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String screenOrder;

    @Column(nullable = false)
    private int intervalSec;

    @Column(nullable = false)
    private boolean showJustFinished;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String top10HiddenGameIds;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StoreTvSetting defaults(Long storeId, LocalDateTime now) {
        StoreTvSetting s = new StoreTvSetting();
        s.storeId = storeId;
        s.update(DEFAULT_ORDER, DEFAULT_INTERVAL, true, List.of(), now);
        return s;
    }

    public void update(List<Screen> order, int intervalSec, boolean showJustFinished, List<Long> hiddenGameIds,
                       LocalDateTime now) {
        this.screenOrder = String.join(",", order.stream().map(Enum::name).toList());
        this.intervalSec = intervalSec;
        this.showJustFinished = showJustFinished;
        this.top10HiddenGameIds = String.join(",", hiddenGameIds.stream().map(String::valueOf).toList());
        this.updatedAt = now;
    }

    public List<Screen> order() {
        return Arrays.stream(screenOrder.split(",")).filter(s -> !s.isBlank()).map(Screen::valueOf).toList();
    }

    public List<Long> hiddenGameIds() {
        return Arrays.stream(top10HiddenGameIds.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
    }
}
