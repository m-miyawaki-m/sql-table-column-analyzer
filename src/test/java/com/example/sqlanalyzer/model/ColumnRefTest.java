package com.example.sqlanalyzer.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ColumnRefTest {

    @Test
    void constructor_and_getters() {
        ColumnRef ref = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);

        assertEquals("ns.findById", ref.getStatementId());
        assertEquals("select", ref.getSqlType());
        assertEquals("users", ref.getTableName());
        assertEquals("id", ref.getColumnName());
        assertEquals(Usage.WHERE, ref.getUsage());
    }

    @Test
    void toCsvLine_returnsCommaSeparatedValues() {
        ColumnRef ref = new ColumnRef("ns.findById", "select", "users", "name", Usage.SELECT);

        assertEquals("ns.findById,select,users,name,SELECT", ref.toCsvLine());
    }

    @Test
    void toCsvLine_withOrderBy() {
        ColumnRef ref = new ColumnRef("ns.list", "select", "orders", "created_at", Usage.ORDER_BY);

        assertEquals("ns.list,select,orders,created_at,ORDER_BY", ref.toCsvLine());
    }

    @Test
    void equals_sameFields_returnsTrue() {
        ColumnRef ref1 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);
        ColumnRef ref2 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);

        assertEquals(ref1, ref2);
    }

    @Test
    void equals_differentUsage_returnsFalse() {
        ColumnRef ref1 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);
        ColumnRef ref2 = new ColumnRef("ns.findById", "select", "users", "id", Usage.SELECT);

        assertNotEquals(ref1, ref2);
    }

    @Test
    void equals_differentTable_returnsFalse() {
        ColumnRef ref1 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);
        ColumnRef ref2 = new ColumnRef("ns.findById", "select", "orders", "id", Usage.WHERE);

        assertNotEquals(ref1, ref2);
    }

    @Test
    void equals_null_returnsFalse() {
        ColumnRef ref = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);

        assertNotEquals(null, ref);
    }

    @Test
    void hashCode_sameFields_sameHashCode() {
        ColumnRef ref1 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);
        ColumnRef ref2 = new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE);

        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void linkedHashSet_deduplicates() {
        Set<ColumnRef> set = new LinkedHashSet<>();
        set.add(new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE));
        set.add(new ColumnRef("ns.findById", "select", "users", "id", Usage.WHERE));
        set.add(new ColumnRef("ns.findById", "select", "users", "name", Usage.SELECT));

        assertEquals(2, set.size());
    }
}
