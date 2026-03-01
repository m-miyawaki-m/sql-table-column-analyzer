package com.example.sqlanalyzer.analyzer;

import com.example.sqlanalyzer.model.ColumnRef;
import com.example.sqlanalyzer.model.Usage;
import com.example.sqlanalyzer.schema.SchemaInfo;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * SQL文を解析し、テーブル・カラムの参照情報を抽出するクラス。
 * JSqlParserを使用してSQLをASTに変換し、各句（SELECT, WHERE, SET等）の
 * カラム参照をColumnRefとして返す。
 */
public class SqlAnalyzer {

    private final SchemaInfo schema;

    /**
     * SqlAnalyzerを生成する。
     *
     * @param schema スキーマ情報（nullの場合は「*」展開やカラムのテーブル解決が制限される）
     */
    public SqlAnalyzer(SchemaInfo schema) {
        this.schema = schema;
    }

    /**
     * SQL文を解析し、テーブル・カラム参照のリストを返す。
     *
     * @param statementId namespace.id形式のstatement ID
     * @param sqlType     SQL種別（select, insert, update, delete）
     * @param sql         解析対象のSQL文
     * @return カラム参照のリスト（重複排除済み）
     */
    public List<ColumnRef> analyze(String statementId, String sqlType, String sql) {
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            LinkedHashSet<ColumnRef> results = new LinkedHashSet<>();

            if (stmt instanceof PlainSelect) {
                processPlainSelect((PlainSelect) stmt, statementId, sqlType, results);
            } else if (stmt instanceof Select) {
                // Other Select variants (SetOperationList, etc.) - not handled in detail
                // but PlainSelect is the primary case
            } else if (stmt instanceof Update) {
                processUpdate((Update) stmt, statementId, sqlType, results);
            } else if (stmt instanceof Insert) {
                processInsert((Insert) stmt, statementId, sqlType, results);
            } else if (stmt instanceof Delete) {
                processDelete((Delete) stmt, statementId, sqlType, results);
            }

            return new ArrayList<>(results);
        } catch (Exception e) {
            System.err.println("WARNING: Failed to parse SQL (statementId=" + statementId + "): " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * PlainSelect文を処理する。
     */
    private void processPlainSelect(PlainSelect ps, String statementId, String sqlType,
                                     LinkedHashSet<ColumnRef> results) {
        // エイリアスマップとテーブルリストを構築
        Map<String, String> aliasMap = new HashMap<>();
        List<String> tableNames = new ArrayList<>();
        buildAliasMap(ps, aliasMap, tableNames);

        // SELECT句
        processSelectItems(ps.getSelectItems(), statementId, sqlType, aliasMap, tableNames, results);

        // WHERE句
        if (ps.getWhere() != null) {
            collectColumns(ps.getWhere(), statementId, sqlType, Usage.WHERE, aliasMap, tableNames, results);
        }

        // JOIN ON句
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                Collection<Expression> onExprs = join.getOnExpressions();
                if (onExprs != null) {
                    for (Expression onExpr : onExprs) {
                        collectColumns(onExpr, statementId, sqlType, Usage.JOIN, aliasMap, tableNames, results);
                    }
                }
            }
        }

        // ORDER BY句
        if (ps.getOrderByElements() != null) {
            for (OrderByElement obe : ps.getOrderByElements()) {
                collectColumns(obe.getExpression(), statementId, sqlType, Usage.ORDER_BY,
                        aliasMap, tableNames, results);
            }
        }

        // GROUP BY句
        GroupByElement groupBy = ps.getGroupBy();
        if (groupBy != null) {
            ExpressionList<?> groupByExprs = groupBy.getGroupByExpressionList();
            if (groupByExprs != null) {
                for (Object expr : groupByExprs) {
                    if (expr instanceof Expression) {
                        collectColumns((Expression) expr, statementId, sqlType, Usage.GROUP_BY,
                                aliasMap, tableNames, results);
                    }
                }
            }
        }
    }

    /**
     * Update文を処理する。
     */
    private void processUpdate(Update update, String statementId, String sqlType,
                                LinkedHashSet<ColumnRef> results) {
        String tableName = update.getTable().getName();
        List<String> tableNames = List.of(tableName);
        Map<String, String> aliasMap = new HashMap<>();

        // SET句
        for (UpdateSet updateSet : update.getUpdateSets()) {
            for (Object colObj : updateSet.getColumns()) {
                if (colObj instanceof Column) {
                    Column col = (Column) colObj;
                    results.add(new ColumnRef(statementId, sqlType, tableName,
                            col.getColumnName().toLowerCase(), Usage.SET));
                }
            }
        }

        // WHERE句
        if (update.getWhere() != null) {
            collectColumns(update.getWhere(), statementId, sqlType, Usage.WHERE,
                    aliasMap, tableNames, results);
        }
    }

    /**
     * Insert文を処理する。
     */
    private void processInsert(Insert insert, String statementId, String sqlType,
                                LinkedHashSet<ColumnRef> results) {
        String tableName = insert.getTable().getName();

        if (insert.getColumns() != null) {
            for (Object colObj : insert.getColumns()) {
                if (colObj instanceof Column) {
                    Column col = (Column) colObj;
                    results.add(new ColumnRef(statementId, sqlType, tableName.toLowerCase(),
                            col.getColumnName().toLowerCase(), Usage.INSERT));
                }
            }
        }
    }

