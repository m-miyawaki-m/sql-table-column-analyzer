package com.example.sqlanalyzer.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlEntryTest {

    @Test
    void singleSqlConstructor_createsEntryWithOneSql() {
        SqlEntry entry = new SqlEntry("com.example.mapper", "findById", "select",
                "SELECT * FROM users WHERE id = ?");

        assertEquals("com.example.mapper", entry.getNamespace());
        assertEquals("findById", entry.getId());
        assertEquals("select", entry.getSqlType());
        assertEquals("SELECT * FROM users WHERE id = ?", entry.getSql());
        assertEquals(1, entry.getSqlList().size());
    }

    @Test
    void listSqlConstructor_createsEntryWithMultipleSql() {
        List<String> sqls = List.of(
                "SELECT * FROM users WHERE id = ?",
                "SELECT * FROM users WHERE name = ?"
        );
        SqlEntry entry = new SqlEntry("com.example.mapper", "findUser", "select", sqls);

        assertEquals(2, entry.getSqlList().size());
        assertEquals("SELECT * FROM users WHERE id = ?", entry.getSql());
    }

    @Test
    void getStatementId_withNamespace_returnsNamespaceDotId() {
        SqlEntry entry = new SqlEntry("com.example.mapper", "findById", "select",
                "SELECT 1");

        assertEquals("com.example.mapper.findById", entry.getStatementId());
    }

    @Test
    void getStatementId_withNullNamespace_returnsIdOnly() {
        SqlEntry entry = new SqlEntry(null, "findById", "select", "SELECT 1");

        assertEquals("findById", entry.getStatementId());
    }

    @Test
    void getStatementId_withEmptyNamespace_returnsIdOnly() {
        SqlEntry entry = new SqlEntry("", "findById", "select", "SELECT 1");

        assertEquals("findById", entry.getStatementId());
    }

    @Test
    void addSql_addsNewSql() {
        SqlEntry entry = new SqlEntry("ns", "id", "select", "SELECT 1");
        entry.addSql("SELECT 2");

        assertEquals(2, entry.getSqlList().size());
        assertEquals("SELECT 1", entry.getSqlList().get(0));
        assertEquals("SELECT 2", entry.getSqlList().get(1));
    }

    @Test
    void addSql_doesNotAddDuplicate() {
        SqlEntry entry = new SqlEntry("ns", "id", "select", "SELECT 1");
        entry.addSql("SELECT 1");

        assertEquals(1, entry.getSqlList().size());
    }

    @Test
    void addSql_dedup_allowsDifferentSql() {
        SqlEntry entry = new SqlEntry("ns", "id", "select", "SELECT 1");
        entry.addSql("SELECT 2");
        entry.addSql("SELECT 1");
        entry.addSql("SELECT 3");
        entry.addSql("SELECT 2");

        assertEquals(3, entry.getSqlList().size());
        assertEquals(List.of("SELECT 1", "SELECT 2", "SELECT 3"), entry.getSqlList());
    }
}
