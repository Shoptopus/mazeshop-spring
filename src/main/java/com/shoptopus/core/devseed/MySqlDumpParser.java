package com.shoptopus.core.devseed;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MySqlDumpParser {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("CREATE TABLE `([^`]+)`");
    private static final Pattern INSERT_PATTERN = Pattern.compile("INSERT INTO `([^`]+)` VALUES (.*);", Pattern.DOTALL);
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^\\s*`([^`]+)`");

    public Map<String, ParsedTable> parse(Path dumpPath) {
        try {
            return parseStatements(Files.readAllLines(dumpPath));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read MySQL dump: " + dumpPath, ex);
        }
    }

    Map<String, ParsedTable> parseStatements(List<String> lines) {
        Map<String, ParsedTable> tables = new LinkedHashMap<>();
        StringBuilder statement = new StringBuilder();
        for (String line : lines) {
            statement.append(line).append('\n');
            if (!line.trim().endsWith(";")) {
                continue;
            }
            String sql = statement.toString();
            statement.setLength(0);
            if (sql.startsWith("CREATE TABLE")) {
                parseCreateTable(sql, tables);
            } else if (sql.startsWith("INSERT INTO")) {
                parseInsert(sql, tables);
            }
        }
        return tables;
    }

    private void parseCreateTable(String sql, Map<String, ParsedTable> tables) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return;
        }
        String tableName = matcher.group(1);
        ParsedTable table = tables.computeIfAbsent(tableName, ParsedTable::new);
        List<String> columns = new ArrayList<>();
        for (String line : sql.split("\n")) {
            Matcher columnMatcher = COLUMN_PATTERN.matcher(line);
            if (columnMatcher.find()) {
                columns.add(columnMatcher.group(1));
            }
        }
        table.columns = columns;
    }

    private void parseInsert(String sql, Map<String, ParsedTable> tables) {
        Matcher matcher = INSERT_PATTERN.matcher(sql.trim());
        if (!matcher.matches()) {
            return;
        }
        String tableName = matcher.group(1);
        ParsedTable table = tables.computeIfAbsent(tableName, ParsedTable::new);
        for (List<String> row : parseRows(matcher.group(2))) {
            table.rows.add(row);
        }
    }

    private List<List<String>> parseRows(String valuesSection) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = null;
        StringBuilder token = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < valuesSection.length(); i++) {
            char ch = valuesSection.charAt(i);
            if (inString) {
                if (escaping) {
                    token.append(unescape(ch));
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '\'') {
                    inString = false;
                    continue;
                }
                token.append(ch);
                continue;
            }

            if (ch == '\'') {
                inString = true;
                continue;
            }
            if (ch == '(') {
                currentRow = new ArrayList<>();
                token.setLength(0);
                continue;
            }
            if (ch == ',') {
                if (currentRow != null) {
                    currentRow.add(normalizeToken(token.toString()));
                    token.setLength(0);
                }
                continue;
            }
            if (ch == ')') {
                if (currentRow != null) {
                    currentRow.add(normalizeToken(token.toString()));
                    rows.add(currentRow);
                    currentRow = null;
                    token.setLength(0);
                }
                continue;
            }
            if (currentRow != null) {
                token.append(ch);
            }
        }
        return rows;
    }

    private String normalizeToken(String token) {
        String trimmed = token.trim();
        return "NULL".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private char unescape(char ch) {
        return switch (ch) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> ch;
        };
    }

    public static final class ParsedTable {
        private final String name;
        private List<String> columns = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();

        ParsedTable(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public List<String> columns() {
            return columns;
        }

        public List<List<String>> rows() {
            return rows;
        }

        public List<Map<String, String>> mappedRows() {
            List<Map<String, String>> mapped = new ArrayList<>();
            for (List<String> row : rows) {
                Map<String, String> item = new LinkedHashMap<>();
                for (int i = 0; i < columns.size() && i < row.size(); i++) {
                    item.put(columns.get(i), row.get(i));
                }
                mapped.add(item);
            }
            return mapped;
        }
    }
}
