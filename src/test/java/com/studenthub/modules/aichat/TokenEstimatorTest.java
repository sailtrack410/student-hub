package com.studenthub.modules.aichat;

import com.studenthub.common.util.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimatorTest {

    @Test
    void emptyTextStillHasMessageOverhead() {
        assertEquals(4, TokenEstimator.estimate(null));
        assertEquals(4, TokenEstimator.estimate(""));
    }

    @Test
    void chineseTextUsesCharsPerToken() {
        int tokens = TokenEstimator.estimate("宿舍报修一般三天内处理完毕", 1.5);
        assertTrue(tokens >= 14 && tokens <= 26, "unexpected tokens: " + tokens);
    }

    @Test
    void englishTextIsCheaperPerChar() {
        int english = TokenEstimator.estimate("The quick brown fox jumps over the lazy dog", 1.5);
        int chinese = TokenEstimator.estimate("敏捷的棕色狐狸跳过了懒狗这句话差不多长", 1.5);
        assertTrue(english < chinese, "english=" + english + ", chinese=" + chinese);
    }

    @Test
    void ratioMustNotBreakEstimate() {
        assertTrue(TokenEstimator.estimate("测试文本", 0) > 0);
        assertTrue(TokenEstimator.estimate("测试文本", -1) > 0);
    }
}
