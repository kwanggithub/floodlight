package org.projectfloodlight.sync.internal;

import java.util.Iterator;

import org.projectfloodlight.sync.Versioned;
import org.projectfloodlight.sync.thrift.VersionedValue;

public class TVersionedValueIterable
    implements Iterable<Versioned<byte[]>> {
    final Iterable<VersionedValue> tvvi;
    
    public TVersionedValueIterable(Iterable<VersionedValue> tvvi) {
        this.tvvi = tvvi;
    }

    @Override
    public Iterator<Versioned<byte[]>> iterator() {
        final Iterator<VersionedValue> vs = tvvi.iterator();
        return new Iterator<Versioned<byte[]>>() {

            @Override
            public boolean hasNext() {
                return vs.hasNext();
            }

            @Override
            public Versioned<byte[]> next() {
                return TProtocolUtil.getVersionedValued(vs.next());
            }

            @Override
            public void remove() {
                vs.remove();
            }
        };
    }
}