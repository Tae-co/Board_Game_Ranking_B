package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매장 이벤트 (음료 1+1 등). 게임방·랭킹 없이 매장 홈 카드·상세와 TV 4d에만 뜬다. 기간은 매장 영업일. */
@Entity
@Table(name = "store_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreEvent {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(length = 300)
    private String benefitText;

    @Column(length = 500)
    private String howToUse;

    @Column(length = 500)
    private String imageUrl;

    /** 1회용 쿠폰: 상세 화면에 사용 완료 버튼, 회원당 한 번 (store_event_use) */
    @Column(nullable = false)
    private boolean oneTime;

    @Column(nullable = false)
    private LocalDate startsOn;

    @Column(nullable = false)
    private LocalDate endsOn;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public StoreEvent(Long storeId, LocalDateTime now) {
        this.storeId = storeId;
        this.createdAt = now;
    }

    public void update(String title, String benefitText, String howToUse, String imageUrl, boolean oneTime,
                       LocalDate startsOn, LocalDate endsOn) {
        this.title = title;
        this.benefitText = benefitText;
        this.howToUse = howToUse;
        this.imageUrl = imageUrl;
        this.oneTime = oneTime;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
