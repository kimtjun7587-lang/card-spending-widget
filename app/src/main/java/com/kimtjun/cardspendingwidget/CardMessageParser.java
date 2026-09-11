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

    static String dedupMaterial(String body, long amount, String kind, long fallbackMillis) {
        String compact = normalizedBody(body).replace(" ", "");
        Matcher matcher = AMOUNT.matcher(compact);
        int chosenStart = -1;
        int chosenEnd = -1;
        while (matcher.find()) {
            try {
                long foundAmount = Long.parseLong(matcher.group(1).replace(",", ""));
                String foundKind = matcher.group(2);
                if (foundAmount == amount && foundKind.equals(kind)) {
                    chosenStart = matcher.start();
                    chosenEnd = matcher.end();
                }
            } catch (Exception ignored) {}
        }

        String window = compact;
        if (chosenStart >= 0) {
            int start = Math.max(0, chosenStart - 48);
            int end = Math.min(compact.length(), chosenEnd + 120);
            window = compact.substring(start, end);
        }
        return amount + "|" + kind + "|" + dateTimeToken(body, fallbackMillis) + "|" + window;
    }

    static String normalizedBody(String body) {
        if (body == null) return "";
        return body.replaceAll("\\s+", " ").trim();
    }
}
