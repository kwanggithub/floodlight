package net.bigdb.data;

import java.util.Iterator;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

/**
 * Iterator implementation that wraps an application-provided iterator and
 * converts the elements returned from the underlying iterator to data nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DataNodeMappingIterator extends UnmodifiableIterator<DataNode> {

    /** The iterator from the application code */
    private Iterator<?> iterator;

    /** Schema node for the elements being iterated over */
    private SchemaNode elementSchemaNode;

    DataNodeMappingIterator(Iterator<?> iterator, SchemaNode elementSchemaNode) {
        this.iterator = iterator;
        this.elementSchemaNode = elementSchemaNode;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DataNode next() {
        try {
            // Convert the object that's returned from the
            // application-defined iterator to a data node.
            Object element = iterator.next();
            DataNodeMapper mapper = DataNodeMapper.getDefaultMapper();
            DataNode elementDataNode =
                    mapper.convertObjectToDataNode(element, elementSchemaNode);
            return elementDataNode;
        } catch (BigDBException e) {
            throw new BigDBInternalError(
                    "Unexpected error converting application object to data node");
        }
    }
}
