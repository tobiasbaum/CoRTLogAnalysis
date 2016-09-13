package de.setsoftware.cortLogAnalysis;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

public class SqlWriter<T> implements Closeable {

    private final Writer w;
    private boolean headerWritten;
    private final Map<String, Function<? super T, ? extends Object>> columns = new LinkedHashMap<>();
    private final Map<String, String> columnTypes = new LinkedHashMap<>();
    private final Set<String> primaryKey = new LinkedHashSet<>();
    private final String tablename;

    public SqlWriter(final File file, String tablename) throws IOException {
        this.w = new FileWriter(file);
        this.tablename = tablename;
    }

    public void addKeyColumn(final String name, final String type, final Function<? super T, ? extends Object> value) {
        this.addColumn(name, type, value);
        this.primaryKey.add(name);
    }

    public void addColumn(final String name, final String type, final Function<? super T, ? extends Object> value) {
        assert !this.headerWritten;
        assert !this.columns.containsKey(name);
        this.columns.put(name, value);
        this.columnTypes.put(name, type);
    }

    public void writeRow(final T item) throws IOException {
        if (!this.headerWritten) {
            this.writeHeader();
            this.headerWritten = true;
        }

        this.w.write("INSERT INTO `");
        this.w.write(this.tablename);
        this.w.write("` VALUES(");
        boolean firstInRecord = true;
        for (final Function<? super T, ? extends Object> f : this.columns.values()) {
            if (firstInRecord) {
                firstInRecord = false;
            } else {
                this.w.write(',');
            }
            this.w.write("'" + this.toSql(f.apply(item)) + "'");
        }
        this.w.write(");\n");
    }

    private String toSql(final Object o) {
        if (o instanceof Instant) {
            return o.toString().replace("T", " ").replaceAll("Z", "");
        } else {
            return o.toString();
        }
    }

    private void writeHeader() throws IOException {
        this.w.write("DROP TABLE IF EXISTS `"+ this.tablename + "`;\n");

        final StringBuilder createTable = new StringBuilder();
        createTable.append("CREATE TABLE `").append(this.tablename).append("` (");
        for (final Entry<String, String> nameAndType : this.columnTypes.entrySet()) {
            createTable.append("`").append(nameAndType.getKey()).append("` ").append(nameAndType.getValue()).append(",");
        }
        createTable.append("PRIMARY KEY (");
        boolean first = true;
        for (final String name : this.primaryKey) {
            if (first) {
                first = false;
            } else {
                createTable.append(",");
            }
            createTable.append("`").append(name).append("`");
        }
        createTable.append("));\n");
        this.w.write(createTable.toString());
    }

    @Override
    public void close() throws IOException {
        this.w.close();
    }

}
