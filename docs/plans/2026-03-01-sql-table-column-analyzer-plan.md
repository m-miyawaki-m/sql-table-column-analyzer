# SQL Table/Column Analyzer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** mybatis-sql-extractorのJSON出力からSQL文を解析し、利用テーブル・カラムをCSVで出力するCLIツールを構築する。

**Architecture:** JSqlParserでSQL文をASTに変換し、Visitorパターンでカラムの出現箇所（SELECT/WHERE/SET等）を分類する。Oracle DDLからスキーマ情報を構築し、`*`の展開とエイリアスなしカラムのテーブル逆引きに使用する。

**Tech Stack:** Java 21, Gradle 8.5, JSqlParser 5.3, Jackson 2.17, JUnit 5

---

### Task 1: Gradle プロジェクト初期化

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `src/main/java/com/example/sqlanalyzer/Main.java`

**Step 1: build.gradle を作成**

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.example'
version = '1.0-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.github.jsqlparser:jsqlparser:5.3'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

application {
    mainClass = 'com.example.sqlanalyzer.Main'
}

test {
    useJUnitPlatform()
}
```

**Step 2: settings.gradle を作成**

```groovy
rootProject.name = 'sql-table-column-analyzer'
```

**Step 3: Main.java の空クラスを作成**

```java
package com.example.sqlanalyzer;

public class Main {
    public static void main(String[] args) {
        System.out.println("SQL Table/Column Analyzer");
    }
}
```

**Step 4: ビルド確認**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add build.gradle settings.gradle gradlew gradlew.bat gradle/ src/
git commit -m "init: Gradle project with JSqlParser and Jackson dependencies"
```

---

### Task 2: SqlEntry モデルクラス

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/model/SqlEntry.java`
- Create: `src/test/java/com/example/sqlanalyzer/model/SqlEntryTest.java`

**Step 1: テストを書く**

```java
package com.example.sqlanalyzer.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlEntryTest {

    @Test
    void constructorAndGetters() {
        SqlEntry entry = new SqlEntry(
                "com.example.mapper.UserMapper",
                "selectById",
                "SELECT",
                "SELECT id, name FROM users WHERE id = ?"
        );

        assertEquals("com.example.mapper.UserMapper", entry.getNamespace());
        assertEquals("selectById", entry.getId());
        assertEquals("SELECT", entry.getSqlType());
        assertEquals("SELECT id, name FROM users WHERE id = ?", entry.getSql());
        assertEquals("com.example.mapper.UserMapper.selectById", entry.getStatementId());
    }

    @Test
    void statementIdWithEmptyNamespace() {
        SqlEntry entry = new SqlEntry("", "selectAll", "SELECT", "SELECT * FROM users");
        assertEquals("selectAll", entry.getStatementId());
    }
}
```

**Step 2: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL（SqlEntryクラスが存在しない）

**Step 3: 実装**

```java
package com.example.sqlanalyzer.model;

public class SqlEntry {
    private final String namespace;
    private final String id;
    private final String sqlType;
    private final String sql;

    public SqlEntry(String namespace, String id, String sqlType, String sql) {
        this.namespace = namespace;
        this.id = id;
        this.sqlType = sqlType;
        this.sql = sql;
    }

    public String getNamespace() { return namespace; }
    public String getId() { return id; }
    public String getSqlType() { return sqlType; }
    public String getSql() { return sql; }

    public String getStatementId() {
        if (namespace == null || namespace.isEmpty()) {
            return id;
        }
        return namespace + "." + id;
    }
}
```

**Step 4: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: add SqlEntry model class"
```

---

### Task 3: ColumnRef モデルクラス

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/model/ColumnRef.java`
- Create: `src/main/java/com/example/sqlanalyzer/model/Usage.java`
- Create: `src/test/java/com/example/sqlanalyzer/model/ColumnRefTest.java`

**Step 1: テストを書く**

```java
package com.example.sqlanalyzer.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColumnRefTest {

    @Test
    void constructorAndGetters() {
        ColumnRef ref = new ColumnRef("selectById", "SELECT", "users", "name", Usage.SELECT);
        assertEquals("selectById", ref.getStatementId());
        assertEquals("SELECT", ref.getSqlType());
        assertEquals("users", ref.getTableName());
        assertEquals("name", ref.getColumnName());
        assertEquals(Usage.SELECT, ref.getUsage());
    }

    @Test
    void toCsvLine() {
        ColumnRef ref = new ColumnRef("selectById", "SELECT", "users", "name", Usage.WHERE);
        assertEquals("selectById,SELECT,users,name,WHERE", ref.toCsvLine());
    }

    @Test
    void equalsAndHashCodeForDedup() {
        ColumnRef ref1 = new ColumnRef("s1", "SELECT", "users", "name", Usage.SELECT);
        ColumnRef ref2 = new ColumnRef("s1", "SELECT", "users", "name", Usage.SELECT);
        ColumnRef ref3 = new ColumnRef("s1", "SELECT", "users", "name", Usage.WHERE);

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
        assertNotEquals(ref1, ref3);
    }
}
```

**Step 2: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL

**Step 3: Usage enum を作成**

```java
package com.example.sqlanalyzer.model;

public enum Usage {
    SELECT,
    WHERE,
    SET,
    INSERT,
    JOIN,
    ORDER_BY,
    GROUP_BY
}
```

**Step 4: ColumnRef を実装**

```java
package com.example.sqlanalyzer.model;

import java.util.Objects;

public class ColumnRef {
    private final String statementId;
    private final String sqlType;
    private final String tableName;
    private final String columnName;
    private final Usage usage;

    public ColumnRef(String statementId, String sqlType, String tableName, String columnName, Usage usage) {
        this.statementId = statementId;
        this.sqlType = sqlType;
        this.tableName = tableName;
        this.columnName = columnName;
        this.usage = usage;
    }

