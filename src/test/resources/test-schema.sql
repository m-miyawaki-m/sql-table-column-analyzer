CREATE TABLE users (
    id NUMBER(10) NOT NULL,
    name VARCHAR2(100),
    email VARCHAR2(200),
    age NUMBER(3),
    created_at DATE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE orders (
    order_id NUMBER(10) NOT NULL,
    user_id NUMBER(10),
    product_name VARCHAR2(200),
    amount NUMBER(10, 2),
    order_date DATE,
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
);

CREATE TABLE departments (
    dept_id NUMBER(10) NOT NULL,
    dept_name VARCHAR2(100),
    CONSTRAINT pk_departments PRIMARY KEY (dept_id)
);
