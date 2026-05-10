package com.board_game_back.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

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
            deleteFromSupabase(oldUrl);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/profile-image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String oldUrl) {
        try {
            String url = uploadToSupabase(file.getBytes(), profileBucket);
            deleteFromSupabase(oldUrl);
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

    private void deleteFromSupabase(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        try {
            String marker = "/storage/v1/object/public/";
            int idx = publicUrl.indexOf(marker);
            if (idx == -1) return;
            String objectPath = publicUrl.substring(idx + marker.length());

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
