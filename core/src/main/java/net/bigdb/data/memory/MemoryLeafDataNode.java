package net.bigdb.data.memory;

import java.math.BigDecimal;
import java.math.BigInteger;

import net.bigdb.BigDBException;
import net.bigdb.data.AbstractLeafDataNode;
import net.bigdb.data.DataNodeTypeMismatchException;
import net.bigdb.data.LeafDataNode;

public class MemoryLeafDataNode extends AbstractLeafDataNode implements LeafDataNode {
    protected LeafType scalarType;
    protected Object value;

    public MemoryLeafDataNode(Boolean value) {
        this.scalarType = LeafType.BOOLEAN;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(Long value) {
        this.scalarType = LeafType.LONG;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(BigInteger value) {
        this.scalarType = LeafType.BIG_INTEGER;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(BigDecimal value) {
        this.scalarType = LeafType.BIG_DECIMAL;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(Double value) {
        this.scalarType = LeafType.DOUBLE;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(String value) {
        this.scalarType = LeafType.STRING;
        this.value = value;
        safeFreeze();
    }

    public MemoryLeafDataNode(byte[] value) {
        this.scalarType = LeafType.BINARY;
        this.value = value;
        safeFreeze();
    }

    @Override
    public boolean isValueNull() {
        return value == null;
    }

    @Override
    public boolean getBoolean(boolean def) throws BigDBException {
        if (scalarType != LeafType.BOOLEAN)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return ((Boolean) value).booleanValue();
    }

    @Override
    public long getLong(long def) throws BigDBException {
        if (scalarType != LeafType.LONG)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return ((Long) value).longValue();
    }

    @Override
    public String getString(String def) throws BigDBException {
        return (value != null) ? value.toString() : def;
    }

    @Override
    public Object getObject(Object def) throws BigDBException {
        return value != null ? value : def;
    }

    @Override
    public BigDecimal getBigDecimal(BigDecimal def) throws BigDBException {
        if (scalarType != LeafType.BIG_DECIMAL)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return (BigDecimal) value;
    }

    @Override
    public BigInteger getBigInteger(BigInteger def) throws BigDBException {
        if (scalarType != LeafType.BIG_INTEGER)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return (BigInteger) value;
    }

    @Override
    public byte[] getBytes(byte[] def) throws BigDBException {
        if (scalarType != LeafType.BINARY)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return (byte[]) value;
    }

    @Override
    public double getDouble(double def) throws BigDBException {
        if (scalarType != LeafType.DOUBLE)
            throw new DataNodeTypeMismatchException();
        if(value == null)
            return def;
        else
            return ((Double) value).doubleValue();
    }

    @Override
    public LeafType getLeafType() throws BigDBException {
        return scalarType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((scalarType == null) ? 0 : scalarType.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        MemoryLeafDataNode other = (MemoryLeafDataNode) obj;
        if (scalarType != other.scalarType)
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }
}
