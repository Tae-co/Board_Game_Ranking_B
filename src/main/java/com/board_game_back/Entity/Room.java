package com.board_game_back.Entity;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
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

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomMember> roomMembers = new ArrayList<>();

    public Room(String name, String inviteCode, Long boardGameId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.boardGameId = boardGameId;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new AppException(ErrorCode.ROOM_NAME_REQUIRED);
        }
        this.name = newName;
    }

    public void assignBoardGame(Long boardGameId) {
        this.boardGameId = boardGameId;
    }

    public void assignCommunity(Long communityId) {
        this.communityId = communityId;
    }

    public void activateSession() {
        this.sessionActive = true;
    }

    public void deactivateSession() {
        this.sessionActive = false;
    }
}
