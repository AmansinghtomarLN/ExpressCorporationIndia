package com.mahavircourier.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the first worksheet of an .xls or .xlsx file into rows of text cells.
 * Row 0 of the result is Excel row 1.
 */
public final class ExcelGridReader {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_COLS = 40;

    private ExcelGridReader() {
    }

    public static List<List<String>> read(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Choose an Excel file");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Excel file is too large");
        }
        if (isZip(data)) {
            return readXlsx(data);
        }
        if (isOle(data)) {
            return readXls(data);
        }
        String head = new String(data, 0, Math.min(data.length, 200), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("<?xml") || head.startsWith("<workbook") || head.contains("<table")) {
            return readXml(data);
        }
        throw new IllegalArgumentException("Use an Excel file (.xls or .xlsx)");
    }

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K';
    }

    private static boolean isOle(byte[] data) {
        return data.length > 8
                && (data[0] & 0xFF) == 0xD0
                && (data[1] & 0xFF) == 0xCF
                && (data[2] & 0xFF) == 0x11
                && (data[3] & 0xFF) == 0xE0;
    }

    private static List<List<String>> readXlsx(byte[] data) {
        try {
            Map<String, byte[]> entries = new TreeMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    entries.put(entry.getName().replace('\\', '/'), readLimited(zip, MAX_BYTES));
                }
            }
            List<String> shared = readSharedStrings(entries.get("xl/sharedStrings.xml"));
            String sheetName = firstSheetName(entries);
            byte[] sheet = entries.get(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("The Excel file has no worksheet");
            }
            return readXlsxSheet(sheet, shared);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read that Excel file");
        }
    }

    private static String firstSheetName(Map<String, byte[]> entries) {
        for (String name : entries.keySet()) {
            if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                return name;
            }
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static List<String> readSharedStrings(byte[] xml) throws Exception {
        List<String> strings = new ArrayList<>();
        if (xml == null || xml.length == 0) {
            return strings;
        }
        Document doc = parseXml(xml);
        NodeList items = doc.getElementsByTagName("si");
        for (int i = 0; i < items.getLength(); i++) {
            StringBuilder text = new StringBuilder();
            NodeList parts = ((Element) items.item(i)).getElementsByTagName("t");
            for (int p = 0; p < parts.getLength(); p++) {
                text.append(parts.item(p).getTextContent());
            }
            strings.add(text.toString());
        }
        return strings;
    }

    private static List<List<String>> readXlsxSheet(byte[] xml, List<String> shared) throws Exception {
        Document doc = parseXml(xml);
        Map<Integer, Map<Integer, String>> cells = new TreeMap<>();
        NodeList rows = doc.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            int rowNumber = intAttr(row, "r", i + 1);
            int nextCol = 0;
            Node child = row.getFirstChild();
            while (child != null) {
                if (child instanceof Element cell && "c".equals(cell.getTagName())) {
                    int col = columnIndex(cell.getAttribute("r"), nextCol);
                    nextCol = col + 1;
                    put(cells, rowNumber, col, xlsxValue(cell, shared));
                }
                child = child.getNextSibling();
            }
        }
        return toGrid(cells);
    }

    private static String xlsxValue(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList parts = cell.getElementsByTagName("t");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.getLength(); i++) {
                text.append(parts.item(i).getTextContent());
            }
            return text.toString().trim();
        }
        String raw = directChildText(cell, "v");
        if (raw.isEmpty()) {
            return "";
        }
        if ("s".equals(type)) {
            int idx = Integer.parseInt(raw.trim());
            return idx >= 0 && idx < shared.size() ? shared.get(idx).trim() : "";
        }
        if ("b".equals(type)) {
            return "1".equals(raw.trim()) ? "TRUE" : "FALSE";
        }
        return plainNumber(raw.trim());
    }

    private static List<List<String>> readXml(byte[] data) {
        try {
            Document doc = parseXml(data);
            if (doc.getElementsByTagName("Workbook").getLength() > 0
                    || doc.getElementsByTagName("Table").getLength() > 0 && doc.getElementsByTagName("Row").getLength() > 0) {
                return readSpreadsheetMl(doc);
            }
            return readHtmlTable(doc);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read that Excel file");
        }
    }

    private static List<List<String>> readSpreadsheetMl(Document doc) {
        Map<Integer, Map<Integer, String>> cells = new TreeMap<>();
        NodeList rows = doc.getElementsByTagName("Row");
        int rowNumber = 0;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            rowNumber = intAttr(row, "Index", intAttr(row, "ss:Index", rowNumber + 1));
            int col = 0;
            Node child = row.getFirstChild();
            while (child != null) {
                if (child instanceof Element cell && cell.getTagName().endsWith("Cell")) {
                    int index = intAttr(cell, "Index", intAttr(cell, "ss:Index", col + 1));
                    col = index;
                    put(cells, rowNumber, col - 1, cell.getTextContent().trim());
                }
                child = child.getNextSibling();
            }
        }
        return toGrid(cells);
    }

    private static List<List<String>> readHtmlTable(Document doc) {
        NodeList rows = doc.getElementsByTagName("tr");
        if (rows.getLength() == 0) {
            throw new IllegalArgumentException("The Excel file has no worksheet");
        }
        Map<Integer, Map<Integer, String>> cells = new TreeMap<>();
        for (int r = 0; r < rows.getLength(); r++) {
            NodeList cols = ((Element) rows.item(r)).getElementsByTagName("td");
            if (cols.getLength() == 0) {
                cols = ((Element) rows.item(r)).getElementsByTagName("th");
            }
            for (int c = 0; c < cols.getLength(); c++) {
                put(cells, r + 1, c, cols.item(c).getTextContent().trim());
            }
        }
        return toGrid(cells);
    }

    private static List<List<String>> readXls(byte[] data) {
        try {
            byte[] workbook = OleStorage.workbook(data);
            return BiffSheet.readFirstSheet(workbook);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read that Excel file");
        }
    }

    private static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static byte[] readLimited(ZipInputStream zip, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = zip.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                throw new IllegalArgumentException("Excel file is too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String directChildText(Element parent, String name) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element.getTextContent() == null ? "" : element.getTextContent();
            }
            child = child.getNextSibling();
        }
        return "";
    }

    private static int intAttr(Element element, String name, int fallback) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            int colon = name.indexOf(':');
            if (colon >= 0) {
                value = element.getAttribute(name.substring(colon + 1));
            }
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int columnIndex(String ref, int fallback) {
        if (ref == null || ref.isBlank()) {
            return fallback;
        }
        int col = 0;
        int i = 0;
        while (i < ref.length()) {
            char c = ref.charAt(i);
            if (c >= '0' && c <= '9') {
                break;
            }
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                return fallback;
            }
            i++;
        }
        return col == 0 ? fallback : col - 1;
    }

    private static String plainNumber(String raw) {
        try {
            return new BigDecimal(raw).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void put(Map<Integer, Map<Integer, String>> cells, int row, int col, String value) {
        if (row < 1 || row > MAX_ROWS || col < 0 || col >= MAX_COLS) {
            return;
        }
        cells.computeIfAbsent(row, key -> new TreeMap<>()).put(col, value == null ? "" : value.trim());
    }

    private static List<List<String>> toGrid(Map<Integer, Map<Integer, String>> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("The Excel file has no worksheet");
        }
        int maxRow = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        int maxCol = 0;
        for (Map<Integer, String> row : cells.values()) {
            for (Integer col : row.keySet()) {
                maxCol = Math.max(maxCol, col);
            }
        }
        List<List<String>> grid = new ArrayList<>();
        for (int row = 1; row <= maxRow; row++) {
            Map<Integer, String> source = cells.getOrDefault(row, Map.of());
            List<String> values = new ArrayList<>();
            for (int col = 0; col <= maxCol; col++) {
                values.add(source.getOrDefault(col, ""));
            }
            grid.add(values);
        }
        return grid;
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int u32(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static final class OleStorage {
        private static byte[] workbook(byte[] file) {
            int sectorShift = u16(file, 0x1E);
            int sectorSize = 1 << sectorShift;
            int miniShift = u16(file, 0x20);
            int miniSize = 1 << miniShift;
            int fatCount = u32(file, 0x2C);
            int dirStart = u32(file, 0x30);
            int miniCutoff = u32(file, 0x38);
            int miniFatStart = u32(file, 0x3C);
            int difatCount = u32(file, 0x48);
            int[] fat = readFat(file, sectorSize, fatCount, difatCount);
            byte[] directory = readChain(file, sectorSize, fat, dirStart, Integer.MAX_VALUE, true);
            Entry root = null;
            Entry workbook = null;
            for (int offset = 0; offset + 128 <= directory.length; offset += 128) {
                Entry entry = Entry.read(directory, offset);
                if (entry == null) {
                    continue;
                }
                if (entry.type == 5) {
                    root = entry;
                }
                if (entry.type == 2 && ("Workbook".equalsIgnoreCase(entry.name) || "Book".equalsIgnoreCase(entry.name))) {
                    workbook = entry;
                }
            }
            if (workbook == null) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
            if (workbook.size < miniCutoff && root != null) {
                byte[] miniStream = readChain(file, sectorSize, fat, root.start, root.size, true);
                int[] miniFat = readMiniFat(file, sectorSize, fat, miniFatStart);
                return readChain(miniStream, miniSize, miniFat, workbook.start, workbook.size, false);
            }
            return readChain(file, sectorSize, fat, workbook.start, workbook.size, true);
        }

        private static int[] readFat(byte[] file, int sectorSize, int fatCount, int difatCount) {
            List<Integer> fatSectors = new ArrayList<>();
            for (int i = 0; i < 109 && fatSectors.size() < fatCount; i++) {
                int sector = u32(file, 0x4C + i * 4);
                if (sector < 0) {
                    break;
                }
                fatSectors.add(sector);
            }
            int difatSector = u32(file, 0x44);
            int guard = 0;
            while (difatCount > 0 && difatSector >= 0 && guard++ < 10000) {
                int pos = (difatSector + 1) * sectorSize;
                int entries = (sectorSize / 4) - 1;
                for (int i = 0; i < entries && fatSectors.size() < fatCount; i++) {
                    int sector = u32(file, pos + i * 4);
                    if (sector < 0) {
                        break;
                    }
                    fatSectors.add(sector);
                }
                difatSector = u32(file, pos + entries * 4);
                difatCount--;
            }
            int[] fat = new int[(fatSectors.size() * sectorSize) / 4];
            for (int i = 0; i < fatSectors.size(); i++) {
                int pos = (fatSectors.get(i) + 1) * sectorSize;
                for (int j = 0; j < sectorSize / 4; j++) {
                    fat[i * (sectorSize / 4) + j] = u32(file, pos + j * 4);
                }
            }
            return fat;
        }

        private static int[] readMiniFat(byte[] file, int sectorSize, int[] fat, int start) {
            if (start < 0) {
                return new int[0];
            }
            byte[] bytes = readChain(file, sectorSize, fat, start, Integer.MAX_VALUE, true);
            int[] mini = new int[bytes.length / 4];
            for (int i = 0; i < mini.length; i++) {
                mini[i] = u32(bytes, i * 4);
            }
            return mini;
        }

        private static byte[] readChain(byte[] source, int sectorSize, int[] fat, int start, int size, boolean headerOffset) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int sector = start;
            int guard = 0;
            while (sector >= 0 && guard++ < 100000) {
                int pos = headerOffset ? (sector + 1) * sectorSize : sector * sectorSize;
                if (pos < 0 || pos >= source.length) {
                    break;
                }
                int len = Math.min(sectorSize, source.length - pos);
                out.write(source, pos, len);
                if (size != Integer.MAX_VALUE && out.size() >= size) {
                    break;
                }
                if (sector >= fat.length) {
                    break;
                }
                int next = fat[sector];
                if (next < 0) {
                    break;
                }
                sector = next;
            }
            byte[] all = out.toByteArray();
            if (size >= 0 && size < all.length && size != Integer.MAX_VALUE) {
                byte[] trimmed = new byte[size];
                System.arraycopy(all, 0, trimmed, 0, size);
                return trimmed;
            }
            return all;
        }

        private static final class Entry {
            private final String name;
            private final int type;
            private final int start;
            private final int size;

            private Entry(String name, int type, int start, int size) {
                this.name = name;
                this.type = type;
                this.start = start;
                this.size = size;
            }

            private static Entry read(byte[] directory, int offset) {
                int nameLen = u16(directory, offset + 0x40);
                if (nameLen < 2 || nameLen > 64) {
                    return null;
                }
                String name = new String(directory, offset, nameLen - 2, StandardCharsets.UTF_16LE);
                int type = directory[offset + 0x42] & 0xFF;
                int start = u32(directory, offset + 0x74);
                int size = u32(directory, offset + 0x78);
                return new Entry(name, type, start, size);
            }
        }
    }

    private static final class BiffSheet {
        private static List<List<String>> readFirstSheet(byte[] data) {
            Cursor cursor = new Cursor(data);
            List<String> shared = List.of();
            Map<Integer, Map<Integer, String>> cells = new TreeMap<>();
            boolean inSheet = false;
            while (cursor.next()) {
                if (cursor.id == 0x0809) {
                    int type = cursor.remaining() >= 4 ? u16(cursor.data, cursor.pos + 2) : 0;
                    if (type == 0x0010) {
                        if (inSheet) {
                            break;
                        }
                        inSheet = true;
                    }
                    continue;
                }
                if (cursor.id == 0x00FC && !inSheet) {
                    shared = cursor.readSharedStrings();
                    continue;
                }
                if (!inSheet) {
                    continue;
                }
                if (cursor.id == 0x00FD && cursor.remaining() >= 8) {
                    int row = cursor.readU16();
                    int col = cursor.readU16();
                    cursor.readU16();
                    int index = cursor.readInt();
                    String text = index >= 0 && index < shared.size() ? shared.get(index) : "";
                    put(cells, row + 1, col, text);
                } else if (cursor.id == 0x0204 && cursor.remaining() >= 8) {
                    int row = cursor.readU16();
                    int col = cursor.readU16();
                    cursor.readU16();
                    put(cells, row + 1, col, cursor.readExcelString());
                } else if (cursor.id == 0x0203 && cursor.remaining() >= 14) {
                    int row = cursor.readU16();
                    int col = cursor.readU16();
                    cursor.readU16();
                    put(cells, row + 1, col, formatDouble(cursor.readDouble()));
                } else if (cursor.id == 0x027E && cursor.remaining() >= 10) {
                    int row = cursor.readU16();
                    int col = cursor.readU16();
                    cursor.readU16();
                    put(cells, row + 1, col, formatDouble(decodeRk(cursor.readInt())));
                } else if (cursor.id == 0x00BD && cursor.remaining() >= 8) {
                    int row = cursor.readU16();
                    int col = cursor.readU16();
                    int values = (cursor.remaining() - 2) / 6;
                    for (int i = 0; i < values; i++) {
                        cursor.readU16();
                        put(cells, row + 1, col + i, formatDouble(decodeRk(cursor.readInt())));
                    }
                }
            }
            return toGrid(cells);
        }

        private static double decodeRk(int rk) {
            double value;
            if ((rk & 0x02) != 0) {
                value = rk >> 2;
            } else {
                value = Double.longBitsToDouble((rk & 0xFFFFFFFCL) << 32);
            }
            if ((rk & 0x01) != 0) {
                value /= 100d;
            }
            return value;
        }
    }

    private static final class Cursor {
        private final byte[] data;
        private int pos;
        private int recEnd;
        private int id;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private boolean next() {
            pos = recEnd;
            if (pos + 4 > data.length) {
                return false;
            }
            id = u16(data, pos);
            int len = u16(data, pos + 2);
            pos += 4;
            recEnd = pos + len;
            if (recEnd > data.length) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
            return true;
        }

        private int remaining() {
            return recEnd - pos;
        }

        private int readU8() {
            need(1);
            return data[pos++] & 0xFF;
        }

        private int readU16() {
            need(2);
            int value = u16(data, pos);
            pos += 2;
            return value;
        }

        private int readInt() {
            need(4);
            int value = u32(data, pos);
            pos += 4;
            return value;
        }

        private double readDouble() {
            need(8);
            long bits = 0;
            for (int i = 0; i < 8; i++) {
                bits |= (data[pos++] & 0xFFL) << (8 * i);
            }
            return Double.longBitsToDouble(bits);
        }

        private void need(int bytes) {
            if (remaining() < bytes) {
                pullContinue(false);
            }
            if (remaining() < bytes) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
        }

        private void pullContinue(boolean stringFlag) {
            pos = recEnd;
            if (pos + 4 > data.length) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
            int nextId = u16(data, pos);
            int len = u16(data, pos + 2);
            if (nextId != 0x003C) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
            pos += 4;
            recEnd = pos + len;
            if (stringFlag && remaining() > 0) {
                pos++;
            }
        }

        private List<String> readSharedStrings() {
            int unique = 0;
            readInt();
            unique = readInt();
            if (unique < 0 || unique > 100000) {
                throw new IllegalArgumentException("Could not read that Excel file");
            }
            List<String> strings = new ArrayList<>(unique);
            for (int i = 0; i < unique; i++) {
                strings.add(readExcelString());
            }
            return strings;
        }

        private String readExcelString() {
            int chars = readU16();
            int flags = readU8();
            boolean wide = (flags & 0x01) != 0;
            boolean extended = (flags & 0x04) != 0;
            boolean rich = (flags & 0x08) != 0;
            int runs = rich ? readU16() : 0;
            int extra = extended ? readInt() : 0;
            String text = readChars(chars, wide);
            if (runs > 0) {
                skip(runs * 4);
            }
            if (extra > 0) {
                skip(extra);
            }
            return text;
        }

        private String readChars(int chars, boolean wide) {
            StringBuilder text = new StringBuilder(chars);
            int left = chars;
            boolean sixteen = wide;
            while (left > 0) {
                int available = sixteen ? remaining() / 2 : remaining();
                if (available <= 0) {
                    pullContinue(true);
                    if (remaining() <= 0) {
                        throw new IllegalArgumentException("Could not read that Excel file");
                    }
                    sixteen = (data[pos - 1] & 0x01) == 0 ? false : true;
                    continue;
                }
                int take = Math.min(left, available);
                for (int i = 0; i < take; i++) {
                    if (sixteen) {
                        text.append((char) readU16());
                    } else {
                        text.append((char) readU8());
                    }
                }
                left -= take;
            }
            return text.toString();
        }

        private void skip(int bytes) {
            int left = bytes;
            while (left > 0) {
                int available = remaining();
                if (available <= 0) {
                    pullContinue(false);
                    continue;
                }
                int take = Math.min(left, available);
                pos += take;
                left -= take;
            }
        }
    }
}
