package com.kimtjun.cardspendingwidget;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CardMessageParser {
    private static final Pattern AMOUNT = Pattern.compile("([0-9,]+)원\\s*(승인취소|취소|승인)");
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})");
    private static final Pattern CUMULATIVE = Pattern.compile("누적\\s*([0-9,]+)원");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

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
        while (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1).replace(",", ""));
                if (amount <= 0L) continue;
                return new Parsed(amount, matcher.group(2));
            } catch (Exception ignored) {}
        }
        return null;
    }

    static boolean hasExplicitDateTime(String body) {
        return DATE_TIME.matcher(body == null ? "" : body).find();
    }

    static long eventTimeMillis(String body, long fallbackMillis) {
        Matcher matcher = DATE_TIME.matcher(body == null ? "" : body);
        String month = null, day = null, hour = null, minute = null;
        while (matcher.find()) {
            month = matcher.group(1);
            day = matcher.group(2);
            hour = matcher.group(3);
            minute = matcher.group(4);
        }
        if (month == null) return fallbackMillis;

        try {
            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime fallback = Instant.ofEpochMilli(fallbackMillis).atZone(zone).toLocalDateTime();
            LocalDateTime candidate = LocalDateTime.of(
                    fallback.getYear(),
                    Integer.parseInt(month),
                    Integer.parseInt(day),
                    Integer.parseInt(hour),
                    Integer.parseInt(minute));
            if (candidate.isAfter(fallback.plusDays(2))) candidate = candidate.minusYears(1);
            return candidate.atZone(zone).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return fallbackMillis;
        }
    }

    static String dateTimeToken(String body, long fallbackMillis) {
        long eventAt = eventTimeMillis(body, fallbackMillis);
        return Instant.ofEpochMilli(eventAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DATE_TIME_FORMAT);
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
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher matcher = AMOUNT.matcher(trimmed.replace(" ", ""));
            while (matcher.find()) {
                try {
                    long found = Long.parseLong(matcher.group(1).replace(",", ""));
                    if (found == amount && matcher.group(2).equals(kind)) return sanitizeMerchant(previous);
                } catch (Exception ignored) {}
            }
            previous = trimmed;
        }
        return "";
    }

    private static String sanitizeMerchant(String value) {
        if (value == null) return "";
        String v = value.replace("[Web발신]", "").trim();
        if (v.equals("롯데카드") || v.contains("1588-8100") || v.contains("15888100")) return "";
        return v.replaceAll("\\s+", "");
    }

    static String dedupMaterial(String body, long amount, String kind, long fallbackMillis) {
        String cumulative = cumulativeToken(body);
        String merchant = merchantToken(body, amount, kind);
        return amount + "|"
                + kind + "|"
                + dateTimeToken(body, fallbackMillis) + "|"
                + cumulative + "|"
                + merchant;
    }

    static String normalizedBody(String body) {
        if (body == null) return "";
        return body.replaceAll("\\s+", " ").trim();
    }
}
