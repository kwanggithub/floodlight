package org.projectfloodlight.db.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.ExpressionVisitor.Result;
import org.projectfloodlight.db.query.QueryContext;
import org.projectfloodlight.db.query.QueryVariable;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.query.parser.VariableReplacer;
import org.projectfloodlight.db.query.parser.XPathParserUtils;
import org.projectfloodlight.db.util.Path;
import org.projectfloodlight.db.util.Path.Type;

import com.google.common.collect.ImmutableList;

/**
 * Models an XPath-Like expression path.
 *
 * @author Rob Vaterlaus
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@Immutable
public class LocationPathExpression extends PathExpression {

    public static class Builder {
        private boolean absolute;
        private final List<Step> steps;

        public Builder() {
            absolute = false;
            steps = new ArrayList<Step>();
        }

        public Builder(boolean absolute, List<Step> steps) {
            this.absolute = absolute;
            this.steps = new ArrayList<Step>(steps);
        }

        public boolean isAbsolute() {
            return absolute;
        }

        public Builder setAbsolute(boolean absolute) {
            this.absolute = absolute;
            return this;
        }

        public Builder addAllSteps(List<Step> steps) {
            this.steps.addAll(steps);
            return this;
        }

        public Builder addStep(Step step) {
            steps.add(step);
            return this;
        }

        public LocationPathExpression getPath() {
            return new LocationPathExpression(absolute, steps != null && !steps.isEmpty() ? ImmutableList.copyOf(steps) : ImmutableList.<Step>of());
        }

        public List<Step> getSteps() {
            return steps;
        }

        public Step getStep(int i) {
            return steps.get(i);
        }
    }

    public final static LocationPathExpression EMPTY_PATH =
            new LocationPathExpression(false, Collections.<Step>emptyList());
    public final static LocationPathExpression ROOT_PATH =
            new LocationPathExpression(true, Collections.<Step>emptyList());

    private final boolean absolute;
    private final List<Step> steps;

    public boolean isAbsolute() {
        return absolute;
    }

    public LocationPathExpression(boolean absolute, List<Step> steps) {
        assert steps != null;
        this.absolute = absolute;
        this.steps = steps;
    }

    public Builder createBuilder() {
        return new Builder(this.absolute, this.steps);
    }

    private static LocationPathExpression constructFromPaths(Iterable<LocationPathExpression> paths) {
        if (paths != null) {
            Builder builder = new Builder();

            // Clone over all of the steps from the list of paths
            boolean setAbsolute = true;
            for (LocationPathExpression path : paths) {
                // Set the new path to be absolute if the first path in the list
                // is absolute.
                if (setAbsolute) {
                    builder.setAbsolute(path.isAbsolute());
                    setAbsolute = false;
                }
                builder.addAllSteps(path.getSteps());
            }
            return builder.getPath();
        } else {
            return LocationPathExpression.EMPTY_PATH;
        }
    }

    public static LocationPathExpression ofStep(Step step) {
        return new LocationPathExpression(false, Collections.singletonList(step));
    }

    public static LocationPathExpression ofName(String name) {
        return LocationPathExpression.ofStep(Step.of(name));
    }

    public static LocationPathExpression ofPaths(Iterable<LocationPathExpression> paths) {
        return constructFromPaths(paths);
    }

    public static LocationPathExpression ofPaths(LocationPathExpression... paths) {
        List<LocationPathExpression> pathList =
                new ArrayList<LocationPathExpression>();
        for (LocationPathExpression path: paths) {
            pathList.add(path);
        }
        return constructFromPaths(pathList);
    }

    public static LocationPathExpression of(boolean absolute, List<Step> steps) {
        return new LocationPathExpression(absolute, ImmutableList.copyOf(steps));
    }

    public static LocationPathExpression parse(String pathString) throws BigDBException {
        return parse(pathString, null);
    }

    /** parse a LocationPathExpression, replacing the XXpvariable varName in the query path with provided value 'value'
     *  E.g.,
     *  <code>
     *     LocationPathExpression.parse("aaa/user[name=$userName]", "userName", providedUserName)
     *  </code>
     *  will select users whose name matches the value provided in the variable providedUserName, and is safe even if
     *  providedUserName contains strange characters.
     *
     * @param pathString
     * @param varName
     * @param value
     * @return the parsed LocationPathExpression.
     * @throws BigDBException
     */
    public static LocationPathExpression parse(String pathString, String varName, String value) throws BigDBException {
        QueryContext context = new QueryContext();
        context.add(QueryVariable.stringVariable(varName, value));
        return parse(pathString, context);
    }

    public static LocationPathExpression parse(String pathString,
            VariableReplacer variableReplacer) throws BigDBException {
        return XPathParserUtils.parseSingleLocationPathExpression(pathString,
                variableReplacer);
    }

    public List<Step> getSteps() {
        return steps;
    }

    public int size() {
        return steps.size();
    }

    public Step getStep(int index) {
        return steps.get(index);
    }

    public LocationPathExpression subpath(int start, int end) {
        return new LocationPathExpression(absolute && (start == 0),
                steps.subList(start, end));
    }

    public LocationPathExpression subpath(int start) {
        return new LocationPathExpression(absolute && (start == 0),
                steps.subList(start, steps.size()));
    }

    public LocationPathExpression getChildLocationPath(Step childStep) {
        return LocationPathExpression.ofPaths(this, LocationPathExpression.of(
                false, Collections.singletonList(childStep)));
    }

    public LocationPathExpression getChildLocationPath(String childName) {
        return getChildLocationPath(Step.of(childName));
    }

    public LocationPathExpression getChildLocationPath(LocationPathExpression relativePath) {
        List<LocationPathExpression> paths =
                new ArrayList<LocationPathExpression>();
        paths.add(this);
        paths.add(relativePath);
        return constructFromPaths(paths);
    }

    /**
     * A location path is "simple" if none of the steps have predicates.
     * @return true if no step in the path has predicates; otherwise false.
     */
    public boolean isSimple() {
        for (Step step: getSteps()) {
            if (!step.getPredicates().isEmpty())
                return false;
        }
        return true;
    }

    /**
     * @return a "simple" location path where all the predicates are removed.
     */
    public LocationPathExpression getSimpleLocationPath() {
        LocationPathExpression.Builder builder =
                new LocationPathExpression.Builder();
        builder.setAbsolute(isAbsolute());
        for (Step step: getSteps()) {
            builder.addStep(Step.of(step.getName()));
        }
        return builder.getPath();
    }

    public boolean simplePathMatch(String path) {
        String[] es = path.split("/");
        for (int i = 0; i < this.steps.size(); ++i) {
            if (i >= es.length) {
                return false;
            }
            if (!es[i].equals(steps.get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    public Path getSimplePath() {
        Path path = new Path(absolute ? Type.ABSOLUTE : Type.RELATIVE);
        for (int i = 0; i < this.steps.size(); ++i) {
            path.add(steps.get(i).getName());
        }
        return path;
    }

    public String getSimplePathString() {
        return getSimplePath().toString();
    }

    @Override
    public Result accept(ExpressionVisitor visitor) throws BigDBException {
        Result result = visitor.visitEnter(this);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        //TODO: visit steps
        result = visitor.visitLeave(this);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(absolute ? "/" : "");
        boolean first = true;
        for (Step step: steps) {
            if(!first) {
                builder.append('/');
            }
            builder.append(step.toString());
            first = false;
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (absolute ? 1231 : 1237);
        result = prime * result + ((steps == null) ? 0 : steps.hashCode());
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
        LocationPathExpression other = (LocationPathExpression) obj;
        if (absolute != other.absolute)
            return false;
        if (steps == null) {
            if (other.steps != null)
                return false;
        } else if (!steps.equals(other.steps))
            return false;
        return true;
    }

}
