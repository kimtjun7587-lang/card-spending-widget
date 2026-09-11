package com.kimtjun.cardspendingwidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CardMessageParser {
    private static final Pattern AMOUNT = Pattern.compile("([0-9,]+)원\\s*(승인취소|취소|승인)");
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{2}/\\d{2}\\s+\\d{2}:\\d{2})");
    private static final Pattern CUMULATIVE = Pattern.compile("누적\\s*([0-9,]+)원");

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
        return parseInternal(body, false);
    }

    static Parsed parseLast(String body) {
        return parseInternal(body, true);
    }

    private static Parsed parseInternal(String body, boolean last) {
        if (body == null) return null;
        Matcher matcher = AMOUNT.matcher(body.replace(" ", ""));
        Parsed result = null;
        while (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1).replace(",", ""));
                if (amount <= 0L) continue;
                result = new Parsed(amount, matcher.group(2));
                if (!last) return result;
            } catch (Exception ignored) {}
        }
        return result;
    }

    static String dateTimeToken(String body, long fallbackMillis) {
        Matcher matcher = DATE_TIME.matcher(body == null ? "" : body);
        String result = null;
        while (matcher.find()) result = matcher.group(1);
        return result != null ? result : Long.toString(fallbackMillis / 60000L);
    }

    private static String cumulativeToken(String body) {
        Matcher matcher = CUMULATIVE.matcher(body == null ? "" : body);
        String result = "";
        while (matcher.find()) result = matcher.group(1).replace(",", "");
        return result;
    }

    private static String merchantToken(String body, long amount, String kind) {
        if (body == null) return "";
        String[] lines = body.split("\\r?\\n");
        String previous = "";
        String chosen = "";
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher matcher = AMOUNT.matcher(trimmed.replace(" ", ""));
            boolean matchedTarget = false;
            while (matcher.find()) {
                try {
                    long found = Long.parseLong(matcher.group(1).replace(",", ""));
                    if (found == amount && matcher.group(2).equals(kind)) matchedTarget = true;
                } catch (Exception ignored) {}
            }
            if (matchedTarget && !previous.isEmpty()) chosen = sanitizeMerchant(previous);
            previous = trimmed;
        }
        return chosen;
    }

    private static String sanitizeMerchant(String value) {
        if (value == null) return "";
        String v = value.replace("[Web발신]", "").trim();
        if (v.equals("롯데카드") || v.contains("1588-8100") || v.contains("15888100")) return "";
        return v.replaceAll("\\s+", "");
    }

    static String dedupMaterial(String body, long amount, String kind, long fallbackMillis) {
        return amount + "|"
                + kind + "|"
                + dateTimeToken(body, fallbackMillis) + "|"
                + cumulativeToken(body) + "|"
                + merchantToken(body, amount, kind);
    }

    static String normalizedBody(String body) {
        if (body == null) return "";
        return body.replaceAll("\\s+", " ").trim();
    }
}
