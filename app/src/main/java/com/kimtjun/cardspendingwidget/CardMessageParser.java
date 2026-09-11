package com.kimtjun.cardspendingwidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CardMessageParser {
    private static final Pattern AMOUNT = Pattern.compile("([0-9,]+)원\\s*(승인취소|취소|승인)");
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{2}/\\d{2}\\s+\\d{2}:\\d{2})");

    static final class Parsed {
        final long amount;
        final String kind;

        Parsed(long amount, String kind) {
            this.amount = amount;
            this.kind = kind;
        }
    }

    private CardMessageParser() {}

    static String normalizeSender(String sender) {
        return sender == null ? "" : sender.replaceAll("[^0-9]", "");
    }

    static boolean isLotteSender(String sender) {
        return normalizeSender(sender).endsWith("15888100");
    }

    static boolean looksLikeLotteNotification(String body) {
        if (body == null) return false;
        return body.contains("1588-8100")
                || body.contains("15888100")
                || body.contains("롯데카드")
                || body.contains("디지로카");
    }

    static Parsed parse(String body) {
        if (body == null) return null;
        Matcher matcher = AMOUNT.matcher(body.replace(" ", ""));
        if (!matcher.find()) return null;
        try {
            long amount = Long.parseLong(matcher.group(1).replace(",", ""));
            if (amount <= 0L) return null;
            return new Parsed(amount, matcher.group(2));
        } catch (Exception ignored) {
            return null;
        }
    }

    static String dateTimeToken(String body, long fallbackMillis) {
        Matcher matcher = DATE_TIME.matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : Long.toString(fallbackMillis / 60000L);
    }

    static String dedupMaterial(String body, long amount, String kind, long fallbackMillis) {
        String normalized = normalizedBody(body);
        Matcher amountMatcher = AMOUNT.matcher(normalized.replace(" ", ""));
        String window = normalized;
        if (amountMatcher.find()) {
            String compact = normalized.replace(" ", "");
            int compactStart = amountMatcher.start();
            int start = Math.max(0, compactStart - 48);
            int end = Math.min(compact.length(), amountMatcher.end() + 120);
            window = compact.substring(start, end);
        }
        return amount + "|" + kind + "|" + dateTimeToken(body, fallbackMillis) + "|" + window;
    }

    static String normalizedBody(String body) {
        if (body == null) return "";
        return body.replaceAll("\\s+", " ").trim();
    }
}
