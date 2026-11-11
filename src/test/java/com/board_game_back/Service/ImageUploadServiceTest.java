package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.board_game_back.Entity.Member;
import com.board_game_back.Repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/** UploadController에서 옮겨 온 이전 이미지 삭제 규칙 (#8: 남의 이미지·다른 프로젝트·다른 버킷은 안 지움) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageUploadServiceTest {

    private static final String BASE = "https://proj.supabase.co";
    private static final String PUBLIC = BASE + "/storage/v1/object/public/";

    @Mock private MemberRepository memberRepository;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private ImageUploadService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "supabaseUrl", BASE);
        ReflectionTestUtils.setField(service, "serviceRoleKey", "key");
        ReflectionTestUtils.setField(service, "bucket", "community");
        ReflectionTestUtils.setField(service, "profileBucket", "profile");
    }

    private void verifyDeleted(String objectPath) {
        verify(restTemplate).exchange(eq(BASE + "/storage/v1/object/" + objectPath),
            eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
    }

    private void verifyNothingDeleted() {
        verify(restTemplate, never()).exchange(any(String.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
    }

    private void givenProfileImage(long memberId, String url) {
        Member m = Member.builder().nickname("앨리스").role("USER").build();
        m.updateProfileImage(url);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
    }

    @Test
    void 업로드하면_지정한_버킷의_공개_URL을_돌려준다() {
        String url = service.uploadImage(new byte[]{1}, null);

        assertThat(url).startsWith(PUBLIC + "community/").endsWith(".jpg");
        verify(restTemplate).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
        verifyNothingDeleted();
    }

    @Test
    void 커뮤니티_이미지는_같은_버킷의_이전_이미지를_지운다() {
        service.uploadImage(new byte[]{1}, PUBLIC + "community/old.jpg");
        verifyDeleted("community/old.jpg");
    }

    @Test
    void 다른_프로젝트나_다른_버킷의_URL은_지우지_않는다() {
        service.uploadImage(new byte[]{1}, "https://other.supabase.co/storage/v1/object/public/community/old.jpg");
        service.uploadImage(new byte[]{1}, PUBLIC + "profile/someone.jpg");
        verifyNothingDeleted();
    }

    @Test
    void 프로필_이미지는_본인의_현재_사진일_때만_지운다() {
        givenProfileImage(1L, PUBLIC + "profile/mine.jpg");

        service.uploadProfileImage(new byte[]{1}, PUBLIC + "profile/someone-else.jpg", 1L);
        verifyNothingDeleted();

        service.uploadProfileImage(new byte[]{1}, PUBLIC + "profile/mine.jpg", 1L);
        verifyDeleted("profile/mine.jpg");
    }

    @Test
    void 요청자를_모르면_프로필_이전_이미지를_지우지_않는다() {
        service.uploadProfileImage(new byte[]{1}, PUBLIC + "profile/mine.jpg", null);
        verifyNothingDeleted();
    }

    @Test
    void 삭제가_실패해도_업로드_결과는_돌려준다() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("storage down"));

        assertThat(service.uploadImage(new byte[]{1}, PUBLIC + "community/old.jpg")).startsWith(PUBLIC + "community/");
    }
}
