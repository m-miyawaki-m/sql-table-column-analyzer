package com.example.sqlanalyzer.input;

import com.example.sqlanalyzer.model.SqlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonInputReaderTest {

    private JsonInputReader reader;
    private List<SqlEntry> entries;

    @BeforeEach
    void setUp() throws IOException {
        reader = new JsonInputReader();
        File testFile = new File(getClass().getClassLoader().getResource("test-input.json").getFile());
        entries = reader.read(testFile);
    }

    @Test
    void read_mergesBranchEntries_intoOneSqlEntryWithTwoSqls() {
        SqlEntry merged = entries.stream()
                .filter(e -> e.getId().equals("selectByCondition"))
                .findFirst()
                .orElseThrow();

        assertEquals(2, merged.getSqlList().size());
        assertEquals("SELECT id, name, email FROM users WHERE 1=1 AND name = ? AND email = ?",
                merged.getSqlList().get(0));
        assertEquals("SELECT id, name, email FROM users WHERE 1=1",
                merged.getSqlList().get(1));
    }

    @Test
    void read_nonBranchEntry_hasOneSql() {
        SqlEntry single = entries.stream()
                .filter(e -> e.getId().equals("selectAll"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, single.getSqlList().size());
        assertEquals("SELECT id, name FROM users", single.getSql());
    }

    @Test
    void read_mergedEntryCount_isTwo() {
        assertEquals(2, entries.size());
    }
}
