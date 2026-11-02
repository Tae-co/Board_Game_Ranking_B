package com.board_game_back.Entity;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 게임방 = 매장 × 공식 게임. 공식 게임을 추가하거나 매장을 등록하면 일괄로 생긴다.
 * {@code closed} = 이 매장에서 잠시 닫음 (손님 화면에서 숨김, 기록은 남김).
 */
@Entity
@Table(name = "store_room", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "board_game_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreRoom {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long boardGameId;

    @Column(nullable = false)
    private boolean closed;

    public StoreRoom(Long storeId, Long boardGameId) {
        this.storeId = storeId;
        this.boardGameId = boardGameId;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}
