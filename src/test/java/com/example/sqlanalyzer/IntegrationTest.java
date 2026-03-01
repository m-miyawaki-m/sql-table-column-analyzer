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

        List<SqlEntry> entries = new JsonInputReader().read(jsonFile);
        SchemaInfo schema = new DdlParser().parse(ddlFile);
        SqlAnalyzer analyzer = new SqlAnalyzer(schema);
        Set<ColumnRef> allRefs = new LinkedHashSet<>();
        for (SqlEntry entry : entries) {
            for (String sql : entry.getSqlList()) {
                allRefs.addAll(analyzer.analyze(entry.getStatementId(), entry.getSqlType(), sql));
            }
        }
        List<ColumnRef> sorted = new ArrayList<>(allRefs);
        sorted.sort(Comparator.comparing(ColumnRef::getStatementId)
                .thenComparing(ColumnRef::getTableName)
                .thenComparing(ColumnRef::getColumnName)
                .thenComparing(r -> r.getUsage().ordinal()));

        StringWriter sw = new StringWriter();
        new CsvWriter().write(sorted, sw);
        String csv = sw.toString();

        // Header
        assertTrue(csv.contains("statement_id,sql_type,table_name,column_name,usage"));

        // selectByCondition: merged - name/email in SELECT and WHERE
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,name,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,name,WHERE"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,email,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectByCondition,SELECT,users,email,WHERE"));

        // selectWithJoin: JOIN columns
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectWithJoin,SELECT,users,id,JOIN"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectWithJoin,SELECT,orders,user_id,JOIN"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectWithJoin,SELECT,orders,amount,WHERE"));

        // insertUser
        assertTrue(csv.contains("com.example.mapper.UserMapper.insertUser,INSERT,users,id,INSERT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.insertUser,INSERT,users,name,INSERT"));

        // selectAllColumns: * expanded via DDL
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,id,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,name,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,email,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,age,SELECT"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.selectAllColumns,SELECT,users,created_at,SELECT"));
        // * should NOT appear when schema is present
        assertFalse(csv.contains("selectAllColumns,SELECT,users,*"));

        // updateSelective
        assertTrue(csv.contains("com.example.mapper.UserMapper.updateSelective,UPDATE,users,name,SET"));
        assertTrue(csv.contains("com.example.mapper.UserMapper.updateSelective,UPDATE,users,id,WHERE"));
    }

    @Test
    void endToEndWithoutSchema() throws IOException {
        File jsonFile = resourceFile("integration-input.json");
        List<SqlEntry> entries = new JsonInputReader().read(jsonFile);
        SqlAnalyzer analyzer = new SqlAnalyzer(null);
        Set<ColumnRef> allRefs = new LinkedHashSet<>();
        for (SqlEntry entry : entries) {
            for (String sql : entry.getSqlList()) {
                allRefs.addAll(analyzer.analyze(entry.getStatementId(), entry.getSqlType(), sql));
            }
        }

        // * should remain unexpanded
        boolean hasStar = allRefs.stream().anyMatch(r ->
                r.getColumnName().equals("*") && r.getStatementId().contains("selectAllColumns"));
        assertTrue(hasStar, "Without schema, * should remain unexpanded");
    }

    private File resourceFile(String name) {
        return new File(getClass().getClassLoader().getResource(name).getFile());
    }
}
