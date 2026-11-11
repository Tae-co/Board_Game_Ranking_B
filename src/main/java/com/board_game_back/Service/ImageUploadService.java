package com.board_game_back.Service;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 커뮤니티·프로필 이미지를 Supabase Storage에 올리고, 바꾼 이전 이미지를 지운다 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final String PUBLIC_MARKER = "/storage/v1/object/public/";

    private final MemberRepository memberRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.profile-bucket}")
    private String profileBucket;

    /** 커뮤니티 이미지 업로드. 실패하면 원인 예외를 그대로 던진다 (컨트롤러가 {"error": 메시지}로 응답) */
    public String uploadImage(byte[] bytes, String oldUrl) {
        String url = upload(bytes, bucket);
        delete(oldUrl, bucket);
        return url;
    }

    /**
     * 프로필 이미지 업로드.
     * 소유권 검증: oldUrl이 요청자 본인의 현재 프로필 이미지일 때만 삭제한다. (#8)
     * 이게 없으면 로그인 사용자가 남의 이미지 URL을 넘겨 임의 삭제할 수 있다.
     */
    public String uploadProfileImage(byte[] bytes, String oldUrl, Long requesterId) {
        String url = upload(bytes, profileBucket);
        Member me = requesterId == null ? null : memberRepository.findById(requesterId).orElse(null);
        if (me != null && oldUrl != null && oldUrl.equals(me.getProfileImage())) {
            delete(oldUrl, profileBucket);
        }
        return url;
    }

    private String upload(byte[] bytes, String targetBucket) {
        String filename = UUID.randomUUID() + ".jpg";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.IMAGE_JPEG);

        restTemplate.exchange(
            supabaseUrl + "/storage/v1/object/" + targetBucket + "/" + filename,
            HttpMethod.PUT,
            new HttpEntity<>(bytes, headers),
            String.class
        );
        return supabaseUrl + PUBLIC_MARKER + targetBucket + "/" + filename;
    }

    private void delete(String publicUrl, String expectedBucket) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        try {
            // 우리 Supabase 프로젝트의 URL이 아니면 무시 (임의 프로젝트 오브젝트 삭제 차단). (#8)
            if (!publicUrl.startsWith(supabaseUrl + PUBLIC_MARKER)) return;
            String objectPath = publicUrl.substring(publicUrl.indexOf(PUBLIC_MARKER) + PUBLIC_MARKER.length());
            // 지정된 버킷 밖이면 무시 (크로스 버킷 삭제 차단).
            if (!objectPath.startsWith(expectedBucket + "/")) return;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceRoleKey);
            restTemplate.exchange(
                supabaseUrl + "/storage/v1/object/" + objectPath,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class
            );
        } catch (Exception ignored) {
            // 삭제 실패는 무시 — 업로드 성공이 우선
        }
    }
}
