package com.example.sqlanalyzer.schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DDLから読み取ったスキーマ情報を保持するクラス。
 * テーブル名とカラム名はすべて小文字で正規化して格納する。
 */
public class SchemaInfo {

    private final Map<String, List<String>> tableColumns = new HashMap<>();

    /**
     * テーブル名とカラム名のリストを登録する。
     * テーブル名・カラム名ともに小文字に変換して格納する。
     *
     * @param tableName テーブル名
     * @param columns   カラム名のリスト
     */
    public void addTable(String tableName, List<String> columns) {
        String key = tableName.toLowerCase();
        List<String> lowerColumns = columns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        tableColumns.put(key, lowerColumns);
    }

    /**
     * 指定テーブルのカラム名リストを返す（大文字小文字を区別しない）。
     * テーブルが存在しない場合はnullを返す。
     *
     * @param tableName テーブル名
     * @return カラム名のリスト、またはnull
     */
    public List<String> getColumns(String tableName) {
        return tableColumns.get(tableName.toLowerCase());
    }

    /**
     * 指定テーブルが登録されているかを返す（大文字小文字を区別しない）。
     *
     * @param tableName テーブル名
     * @return テーブルが存在すればtrue
     */
    public boolean hasTable(String tableName) {
        return tableColumns.containsKey(tableName.toLowerCase());
    }

    /**
     * 候補テーブルの中から、指定カラムを持つテーブルを特定する。
     * 一致するテーブルがちょうど1つの場合にそのテーブル名を返す。
     * 一致が0件または2件以上（曖昧）の場合はnullを返す。
     *
     * @param columnName      カラム名
     * @param candidateTables 候補テーブル名のリスト
     * @return 一意に特定できたテーブル名（小文字）、またはnull
     */
    public String resolveTableByColumn(String columnName, List<String> candidateTables) {
        String lowerColumn = columnName.toLowerCase();
        List<String> matched = candidateTables.stream()
                .map(String::toLowerCase)
                .filter(table -> {
                    List<String> cols = tableColumns.get(table);
                    return cols != null && cols.contains(lowerColumn);
                })
                .collect(Collectors.toList());

        if (matched.size() == 1) {
            return matched.get(0);
        }
        return null;
    }
}
