package org.projectfloodlight.db.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.lang.reflect.Method;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.yang.RangePart;

public class RangeValidator<T extends Comparable<T>> extends Validator {

    T max = null;
    T min = null;
    private List<Range<T>> ranges = new ArrayList<Range<T>>();

    private Object valueOf(@SuppressWarnings("rawtypes") Class<? extends Comparable> clazz, String value) 
            throws BigDBException {
        try {
            Method m = clazz.getDeclaredMethod("valueOf", new Class[] {String.class});
            return m.invoke(null, value);
        } catch (Exception e) {
            throw new BigDBException("Cannot get the value: " + value, e);
        }
    }
    
    public RangeValidator(T start, T end) {
        this(start, end, Type.RANGE_VALIDATOR);
    }
    public RangeValidator(T start, T end, Type type) {
        super(type);
        max = end;
        min = start;
        this.getRanges().add(new Range<T>(start, end));
    }
    
    /**
     * check the new range is more limiting and set the range with new ranges.
     * @param rangeParts
     * @throws BigDBException 
     */
    @SuppressWarnings("unchecked")
    public void checkAndSet(Collection<RangePart> rangeParts) 
            throws BigDBException {
        // assume that max/min have been set
        assert max != null;
        assert min != null;
        
        List<Range<T>> rangesToMerge = new ArrayList<Range<T>>();
        T prevL = null;
        T prevH = null;
        for (RangePart rp : rangeParts) {
            T l = null;
            T h = null;
            String low = rp.getStart().getStringValue();
            String upper = rp.getEnd().getStringValue();
            if (low.equals("min")) {
                l = min;
            } else {
                l = (T) this.valueOf(min.getClass(), low);
            }
            if (upper.equals("max")) {
                h = max;
            } else {
                h = (T) this.valueOf(max.getClass(), upper);
            }
            // check the ranges are ascending
            if (prevL == null || prevH == null) {
                prevL = l;
                prevH = h;
            } else {
                if (l.compareTo(h) > 0 || prevH.compareTo(l) >= 0) {
                    // overlap, throw
                    throw new BigDBException("Invalid range specified.");
                }
            }
            rangesToMerge.add(new Range<T>(l, h));
        }
        // verify that the new range is more limited than the current ones
        for (Range<T> nr : rangesToMerge) {
            // make sure at least one of current ranges contains the new one
            boolean ok = false;
            for (Range<T> r : getRanges()) {
                if (r.getStart().compareTo(nr.getStart()) <=0 &&
                    r.getEnd().compareTo(nr.getEnd()) >= 0) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                // TODO: throw better message
                throw new BigDBException("Invalid range specified: new " + 
                            " range should be more restrictive.");
            }
        }
        // set to the new ranges
        setRanges(rangesToMerge);
    }
    
    protected void validate(Comparable<T> value) throws ValidationException {
        for (Range<T> range: getRanges()) {
            if (range.isInRange(value)) {
                // If the value falls within any one of the ranges, then
                // it's valid and we can return immediately.
                return;
            }
        }
        
        // Didn't fall within any of the ranges, so we throw a
        // validation exception.
        throw new ValidationException(
                String.format("Value \"%s\" outside range", value));        
    }
    
    @Override
    public void validate(DataNode data) throws BigDBException {
        
        @SuppressWarnings("unchecked")
        Comparable<T> value = (Comparable<T>) data.getObject();
        this.validate(value);
    }
    
    @Override
    public RangeValidator<T> clone() {
        @SuppressWarnings("unchecked")
        RangeValidator<T> rv = (RangeValidator<T>) super.clone();
        rv.ranges = new ArrayList<Range<T>>();
        for (Range<T> range: ranges) {
            rv.ranges.add(new Range<T>(range.getStart(), range.getEnd()));
        }
        return rv;
    }

    public List<Range<T>> getRanges() {
        return ranges;
    }

    public void setRanges(List<Range<T>> ranges) {
        // need to adjust the max and min
        if (ranges.size() == 0) {
            return;
        }
        T newmin = null;
        T newmax = null;
        for (Range<T> r : ranges) {
            if (newmin == null) {
                newmin = r.getStart();
            } else {
                if (newmin.compareTo(r.getStart()) > 0) {
                    newmin = r.getStart();
                }
            }
            if (newmax == null) {
                newmax = r.getEnd();
            } else {
                if (newmax.compareTo(r.getEnd()) < 0) {
                    newmax = r.getEnd();
                }
            }
        }
        assert newmin != null && newmax != null;
        max = newmax;
        min = newmin;
        this.ranges = ranges;
    }
}
