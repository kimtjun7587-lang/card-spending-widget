package com.kimtjun.cardspendingwidget;

import static org.junit.Assert.*;
import org.junit.Test;

public class CardMessageParserTest {
    @Test
    public void lotteSenderIsDetected() {
        assertTrue(CardMessageParser.isLotteSender("1588-8100"));
        assertTrue(CardMessageParser.isLotteSender("15888100"));
        assertFalse(CardMessageParser.isLotteSender("1588-0000"));
    }

    @Test
    public void approvalAmountIsParsed() {
        String body = "씨유(CU)반달섬미켈란\n4,500원 승인\n김*준 디지로카 런던 6*7*\n일시불 09/11 21:44\n누적 537,400원";
        CardMessageParser.Parsed parsed = CardMessageParser.parse(body);
        assertNotNull(parsed);
        assertEquals(4500L, parsed.amount);
        assertEquals("승인", parsed.kind);
    }

    @Test
    public void cancellationIsParsedBeforeApproval() {
        String body = "가맹점 4,500원 승인취소 09/11 21:44";
        CardMessageParser.Parsed parsed = CardMessageParser.parse(body);
        assertNotNull(parsed);
        assertEquals(4500L, parsed.amount);
        assertEquals("승인취소", parsed.kind);
    }

    @Test
    public void cumulativeAmountAloneIsIgnored() {
        assertNull(CardMessageParser.parse("09월 결제대금 1,125,624원 출금되었습니다."));
    }

    @Test
    public void differentTransactionBodiesProduceDifferentDedupMaterial() {
        String one = "편의점 4,500원 승인 09/11 21:44 누적 537,400원";
        String two = "편의점 4,500원 승인 09/11 21:44 누적 541,900원";
        assertNotEquals(
                CardMessageParser.dedupMaterial(one, 4500L, "승인", 0L),
                CardMessageParser.dedupMaterial(two, 4500L, "승인", 0L));
    }
}
