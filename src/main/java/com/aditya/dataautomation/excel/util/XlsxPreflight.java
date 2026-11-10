package com.aditya.dataautomation.excel.util;

import com.aditya.dataautomation.excel.exception.InvalidExcelFileException;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public final class XlsxPreflight {

    private static final int MAX_ZIP_ENTRIES = 1000;

    private XlsxPreflight() {
    }

    public static void verify(InputStream inputStream, int maxSheets, long maxUncompressedBytes, int maxRows) {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            int sheetCount = 0;
            int entryCount = 0;
            long totalUncompressedBytes = 0;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new InvalidExcelFileException("Workbook contains too many files in archive (" + entryCount + ").");
                }

                String name = entry.getName().toLowerCase();
                boolean isSheetXml = name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml");
                if (isSheetXml) {
                    sheetCount++;
                    if (sheetCount > maxSheets) {
                        throw new InvalidExcelFileException("Workbook exceeds maximum allowed limit of " + maxSheets + " sheets.");
                    }
                }

                int sheetRowCount = 0;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    totalUncompressedBytes += read;
                    if (totalUncompressedBytes > maxUncompressedBytes) {
                        throw new InvalidExcelFileException(
                                "Workbook exceeds maximum uncompressed safety limit of " +
                                (maxUncompressedBytes / (1024 * 1024)) + " MB (zip bomb protection).");
                    }

                    if (isSheetXml && maxRows > 0) {
                        String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                        int idx = 0;
                        while ((idx = chunk.indexOf("<row", idx)) != -1) {
                            sheetRowCount++;
                            idx += 4;
                            if (sheetRowCount > maxRows) {
                                throw new InvalidExcelFileException(
                                        "Sheet exceeds the maximum allowed limit of " + maxRows + " rows.");
                            }
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (InvalidExcelFileException e) {
            throw e;
        } catch (Exception e) {
            log.warn("XlsxPreflight verification failed: {}", e.getMessage());
            throw new InvalidExcelFileException("Failed to verify workbook structure and safety limits.", e);
        }
    }

    public static void verify(InputStream inputStream, int maxSheets, long maxUncompressedBytes) {
        verify(inputStream, maxSheets, maxUncompressedBytes, 100000);
    }
}
