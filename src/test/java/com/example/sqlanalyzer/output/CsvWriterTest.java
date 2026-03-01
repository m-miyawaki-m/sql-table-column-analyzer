package com.example.sqlanalyzer.output;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvWriterTest {

    private final CsvWriter csvWriter = new CsvWriter();

    @Test
    void writeToWriter_twoRefs_headerAndTwoDataLines() throws IOException {
        List<ColumnRef> refs = List.of(
                new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE),
                new ColumnRef("ns.findById", "select", "users", "name", Usage.SELECT)
        );

        StringWriter sw = new StringWriter();
        csvWriter.write(refs, sw);

        String[] lines = sw.toString().split(System.lineSeparator());
        assertEquals(3, lines.length);
        assertEquals("statement_id,sql_type,table_name,column_name,usage", lines[0]);
        assertEquals("ns.findById,select,users,id,WHERE", lines[1]);
        assertEquals("ns.findById,select,users,name,SELECT", lines[2]);
    }

    @Test
    void emptyList_headerOnly() throws IOException {
        List<ColumnRef> refs = List.of();

        StringWriter sw = new StringWriter();
        csvWriter.write(refs, sw);

        String[] lines = sw.toString().split(System.lineSeparator());
        assertEquals(1, lines.length);
        assertEquals("statement_id,sql_type,table_name,column_name,usage", lines[0]);
    }
}
