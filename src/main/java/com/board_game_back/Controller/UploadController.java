package com.board_game_back.Controller;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final MemberRepository memberRepository;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.profile-bucket}")
    private String profileBucket;

    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String oldUrl) {
        try {
            String url = uploadToSupabase(file.getBytes(), bucket);
            deleteFromSupabase(oldUrl, bucket);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/profile-image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String oldUrl,
            @AuthenticationPrincipal Long requesterId) {
        try {
            String url = uploadToSupabase(file.getBytes(), profileBucket);
            // 소유권 검증: oldUrl이 요청자 본인의 현재 프로필 이미지일 때만 삭제한다. (#8)
            // 이게 없으면 로그인 사용자가 남의 이미지 URL을 넘겨 임의 삭제할 수 있다.
            Member me = requesterId == null ? null : memberRepository.findById(requesterId).orElse(null);
            if (me != null && oldUrl != null && oldUrl.equals(me.getProfileImage())) {
                deleteFromSupabase(oldUrl, profileBucket);
            }
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private String uploadToSupabase(byte[] bytes, String targetBucket) {
        String filename = UUID.randomUUID() + ".jpg";
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + targetBucket + "/" + filename;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.IMAGE_JPEG);

        restTemplate.exchange(uploadUrl, HttpMethod.PUT, new HttpEntity<>(bytes, headers), String.class);

        return supabaseUrl + "/storage/v1/object/public/" + targetBucket + "/" + filename;
    }

    private void deleteFromSupabase(String publicUrl, String expectedBucket) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        try {
            String marker = "/storage/v1/object/public/";
            // 우리 Supabase 프로젝트의 URL이 아니면 무시 (임의 프로젝트 오브젝트 삭제 차단). (#8)
            if (!publicUrl.startsWith(supabaseUrl + marker)) return;
            String objectPath = publicUrl.substring(publicUrl.indexOf(marker) + marker.length());
            // 지정된 버킷 밖이면 무시 (크로스 버킷 삭제 차단).
            if (!objectPath.startsWith(expectedBucket + "/")) return;

            RestTemplate restTemplate = new RestTemplate();
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
