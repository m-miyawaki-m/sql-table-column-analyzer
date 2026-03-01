package com.example.sqlanalyzer.schema;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * DDLファイルをパースしてSchemaInfoを構築するクラス。
 * JSqlParserを使用してCREATE TABLE文からテーブル名とカラム名を抽出する。
 */
public class DdlParser {

    private static final Logger logger = Logger.getLogger(DdlParser.class.getName());

    /**
     * DDLファイルを読み込み、SchemaInfoを構築して返す。
     * 一括パースに失敗した場合は、セミコロンで分割して個別にパースを試みる。
     *
     * @param ddlFile DDLファイル
     * @return パース結果のSchemaInfo
     * @throws IOException ファイル読み込みに失敗した場合
     */
    public SchemaInfo parse(File ddlFile) throws IOException {
        String content = Files.readString(ddlFile.toPath());
        SchemaInfo schemaInfo = new SchemaInfo();

        try {
            // 一括パースを試みる
            Statements statements = CCJSqlParserUtil.parseStatements(content);
            for (Statement stmt : statements) {
                processStatement(stmt, schemaInfo);
            }
        } catch (Exception e) {
            // 一括パースに失敗した場合、セミコロンで分割して個別にパース
            logger.warning("Bulk parse failed, falling back to individual statement parsing: " + e.getMessage());
            parseIndividually(content, schemaInfo);
        }

        return schemaInfo;
    }

    /**
     * 文字列をセミコロンで分割し、個別にパースする（フォールバック）。
     */
    private void parseIndividually(String content, SchemaInfo schemaInfo) {
        String[] parts = content.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Statement stmt = CCJSqlParserUtil.parse(trimmed);
                processStatement(stmt, schemaInfo);
            } catch (Exception e) {
                logger.warning("Failed to parse statement: " + e.getMessage());
            }
        }
    }

    /**
     * 単一のStatementを処理し、CREATE TABLEであればSchemaInfoに登録する。
     */
    private void processStatement(Statement stmt, SchemaInfo schemaInfo) {
        if (stmt instanceof CreateTable createTable) {
            String tableName = createTable.getTable().getName();
            List<ColumnDefinition> columnDefs = createTable.getColumnDefinitions();
            if (columnDefs != null) {
                List<String> columnNames = columnDefs.stream()
                        .map(ColumnDefinition::getColumnName)
                        .collect(Collectors.toList());
                schemaInfo.addTable(tableName, columnNames);
            }
        }
    }
}
