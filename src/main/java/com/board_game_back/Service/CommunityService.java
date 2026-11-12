package com.board_game_back.Service;

import com.board_game_back.Exception.AppException;
import com.board_game_back.Exception.ErrorCode;
import com.board_game_back.DTO.CommunityDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MatchParticipantRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import com.board_game_back.Repository.SeasonRankSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.board_game_back.Utils.InviteCodeUtil;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityAdminRepository communityAdminRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final MemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final BoardGameRepository boardGameRepository;
    private final PlayerGameRatingRepository playerGameRatingRepository;
    private final RoomService roomService;
    private final MatchParticipantRepository matchParticipantRepository;
    private final SeasonRankSnapshotRepository seasonRankSnapshotRepository;

    private static final int MAX_ADMINS = 5;  // 소유자 포함

    @Transactional
    public CommunityDto.Response createCommunity(CommunityDto.CreateRequest req, Long createdBy) {
        String inviteCode = InviteCodeUtil.generateUnique(communityRepository::existsByInviteCode);
        Community community = new Community(req.name(), req.region(), req.imageUrl(), createdBy);
        community.assignInviteCode(inviteCode);
        communityRepository.save(community);

        Member creator = memberRepository.findById(createdBy)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        communityAdminRepository.save(new CommunityAdmin(community, creator));
        communityMemberRepository.save(new CommunityMember(community, creator));

        if (req.adminMemberIds() != null) {
            for (Long memberId : req.adminMemberIds()) {
                if (memberId.equals(createdBy)) continue;
                if (communityAdminRepository.countByCommunityId(community.getId()) >= 5) break;
                memberRepository.findById(memberId).ifPresent(member -> {
                    communityAdminRepository.save(new CommunityAdmin(community, member));
                    if (!communityMemberRepository.existsByCommunityIdAndMemberId(community.getId(), member.getId())) {
                        communityMemberRepository.save(new CommunityMember(community, member));
                    }
                });
            }
        }

        return toResponse(community);
    }

    @Transactional
    public CommunityDto.JoinResponse joinCommunity(String inviteCode, Long memberId) {
        Community community = communityRepository.findByInviteCode(inviteCode.toUpperCase().trim())
            .orElseThrow(() -> new AppException(ErrorCode.INVALID_INVITE_CODE));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean alreadyMember = communityMemberRepository.existsByCommunityIdAndMemberId(community.getId(), memberId);
        if (!alreadyMember) {
            communityMemberRepository.save(new CommunityMember(community, member));
        }

        return CommunityDto.JoinResponse.of(toResponse(community), alreadyMember);
    }

    @Transactional(readOnly = true)
    public CommunityDto.Response getMyCommunity(Long memberId) {
        return communityRepository.findByCreatedBy(memberId)
            .map(this::toResponse)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public CommunityDto.DetailResponse getCommunityDetail(Long communityId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));

        int groupCount = (int) roomRepository.countByCommunityId(communityId);
        long memberCount = communityMemberRepository.countByCommunityId(communityId);

        // 어드민이 된 순서로 준다 — 앱이 소유자가 내려놓을 때 넘겨받을 사람(가장 오래된 어드민)을 미리 보여준다
        List<CommunityDto.AdminInfo> admins = communityAdminRepository.findByCommunityId(communityId)
            .stream()
            .sorted(CommunityAdmin.OLDEST_FIRST)
            .map(ca -> new CommunityDto.AdminInfo(ca.getMember().getId(), ca.getMember().getNickname(), ca.getMember().getProfileImage()))
            .collect(Collectors.toList());

        return new CommunityDto.DetailResponse(
            community.getId(), community.getName(), community.getRegion(),
            community.getImageUrl(), community.getStatus(),
            groupCount, memberCount, admins, community.getInviteCode(), community.getCreatedBy()
        );
    }

    @Transactional(readOnly = true)
    public List<CommunityDto.RoomResponse> getCommunityRooms(Long communityId, Long memberId) {
        List<Room> rooms = roomRepository.findByCommunityId(communityId);
        if (rooms.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> boardGameIds = rooms.stream()
            .map(Room::getBoardGameId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<Long, String> imageUrlByBoardGameId = new HashMap<>();
        boardGameRepository.findByIdIn(boardGameIds)
            .forEach(bg -> imageUrlByBoardGameId.put(bg.getId(), bg.getImageUrl()));

        List<Long> roomIds = rooms.stream()
            .map(Room::getId)
            .toList();
        Map<Long, Long> memberCountByRoomId = toLongMap(roomMemberRepository.countByRoomIds(roomIds));
        Set<Long> joinedRoomIds = memberId == null
            ? Collections.emptySet()
            : Set.copyOf(roomMemberRepository.findJoinedRoomIds(memberId, roomIds));

        return rooms.stream()
            .map(r -> {
                String imageUrl = r.getBoardGameId() == null ? null : imageUrlByBoardGameId.get(r.getBoardGameId());
                boolean isMember = joinedRoomIds.contains(r.getId());
                long memberCount = memberCountByRoomId.getOrDefault(r.getId(), 0L);
                return new CommunityDto.RoomResponse(
                    r.getId(), r.getName(), r.getInviteCode(), r.getBoardGameId(), imageUrl,
                    r.isSessionActive(), isMember, memberCount);
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public CommunityDto.Response updateCommunity(Long communityId, CommunityDto.UpdateRequest req, Long requesterId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));

        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new AppException(ErrorCode.COMMUNITY_ADMIN_ONLY_EDIT);
        }

        community.update(req.name(), req.region(), req.imageUrl());
        if (req.adminMemberIds() != null) {
            syncAdmins(community, req.adminMemberIds(), requesterId, Boolean.TRUE.equals(req.ownerLeavesAdmin()));
        }

        communityRepository.save(community);
        return toResponse(community);
    }

    /**
     * 어드민 목록을 요청대로 맞춘다. 전부 지우고 다시 넣지 않고 바뀐 것만 반영한다 —
     * 어드민이 된 시각이 남아야 소유권을 "가장 오래된 어드민"에게 넘길 수 있다.
     *
     * <p>소유자(createdBy)는 항상 어드민이고, 본인이 ownerLeavesAdmin으로 내려놓을 때만 빠진다.
     * 그때 소유권(삭제 권한 포함)은 남은 어드민 중 가장 오래된 사람에게 넘어간다.
     * 소유자가 아닌 요청자가 보낸 목록에서 소유자가 빠져 있어도 무시한다.
     *
     * @param adminMemberIds 소유자를 뺀 나머지 어드민 (소유자 id가 있어도 무시)
     */
    private void syncAdmins(Community community, List<Long> adminMemberIds, Long requesterId, boolean ownerLeavesAdmin) {
        Long ownerId = community.getCreatedBy();
        boolean ownerLeaves = ownerLeavesAdmin && ownerId.equals(requesterId);

        Set<Long> wanted = new LinkedHashSet<>(adminMemberIds);
        wanted.remove(ownerId);
        if (ownerLeaves && wanted.isEmpty()) {
            throw new AppException(ErrorCode.LAST_ADMIN_CANNOT_RESIGN);
        }
        if (wanted.size() + (ownerLeaves ? 0 : 1) > MAX_ADMINS) {
            throw new AppException(ErrorCode.ADMIN_LIMIT_EXCEEDED, MAX_ADMINS);
        }

        List<CommunityAdmin> remaining = new ArrayList<>();
        Set<Long> currentIds = new HashSet<>();
        for (CommunityAdmin admin : communityAdminRepository.findByCommunityId(community.getId())) {
            Long memberId = admin.getMember().getId();
            currentIds.add(memberId);
            boolean keep = memberId.equals(ownerId) ? !ownerLeaves : wanted.contains(memberId);
            if (keep) remaining.add(admin);
            else communityAdminRepository.delete(admin);
        }

        for (Long memberId : wanted) {
            if (currentIds.contains(memberId)) continue;
            memberRepository.findById(memberId).ifPresent(member -> {
                remaining.add(communityAdminRepository.save(new CommunityAdmin(community, member)));
                if (!communityMemberRepository.existsByCommunityIdAndMemberId(community.getId(), member.getId())) {
                    communityMemberRepository.save(new CommunityMember(community, member));
                }
            });
        }

        if (ownerLeaves) {
            CommunityAdmin next = remaining.stream()
                .min(CommunityAdmin.OLDEST_FIRST)
                .orElseThrow(() -> new AppException(ErrorCode.LAST_ADMIN_CANNOT_RESIGN));
            community.transferOwnership(next.getMember().getId());
        }
    }

    @Transactional
    public void addRoomToCommunity(Long communityId, Long roomId, Long requesterId) {
        communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new AppException(ErrorCode.COMMUNITY_ADMIN_ONLY_LINK_ROOM);
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        room.assignCommunity(communityId);
        roomRepository.save(room);
    }

    @Transactional
    public void deleteCommunity(Long communityId, Long requesterId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));
        if (!community.getCreatedBy().equals(requesterId)) {
            throw new AppException(ErrorCode.COMMUNITY_OWNER_ONLY_DELETE);
        }
        roomService.deleteRoomsOfCommunity(communityId);
        communityAdminRepository.deleteByCommunityId(communityId);
        communityMemberRepository.deleteByCommunityId(communityId);
        communityRepository.delete(community);
    }

    @Transactional(readOnly = true)
    public List<CommunityDto.Response> getJoinedCommunities(Long memberId) {
        List<Long> communityIds = communityMemberRepository.findCommunityIdsByMemberId(memberId).stream()
            .distinct()
            .toList();
        List<Community> communities = communityRepository.findAllById(communityIds).stream()
            .filter(c -> !c.getCreatedBy().equals(memberId))
            .toList();
        return buildCommunityResponses(communities);
    }

    @Transactional
    public void removeCommunityMember(Long communityId, Long memberId, Long requesterId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new AppException(ErrorCode.COMMUNITY_ADMIN_ONLY_KICK);
        }
        // 소유자는 어드민 해제도 본인만 할 수 있다 — 강퇴로 우회하지 못하게 막는다
        if (community.getCreatedBy().equals(memberId)) {
            throw new AppException(ErrorCode.COMMUNITY_OWNER_CANNOT_BE_KICKED);
        }
        detachMember(communityId, memberId);
    }

    /** 본인이 커뮤니티에서 나간다. 강퇴와 같은 정리(어드민 권한 해제·방 나가기)를 한다 */
    @Transactional
    public void leaveCommunity(Long communityId, Long memberId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_NOT_FOUND));
        // 소유자가 나가면 주인 없는 커뮤니티가 된다 — 설정에서 소유권을 넘기거나 삭제해야 한다
        if (community.getCreatedBy().equals(memberId)) {
            throw new AppException(ErrorCode.COMMUNITY_OWNER_CANNOT_LEAVE);
        }
        if (!communityMemberRepository.existsByCommunityIdAndMemberId(communityId, memberId)) {
            throw new AppException(ErrorCode.NOT_COMMUNITY_MEMBER);
        }
        detachMember(communityId, memberId);
    }

    private void detachMember(Long communityId, Long memberId) {
        communityMemberRepository.findByCommunityIdAndMemberId(communityId, memberId)
            .ifPresent(communityMemberRepository::delete);
        // 어드민을 내보내면 어드민 권한도 함께 뺀다 (멤버가 아닌 어드민이 남지 않게)
        communityAdminRepository.findByCommunityId(communityId).stream()
            .filter(admin -> admin.getMember().getId().equals(memberId))
            .forEach(communityAdminRepository::delete);
        List<Room> rooms = roomRepository.findByCommunityId(communityId);
        for (Room room : rooms) {
            // 방 멤버면 방 나가기와 같은 경로로 — 방장이면 다음 사람에게 넘기고, 혼자였으면 방을 정리한다
            if (roomMemberRepository.findByRoomIdAndMemberId(room.getId(), memberId).isPresent()) {
                roomService.leaveRoom(room.getId(), memberId, memberId);
            } else {
                playerGameRatingRepository.deleteByMember_IdAndRoom_Id(memberId, room.getId());
            }
        }
        anonymizeRecords(communityId, memberId);
    }

    /**
     * 나간 사람의 이 커뮤니티 경기 기록·지난 시즌 순위를 그 사람 몫의 '나간 멤버' 계정으로 옮긴다.
     * 참가 중이던 방은 방 나가기({@link RoomService#leaveRoom})가 방마다 이미 옮겼고, 여기서는 예전에 나간 방에 남은 줄을 정리한다.
     * 기록을 지우면 재계산 때 다른 멤버 점수가 바뀌므로, 같은 한 사람으로 남겨 리플레이 결과를 그대로 둔다.
     * '나간 멤버'는 어느 방의 멤버도 아니라 재계산이 끝나면 점수가 지워지고 랭킹에 안 나온다.
     * 다시 들어와도 옛 기록은 돌아오지 않는다(새로 시작).
     */
    private void anonymizeRecords(Long communityId, Long memberId) {
        boolean hasMatches = matchParticipantRepository.existsByMemberInCommunity(memberId, communityId);
        boolean hasSnapshots = seasonRankSnapshotRepository.existsByMemberInCommunity(memberId, communityId);
        if (!hasMatches && !hasSnapshots) return;

        Member ghost = roomService.createLeftPlaceholder();
        if (hasMatches) matchParticipantRepository.reassignMemberInCommunity(memberId, ghost, communityId);
        if (hasSnapshots) seasonRankSnapshotRepository.reassignMemberInCommunity(memberId, ghost.getId(), communityId);
    }

    @Transactional(readOnly = true)
    public List<CommunityDto.MemberInfo> getCommunityMembers(Long communityId) {
        return communityMemberRepository.findByCommunityId(communityId).stream()
            .map(cm -> new CommunityDto.MemberInfo(cm.getMember().getId(), cm.getMember().getNickname(), cm.getMember().getProfileImage()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommunityDto.Response> getMyCommunitiesList(Long memberId) {
        return buildCommunityResponses(communityRepository.findAllByCreatedBy(memberId));
    }

    private CommunityDto.Response toResponse(Community c) {
        return buildCommunityResponses(List.of(c)).stream()
            .findFirst()
            .orElseThrow();
    }

    private List<CommunityDto.Response> buildCommunityResponses(List<Community> communities) {
        if (communities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> communityIds = communities.stream()
            .map(Community::getId)
            .toList();
        Map<Long, Long> memberCountByCommunityId = toLongMap(communityMemberRepository.countByCommunityIds(communityIds));
        Map<Long, Long> groupCountRaw = toLongMap(roomRepository.countByCommunityIds(communityIds));
        Map<Long, List<CommunityDto.AdminInfo>> adminsByCommunityId = communityAdminRepository.findByCommunityIdIn(communityIds)
            .stream()
            .collect(Collectors.groupingBy(
                admin -> admin.getCommunity().getId(),
                LinkedHashMap::new,
                Collectors.mapping(
                    admin -> new CommunityDto.AdminInfo(
                        admin.getMember().getId(),
                        admin.getMember().getNickname(),
                        admin.getMember().getProfileImage()
                    ),
                    Collectors.toList()
                )
            ));

        return communities.stream()
            .map(c -> new CommunityDto.Response(
                c.getId(),
                c.getName(),
                c.getRegion(),
                c.getImageUrl(),
                c.getStatus(),
                memberCountByCommunityId.getOrDefault(c.getId(), 0L),
                groupCountRaw.getOrDefault(c.getId(), 0L).intValue(),
                adminsByCommunityId.getOrDefault(c.getId(), Collections.emptyList()),
                c.getInviteCode()
            ))
            .collect(Collectors.toList());
    }

    private Map<Long, Long> toLongMap(Collection<Object[]> rows) {
        return rows.stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).longValue()
            ));
    }
}
