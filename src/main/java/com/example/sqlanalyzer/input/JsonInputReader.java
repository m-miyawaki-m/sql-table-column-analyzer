package com.example.sqlanalyzer.input;

import com.example.sqlanalyzer.model.SqlEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mybatis-sql-extractorが出力するJSON形式のファイルを読み込み、
 * SqlEntryのリストに変換するクラス。
 *
 * 同一のnamespace + idを持つエントリはマージされ、
 * 複数のSQL文を1つのSqlEntryに統合する。
 */
public class JsonInputReader {

    private final ObjectMapper objectMapper;

    public JsonInputReader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * JSONファイルを読み込み、SqlEntryのリストを返す。
     * 同一のnamespace.idを持つエントリはマージされる。
     *
     * @param jsonFile 読み込むJSONファイル
     * @return マージ済みのSqlEntryリスト
     * @throws IOException ファイル読み込みエラー
     */
    public List<SqlEntry> read(File jsonFile) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonFile);

        // LinkedHashMapで挿入順序を保持
        Map<String, SqlEntry> entryMap = new LinkedHashMap<>();

        for (JsonNode node : rootNode) {
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
