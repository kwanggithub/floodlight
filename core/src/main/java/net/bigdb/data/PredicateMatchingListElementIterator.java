package net.bigdb.data;

import java.util.Iterator;
import java.util.NoSuchElementException;

import net.bigdb.BigDBException;
import net.bigdb.query.Step;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * List element iterator that iterates over a base list element iterator and
 * only returns the elements that match the predicates in the specified
 * matching query step.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class PredicateMatchingListElementIterator implements Iterator<DataNode> {

    protected static final Logger logger =
            LoggerFactory.getLogger(PredicateMatchingListElementIterator.class);
    
    protected SchemaNode listElementSchemaNode;
    protected IndexSpecifier keySpecifier;
    protected Iterator<DataNode> baseIterator;
    protected DataNode nextListElement;
    protected Step matchingStep;
    
    public PredicateMatchingListElementIterator(
            SchemaNode listElementSchemaNode,
            Iterator<DataNode> baseIterator, Step matchingStep) {
        this.listElementSchemaNode = listElementSchemaNode;
        ListSchemaNode listSchemaNode =
                (ListSchemaNode) listElementSchemaNode.getParentSchemaNode();
        this.keySpecifier = listSchemaNode.getKeySpecifier();
        this.baseIterator = baseIterator;
        this.matchingStep = matchingStep;
    }
    
    @Override
    public boolean hasNext() {
        
        // This code is based on the FilterIterator class in floodlight. We
        // duplicate the logic, because we don't want the core BigDB code to
        // have any dependencies on floodlight.
        
        if (nextListElement != null) return true;
        
        try {
            while (baseIterator.hasNext()) {
                nextListElement = baseIterator.next();
                Step listElementStep = DataNodeUtilities.getListElementStep(
                        matchingStep.getName(), keySpecifier, nextListElement);
                if (AbstractListDataNode.matchesPredicates(listElementSchemaNode,
                        nextListElement, listElementStep, matchingStep))
                    return true;
            }
        }
        catch (BigDBException e) {
            logger.error("Exception caught during list element iteration; " + e, e);
        }
        
        nextListElement = null;
        
        return false;
    }

    @Override
    public DataNode next() {
        if (hasNext()) {
            DataNode current = nextListElement;
            nextListElement = null;
            return current;
        }
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
