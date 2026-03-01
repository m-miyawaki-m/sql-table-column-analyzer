package com.example.sqlanalyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis Mapper XMLから抽出されたSQL文のエントリを表すモデルクラス。
 * 1つのstatement（namespace + id）に対して、複数のSQL文を保持できる。
 */
public class SqlEntry {

    private final String namespace;
    private final String id;
    private final String sqlType;
    private final List<String> sqlList;

    /**
     * 単一SQLでSqlEntryを生成する。
     *
     * @param namespace MyBatis MapperのNamespace
     * @param id        SQL文のID
     * @param sqlType   SQL種別（select, insert, update, delete）
     * @param sql       SQL文
     */
    public SqlEntry(String namespace, String id, String sqlType, String sql) {
        this.namespace = namespace;
        this.id = id;
        this.sqlType = sqlType;
        this.sqlList = new ArrayList<>();
        this.sqlList.add(sql);
    }

    /**
     * 複数SQLでSqlEntryを生成する。
     *
     * @param namespace MyBatis MapperのNamespace
     * @param id        SQL文のID
     * @param sqlType   SQL種別（select, insert, update, delete）
     * @param sqlList   SQL文のリスト
     */
    public SqlEntry(String namespace, String id, String sqlType, List<String> sqlList) {
        this.namespace = namespace;
        this.id = id;
        this.sqlType = sqlType;
        this.sqlList = new ArrayList<>(sqlList);
    }

    /**
     * namespace.id 形式のstatement IDを返す。
     * namespaceがnullまたは空文字の場合はidのみを返す。
     *
     * @return statement ID
     */
    public String getStatementId() {
        if (namespace == null || namespace.isEmpty()) {
            return id;
        }
        return namespace + "." + id;
    }

    /**
     * sqlListの最初のSQL文を返す。
     *
     * @return 最初のSQL文
     */
    public String getSql() {
        return sqlList.get(0);
    }

    /**
     * 保持しているSQL文のリストを返す。
     *
     * @return SQL文のリスト
     */
    public List<String> getSqlList() {
        return sqlList;
    }

    /**
     * SQL文をリストに追加する。既に同一のSQL文が存在する場合は追加しない（重複排除）。
     *
     * @param sql 追加するSQL文
     */
    public void addSql(String sql) {
        if (!sqlList.contains(sql)) {
            sqlList.add(sql);
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public String getId() {
        return id;
    }

    public String getSqlType() {
        return sqlType;
    }
}
