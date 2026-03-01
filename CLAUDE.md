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
```bash
./gradlew build     # ビルド
./gradlew test      # テスト実行
./gradlew run --args="--input <json> --ddl <ddl> --output <csv>"  # 実行
```

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
