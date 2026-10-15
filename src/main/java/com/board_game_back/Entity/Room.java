package com.board_game_back.Entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String inviteCode;

    private Long boardGameId;

    private Long communityId;

    private boolean sessionActive = false;

    // 칩 게임의 시작 칩. 경기가 시작된 시즌에 바꾸면 pending에 두고 다음 시즌에 적용한다.
    private int startingChips = 1000;
    private Integer pendingStartingChips;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomMember> roomMembers = new ArrayList<>();

    public Room(String name, String inviteCode, Long boardGameId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.boardGameId = boardGameId;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("방 이름은 비워둘 수 없습니다.");
        }
        this.name = newName;
    }

    public void assignBoardGame(Long boardGameId) {
        this.boardGameId = boardGameId;
    }

    public void assignCommunity(Long communityId) {
        this.communityId = communityId;
    }

    public void changeStartingChips(int chips) {
        if (chips <= 0) throw new IllegalArgumentException("시작 칩은 1 이상이어야 합니다.");
        this.startingChips = chips;
        this.pendingStartingChips = null;
    }

    public void reserveStartingChips(int chips) {
        if (chips <= 0) throw new IllegalArgumentException("시작 칩은 1 이상이어야 합니다.");
        this.pendingStartingChips = chips == this.startingChips ? null : chips;
    }

    /** 시즌 롤오버 때 예약해 둔 시작 칩을 적용한다. */
    public void applyPendingStartingChips() {
        if (pendingStartingChips != null) changeStartingChips(pendingStartingChips);
    }

    public void activateSession() {
        this.sessionActive = true;
    }

    public void deactivateSession() {
        this.sessionActive = false;
    }
}
