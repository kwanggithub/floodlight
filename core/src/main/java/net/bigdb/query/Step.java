package net.bigdb.query;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.BinaryOperatorExpression;
import net.bigdb.expression.BooleanLiteralExpression;
import net.bigdb.expression.DecimalLiteralExpression;
import net.bigdb.expression.DoubleLiteralExpression;
import net.bigdb.expression.Expression;
import net.bigdb.expression.FunctionCallExpression;
import net.bigdb.expression.IntegerLiteralExpression;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.expression.StringLiteralExpression;
import net.bigdb.expression.UnaryOperatorExpression;

import com.google.common.collect.ImmutableList;

/** a step in a LocationPathExpression. Immutable.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class Step {
    /** a builder for a step. Not threadsafe - use in only the creating thread. */
    public static class Builder {
        private String name = null;
        private String axisName = null;
        private List<Expression> predicates = null;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getAxisName() {
            return axisName;
        }
        public void setAxisName(String axisName) {
            this.axisName = axisName;
        }

        public void addPredicate(Expression predicate) {
            if(predicates == null)
                predicates = new ArrayList<Expression>();
            this.predicates.add(predicate);
        }

        public Step getStep() {
            return new Step(name, predicates, axisName != null ? axisName : DEFAULT_AXIS_NAME);
        }
    }

    public final static String DEFAULT_AXIS_NAME = "child";

    // FIXME: Should probably use enum for axis
    private final String axisName;
    private final String name;
    protected final List<Expression> predicates;

    private Step(String name, Collection<Expression> expressions,
                String axisName) {
        assert name != null;
        assert axisName != null;
        this.name = name;
        this.predicates = expressions != null && expressions.size() > 0 ? ImmutableList.copyOf(expressions) : ImmutableList.<Expression>of();
        this.axisName = axisName;
    }

    public String getAxisName() {
        return axisName;
    }

    public String getName() {
        return name;
    }

    public List<Expression> getPredicates() {
        return predicates;
    }

    public static Step of(String name) {
        return new Step(name, null, DEFAULT_AXIS_NAME);
    }

    public static Step of(String name, Collection<Expression> expressions) {
        return new Step(name, expressions, DEFAULT_AXIS_NAME);
    }

    public static Step of(String name, Collection<Expression> expressions, String axisName) {
        return new Step(name, expressions, axisName);
    }

    public static class ExactMatchPredicate {
        private final LocationPathExpression path;
        private final Object value;

        public ExactMatchPredicate(LocationPathExpression path, Object value) {
            this.path = path;
            this.value = value;
        }

        public LocationPathExpression getPath() {
            return path;
        }

        public String getName() {
            return path.getSimplePathString();
        }

        public Object getValue() {
            return value;
        }
    }

    public List<ExactMatchPredicate> getExactMatchPredicates() {
        List<ExactMatchPredicate> result = null;
        if (predicates != null) {
            for (Expression expression: predicates) {
                // FIXME: This is kludgy code that only handles the most
                // common types of expressions. It should be replaced/rewritten
                if (!(expression instanceof BinaryOperatorExpression))
                    continue;
                BinaryOperatorExpression binaryExpression =
                        (BinaryOperatorExpression) expression;
                if (binaryExpression.getOperator() != BinaryOperatorExpression.Operator.EQ)
                    continue;
                Expression leftHandExpression = binaryExpression.getLeftExpression();
                Expression rightHandExpression = binaryExpression.getRightExpression();
                LocationPathExpression pathExpression = null;
                Expression valueExpression = null;
                if (leftHandExpression instanceof LocationPathExpression) {
                    pathExpression = (LocationPathExpression)leftHandExpression;
                    valueExpression = rightHandExpression;
                } else if (rightHandExpression instanceof LocationPathExpression) {
                    pathExpression = (LocationPathExpression)rightHandExpression;
                    valueExpression = leftHandExpression;
                }
                boolean negate = false;
                boolean unexpectedUnaryOperator = false;
                while (valueExpression instanceof UnaryOperatorExpression) {
                    UnaryOperatorExpression unaryOperatorExpression =
                            (UnaryOperatorExpression) valueExpression;
                    if (unaryOperatorExpression.getOperator() != UnaryOperatorExpression.Operator.MINUS) {
                        unexpectedUnaryOperator = true;
                        break;
                    }
                    negate = !negate;
                    valueExpression = unaryOperatorExpression.getExpression();
                }
                if (unexpectedUnaryOperator)
                    continue;
                if (pathExpression == null)
                    continue;
                Object value = null;
                if (valueExpression instanceof BooleanLiteralExpression) {
                    value = ((BooleanLiteralExpression)valueExpression).getValue();
                } else if (valueExpression instanceof DecimalLiteralExpression) {
                    BigDecimal bigDecimalValue = ((DecimalLiteralExpression)valueExpression).getValue();
                    if (negate)
                        bigDecimalValue = bigDecimalValue.negate();
                    value = bigDecimalValue;
                } else if (valueExpression instanceof DoubleLiteralExpression) {
                    Double doubleValue = ((DoubleLiteralExpression)valueExpression).getValue();
                    if (negate)
                        doubleValue = -doubleValue;
                    value = doubleValue;
                } else if (valueExpression instanceof IntegerLiteralExpression) {
                    Long longValue = ((IntegerLiteralExpression)valueExpression).getLongValue();
                    if (longValue != null) {
                        if (negate)
                            longValue = -longValue;
                        value = longValue;
                    } else {
                        BigInteger bigIntegerValue = ((IntegerLiteralExpression)valueExpression).getBigIntegerValue();
                        if (bigIntegerValue != null) {
                            if (negate)
                                bigIntegerValue = bigIntegerValue.negate();
                            value = bigIntegerValue;
                        }
                    }
                } else if (valueExpression instanceof StringLiteralExpression) {
                    value = ((StringLiteralExpression)valueExpression).getValue();
                }
                if (value == null)
                    continue;
                if (result == null)
                    result = new ArrayList<ExactMatchPredicate>();
                result.add(new ExactMatchPredicate(pathExpression, value));
            }
        }
        return result;
    }

    public Object getExactMatchPredicateValue(String name) {
        List<ExactMatchPredicate> exactMatchPredicates =
                getExactMatchPredicates();
        if (exactMatchPredicates != null) {
            for (ExactMatchPredicate predicate: exactMatchPredicates) {
                if (predicate.getName().equals(name))
                    return predicate.getValue();
            }
        }
        return null;
    }

    public String getExactMatchPredicateString(String name) throws BigDBException {
        Object value = getExactMatchPredicateValue(name);
        if (value == null)
            return null;
        return value.toString();
    }

    public static class PrefixMatchPredicate {
        private final LocationPathExpression path;
        private final String prefix;

        public PrefixMatchPredicate(LocationPathExpression path, String prefix) {
            this.path = path;
            this.prefix = prefix;
        }

        public LocationPathExpression getPath() {
            return path;
        }

        public String getName() {
            return path.getSimplePathString();
        }

        public String getPrefix() {
            return prefix;
        }
    }

    public List<PrefixMatchPredicate> getPrefixMatchPredicates() {
        List<PrefixMatchPredicate> result = null;
        if (predicates != null) {
            for (Expression expression: predicates) {
                if (!(expression instanceof FunctionCallExpression))
                    continue;
                FunctionCallExpression functionCallExpression =
                        (FunctionCallExpression) expression;
                if (!functionCallExpression.getFunctionName().equals("starts-with"))
                    continue;
                List<Expression> args = functionCallExpression.getArgs();
                if (args.size() != 2)
                    continue;
                Expression pathArg = args.get(0);
                if (!(pathArg instanceof LocationPathExpression))
                    continue;
                LocationPathExpression pathExpression = (LocationPathExpression) pathArg;
                Expression prefixArg = args.get(1);
                if (!(prefixArg instanceof StringLiteralExpression))
                    continue;
                String prefix = ((StringLiteralExpression)prefixArg).getValue();
                if (prefix == null)
                    continue;
                if (result == null)
                    result = new ArrayList<PrefixMatchPredicate>();
                result.add(new PrefixMatchPredicate(pathExpression, prefix));
            }
        }
        return result;
    }

    public String getPrefixMatchPredicateString(String name) {
        Collection<PrefixMatchPredicate> prefixMatchPredicates =
                getPrefixMatchPredicates();
        if (prefixMatchPredicates != null) {
            for (PrefixMatchPredicate predicate: prefixMatchPredicates) {
                if (predicate.getName().equals(name))
                    return predicate.getPrefix();
            }
        }
        return null;
    }

    /**
     * Check if the predicate for the step specifies a single non-negative
     * integer index to be used for indexed access of a list-like element, i.e.
     * either a leaf-list or an unkeyed list.
     *
     * @return the specified index if the predicate is a single non-negative
     *         integer; otherwise returns -1.
     */
    public int getIndexPredicate() {
        if (predicates.size() != 1)
            return -1;
        Expression expression = predicates.get(0);
        if (expression instanceof IntegerLiteralExpression) {
            long value = ((IntegerLiteralExpression)expression).getLongValue();
            if ((value >= 0) && (value <= Integer.MAX_VALUE))
                return (int) value;
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        for (Expression predicate: predicates) {
            builder.append('[');
            builder.append(predicate.toString());
            builder.append(']');
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result + ((axisName == null) ? 0 : axisName.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result =
                prime * result +
                        ((predicates == null) ? 0 : predicates.hashCode());
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
        Step other = (Step) obj;
        if (axisName == null) {
            if (other.axisName != null)
                return false;
        } else if (!axisName.equals(other.axisName))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (predicates == null) {
            if (other.predicates != null)
                return false;
        } else if (!predicates.equals(other.predicates))
            return false;
        return true;
    }
}
