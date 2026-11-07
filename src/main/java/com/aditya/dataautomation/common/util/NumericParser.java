package com.aditya.dataautomation.common.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NumericParser {

    private static final Pattern CURRENCY_SYMBOLS = Pattern.compile("^[\\$€£¥₹₩]|^(?:Rs\\.?|INR|USD|EUR|GBP)\\s*", Pattern.CASE_INSENSITIVE);

    private NumericParser() {
    }

    public static BigDecimal parseBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof BigInteger bi) return new BigDecimal(bi);
        if (val instanceof Long l) return BigDecimal.valueOf(l);
        if (val instanceof Integer i) return BigDecimal.valueOf(i);
        if (val instanceof Short s) return BigDecimal.valueOf(s);
        if (val instanceof Byte b) return BigDecimal.valueOf(b);
        if (val instanceof Double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return BigDecimal.valueOf(d);
        }
        if (val instanceof Float f) {
            if (Float.isNaN(f) || Float.isInfinite(f)) return null;
            return BigDecimal.valueOf(f.doubleValue());
        }
        if (val instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (Exception e) {
                return null;
            }
        }

        String str = val.toString();
        if (str == null) return null;
        String s = str.replace('\u00A0', ' ').trim();
        if (s.isEmpty()) return null;

        boolean isNegative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            isNegative = true;
            s = s.substring(1, s.length() - 1).trim();
        } else if (s.endsWith("-")) {
            isNegative = true;
            s = s.substring(0, s.length() - 1).trim();
        } else if (s.startsWith("-")) {
            isNegative = true;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }

        s = CURRENCY_SYMBOLS.matcher(s).replaceFirst("").trim();
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }

        s = s.replace(",", "").replace(" ", "").replace("_", "");
        if (s.isEmpty()) return null;

        try {
            BigDecimal result = new BigDecimal(s);
            return isNegative ? result.negate() : result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDouble(Object val) {
        BigDecimal bd = parseBigDecimal(val);
        return bd != null ? bd.doubleValue() : null;
    }

    public static boolean isNumeric(Object val) {
        return parseBigDecimal(val) != null;
    }

    public static int compare(Object a, Object b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        BigDecimal bdA = parseBigDecimal(a);
        BigDecimal bdB = parseBigDecimal(b);

        if (bdA != null && bdB != null) {
            return bdA.compareTo(bdB);
        }
        if (bdA != null) {
            return -1;
        }
        if (bdB != null) {
            return 1;
        }

        String s1 = a.toString();
        String s2 = b.toString();
        int cmp = s1.compareToIgnoreCase(s2);
        if (cmp != 0) {
            return cmp;
        }
        return s1.compareTo(s2);
    }
}