    public String getStatementId() { return statementId; }
    public String getSqlType() { return sqlType; }
    public String getTableName() { return tableName; }
    public String getColumnName() { return columnName; }
    public Usage getUsage() { return usage; }

    public String toCsvLine() {
        return String.join(",", statementId, sqlType, tableName, columnName, usage.name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnRef that)) return false;
        return Objects.equals(statementId, that.statementId)
                && Objects.equals(sqlType, that.sqlType)
                && Objects.equals(tableName, that.tableName)
                && Objects.equals(columnName, that.columnName)
                && usage == that.usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(statementId, sqlType, tableName, columnName, usage);
    }
}
```

**Step 5: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**Step 6: Commit**

```bash
git add src/
git commit -m "feat: add ColumnRef model and Usage enum"
```

---

### Task 4: JsonInputReader

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/input/JsonInputReader.java`
- Create: `src/test/java/com/example/sqlanalyzer/input/JsonInputReaderTest.java`
- Create: `src/test/resources/test-input.json`

**Step 1: テスト用JSONファイルを作成**

`src/test/resources/test-input.json`:
```json
[
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_SET",
    "sql": "SELECT id, name, email FROM users WHERE 1=1 AND name = ? AND email = ?",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_NULL",
    "sql": "SELECT id, name, email FROM users WHERE 1=1",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectAll",
    "type": "SELECT",
    "sql": "SELECT id, name FROM users",
    "parameters": []
  }
]
```

**Step 2: テストを書く**

```java
package com.example.sqlanalyzer.input;

import com.example.sqlanalyzer.model.SqlEntry;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonInputReaderTest {

    @Test
    void readAndMergeBranchPatterns() throws IOException {
        File jsonFile = new File(getClass().getClassLoader().getResource("test-input.json").getFile());
        JsonInputReader reader = new JsonInputReader();
        List<SqlEntry> entries = reader.read(jsonFile);

        // selectByCondition は ALL_SET/ALL_NULL がマージされ、SQLが2つ
        // selectAll は1つ → 合計2つのSqlEntry
        assertEquals(2, entries.size());

        SqlEntry merged = entries.stream()
                .filter(e -> e.getId().equals("selectByCondition"))
                .findFirst().orElseThrow();
        assertEquals("com.example.mapper.UserMapper", merged.getNamespace());
        assertEquals("SELECT", merged.getSqlType());
        // マージされたSQLは複数持つ
        assertTrue(merged.getSqlList().size() >= 2);
    }

    @Test
    void readNoBranchPattern() throws IOException {
        File jsonFile = new File(getClass().getClassLoader().getResource("test-input.json").getFile());
        JsonInputReader reader = new JsonInputReader();
        List<SqlEntry> entries = reader.read(jsonFile);

        SqlEntry simple = entries.stream()
                .filter(e -> e.getId().equals("selectAll"))
                .findFirst().orElseThrow();
        assertEquals(1, simple.getSqlList().size());
        assertEquals("SELECT id, name FROM users", simple.getSqlList().get(0));
    }
}
```

**Step 3: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL

**Step 4: SqlEntryにsqlListフィールドを追加**

SqlEntryを修正して、マージ対応のために`sqlList`を持たせる:

```java
package com.example.sqlanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class SqlEntry {
    private final String namespace;
    private final String id;
    private final String sqlType;
    private final List<String> sqlList;

    public SqlEntry(String namespace, String id, String sqlType, String sql) {
        this.namespace = namespace;
        this.id = id;
        this.sqlType = sqlType;
        this.sqlList = new ArrayList<>();
        this.sqlList.add(sql);
    }

    public SqlEntry(String namespace, String id, String sqlType, List<String> sqlList) {
        this.namespace = namespace;
        this.id = id;
        this.sqlType = sqlType;
        this.sqlList = new ArrayList<>(sqlList);
    }

    public String getNamespace() { return namespace; }
    public String getId() { return id; }
    public String getSqlType() { return sqlType; }
    public String getSql() { return sqlList.isEmpty() ? "" : sqlList.get(0); }
    public List<String> getSqlList() { return sqlList; }

    public String getStatementId() {
        if (namespace == null || namespace.isEmpty()) {
            return id;
        }
        return namespace + "." + id;
    }

    public void addSql(String sql) {
        if (!sqlList.contains(sql)) {
            sqlList.add(sql);
        }
    }
}
```

**Step 5: JsonInputReader を実装**

```java
package com.example.sqlanalyzer.input;

import com.example.sqlanalyzer.model.SqlEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JsonInputReader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SqlEntry> read(File jsonFile) throws IOException {
        JsonNode root = objectMapper.readTree(jsonFile);
        if (!root.isArray()) {
            throw new IOException("JSON root must be an array");
        }

        // namespace+id をキーにマージ
        Map<String, SqlEntry> entryMap = new LinkedHashMap<>();

        for (JsonNode node : root) {
            String namespace = node.has("namespace") ? node.get("namespace").asText() : "";
            String id = node.get("id").asText();
            String type = node.get("type").asText();
            String sql = node.get("sql").asText();
            String key = namespace + "." + id;

            if (entryMap.containsKey(key)) {
                entryMap.get(key).addSql(sql);
            } else {
                entryMap.put(key, new SqlEntry(namespace, id, type, sql));
            }
        }

        return new ArrayList<>(entryMap.values());
    }
}
```

**Step 6: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**Step 7: Commit**

```bash
git add src/
git commit -m "feat: add JsonInputReader with branch pattern merging"
```

---

