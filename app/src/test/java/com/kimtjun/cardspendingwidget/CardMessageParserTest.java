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
        String body = "가맹점\n4,500원 승인취소\n09/11 21:44";
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
    public void latestApprovalWinsWhenNotificationContainsHistory() {
        String body = "롯데카드\n편의점\n4,500원 승인\n09/11 20:10\n누적 532,900원\n카페\n8,500원 승인\n09/11 21:44\n누적 541,400원";
        CardMessageParser.Parsed parsed = CardMessageParser.parseLast(body);
        assertNotNull(parsed);
        assertEquals(8500L, parsed.amount);
        assertEquals("승인", parsed.kind);
    }

    @Test
    public void differentCumulativeTotalsProduceDifferentDedupMaterial() {
        String one = "편의점\n4,500원 승인\n09/11 21:44\n누적 537,400원";
        String two = "편의점\n4,500원 승인\n09/11 21:44\n누적 541,900원";
        assertNotEquals(
                CardMessageParser.dedupMaterial(one, 4500L, "승인", 0L),
                CardMessageParser.dedupMaterial(two, 4500L, "승인", 0L));
    }

    @Test
    public void smsAndNotificationWrapperShareSameDedupMaterial() {
        String sms = "씨유(CU)반달섬미켈란\n4,500원 승인\n김*준 디지로카 런던 6*7*\n일시불 09/11 21:44\n누적 537,400원";
        String notification = "롯데카드\n1588-8100\n" + sms;
        assertEquals(
                CardMessageParser.dedupMaterial(sms, 4500L, "승인", 0L),
                CardMessageParser.dedupMaterial(notification, 4500L, "승인", 0L));
    }
}
