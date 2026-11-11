package com.board_game_back.Controller;

import com.board_game_back.DTO.UploadDto;
import com.board_game_back.Service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping("/image")
    public ResponseEntity<UploadDto.Response> uploadImage(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String oldUrl) {
        try {
            return ResponseEntity.ok(UploadDto.Response.ok(imageUploadService.uploadImage(file.getBytes(), oldUrl)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(UploadDto.Response.fail(e.getMessage()));
        }
    }

    @PostMapping("/profile-image")
    public ResponseEntity<UploadDto.Response> uploadProfileImage(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String oldUrl,
            @AuthenticationPrincipal Long requesterId) {
        try {
            String url = imageUploadService.uploadProfileImage(file.getBytes(), oldUrl, requesterId);
            return ResponseEntity.ok(UploadDto.Response.ok(url));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(UploadDto.Response.fail(e.getMessage()));
        }
    }
}
