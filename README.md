# SQL Table/Column Analyzer

mybatis-sql-extractorのJSON出力からSQL文を解析し、利用テーブル・カラム・用途（SELECT/WHERE/SET等）をCSVで出力するツール。

## 必要なライブラリ

`libs/` ディレクトリに以下のjarを配置してください。

### 実行時ライブラリ

| ファイル名 | 説明 | ダウンロードURL |
|-----------|------|----------------|
| `jsqlparser-5.3.jar` | SQL構文解析 | https://repo1.maven.org/maven2/com/github/jsqlparser/jsqlparser/5.3/jsqlparser-5.3.jar |
| `jmh-core-1.37.jar` | JSqlParser推移的依存 | https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar |
| `jopt-simple-5.0.4.jar` | JSqlParser推移的依存 | https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar |
| `commons-math3-3.6.1.jar` | JSqlParser推移的依存 | https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar |
| `jackson-databind-2.17.0.jar` | JSON読み込み | https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.0/jackson-databind-2.17.0.jar |
| `jackson-core-2.17.0.jar` | Jackson推移的依存 | https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.0/jackson-core-2.17.0.jar |
| `jackson-annotations-2.17.0.jar` | Jackson推移的依存 | https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.0/jackson-annotations-2.17.0.jar |
| `byte-buddy-1.14.9.jar` | Jackson推移的依存 | https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.14.9/byte-buddy-1.14.9.jar |

### 一括ダウンロード

```bash
mkdir -p libs && cd libs
curl -LO https://repo1.maven.org/maven2/com/github/jsqlparser/jsqlparser/5.3/jsqlparser-5.3.jar
curl -LO https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar
curl -LO https://repo1.maven.org/maven2/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar
curl -LO https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar
curl -LO https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.0/jackson-databind-2.17.0.jar
curl -LO https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.0/jackson-core-2.17.0.jar
curl -LO https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.0/jackson-annotations-2.17.0.jar
curl -LO https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.14.9/byte-buddy-1.14.9.jar
```

## ビルド・実行

```bash
./gradlew build
./gradlew run --args="--input <json> --ddl <ddl> --output <csv>"
```

## CLIオプション

| オプション | 説明 |
|-----------|------|
| `--input, -i <path>` | mybatis-sql-extractorのJSON出力ファイル（必須） |
| `--ddl, -d <path>` | Oracle DDLファイル（`*` 展開用、省略可） |
| `--output, -o <path>` | CSV出力先（省略時は標準出力） |
| `--help, -h` | ヘルプ表示 |

## 出力CSV形式

```csv
statement_id,sql_type,table_name,column_name,usage
com.example.mapper.UserMapper.selectByCondition,SELECT,users,id,SELECT
com.example.mapper.UserMapper.selectByCondition,SELECT,users,name,WHERE
com.example.mapper.UserMapper.updateSelective,UPDATE,users,name,SET
```

### usageの種類

| usage | 意味 |
|-------|------|
| SELECT | SELECT句で参照 |
| WHERE | WHERE句で条件に使用 |
| SET | UPDATE SET句で更新 |
| INSERT | INSERT対象カラム |
| JOIN | JOIN条件で使用 |
| ORDER_BY | ORDER BY句 |
| GROUP_BY | GROUP BY句 |
