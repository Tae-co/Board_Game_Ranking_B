package com.board_game_back.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String region;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    @Column(unique = true, length = 6)
    private String inviteCode;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommunityAdmin> admins = new ArrayList<>();

    public Community(String name, String region, String imageUrl, Long createdBy) {
        this.name = name;
        this.region = region;
        this.imageUrl = imageUrl;
        this.createdBy = createdBy;
    }

    public void update(String name, String region, String imageUrl) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        if (region != null) this.region = region;
        if (imageUrl != null) this.imageUrl = imageUrl;
    }

    public void assignInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
