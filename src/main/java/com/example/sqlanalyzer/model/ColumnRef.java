package com.example.sqlanalyzer.model;

import java.util.Objects;

/**
 * SQL文中のテーブル・カラム参照を表すモデルクラス。
 * statementId, sqlType, tableName, columnName, usageの5フィールドで一意性を判定する。
 */
public class ColumnRef {

    private final String statementId;
    private final String sqlType;
    private final String tableName;
    private final String columnName;
    private final Usage usage;

    /**
     * ColumnRefを生成する。
     *
     * @param statementId namespace.id形式のstatement ID
     * @param sqlType     SQL種別（select, insert, update, delete）
     * @param tableName   テーブル名
     * @param columnName  カラム名
     * @param usage       カラムの使用用途
     */
    public ColumnRef(String statementId, String sqlType, String tableName,
                     String columnName, Usage usage) {
        this.statementId = statementId;
        this.sqlType = sqlType;
        this.tableName = tableName;
        this.columnName = columnName;
        this.usage = usage;
    }

    /**
     * CSV出力用にカンマ区切りの文字列を返す。
     *
     * @return カンマ区切りの文字列
     */
    public String toCsvLine() {
        return statementId + "," + sqlType + "," + tableName + "," + columnName + "," + usage;
    }

    public String getStatementId() {
        return statementId;
    }

    public String getSqlType() {
        return sqlType;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public Usage getUsage() {
        return usage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColumnRef columnRef = (ColumnRef) o;
        return Objects.equals(statementId, columnRef.statementId)
                && Objects.equals(sqlType, columnRef.sqlType)
                && Objects.equals(tableName, columnRef.tableName)
                && Objects.equals(columnName, columnRef.columnName)
                && usage == columnRef.usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(statementId, sqlType, tableName, columnName, usage);
    }
}
