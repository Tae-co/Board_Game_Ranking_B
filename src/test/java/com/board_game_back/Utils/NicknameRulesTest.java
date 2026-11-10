package com.board_game_back.Utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameRulesTest {

    @Test
    void 한글_영문_숫자_이모지는_허용() {
        assertThat(NicknameRules.isValid("홍길동")).isTrue();
        assertThat(NicknameRules.isValid("abc123")).isTrue();
        assertThat(NicknameRules.isValid("🎲주사위")).isTrue();
        assertThat(NicknameRules.isValid("❤️하트")).isTrue();
        assertThat(NicknameRules.isValid("👍🏽굿")).isTrue();
        assertThat(NicknameRules.isValid("👨‍👩‍👧")).isTrue();
        assertThat(NicknameRules.isValid("🇰🇷한국")).isTrue();
    }

    @Test
    void 특수문자_공백은_금지() {
        assertThat(NicknameRules.isValid("a%b")).isFalse();
        assertThat(NicknameRules.isValid("a_b")).isFalse();
        assertThat(NicknameRules.isValid("a b")).isFalse();
        assertThat(NicknameRules.isValid("#1등")).isFalse();
        assertThat(NicknameRules.isValid("★별★")).isFalse();
        assertThat(NicknameRules.isValid("©저작")).isFalse();
        assertThat(NicknameRules.isValid("<script>")).isFalse();
    }

    @Test
    void 길이는_2에서_20() {
        assertThat(NicknameRules.isValid("가")).isFalse();
        assertThat(NicknameRules.isValid("가".repeat(20))).isTrue();
        assertThat(NicknameRules.isValid("가".repeat(21))).isFalse();
        assertThat(NicknameRules.isValid(null)).isFalse();
    }

    @Test
    void 소셜_이름은_특수문자를_지우고_비면_기본값() {
        assertThat(NicknameRules.sanitize("John Smith", "플레이어")).isEqualTo("JohnSmith");
        assertThat(NicknameRules.sanitize("★별★🎲", "플레이어")).isEqualTo("별🎲");
        assertThat(NicknameRules.sanitize("%%%", "플레이어")).isEqualTo("플레이어");
        assertThat(NicknameRules.sanitize(null, "플레이어")).isEqualTo("플레이어");
        assertThat(NicknameRules.sanitize("가".repeat(25), "플레이어")).hasSize(20);
    }
}