### Task 5: SchemaInfo と DdlParser

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/schema/SchemaInfo.java`
- Create: `src/main/java/com/example/sqlanalyzer/schema/DdlParser.java`
- Create: `src/test/java/com/example/sqlanalyzer/schema/DdlParserTest.java`
- Create: `src/test/resources/test-schema.sql`

**Step 1: テスト用DDLファイルを作成**

`src/test/resources/test-schema.sql`:
```sql
CREATE TABLE users (
    id NUMBER(10) NOT NULL,
    name VARCHAR2(100),
    email VARCHAR2(200),
    age NUMBER(3),
    created_at DATE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE orders (
    order_id NUMBER(10) NOT NULL,
    user_id NUMBER(10),
    product_name VARCHAR2(200),
    amount NUMBER(10, 2),
    order_date DATE,
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
);

CREATE TABLE departments (
    dept_id NUMBER(10) NOT NULL,
    dept_name VARCHAR2(100),
    CONSTRAINT pk_departments PRIMARY KEY (dept_id)
);
```

**Step 2: テストを書く**

```java
package com.example.sqlanalyzer.schema;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DdlParserTest {

    @Test
    void parseCreateTable() throws IOException {
        File ddlFile = new File(getClass().getClassLoader().getResource("test-schema.sql").getFile());
        DdlParser parser = new DdlParser();
        SchemaInfo schema = parser.parse(ddlFile);

        List<String> userColumns = schema.getColumns("users");
        assertNotNull(userColumns);
        assertTrue(userColumns.contains("id"));
        assertTrue(userColumns.contains("name"));
        assertTrue(userColumns.contains("email"));
        assertTrue(userColumns.contains("age"));
        assertTrue(userColumns.contains("created_at"));
        assertEquals(5, userColumns.size());
    }

    @Test
    void caseInsensitiveTableLookup() throws IOException {
        File ddlFile = new File(getClass().getClassLoader().getResource("test-schema.sql").getFile());
        DdlParser parser = new DdlParser();
        SchemaInfo schema = parser.parse(ddlFile);

        // テーブル名は大文字小文字を区別しない
        assertEquals(schema.getColumns("USERS"), schema.getColumns("users"));
    }

    @Test
    void unknownTableReturnsNull() throws IOException {
        File ddlFile = new File(getClass().getClassLoader().getResource("test-schema.sql").getFile());
        DdlParser parser = new DdlParser();
        SchemaInfo schema = parser.parse(ddlFile);

        assertNull(schema.getColumns("nonexistent"));
    }

    @Test
    void resolveTableByColumnName() throws IOException {
        File ddlFile = new File(getClass().getClassLoader().getResource("test-schema.sql").getFile());
        DdlParser parser = new DdlParser();
        SchemaInfo schema = parser.parse(ddlFile);

        // order_id は orders テーブルにのみ存在
        String table = schema.resolveTableByColumn("order_id", List.of("users", "orders"));
        assertEquals("orders", table);
    }

    @Test
    void resolveTableByColumnAmbiguous() throws IOException {
        File ddlFile = new File(getClass().getClassLoader().getResource("test-schema.sql").getFile());
        DdlParser parser = new DdlParser();
        SchemaInfo schema = parser.parse(ddlFile);

        // dept_name は departments のみだが、もし同名カラムが複数テーブルにあればnull
        // このテストではユニークなケースを確認
        String table = schema.resolveTableByColumn("dept_name", List.of("users", "departments"));
        assertEquals("departments", table);
    }
}
```

**Step 3: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL

**Step 4: SchemaInfo を実装**

```java
package com.example.sqlanalyzer.schema;

import java.util.*;

public class SchemaInfo {

    // テーブル名（小文字） → カラム名リスト（小文字）
    private final Map<String, List<String>> tables = new LinkedHashMap<>();

    public void addTable(String tableName, List<String> columns) {
        List<String> lowerColumns = columns.stream()
                .map(String::toLowerCase)
                .toList();
        tables.put(tableName.toLowerCase(), lowerColumns);
    }

    public List<String> getColumns(String tableName) {
        return tables.get(tableName.toLowerCase());
    }

    public boolean hasTable(String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }

    /**
     * カラム名から所属テーブルを逆引きする。
     * 候補テーブルの中で当該カラムを持つテーブルが1つだけならそのテーブル名を返す。
     * 複数テーブルに存在する場合や見つからない場合はnullを返す。
     */
    public String resolveTableByColumn(String columnName, List<String> candidateTables) {
        String lowerCol = columnName.toLowerCase();
        String found = null;
        for (String table : candidateTables) {
            List<String> cols = getColumns(table);
            if (cols != null && cols.contains(lowerCol)) {
                if (found != null) {
                    return null; // 複数テーブルに存在 → 曖昧
                }
                found = table.toLowerCase();
            }
        }
        return found;
    }
}
```

**Step 5: DdlParser を実装**

```java
package com.example.sqlanalyzer.schema;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DdlParser {

    public SchemaInfo parse(File ddlFile) throws IOException {
        String ddlContent = Files.readString(ddlFile.toPath());
        SchemaInfo schema = new SchemaInfo();

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(ddlContent);
            for (Statement stmt : statements) {
                if (stmt instanceof CreateTable createTable) {
                    String tableName = createTable.getTable().getName();
                    List<String> columns = new ArrayList<>();
                    if (createTable.getColumnDefinitions() != null) {
                        for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                            columns.add(colDef.getColumnName());
                        }
                    }
                    schema.addTable(tableName, columns);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: DDL parse error: " + e.getMessage());
            // 個別のCREATE TABLE文ごとに再試行
            parseIndividualStatements(ddlContent, schema);
        }

        return schema;
    }

    private void parseIndividualStatements(String ddlContent, SchemaInfo schema) {
        // セミコロンで分割して個別にパース
        String[] parts = ddlContent.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                Statement stmt = CCJSqlParserUtil.parse(trimmed);
                if (stmt instanceof CreateTable createTable) {
                    String tableName = createTable.getTable().getName();
                    List<String> columns = new ArrayList<>();
                    if (createTable.getColumnDefinitions() != null) {
                        for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                            columns.add(colDef.getColumnName());
                        }
                    }
                    schema.addTable(tableName, columns);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse DDL statement: " + e.getMessage());
            }
        }
    }
}
```

**Step 6: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**Step 7: Commit**

```bash
git add src/
git commit -m "feat: add SchemaInfo and DdlParser for DDL-based column resolution"
```

---

### Task 6: SqlAnalyzer（メインのSQL解析ロジック）

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/analyzer/SqlAnalyzer.java`
- Create: `src/test/java/com/example/sqlanalyzer/analyzer/SqlAnalyzerTest.java`

