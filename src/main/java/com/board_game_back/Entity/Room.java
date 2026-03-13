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
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 방 이름 (예: '우리 가족', '보드게임 동아리')

    @Column(unique = true)
    private String inviteCode; // 6자리 랜덤 초대 코드

    private Long boardGameId; // 방에 고정된 게임

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<RoomMember> roomMembers = new ArrayList<>();

    public Room(String name, String inviteCode, Long boardGameId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.boardGameId = boardGameId;
    }
}
