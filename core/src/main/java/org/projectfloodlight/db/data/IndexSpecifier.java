package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;

public final class IndexSpecifier {

    public enum SortOrder { FORWARD, REVERSE };

    public static final class Field {

        private final String name;
        private final SortOrder sortOrder;
        private final boolean caseSensitive;

        public Field(String name, SortOrder sortOrder, boolean caseSensitive) {
            this.name = name;
            this.sortOrder = sortOrder;
            this.caseSensitive = caseSensitive;
        }

        public String getName() {
            return name;
        }

        public SortOrder getSortOrder() {
            return sortOrder;
        }

        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        @Override
        public String toString() {
            return String.format("(%s,%s,%s)", name, sortOrder,
                    caseSensitive);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (caseSensitive ? 1231 : 1237);
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result +
                    ((sortOrder == null) ? 0 : sortOrder.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Field other = (Field) obj;
            if (caseSensitive != other.caseSensitive)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (sortOrder != other.sortOrder)
                return false;
            return true;
        }
    }

    public static class Builder {

        private final List<Field> fields = new ArrayList<Field>();
        private boolean unique;

        public Builder() {
            this.unique = true;
        }

        public Builder(boolean unique) {
            this.unique = unique;
        }

        public boolean isUnique() {
            return unique;
        }

        public Builder setUnique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public List<Field> getFields() {
            return fields;
        }

        public Builder addField(String name, SortOrder sortOrder, boolean caseSensitive) {
            Field field = new Field(name, sortOrder, caseSensitive);
            return addField(field);
        }

        public Builder addField(Field field) {
            this.fields.add(field);
            return this;
        }

        public Builder addFields(List<Field> fields) {
            this.fields.addAll(fields);
            return this;
        }

        public IndexSpecifier getIndexSpecifier() {
            return IndexSpecifier.fromFields(fields, unique);
        }
    }

    private final List<Field> fields;
    private final boolean unique;

    private IndexSpecifier(List<Field> fields, boolean unique) {
        assert fields != null;
        this.fields = ImmutableList.copyOf(fields);
        this.unique = unique;
    }

    public static IndexSpecifier fromFields(List<Field> fields, boolean unique) {
        return new IndexSpecifier(fields, unique);
    }

    public static IndexSpecifier fromFieldNames(List<String> fieldNames) {
        return new IndexSpecifier(fieldNames);
    }

    public static IndexSpecifier fromFieldNames(String... fieldNames) {
        return fromFieldNames(Arrays.asList(fieldNames));
    }

    private static List<Field> keyNamesToFieldList(List<String> keyNames) {
        if (keyNames == null || keyNames.isEmpty())
            return ImmutableList.of();
        List<Field> fieldList = new ArrayList<Field>();
        for (String keyName: keyNames) {
            fieldList.add(new Field(keyName, SortOrder.FORWARD, true));
        }
        return fieldList;
    }

    private IndexSpecifier(List<String> keyNames) {
        this(keyNamesToFieldList(keyNames), true);
    }

    public List<Field> getFields() {
        return fields;
    }

    public boolean isUnique() {
        return unique;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;
        for (Field field: fields) {
            if (firstTime)
                firstTime = false;
            else
                builder.append(',');
            if (field.sortOrder == SortOrder.REVERSE)
                builder.append('-');
            builder.append(field.name);
            if (!field.caseSensitive)
                builder.append("/i");
        }
        if (unique)
            builder.append(":u");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fields == null) ? 0 : fields.hashCode());
        result = prime * result + (unique ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IndexSpecifier other = (IndexSpecifier) obj;
        if (fields == null) {
            if (other.fields != null)
                return false;
        } else if (!fields.equals(other.fields))
            return false;
        if (unique != other.unique)
            return false;
        return true;
    }
}