**Step 1: テストを書く**

```java
package com.example.sqlanalyzer.analyzer;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import com.example.sqlanalyzer.schema.SchemaInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SqlAnalyzerTest {

    private SqlAnalyzer analyzer;
    private SchemaInfo schema;

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
        List<ColumnRef> refs = analyzer.analyze("stmt1", "SELECT",
                "SELECT id, name FROM users WHERE age = ?");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.WHERE);
    }

    @Test
    void selectWithAlias() {
        List<ColumnRef> refs = analyzer.analyze("stmt2", "SELECT",
                "SELECT u.id, u.name FROM users u WHERE u.age = ?");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.WHERE);
    }

    @Test
    void selectStar() {
        List<ColumnRef> refs = analyzer.analyze("stmt3", "SELECT",
                "SELECT * FROM users");

        assertContains(refs, "users", "id", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "email", Usage.SELECT);
        assertContains(refs, "users", "age", Usage.SELECT);
    }

    @Test
    void selectStarWithoutSchema() {
        SqlAnalyzer noSchemaAnalyzer = new SqlAnalyzer(null);
        List<ColumnRef> refs = noSchemaAnalyzer.analyze("stmt4", "SELECT",
                "SELECT * FROM users");

        assertContains(refs, "users", "*", Usage.SELECT);
    }

    @Test
    void updateWithSet() {
        List<ColumnRef> refs = analyzer.analyze("stmt5", "UPDATE",
                "UPDATE users SET name = ?, email = ? WHERE id = ?");

        assertContains(refs, "users", "name", Usage.SET);
        assertContains(refs, "users", "email", Usage.SET);
        assertContains(refs, "users", "id", Usage.WHERE);
    }

    @Test
    void insertStatement() {
        List<ColumnRef> refs = analyzer.analyze("stmt6", "INSERT",
                "INSERT INTO users (id, name, email) VALUES (?, ?, ?)");

        assertContains(refs, "users", "id", Usage.INSERT);
        assertContains(refs, "users", "name", Usage.INSERT);
        assertContains(refs, "users", "email", Usage.INSERT);
    }

    @Test
    void joinQuery() {
        List<ColumnRef> refs = analyzer.analyze("stmt7", "SELECT",
                "SELECT u.name, o.product_name FROM users u " +
                "JOIN orders o ON u.id = o.user_id WHERE o.amount > ?");

        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "orders", "product_name", Usage.SELECT);
        assertContains(refs, "users", "id", Usage.JOIN);
        assertContains(refs, "orders", "user_id", Usage.JOIN);
        assertContains(refs, "orders", "amount", Usage.WHERE);
    }

    @Test
    void orderByAndGroupBy() {
        List<ColumnRef> refs = analyzer.analyze("stmt8", "SELECT",
                "SELECT name, COUNT(*) FROM users GROUP BY name ORDER BY name");

        assertContains(refs, "users", "name", Usage.SELECT);
        assertContains(refs, "users", "name", Usage.GROUP_BY);
        assertContains(refs, "users", "name", Usage.ORDER_BY);
    }

    @Test
    void deleteStatement() {
        List<ColumnRef> refs = analyzer.analyze("stmt9", "DELETE",
                "DELETE FROM users WHERE id = ?");

        assertContains(refs, "users", "id", Usage.WHERE);
    }

    @Test
    void deduplication() {
        List<ColumnRef> refs = analyzer.analyze("stmt10", "SELECT",
                "SELECT name FROM users WHERE name = ?");

        // name が SELECT と WHERE に出現するが、各usageで1回ずつ
        long selectCount = refs.stream()
                .filter(r -> r.getColumnName().equals("name") && r.getUsage() == Usage.SELECT)
                .count();
        long whereCount = refs.stream()
                .filter(r -> r.getColumnName().equals("name") && r.getUsage() == Usage.WHERE)
                .count();
        assertEquals(1, selectCount);
        assertEquals(1, whereCount);
    }

    private void assertContains(List<ColumnRef> refs, String table, String column, Usage usage) {
        boolean found = refs.stream().anyMatch(r ->
                r.getTableName().equalsIgnoreCase(table)
                && r.getColumnName().equalsIgnoreCase(column)
                && r.getUsage() == usage);
        assertTrue(found,
                "Expected " + table + "." + column + " [" + usage + "] but found: " +
                refs.stream().map(r -> r.getTableName() + "." + r.getColumnName() + "[" + r.getUsage() + "]")
                        .collect(Collectors.joining(", ")));
    }
}
```

**Step 2: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL

**Step 3: SqlAnalyzer を実装**

