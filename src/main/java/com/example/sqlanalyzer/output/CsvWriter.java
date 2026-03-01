package com.example.sqlanalyzer.output;

import com.example.sqlanalyzer.model.ColumnRef;

import java.io.*;
import java.util.List;

/**
 * ColumnRefリストをCSV形式で出力するクラス。
 */
public class CsvWriter {

    private static final String HEADER = "statement_id,sql_type,table_name,column_name,usage";

    /**
     * ColumnRefリストをCSV形式でWriterに書き出す。
     *
     * @param refs   出力するColumnRefリスト
     * @param writer 出力先Writer
     * @throws IOException 書き込みエラー時
     */
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

    /**
     * ColumnRefリストをCSV形式でファイルに書き出す。
     *
     * @param refs       出力するColumnRefリスト
     * @param outputFile 出力先ファイル
     * @throws IOException 書き込みエラー時
     */
    public void write(List<ColumnRef> refs, File outputFile) throws IOException {
        try (FileWriter fw = new FileWriter(outputFile)) {
            write(refs, fw);
        }
    }
}
