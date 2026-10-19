package com.board_game_back.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Comparator;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "member_id"}))
public class CommunityAdmin {

    /** 어드민이 된 순서. 소유권은 남은 어드민 중 가장 오래된 사람에게 넘어간다. */
    public static final Comparator<CommunityAdmin> OLDEST_FIRST =
        Comparator.comparing(CommunityAdmin::getCreatedAt).thenComparing(CommunityAdmin::getId);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CommunityAdmin(Community community, Member member) {
        this.community = community;
        this.member = member;
    }
}
