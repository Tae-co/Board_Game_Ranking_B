package com.board_game_back.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 닉네임 규칙: 2~20자, 글자(한글·영문 등)·숫자·이모지·띄어쓰기만 허용. %·_ 같은 특수문자는 금지.
 * 띄어쓰기는 단어 사이 한 칸만 (앞뒤·연속 공백 불가).
 * 프론트 src/utils/nickname.js 와 같은 규칙을 유지할 것.
 */
public final class NicknameRules {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 20;

    // 이모지: 기본이 컬러 이모지(Emoji_Presentation, 국기·피부색 포함)이거나 그림문자+FE0F(❤️).
    // Extended_Pictographic 단독은 ★·©·™ 같은 기호까지 포함하므로 FE0F가 붙을 때만 허용.
    // ZWJ(200D)·태그(E0020~E007F)는 합성 이모지용, FE0F·20E3(1️⃣)은 \p{M}에 포함
    private static final String TOKEN =
        "(?:\\p{IsExtended_Pictographic}\\x{FE0F}"
        + "|[\\p{L}\\p{M}\\p{Nd}\\p{IsEmoji_Presentation}\\x{200D}\\x{E0020}-\\x{E007F}])";
    private static final Pattern VALID = Pattern.compile("^" + TOKEN + "+(?: " + TOKEN + "+)*$");
    private static final Pattern ALLOWED = Pattern.compile(TOKEN + "| ");

    private NicknameRules() {}

    public static boolean isValid(String nickname) {
        return nickname != null
            && nickname.length() >= MIN_LENGTH
            && nickname.length() <= MAX_LENGTH
            && VALID.matcher(nickname).matches();
    }

    /** 소셜 가입용: 허용 안 되는 문자를 지우고(공백은 한 칸으로) 20자로 자른다. 2자 미만이 되면 fallback. */
    public static String sanitize(String raw, String fallback) {
        if (raw == null) return fallback;
        StringBuilder sb = new StringBuilder();
        Matcher m = ALLOWED.matcher(raw);
        while (m.find()) sb.append(m.group());
        String cleaned = sb.toString().replaceAll(" {2,}", " ").trim();
        if (cleaned.length() > MAX_LENGTH) {
            int end = MAX_LENGTH;
            if (Character.isHighSurrogate(cleaned.charAt(end - 1))) end--;
            cleaned = cleaned.substring(0, end).trim();
        }
        return cleaned.length() < MIN_LENGTH ? fallback : cleaned;
    }
}
