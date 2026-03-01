package com.example.sqlanalyzer.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class DdlParserTest {

    private static SchemaInfo schemaInfo;

    @BeforeAll
    static void setUp() throws Exception {
        File ddlFile = new File(
                Objects.requireNonNull(
                        DdlParserTest.class.getClassLoader().getResource("test-schema.sql")
                ).toURI()
        );
        DdlParser parser = new DdlParser();
        schemaInfo = parser.parse(ddlFile);
    }

    @Test
    void parseCreateTable_usersHasFiveColumns() {
        List<String> columns = schemaInfo.getColumns("users");
        assertNotNull(columns);
        assertEquals(5, columns.size());
        assertEquals(List.of("id", "name", "email", "age", "created_at"), columns);
    }

    @Test
    void caseInsensitiveLookup_USERSequalsUsers() {
        assertTrue(schemaInfo.hasTable("USERS"));
        assertTrue(schemaInfo.hasTable("users"));
        assertTrue(schemaInfo.hasTable("Users"));
        List<String> columns = schemaInfo.getColumns("USERS");
        assertNotNull(columns);
        assertEquals(5, columns.size());
    }

    @Test
    void unknownTable_returnsNull() {
        assertFalse(schemaInfo.hasTable("nonexistent"));
        assertNull(schemaInfo.getColumns("nonexistent"));
    }

    @Test
    void resolveTableByColumn_uniqueColumn_returnsOrders() {
        // order_id はordersテーブルにのみ存在する
        String resolved = schemaInfo.resolveTableByColumn(
                "order_id", List.of("users", "orders", "departments"));
        assertEquals("orders", resolved);
    }

    @Test
    void resolveTableByColumn_columnInSingleCandidateTable() {
        // candidateにordersだけを指定し、order_idで解決できることを確認
        String resolved = schemaInfo.resolveTableByColumn(
                "order_id", List.of("orders"));
        assertEquals("orders", resolved);
    }
}