```java
package com.example.sqlanalyzer.analyzer;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import com.example.sqlanalyzer.schema.SchemaInfo;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.*;
import java.util.stream.Collectors;

public class SqlAnalyzer {

    private final SchemaInfo schema;

    public SqlAnalyzer(SchemaInfo schema) {
        this.schema = schema;
    }

    public List<ColumnRef> analyze(String statementId, String sqlType, String sql) {
        Set<ColumnRef> refs = new LinkedHashSet<>();

        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            Map<String, String> aliasMap = new HashMap<>();

            if (stmt instanceof Select select) {
                collectAliases(select, aliasMap);
                analyzeSelect(select, statementId, sqlType, aliasMap, refs);
            } else if (stmt instanceof Update update) {
                Table table = update.getTable();
                if (table.getAlias() != null) {
                    aliasMap.put(table.getAlias().getName().toLowerCase(), table.getName());
                }
                analyzeUpdate(update, statementId, sqlType, aliasMap, refs);
            } else if (stmt instanceof Insert insert) {
                analyzeInsert(insert, statementId, sqlType, refs);
            } else if (stmt instanceof Delete delete) {
                Table table = delete.getTable();
                if (table.getAlias() != null) {
                    aliasMap.put(table.getAlias().getName().toLowerCase(), table.getName());
                }
                analyzeDelete(delete, statementId, sqlType, aliasMap, refs);
            }
        } catch (Exception e) {
            System.err.println("Warning: SQL parse error for " + statementId + ": " + e.getMessage());
        }

        return new ArrayList<>(refs);
    }

    private void collectAliases(Select select, Map<String, String> aliasMap) {
        if (select instanceof PlainSelect ps) {
            collectAliasesFromPlainSelect(ps, aliasMap);
        } else if (select instanceof SetOperationList sol) {
            for (Select s : sol.getSelects()) {
                collectAliases(s, aliasMap);
            }
        } else if (select instanceof ParenthesedSelect ps) {
            collectAliases(ps.getSelect(), aliasMap);
        }
    }

    private void collectAliasesFromPlainSelect(PlainSelect ps, Map<String, String> aliasMap) {
        FromItem from = ps.getFromItem();
        if (from instanceof Table table) {
            if (table.getAlias() != null) {
                aliasMap.put(table.getAlias().getName().toLowerCase(), table.getName());
            }
        }
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                FromItem joinItem = join.getFromItem();
                if (joinItem instanceof Table table) {
                    if (table.getAlias() != null) {
                        aliasMap.put(table.getAlias().getName().toLowerCase(), table.getName());
                    }
                }
            }
        }
    }

    private List<String> collectTables(PlainSelect ps) {
        List<String> tables = new ArrayList<>();
        FromItem from = ps.getFromItem();
        if (from instanceof Table table) {
            tables.add(table.getName().toLowerCase());
        }
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getFromItem() instanceof Table table) {
                    tables.add(table.getName().toLowerCase());
                }
            }
        }
        return tables;
    }

    private void analyzeSelect(Select select, String stmtId, String sqlType,
                                Map<String, String> aliasMap, Set<ColumnRef> refs) {
        if (select instanceof PlainSelect ps) {
            analyzePlainSelect(ps, stmtId, sqlType, aliasMap, refs);
        } else if (select instanceof SetOperationList sol) {
            for (Select s : sol.getSelects()) {
                analyzeSelect(s, stmtId, sqlType, aliasMap, refs);
            }
        } else if (select instanceof ParenthesedSelect ps) {
            analyzeSelect(ps.getSelect(), stmtId, sqlType, aliasMap, refs);
        }
    }

    private void analyzePlainSelect(PlainSelect ps, String stmtId, String sqlType,
                                     Map<String, String> aliasMap, Set<ColumnRef> refs) {
        List<String> tables = collectTables(ps);

        // SELECT句
        if (ps.getSelectItems() != null) {
            for (SelectItem<?> item : ps.getSelectItems()) {
                Expression expr = item.getExpression();
                if (expr instanceof AllColumns) {
                    // SELECT *
                    expandStar(null, tables, stmtId, sqlType, refs);
                } else if (expr instanceof AllTableColumns atc) {
                    // SELECT t.*
                    String tableAlias = atc.getTable().getName();
                    String realTable = resolveAlias(tableAlias, aliasMap);
                    expandStar(realTable, tables, stmtId, sqlType, refs);
                } else {
                    collectColumns(expr, stmtId, sqlType, Usage.SELECT, aliasMap, tables, refs);
                }
            }
        }

        // WHERE句
        if (ps.getWhere() != null) {
            collectColumns(ps.getWhere(), stmtId, sqlType, Usage.WHERE, aliasMap, tables, refs);
        }

        // JOIN条件
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getOnExpressions() != null) {
                    for (Expression onExpr : join.getOnExpressions()) {
                        collectColumns(onExpr, stmtId, sqlType, Usage.JOIN, aliasMap, tables, refs);
                    }
                }
            }
        }

        // GROUP BY
        if (ps.getGroupBy() != null) {
            for (Expression expr : ps.getGroupBy().getGroupByExpressionList()) {
                collectColumns(expr, stmtId, sqlType, Usage.GROUP_BY, aliasMap, tables, refs);
            }
        }

        // ORDER BY
        if (ps.getOrderByElements() != null) {
            for (OrderByElement obe : ps.getOrderByElements()) {
                collectColumns(obe.getExpression(), stmtId, sqlType, Usage.ORDER_BY, aliasMap, tables, refs);
            }
        }
    }

    private void analyzeUpdate(Update update, String stmtId, String sqlType,
                                Map<String, String> aliasMap, Set<ColumnRef> refs) {
        String tableName = update.getTable().getName();
        List<String> tables = List.of(tableName.toLowerCase());

        // SET句
        if (update.getUpdateSets() != null) {
            for (UpdateSet us : update.getUpdateSets()) {
                if (us.getColumns() != null) {
                    for (Column col : us.getColumns()) {
                        refs.add(new ColumnRef(stmtId, sqlType, tableName, col.getColumnName(), Usage.SET));
                    }
                }
            }
        }

        // WHERE句
        if (update.getWhere() != null) {
            collectColumns(update.getWhere(), stmtId, sqlType, Usage.WHERE, aliasMap, tables, refs);
        }
    }

    private void analyzeInsert(Insert insert, String stmtId, String sqlType, Set<ColumnRef> refs) {
        String tableName = insert.getTable().getName();
        if (insert.getColumns() != null) {
            for (Column col : insert.getColumns()) {
                refs.add(new ColumnRef(stmtId, sqlType, tableName, col.getColumnName(), Usage.INSERT));
            }
        }
    }

    private void analyzeDelete(Delete delete, String stmtId, String sqlType,
                                Map<String, String> aliasMap, Set<ColumnRef> refs) {
        String tableName = delete.getTable().getName();
        List<String> tables = List.of(tableName.toLowerCase());

        if (delete.getWhere() != null) {
            collectColumns(delete.getWhere(), stmtId, sqlType, Usage.WHERE, aliasMap, tables, refs);
        }
    }

    private void collectColumns(Expression expr, String stmtId, String sqlType, Usage usage,
                                 Map<String, String> aliasMap, List<String> tables, Set<ColumnRef> refs) {
        if (expr == null) return;

        expr.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                String colName = column.getColumnName();
                String tableName;

                if (column.getTable() != null && column.getTable().getName() != null) {
                    tableName = resolveAlias(column.getTable().getName(), aliasMap);
                } else if (tables.size() == 1) {
                    tableName = tables.get(0);
                } else {
                    // 複数テーブルの場合、スキーマから逆引き
                    tableName = resolveTableForColumn(colName, tables);
                }

                refs.add(new ColumnRef(stmtId, sqlType, tableName, colName, usage));
                return null;
            }

            @Override
            public <S> Void visit(ParenthesedSelect subSelect, S context) {
                // サブクエリは再帰解析しない（テーブル参照として扱う）
                return null;
            }
        });
    }

    private void expandStar(String tableName, List<String> allTables,
                             String stmtId, String sqlType, Set<ColumnRef> refs) {
        List<String> targetTables = (tableName != null)
                ? List.of(tableName.toLowerCase())
                : allTables;

        for (String table : targetTables) {
            if (schema != null && schema.hasTable(table)) {
                for (String col : schema.getColumns(table)) {
                    refs.add(new ColumnRef(stmtId, sqlType, table, col, Usage.SELECT));
                }
            } else {
                refs.add(new ColumnRef(stmtId, sqlType, table, "*", Usage.SELECT));
            }
        }
    }

    private String resolveAlias(String nameOrAlias, Map<String, String> aliasMap) {
        String resolved = aliasMap.get(nameOrAlias.toLowerCase());
        return (resolved != null) ? resolved.toLowerCase() : nameOrAlias.toLowerCase();
    }

    private String resolveTableForColumn(String columnName, List<String> tables) {
        if (schema != null) {
            String resolved = schema.resolveTableByColumn(columnName, tables);
            if (resolved != null) return resolved;
        }
        return "UNKNOWN";
    }
}
```

