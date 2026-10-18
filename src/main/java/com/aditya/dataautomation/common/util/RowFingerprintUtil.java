package com.aditya.dataautomation.common.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Utility for generating collision-resistant row fingerprints.
 * Eliminates delimiter collisions and distinguishes actual nulls from string "null".
 */
public final class RowFingerprintUtil {

    private RowFingerprintUtil() {}

    public static String computeFingerprint(Map<String, Object> row, List<String> columns) {
        if (row == null) {
            return "§NULL_ROW§";
        }
        if (columns == null || columns.isEmpty()) {
            return "§EMPTY_COLS§";
        }

        StringBuilder sb = new StringBuilder(columns.size() * 32);
        for (String col : columns) {
            Object val = row.get(col);
            if (val == null) {
                sb.append("§N;");
            } else if (val instanceof String s) {
                sb.append("§S:").append(s.length()).append(":").append(s).append(";");
            } else if (val instanceof Number n) {
                try {
                    BigDecimal bd = (val instanceof BigDecimal b) ? b : new BigDecimal(n.toString());
                    String norm = bd.stripTrailingZeros().toPlainString();
                    sb.append("§D:").append(norm).append(";");
                } catch (Exception e) {
                    String str = n.toString();
                    sb.append("§D:").append(str).append(";");
                }
            } else if (val instanceof Boolean b) {
                sb.append("§B:").append(b).append(";");
            } else {
                String str = val.toString();
                sb.append("§O:").append(str.length()).append(":").append(str).append(";");
            }
        }
        return sb.toString();
    }
}
