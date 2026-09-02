package com.board_game_back.Service;

import com.board_game_back.DTO.CommunityDto;
import com.board_game_back.Entity.BoardGame;
import com.board_game_back.Entity.Community;
import com.board_game_back.Entity.CommunityAdmin;
import com.board_game_back.Entity.CommunityMember;
import com.board_game_back.Exception.CommunityFullException;
import com.board_game_back.Utils.PlanLimits;
import com.board_game_back.Entity.Member;
import com.board_game_back.Entity.Room;
import com.board_game_back.Repository.BoardGameRepository;
import com.board_game_back.Repository.CommunityAdminRepository;
import com.board_game_back.Repository.CommunityMemberRepository;
import com.board_game_back.Repository.CommunityRepository;
import com.board_game_back.Repository.MemberRepository;
import com.board_game_back.Repository.PlayerGameRatingRepository;
import com.board_game_back.Repository.RoomMemberRepository;
import com.board_game_back.Repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final SubscriptionService subscriptionService;

    @Transactional
    public CommunityDto.Response createCommunity(CommunityDto.CreateRequest req, Long createdBy) {
        String inviteCode = InviteCodeUtil.generateUnique(communityRepository::existsByInviteCode);
        Community community = new Community(req.name(), req.region(), req.imageUrl(), createdBy);
        community.assignInviteCode(inviteCode);
        communityRepository.save(community);

        Member creator = memberRepository.findById(createdBy)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

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
    public CommunityDto.Response joinCommunity(String inviteCode, Long memberId) {
        Community community = communityRepository.findByInviteCode(inviteCode.toUpperCase().trim())
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        if (!communityMemberRepository.existsByCommunityIdAndMemberId(community.getId(), memberId)) {
            // 무료 커뮤니티는 인원 한도가 있다. 다른 사람이 들어오는 동작이라 그 사람 앱을
            // 믿을 수 없어서 서버가 막는다 (나머지 한도는 운영자 본인 화면에서만 발동).
            // 여기서는 Pro 얘기를 하지 않는다 — 새로 온 사람 앞에서 페이월이 터지면 안 된다.
            // 구독 유도는 운영자가 초대를 누를 때 그쪽 화면에서 한다.
            long memberCount = communityMemberRepository.countByCommunityId(community.getId());
            if (memberCount >= PlanLimits.FREE_MEMBERS && !subscriptionService.isCommunityPro(community.getId(), community.getCreatedBy())) {
                // IllegalStateException은 GlobalExceptionHandler가 409 + message로 내보낸다
                throw new CommunityFullException(memberCount, PlanLimits.FREE_MEMBERS);
            }
            communityMemberRepository.save(new CommunityMember(community, member));
        }

        return toResponse(community);
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
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        int groupCount = (int) roomRepository.countByCommunityId(communityId);
        long memberCount = communityMemberRepository.countByCommunityId(communityId);

        List<CommunityDto.AdminInfo> admins = communityAdminRepository.findByCommunityId(communityId)
            .stream()
            .map(ca -> new CommunityDto.AdminInfo(ca.getMember().getId(), ca.getMember().getNickname(), ca.getMember().getProfileImage()))
            .collect(Collectors.toList());

        return new CommunityDto.DetailResponse(
            community.getId(), community.getName(), community.getRegion(),
            community.getImageUrl(), community.getStatus(),
            groupCount, memberCount, admins, community.getInviteCode()
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
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));

        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new SecurityException("커뮤니티 어드민만 수정할 수 있습니다.");
        }

        community.update(req.name(), req.region(), req.imageUrl());

        communityAdminRepository.deleteByCommunityId(communityId);

        Member creator = memberRepository.findById(community.getCreatedBy())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        communityAdminRepository.save(new CommunityAdmin(community, creator));

        if (req.adminMemberIds() != null) {
            for (Long memberId : req.adminMemberIds()) {
                if (memberId.equals(community.getCreatedBy())) continue;
                if (communityAdminRepository.countByCommunityId(communityId) >= 5) break;
                memberRepository.findById(memberId).ifPresent(member -> {
                    communityAdminRepository.save(new CommunityAdmin(community, member));
                    if (!communityMemberRepository.existsByCommunityIdAndMemberId(communityId, member.getId())) {
                        communityMemberRepository.save(new CommunityMember(community, member));
                    }
                });
            }
        }

        communityRepository.save(community);
        return toResponse(community);
    }

    @Transactional
    public void addRoomToCommunity(Long communityId, Long roomId, Long requesterId) {
        communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new SecurityException("커뮤니티 어드민만 그룹을 연결할 수 있습니다.");
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
        room.assignCommunity(communityId);
        roomRepository.save(room);
    }

    @Transactional
    public void deleteCommunity(Long communityId, Long requesterId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));
        if (!community.getCreatedBy().equals(requesterId)) {
            throw new SecurityException("커뮤니티 생성자만 삭제할 수 있습니다.");
        }
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
        communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 커뮤니티입니다."));
        if (!communityAdminRepository.existsByCommunityIdAndMemberId(communityId, requesterId)) {
            throw new SecurityException("커뮤니티 어드민만 멤버를 제거할 수 있습니다.");
        }
        communityMemberRepository.findByCommunityIdAndMemberId(communityId, memberId)
            .ifPresent(communityMemberRepository::delete);
        List<Room> rooms = roomRepository.findByCommunityId(communityId);
        for (Room room : rooms) {
            playerGameRatingRepository.deleteByMember_IdAndRoom_Id(memberId, room.getId());
            roomMemberRepository.deleteByRoomIdAndMemberId(room.getId(), memberId);
        }
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
