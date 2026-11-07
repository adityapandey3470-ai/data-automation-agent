package com.aditya.dataautomation.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NumericParserTest {

    private static boolean sameNumber(String expected, Object actual) {
        return actual instanceof BigDecimal bd && bd.compareTo(new BigDecimal(expected)) == 0;
    }

    @Test
    void parsesIndianAndWesternMoneyFormats() {
        assertTrue(sameNumber("1200", NumericParser.parseBigDecimal("1,200")));
        assertTrue(sameNumber("1200", NumericParser.parseBigDecimal("\u20B91,200")));
        assertTrue(sameNumber("1200.50", NumericParser.parseBigDecimal("$1,200.50")));
        assertTrue(sameNumber("500", NumericParser.parseBigDecimal("Rs. 500")));
    }

    @Test
    void parsesNegativeNumbers() {
        assertTrue(sameNumber("-500", NumericParser.parseBigDecimal("(500)")));
        assertTrue(sameNumber("-500", NumericParser.parseBigDecimal("-500")));
        assertTrue(sameNumber("-500", NumericParser.parseBigDecimal("\u20B9-500")));
    }

    @Test
    void rejectsTextAndBlanks() {
        assertNull(NumericParser.parseBigDecimal("N/A"));
        assertNull(NumericParser.parseBigDecimal("abc"));
        assertNull(NumericParser.parseBigDecimal("1.2.3"));
        assertNull(NumericParser.parseBigDecimal(""));
        assertNull(NumericParser.parseBigDecimal(null));
        assertFalse(NumericParser.isNumeric("N/A"));
        assertTrue(NumericParser.isNumeric("\u20B91,200"));
    }

    @Test
    void sortOrderBlanksLastNumbersBeforeText() {
        assertTrue(NumericParser.compare(5L, "abc") < 0);
        assertTrue(NumericParser.compare("abc", 5L) > 0);
        assertTrue(NumericParser.compare(null, 1L) > 0);
        assertTrue(NumericParser.compare(1L, null) < 0);
        assertTrue(NumericParser.compare(2L, 10L) < 0);
        assertTrue(NumericParser.compare("\u20B91,200", 900L) > 0);
        assertEquals(0, NumericParser.compare(null, null));
    }

    /** Regression: mixed number/text columns used to crash List.sort ("Comparison method violates its general contract"). */
    @Test
    void sortingMixedColumnOf100kRowsDoesNotCrash() {
        Random r = new Random(7);
        String[] junk = {"N/A", "10th", "1x", "abc", "-", "TBD", "5 units", "(500)", "12%"};
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            if (r.nextInt(50) == 0) values.add(junk[r.nextInt(junk.length)]);
            else if (r.nextInt(20) == 0) values.add(null);
            else values.add((long) r.nextInt(100_000));
        }
        values.sort(NumericParser::compare);
        assertEquals(100_000, values.size());
    }
}
