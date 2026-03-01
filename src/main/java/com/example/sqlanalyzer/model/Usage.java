package com.example.sqlanalyzer.model;

/**
 * SQL文中でカラムがどのような用途で使用されているかを表す列挙型。
 */
public enum Usage {
    SELECT,
    WHERE,
    SET,
    INSERT,
    JOIN,
    ORDER_BY,
    GROUP_BY
}