**Step 4: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**注意**: JSqlParserのAPIバージョン差異でコンパイルエラーが出る場合は、
`ExpressionVisitorAdapter`の代わりに`ExpressionDeParser`を使うか、
APIに合わせて調整する。主要なクラス:
- `net.sf.jsqlparser.parser.CCJSqlParserUtil` - パース
- `net.sf.jsqlparser.schema.Column` - カラム参照
- `net.sf.jsqlparser.schema.Table` - テーブル参照
- `net.sf.jsqlparser.statement.select.PlainSelect` - SELECT文
- `net.sf.jsqlparser.expression.ExpressionVisitorAdapter` - Expression走査

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: add SqlAnalyzer with JSqlParser-based column extraction"
```

---

### Task 7: CsvWriter

**Files:**
- Create: `src/main/java/com/example/sqlanalyzer/output/CsvWriter.java`
- Create: `src/test/java/com/example/sqlanalyzer/output/CsvWriterTest.java`

**Step 1: テストを書く**

```java
package com.example.sqlanalyzer.output;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvWriterTest {

    @Test
    void writeToWriter() throws IOException {
        List<ColumnRef> refs = List.of(
                new ColumnRef("stmt1", "SELECT", "users", "id", Usage.SELECT),
                new ColumnRef("stmt1", "SELECT", "users", "name", Usage.WHERE)
        );

        StringWriter sw = new StringWriter();
        CsvWriter writer = new CsvWriter();
        writer.write(refs, sw);

        String output = sw.toString();
        String[] lines = output.split("\n");
        assertEquals("statement_id,sql_type,table_name,column_name,usage", lines[0]);
        assertEquals("stmt1,SELECT,users,id,SELECT", lines[1]);
        assertEquals("stmt1,SELECT,users,name,WHERE", lines[2]);
        assertEquals(3, lines.length);
    }

    @Test
    void emptyList() throws IOException {
        StringWriter sw = new StringWriter();
        CsvWriter writer = new CsvWriter();
        writer.write(List.of(), sw);

        String output = sw.toString();
        String[] lines = output.split("\n");
        assertEquals(1, lines.length); // ヘッダのみ
        assertEquals("statement_id,sql_type,table_name,column_name,usage", lines[0]);
    }
}
```

**Step 2: テスト失敗を確認**

Run: `./gradlew test`
Expected: FAIL

**Step 3: CsvWriter を実装**

```java
package com.example.sqlanalyzer.output;

import com.example.sqlanalyzer.model.ColumnRef;

import java.io.*;
import java.util.List;

public class CsvWriter {