    /**
     * Delete文を処理する。
     */
    private void processDelete(Delete delete, String statementId, String sqlType,
                                LinkedHashSet<ColumnRef> results) {
        String tableName = delete.getTable().getName();
        List<String> tableNames = List.of(tableName);
        Map<String, String> aliasMap = new HashMap<>();

        // WHERE句
        if (delete.getWhere() != null) {
            collectColumns(delete.getWhere(), statementId, sqlType, Usage.WHERE,
                    aliasMap, tableNames, results);
        }
    }

    /**
     * PlainSelectからエイリアスマップとテーブル名リストを構築する。
     */
    private void buildAliasMap(PlainSelect ps, Map<String, String> aliasMap, List<String> tableNames) {
        // FROM句
        FromItem fromItem = ps.getFromItem();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            String tableName = table.getName();
            tableNames.add(tableName);
            if (table.getAlias() != null) {
                aliasMap.put(table.getAlias().getName().toLowerCase(), tableName);
            }
        }

        // JOIN句
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                FromItem joinItem = join.getFromItem();
                if (joinItem instanceof Table) {
                    Table joinTable = (Table) joinItem;
                    String joinTableName = joinTable.getName();
                    tableNames.add(joinTableName);
                    if (joinTable.getAlias() != null) {
                        aliasMap.put(joinTable.getAlias().getName().toLowerCase(), joinTableName);
                    }
                }
            }
        }
    }

    /**
     * SELECT句のアイテムを処理する。
     */
    private void processSelectItems(List<SelectItem<?>> selectItems, String statementId, String sqlType,
                                     Map<String, String> aliasMap, List<String> tableNames,
                                     LinkedHashSet<ColumnRef> results) {
        for (SelectItem<?> item : selectItems) {
            Expression expr = item.getExpression();

            if (expr instanceof AllColumns) {
                // SELECT *
                expandStar(null, statementId, sqlType, tableNames, results);
            } else if (expr instanceof AllTableColumns) {
                // SELECT t.*
                AllTableColumns atc = (AllTableColumns) expr;
                String tableRef = atc.getTable().getName();
                String resolvedTable = resolveAlias(tableRef, aliasMap);
                expandStar(resolvedTable, statementId, sqlType, tableNames, results);
            } else {
                // 通常の式（カラム、関数等）
                collectColumns(expr, statementId, sqlType, Usage.SELECT, aliasMap, tableNames, results);
            }
        }
    }

    /**
     * 「*」を展開する。
     * schemaがある場合はテーブルの全カラムに展開し、ない場合は「*」として出力する。
     *
     * @param tableName 特定テーブルの場合はテーブル名、SELECT *の場合はnull
     */
    private void expandStar(String tableName, String statementId, String sqlType,
                             List<String> tableNames, LinkedHashSet<ColumnRef> results) {
        if (tableName != null) {
            // 特定テーブルの *
            if (schema != null && schema.hasTable(tableName)) {
                List<String> columns = schema.getColumns(tableName);
                for (String col : columns) {
                    results.add(new ColumnRef(statementId, sqlType, tableName.toLowerCase(),
                            col.toLowerCase(), Usage.SELECT));
                }
            } else {
                results.add(new ColumnRef(statementId, sqlType, tableName.toLowerCase(),
                        "*", Usage.SELECT));
            }
        } else {
            // SELECT * (全テーブル)
            for (String tn : tableNames) {
                if (schema != null && schema.hasTable(tn)) {
                    List<String> columns = schema.getColumns(tn);
                    for (String col : columns) {
                        results.add(new ColumnRef(statementId, sqlType, tn.toLowerCase(),
                                col.toLowerCase(), Usage.SELECT));
                    }
                } else {
                    results.add(new ColumnRef(statementId, sqlType, tn.toLowerCase(),
                            "*", Usage.SELECT));
                }
            }
        }
    }

    /**
     * 式からカラム参照を再帰的に収集する。
     * ExpressionVisitorAdapterを使用して式ツリーを走査する。
     */
    private void collectColumns(Expression expr, String statementId, String sqlType,
                                 Usage usage, Map<String, String> aliasMap,
                                 List<String> tableNames, LinkedHashSet<ColumnRef> results) {
        if (expr == null) {
            return;
        }

        expr.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                String colName = column.getColumnName().toLowerCase();
                String resolvedTable;

                if (column.getTable() != null && column.getTable().getName() != null) {
                    // テーブル修飾あり（例: u.name）
                    String tableRef = column.getTable().getName();
                    resolvedTable = resolveAlias(tableRef, aliasMap);
                } else {
                    // テーブル修飾なし
                    resolvedTable = resolveUnqualifiedColumn(colName, tableNames);
                }

                results.add(new ColumnRef(statementId, sqlType,
                        resolvedTable.toLowerCase(), colName, usage));
                return null;
            }
        });
    }

    /**
     * エイリアスを実テーブル名に解決する。
     */
    private String resolveAlias(String tableRef, Map<String, String> aliasMap) {
        String resolved = aliasMap.get(tableRef.toLowerCase());
        return resolved != null ? resolved : tableRef;
    }

    /**
     * テーブル修飾のないカラム名からテーブルを解決する。
     * - テーブルが1つの場合はそのテーブル
     * - 複数テーブルの場合はSchemaInfoで解決を試みる
     * - 解決できない場合は"UNKNOWN"
     */
    private String resolveUnqualifiedColumn(String columnName, List<String> tableNames) {
        if (tableNames.size() == 1) {
            return tableNames.get(0);
        }

        if (schema != null) {
            String resolved = schema.resolveTableByColumn(columnName, tableNames);
            if (resolved != null) {
                return resolved;
            }
        }

        return "UNKNOWN";
    }
}
