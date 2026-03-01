# SQL Table/Column Analyzer 設計ドキュメント

## 概要

mybatis-sql-extractorが出力するJSON（SQL + メタ情報）を入力とし、
JSqlParserでSQL文を構文解析して利用テーブル・カラムを抽出し、CSVで出力するツール。
Oracle DDLファイルを併用することで `SELECT *` を実カラムに展開する。

## 入力

1. **mybatis-sql-extractorのJSON出力ファイル**（必須）
   - statement_id, sql_type, sql, branchPattern等を含む
   - 同一IDのALL_SET/ALL_NULLパターンはマージ（和集合）して解析

2. **Oracle DDLファイル**（任意）
   - CREATE TABLE文を含むSQLファイル
   - `*` を実カラムに展開するために使用
   - 省略時は `*` を未展開のまま出力

## 出力

### CSV形式

```csv
statement_id,sql_type,table_name,column_name,usage
selectByCondition,SELECT,users,id,SELECT
selectByCondition,SELECT,users,name,SELECT
selectByCondition,SELECT,users,name,WHERE
selectByCondition,SELECT,users,email,SELECT
selectByCondition,SELECT,users,email,WHERE
updateSelective,UPDATE,users,name,SET
updateSelective,UPDATE,users,email,SET
updateSelective,UPDATE,users,id,WHERE
```

### usageの種類

| usage | 意味 | 例 |
|-------|------|-----|
| SELECT | SELECT句で参照 | `SELECT name FROM ...` |
| WHERE | WHERE句で条件に使用 | `WHERE id = ?` |
| SET | UPDATE SET句で更新 | `SET name = ?` |
| INSERT | INSERT対象カラム | `INSERT INTO t(name)` |
| JOIN | JOIN条件で使用 | `ON a.id = b.a_id` |
| ORDER_BY | ORDER BY句 | `ORDER BY name` |
| GROUP_BY | GROUP BY句 | `GROUP BY dept` |

## アーキテクチャ

```
DDL File ──→ DdlParser ──→ SchemaInfo（テーブル→カラム一覧Map）
                                    ↓
JSON File ──→ JsonInputReader ──→ SqlEntry[] ──→ SqlAnalyzer（JSqlParser）
                                                    ↓
                                        エイリアス解決 + * 展開
                                                    ↓
                                               CsvWriter
```

## クラス設計

| クラス | パッケージ | 役割 |
|-------|-----------|------|
| `Main` | sqlanalyzer | CLIエントリーポイント |
| `SqlEntry` | model | JSON入力1件分のデータクラス |
| `ColumnRef` | model | 抽出結果1行分のデータクラス |
| `JsonInputReader` | input | JSON読み込み、分岐パターンのマージ |
| `SchemaInfo` | schema | テーブル→カラム一覧のMap |
| `DdlParser` | schema | DDLをパースしSchemaInfoを構築 |
| `SqlAnalyzer` | analyzer | JSqlParserでSQL解析、テーブル・カラム・usage抽出 |
| `CsvWriter` | output | CSV出力 |

## CLIオプション

```
--input, -i <path>   mybatis-sql-extractorのJSON出力ファイル（必須）
--ddl, -d <path>     Oracle DDLファイル（* 展開用、省略可）
--output, -o <path>  CSV出力先（省略時は標準出力）
```

## 処理仕様

### エイリアス解決

- `SELECT u.name FROM users u` → `table_name=users, column_name=name`
- テーブルエイリアスはJSqlParserのAST上で実テーブル名に逆引き

### `*` 展開

- DDLあり: `SELECT * FROM users` → DDLからusersの全カラムを展開、各カラム1行ずつ出力
- DDLなし or 定義のないテーブル: `column_name=*` のまま出力（警告ログ）

### サブクエリ

- `SELECT * FROM (SELECT id, name FROM users) t` → 内側のSELECT句から `id, name` を解決

### 分岐パターンのマージ

- 同一statement_idのALL_SET/ALL_NULLを両方解析
- テーブル・カラム・usageの和集合を取る
- 重複行（同一statement_id + table + column + usage）は除去

### カラム所属テーブルの解決

- エイリアス修飾あり: エイリアスから実テーブル名を解決
- エイリアス修飾なし + 単一テーブル: そのテーブルに帰属
- エイリアス修飾なし + 複数テーブル: DDLからカラム名でテーブルを逆引き
  - 複数テーブルに同名カラムがある場合: `table_name=UNKNOWN`（警告ログ）
  - DDLなし: `table_name=UNKNOWN`

## エラーハンドリング

| ケース | 対策 |
|-------|------|
| JSqlParser解析失敗 | 警告ログ、当該statementスキップ、ツール継続 |
| DDLファイル省略 | `*` は未展開、`table_name=UNKNOWN` の可能性増 |
| DDLに定義のないテーブルの `*` | `*` のまま出力、警告ログ |
| DDLパース失敗 | 警告ログ、当該テーブルのスキーマなしで続行 |
| カラム所属テーブル不明 | `table_name=UNKNOWN`、警告ログ |
| 分岐マージ時の重複 | 同一(statement_id, table, column, usage)は除去 |

## Tech Stack

- Java 21
- Gradle 8.5
- JSqlParser 5.x（SQL解析）
- Jackson（JSON読み込み）
- JUnit 5