    private static final String HEADER = "statement_id,sql_type,table_name,column_name,usage";

    public void write(List<ColumnRef> refs, Writer writer) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        bw.write(HEADER);
        bw.newLine();
        for (ColumnRef ref : refs) {
            bw.write(ref.toCsvLine());
            bw.newLine();
        }
        bw.flush();
    }

    public void write(List<ColumnRef> refs, File outputFile) throws IOException {
        try (FileWriter fw = new FileWriter(outputFile)) {
            write(refs, fw);
        }
    }
}
```

**Step 4: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: add CsvWriter for column reference output"
```

---

### Task 8: Main CLI

**Files:**
- Modify: `src/main/java/com/example/sqlanalyzer/Main.java`

**Step 1: Main.java を実装**

```java
package com.example.sqlanalyzer;

import com.example.sqlanalyzer.analyzer.SqlAnalyzer;
import com.example.sqlanalyzer.input.JsonInputReader;
import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.SqlEntry;
import com.example.sqlanalyzer.output.CsvWriter;
import com.example.sqlanalyzer.schema.DdlParser;
import com.example.sqlanalyzer.schema.SchemaInfo;

import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String inputPath = null;
        String ddlPath = null;
        String outputPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> { if (i + 1 < args.length) inputPath = args[++i]; }
                case "--ddl", "-d" -> { if (i + 1 < args.length) ddlPath = args[++i]; }
                case "--output", "-o" -> { if (i + 1 < args.length) outputPath = args[++i]; }
                case "--help", "-h" -> { printUsage(); return; }
                default -> { if (inputPath == null && !args[i].startsWith("-")) inputPath = args[i]; }
            }
        }

        if (inputPath == null) {
            System.err.println("Error: input path is required.");
            printUsage();
            System.exit(1);
        }

        try {
            // 1. DDL読み込み（任意）
            SchemaInfo schema = null;
            if (ddlPath != null) {
                File ddlFile = new File(ddlPath);
                if (!ddlFile.exists()) {
                    System.err.println("Error: DDL file does not exist: " + ddlPath);
                    System.exit(1);
                }
                schema = new DdlParser().parse(ddlFile);
                System.err.println("Loaded schema: " + ddlFile.getName());
            }

            // 2. JSON入力読み込み
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                System.err.println("Error: input file does not exist: " + inputPath);
                System.exit(1);
            }
            List<SqlEntry> entries = new JsonInputReader().read(inputFile);
            System.err.println("Loaded " + entries.size() + " SQL entries.");

            // 3. SQL解析
            SqlAnalyzer analyzer = new SqlAnalyzer(schema);
            Set<ColumnRef> allRefs = new LinkedHashSet<>();

            for (SqlEntry entry : entries) {
                for (String sql : entry.getSqlList()) {
                    List<ColumnRef> refs = analyzer.analyze(
                            entry.getStatementId(), entry.getSqlType(), sql);
                    allRefs.addAll(refs);
                }
            }

            List<ColumnRef> sortedRefs = new ArrayList<>(allRefs);
            sortedRefs.sort(Comparator.comparing(ColumnRef::getStatementId)
                    .thenComparing(ColumnRef::getTableName)
                    .thenComparing(ColumnRef::getColumnName)
                    .thenComparing(r -> r.getUsage().ordinal()));

            // 4. CSV出力
            CsvWriter csvWriter = new CsvWriter();
            if (outputPath != null) {
                csvWriter.write(sortedRefs, new File(outputPath));
                System.err.println("Output written to: " + outputPath);
            } else {
                csvWriter.write(sortedRefs, new OutputStreamWriter(System.out));
            }

            System.err.println("Extracted " + sortedRefs.size() + " column references.");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("SQL Table/Column Analyzer");
        System.out.println();
        System.out.println("Usage: sql-table-column-analyzer [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --input, -i <path>   mybatis-sql-extractor JSON output file (required)");
        System.out.println("  --ddl, -d <path>     Oracle DDL file for * expansion (optional)");
        System.out.println("  --output, -o <path>  Output CSV file (default: stdout)");
        System.out.println("  --help, -h           Show this help message");
    }
}
```

**Step 2: ビルド確認**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/
git commit -m "feat: add Main CLI with --input, --ddl, --output options"
```

---

### Task 9: 統合テスト

**Files:**
- Create: `src/test/java/com/example/sqlanalyzer/IntegrationTest.java`
- Create: `src/test/resources/integration-input.json`
- Create: `src/test/resources/integration-schema.sql`

**Step 1: テストデータを作成**

`src/test/resources/integration-input.json`:
```json
[
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_SET",
    "sql": "SELECT id, name, email FROM users WHERE 1=1 AND name = ? AND email = ?",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_NULL",
    "sql": "SELECT id, name, email FROM users WHERE 1=1",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectWithJoin",
    "type": "SELECT",
    "sql": "SELECT u.name, o.product_name FROM users u JOIN orders o ON u.id = o.user_id WHERE o.amount > ?",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "updateSelective",
    "type": "UPDATE",
    "branchPattern": "ALL_SET",
    "sql": "UPDATE users SET name = ?, email = ? WHERE id = ?",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "updateSelective",
    "type": "UPDATE",
    "branchPattern": "ALL_NULL",
    "sql": "UPDATE users SET WHERE id = ?",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "insertUser",
    "type": "INSERT",
    "sql": "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
    "parameters": []
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectAllColumns",
    "type": "SELECT",
    "sql": "SELECT * FROM users WHERE id = ?",
    "parameters": []
  }
]
```

`src/test/resources/integration-schema.sql`: （Task 5 の test-schema.sql と同じ内容を使用）

**Step 2: 統合テストを書く**

```java
package com.example.sqlanalyzer;

