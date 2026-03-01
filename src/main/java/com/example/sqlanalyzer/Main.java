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

            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                System.err.println("Error: input file does not exist: " + inputPath);
                System.exit(1);
            }
            List<SqlEntry> entries = new JsonInputReader().read(inputFile);
            System.err.println("Loaded " + entries.size() + " SQL entries.");

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
