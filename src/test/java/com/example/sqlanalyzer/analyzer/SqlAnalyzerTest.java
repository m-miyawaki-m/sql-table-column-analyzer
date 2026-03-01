package com.example.sqlanalyzer.analyzer;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import com.example.sqlanalyzer.schema.SchemaInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlAnalyzerのテストクラス。
 */
class SqlAnalyzerTest {

    private SchemaInfo schema;
    private SqlAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        schema = new SchemaInfo();
        schema.addTable("users", List.of("id", "name", "email", "age"));
        schema.addTable("orders", List.of("order_id", "user_id", "product_name", "amount"));
        schema.addTable("departments", List.of("dept_id", "dept_name"));

        analyzer = new SqlAnalyzer(schema);
    }

    @Test
    void simpleSelect() {
        List<ColumnRef> refs = analyzer.analyze("ns.selectUser", "select",
                "SELECT id, name FROM users WHERE age = ?");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.WHERE);
        assertEquals(3, refs.size());
    }

    @Test
    void selectWithAlias() {
        List<ColumnRef> refs = analyzer.analyze("ns.selectUser", "select",
                "SELECT u.id, u.name FROM users u WHERE u.age = ?");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.WHERE);
        assertEquals(3, refs.size());
    }

    @Test
    void selectStar() {
        List<ColumnRef> refs = analyzer.analyze("ns.selectAll", "select",
                "SELECT * FROM users");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "email", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.SELECT);
        assertEquals(4, refs.size());
    }

    @Test
    void selectStarWithoutSchema() {
        SqlAnalyzer noSchemaAnalyzer = new SqlAnalyzer(null);
        List<ColumnRef> refs = noSchemaAnalyzer.analyze("ns.selectAll", "select",
                "SELECT * FROM users");

        assertContains(refs, "users", "*", Usage.SELECT);
        assertEquals(1, refs.size());
    }

    @Test
    void updateWithSet() {
        List<ColumnRef> refs = analyzer.analyze("ns.updateUser", "update",
                "UPDATE users SET name = ?, email = ? WHERE id = ?");

        assertContains(refs, "users", "name", Usage.SET);
        assertContains(refs, "users", "email", Usage.SET);
        assertContains(refs, "users", "id", Usage.WHERE);
        assertEquals(3, refs.size());
    }

    @Test
    void insertStatement() {
        List<ColumnRef> refs = analyzer.analyze("ns.insertUser", "insert",
                "INSERT INTO users (id, name, email) VALUES (?, ?, ?)");

        assertContains(refs, "users", "id", Usage.INSERT);
        assertContains(refs, "users", "name", Usage.INSERT);
        assertContains(refs, "users", "email", Usage.INSERT);
        assertEquals(3, refs.size());
    }

    @Test
    void joinQuery() {
        List<ColumnRef> refs = analyzer.analyze("ns.joinQuery", "select",
                "SELECT u.name, o.product_name FROM users u JOIN orders o ON u.id = o.user_id WHERE o.amount > ?");

        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "orders", "product_name", Usage.SELECT);
        assertContains(refs, "users", "id", Usage.JOIN);
        assertContains(refs, "orders", "user_id", Usage.JOIN);
        assertContains(refs, "orders", "amount", Usage.WHERE);
        assertEquals(5, refs.size());
    }

    @Test
    void orderByAndGroupBy() {
        List<ColumnRef> refs = analyzer.analyze("ns.groupQuery", "select",
                "SELECT name, COUNT(*) FROM users GROUP BY name ORDER BY name");

        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.GROUP_BY);
        assertContains(refs, "users", "name", Usage.ORDER_BY);
        assertEquals(3, refs.size());
    }

    @Test
    void deleteStatement() {
        List<ColumnRef> refs = analyzer.analyze("ns.deleteUser", "delete",
                "DELETE FROM users WHERE id = ?");

        assertContains(refs, "users", "id", Usage.WHERE);
        assertEquals(1, refs.size());
    }

    @Test
    void deduplication() {
        List<ColumnRef> refs = analyzer.analyze("ns.dedup", "select",
                "SELECT name FROM users WHERE name = ?");

        long selectCount = refs.stream()
                .filter(r -> "name".equals(r.getColumnName()) && r.getUsage() == Usage.SELECT)
                .count();
        long whereCount = refs.stream()
                .filter(r -> "name".equals(r.getColumnName()) && r.getUsage() == Usage.WHERE)
                .count();

        assertEquals(1, selectCount, "Should have exactly 1 name[SELECT]");
        assertEquals(1, whereCount, "Should have exactly 1 name[WHERE]");
        assertEquals(2, refs.size());
    }

    /**
     * ヘルパーメソッド: リスト内に指定のテーブル・カラム・使用用途を持つColumnRefが存在するかを検証する。
     */
    private void assertContains(List<ColumnRef> refs, String table, String column, Usage usage) {
        boolean found = refs.stream().anyMatch(r ->
                r.getTableName().equalsIgnoreCase(table)
                        && r.getColumnName().equalsIgnoreCase(column)
                        && r.getUsage() == usage);
        assertTrue(found, "Expected to find " + table + "." + column + "[" + usage + "] in results: " + formatRefs(refs));
    }

    /**
     * デバッグ用にColumnRefリストを文字列に変換する。
     */
    private String formatRefs(List<ColumnRef> refs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < refs.size(); i++) {
            ColumnRef r = refs.get(i);
            if (i > 0) sb.append(", ");
            sb.append(r.getTableName()).append(".").append(r.getColumnName())
                    .append("[").append(r.getUsage()).append("]");
        }
        sb.append("]");
        return sb.toString();
    }
}