import com.example.sqlanalyzer.analyzer.SqlAnalyzer;
import com.example.sqlanalyzer.input.JsonInputReader;
import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.SqlEntry;
import com.example.sqlanalyzer.model.Usage;
import com.example.sqlanalyzer.output.CsvWriter;
import com.example.sqlanalyzer.schema.DdlParser;
import com.example.sqlanalyzer.schema.SchemaInfo;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    @Test
    void endToEndWithSchema() throws IOException {
        File jsonFile = resourceFile("integration-input.json");
        File ddlFile = resourceFile("test-schema.sql");

        // 1. Read inputs
        List<SqlEntry> entries = new JsonInputReader().read(jsonFile);
        SchemaInfo schema = new DdlParser().parse(ddlFile);

        // 2. Analyze
        SqlAnalyzer analyzer = new SqlAnalyzer(schema);
        Set<ColumnRef> allRefs = new LinkedHashSet<>();
        for (SqlEntry entry : entries) {
            for (String sql : entry.getSqlList()) {
                try {
                    allRefs.addAll(analyzer.analyze(entry.getStatementId(), entry.getSqlType(), sql));
                } catch (Exception e) {
                    // ALL_NULL の不正SQL（SET句なし等）はスキップ
                }
            }
        }

        List<ColumnRef> sorted = new ArrayList<>(allRefs);
        sorted.sort(Comparator.comparing(ColumnRef::getStatementId)
                .thenComparing(ColumnRef::getTableName)
                .thenComparing(ColumnRef::getColumnName)
                .thenComparing(r -> r.getUsage().ordinal()));

        // 3. Write CSV
        StringWriter sw = new StringWriter();
        new CsvWriter().write(sorted, sw);
        String csv = sw.toString();

        // 4. Assertions
        assertTrue(csv.contains("statement_id,sql_type,table_name,column_name,usage"));

        // selectByCondition: マージでname/emailがSELECTとWHEREの両方に
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,name,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,name,WHERE"));

        // selectWithJoin: JOIN条件のカラム
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectWithJoin,SELECT,users,id,JOIN"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectWithJoin,SELECT,orders,user_id,JOIN"));

        // insertUser: INSERT usage
        assertTrue(csv.contains("com.example.mapper.UserMapper.insertUser,INSERT,users,id,INSERT"));

        // selectAllColumns: * がDDLで展開される
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,id,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,name,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,email,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,age,SELECT"));
        // * は展開されているので * 自体は出力されない
        assertFalse(csv.contains("selectAllColumns,SELECT,users,*,SELECT"));
    }

    @Test
    void endToEndWithoutSchema() throws IOException {
        File jsonFile = resourceFile("integration-input.json");

        List<SqlEntry> entries = new JsonInputReader().read(jsonFile);
        SqlAnalyzer analyzer = new SqlAnalyzer(null); // スキーマなし
        Set<ColumnRef> allRefs = new LinkedHashSet<>();
        for (SqlEntry entry : entries) {
            for (String sql : entry.getSqlList()) {
                try {
                    allRefs.addAll(analyzer.analyze(entry.getStatementId(), entry.getSqlType(), sql));
                } catch (Exception e) {
                    // skip
                }
            }
        }

        List<ColumnRef> sorted = new ArrayList<>(allRefs);

        // * は未展開
        boolean hasStar = sorted.stream().anyMatch(r ->
                r.getColumnName().equals("*") && r.getStatementId().contains("selectAllColumns"));
        assertTrue(hasStar, "Without schema, * should remain unexpanded");
    }

    private File resourceFile(String name) {
        return new File(getClass().getClassLoader().getResource(name).getFile());
    }
}
```

**Step 3: テスト成功を確認**

Run: `./gradlew test`
Expected: PASS

**注意**: `UPDATE users SET WHERE id = ?` のような不正SQLはJSqlParserがパースエラーを出す可能性がある。その場合、`SqlAnalyzer.analyze()`のtry-catchで安全にスキップされる。これは設計通りの動作。

**Step 4: Commit**

```bash
git add src/
git commit -m "test: add integration tests for end-to-end pipeline"
```

---

### Task 10: CLAUDE.md と最終確認

**Files:**
- Create: `CLAUDE.md`

**Step 1: CLAUDE.md を作成**

```markdown
# SQL Table/Column Analyzer

## Overview
mybatis-sql-extractorのJSON出力からSQL文を解析し、利用テーブル・カラム・用途（SELECT/WHERE/SET等）をCSVで出力するツール。
JSqlParserでSQL構文解析を行い、Oracle DDLファイルによる `*` 展開とエイリアス解決に対応。

## Tech Stack
- Java 21
- Gradle 8.5
- JSqlParser 5.3
- Jackson 2.17
- JUnit 5

## Build & Test
\```bash
./gradlew build     # ビルド
./gradlew test      # テスト実行
./gradlew run --args="--input <json> --ddl <ddl> --output <csv>"  # 実行
\```

## Directory Structure
- `src/main/java/com/example/sqlanalyzer/` - メインソース
  - `Main.java` - CLIエントリーポイント
  - `model/` - SqlEntry, ColumnRef, Usage
  - `input/` - JSON入力読み込み
  - `schema/` - DDL解析・スキーマ情報
  - `analyzer/` - SQL解析・カラム抽出
  - `output/` - CSV出力
- `src/test/` - テストコード・テストデータ
- `docs/` - 設計・計画ドキュメント
```

**Step 2: 全テスト + ビルド確認**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add CLAUDE.md project guide"
```

**Step 4: GitHub公開**

```bash
gh repo create sql-table-column-analyzer --public --source=. --remote=origin --description "SQL文からテーブル・カラム・用途を抽出しCSV出力するツール"
git push -u origin --all
```
