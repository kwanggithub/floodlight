package org.projectfloodlight.db.query;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.parser.VariableReplacer;
import org.projectfloodlight.db.query.parser.XPathParserUtils;
import org.projectfloodlight.db.util.Path;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** models a query towards BigDB. Immutable. Use the methods of() and parse() to construct simple Querys.
 * use the Query.Builder to construct more complex queries.
 *
 * @author Rob Vaterlaus
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@Immutable
public class Query {

    /** builder class for the query.
     *
     *  <h2>Variable replacement</h2>
     *  Queries support xpath variables that are replaced during parsing.
     *  Example:
     *
     *  <p>
     *  <code>
     *  Query.builder().setBasePath("/people[name = $name]").setVariable("name", "John Doe").getQuery();
     *  </code>
     **/
    public static class Builder {
        // either unparsedBasePath or basePath is null
        private String unparsedBasePath;
        private LocationPathExpression basePath;

        // somewhat hacky: we support parsed (LocationPathExpression) and unparsed selected paths
        // to keep order, selectedPaths is a set of object. Conversion is done in getQuery()
        private Set<Object> selectedPaths = new LinkedHashSet<Object>();
        private boolean hasUnparsedSelectedPaths = false;

        private EnumSet<StateType> includedStateTypes = EnumSet.allOf(StateType.class);

        private QueryContext context = null;

        /** set a parsed base path */
        public Builder setBasePath(LocationPathExpression basePath) {
            this.unparsedBasePath = null;
            this.basePath = basePath;
            return this;
        }

        /** set a unparsed base path to be parsed when getQuery() is called. Variable replacement
         *  also takes place when getQuery() is called.
         **/
        public Builder setBasePath(String basePath) {
            this.basePath = null;
            this.unparsedBasePath = basePath;
            return this;
        }

        /** reset the list of selected paths to the give list */
        public Builder  setSelectedPaths(Set<LocationPathExpression> selectedPaths) {
            this.selectedPaths = new LinkedHashSet<Object>(selectedPaths);
            return this;
        }

        /** add an unparsed selectedpath to the list of select pathes. Will be parsed / var replaced when
         *  getQuery is called */
        public Builder addSelectPath(String pathString) throws BigDBException {
            hasUnparsedSelectedPaths = true;
            selectedPaths.add(pathString);
            return this;
        }

        /** add a parsed select path */
        public Builder addSelectPath(LocationPathExpression path) throws BigDBException {
            selectedPaths.add(path);
            return this;
        }

        /** set a string variable to be placed during parsing of LocationPathExressions */
        public Builder setVariable(String name, String value) {
            addVariableInt(QueryVariable.stringVariable(name, value));
            return this;
        }

        /** set a long variable to be placed during parsing of LocationPathExressions */
        public Builder setVariable(String name, long value) {
            addVariableInt(QueryVariable.integerVariable(name, value));
            return this;
        }

        /** set a boolean variable to be placed during parsing of LocationPathExressions */
        public Builder setVariable(String name, boolean value) {
            addVariableInt(QueryVariable.booleanVariable(name, value));
            return this;
        }

        /** set a BigDecimal variable to be placed during parsing of LocationPathExressions */
        public Builder setVariable(String name, BigDecimal value) {
            addVariableInt(QueryVariable.decimalVariable(name, value));
            return this;
        }

        private void addVariableInt(QueryVariable var) {
            if(context == null)
                context = new QueryContext();
            context.add(var);
        }

        public Builder setIncludedStateTypes(Set<StateType> includedStateTypes) {
            this.includedStateTypes = EnumSet.copyOf(includedStateTypes);
            return this;
        }

        public Builder setIncludedStateType(StateType stateType) {
            includedStateTypes = EnumSet.of(stateType);
            return this;
        }

        public Builder addIncludedStateTypes(StateType stateType) {
            includedStateTypes.add(stateType);
            return this;
        }

        /** parse any unparsed parts of the query / replace variables and return the parsed queries.
         *  Exceptions that stem from syntax errors in the xpath / missing variables etc. will surface
         *  here as BigDBException
         *
         * @return the fully parsed immutable Query
         * @throws BigDBException
         */
        @SuppressWarnings("unchecked")
        public Query getQuery() throws BigDBException {
            final VariableReplacer localContext = this.context != null ? this.context : VariableReplacer.NONE;

            final LocationPathExpression localBasePath = this.basePath != null ? this.basePath :
                XPathParserUtils.parseSingleLocationPathExpression(unparsedBasePath, localContext);

            Set<LocationPathExpression> localSelectedPaths;
            if(this.selectedPaths.isEmpty()) {
                localSelectedPaths = ImmutableSet.<LocationPathExpression>of();
            } else if(!hasUnparsedSelectedPaths) {
                localSelectedPaths = ImmutableSet.copyOf( (Set<LocationPathExpression>) (Set<?>) this.selectedPaths);
            } else {
                Set<LocationPathExpression> parsedPaths = new LinkedHashSet<LocationPathExpression>();
                for(Object o : this.selectedPaths) {
                    if(o instanceof LocationPathExpression) {
                        parsedPaths.add((LocationPathExpression) o);
                    } else if (o instanceof String) {
                        parsedPaths.addAll(XPathParserUtils.parseAndExpandUnions((String) o, localContext));
                    }
                }
                localSelectedPaths = Collections.unmodifiableSet(parsedPaths);
            }

            return new Query(localBasePath, localSelectedPaths, includedStateTypes);
        }

    }

    public final static Query ROOT_QUERY = Query.of(LocationPathExpression.ROOT_PATH);

    public enum StateType { CONFIG, OPERATIONAL };

    private final LocationPathExpression basePath;
    private final Set<LocationPathExpression> selectedPaths;
    private final Set<StateType> includedStateTypes;

    private Query(LocationPathExpression basePath,
            Set<LocationPathExpression> selectedPaths,
            Set<StateType> includedStateTypes) {
        assert basePath != null;
        assert selectedPaths != null;

        this.basePath = basePath;
        // NB it is the responsibility of the caller to ensure that selectedPaths is
        // immutable + no references can escape
        this.selectedPaths = selectedPaths;
        this.includedStateTypes = Sets.immutableEnumSet(includedStateTypes);
    }

    public static Query of(LocationPathExpression basePath) {
        return of(basePath, ImmutableSet.<LocationPathExpression>of());
    }

    public static Query of(LocationPathExpression basePath, Set<LocationPathExpression> selectedPaths) {
        return of(basePath, selectedPaths, EnumSet.allOf(StateType.class));
    }

    public static Query of(LocationPathExpression basePath, Set<LocationPathExpression> selectedPaths, Set<StateType> includedStateTypes) {
        return new Query(basePath, ImmutableSet.copyOf(selectedPaths), includedStateTypes);
    }

    /** convenience method that parses a query from a single baseQuery string.
     *
     *  <p><strong>CAREFUL:</strong> Do <emph>NOT</emph> construct the baseQuery by concatenating strings
     *  that contain user supplied input. You will end up with XPath Injections. Use the Builder and an xpath variable.
     *  </p>
     *  <p>
     *  DO NOT DO THIS:
     *     <code>
     *      // BROKEN -- don't use. This will fail if userNameFromClient is not well formed
       *      <del>Query.parse("login[username=\""+ userNameFromClient "\"]")</del>
     *     </code>
     *  </p>
     *  <p>
     *  DO INSTEAD:
     *     <code>
     *       Query.parse("login[username = $username]", "username", userNameFromClient)
     *         //or
      *      Query.builder().setBasePath("login[username = $username]").setVariable("username", userNameFromClient).getQuery()
     *     </code>
     *  </p>
     *
     * @param baseQueryString
     * @throws BigDBException
     * @return Query
     */
    public static Query parse(String baseQueryString) throws BigDBException {
        return of(XPathParserUtils.parseSingleLocationPathExpression(baseQueryString, null));
    }

    /** convenience method that parses the given baseQueryString and replaced a single variable named 'varnName' with the
     *  suplied string value. For more complex cases, use a builder.
     *
     * @param baseQueryString the base query string containing a variable, e.g., '/users[login=$foo]'
     * @param varName name of the variable being replaced
     * @param value value to replace the variable with
     * @return the parsed query
     * @throws BigDBException
     */
    public static Query parse(String baseQueryString, String varName, String value) throws BigDBException {
        QueryContext context = new QueryContext();
        context.add(QueryVariable.stringVariable(varName, value));
        return of(XPathParserUtils.parseSingleLocationPathExpression(baseQueryString, context));
    }

    /** return a query with the basePath subselected to include only steps
     * [startStep, endStep]
     *
     * @param startStep
     * @param endStep
     * @return
     */
    public Query subQuery(int startStep, int endStep) {
        return new Query(basePath.subpath(startStep, endStep), selectedPaths,
                includedStateTypes);
    }

    public LocationPathExpression getBasePath() {
        return basePath;
    }

    public Set<LocationPathExpression> getSelectedPaths() {
        return selectedPaths;
    }

    // FIXME: Should eventually get rid of this. This is mainly for API backward
    // compatibility
    public List<Step> getSteps() {
        return basePath.getSteps();
    }

    // FIXME: Should eventually get rid of this. This is mainly for API backward
    // compatibility
    public Step getStep(int index) {
        return basePath.getSteps().get(index);
    }

    public Path getSimpleBasePath() {
        return basePath.getSimplePath();
    }

    public Set<StateType> getIncludedStateTypes() {
        return includedStateTypes;
    }

    @Override
    public String toString() {
        return "Query [basePath=" + basePath + ", selectedPaths=" +
                selectedPaths + ", includedStateTypes=" + includedStateTypes +
                "]";
    }

    /** return a scheme- bigdb URI representing this query
     *  <ul>
     *   <li> the base path is encoded as the URI path (reserved chars like '[' encoded with percent encoding )
     *   <li> the selected paths are encoded as a series of 'select=' query string stanza
     *   <li> if only config [ or only operational] state is selected a query stanza config=true [ config=false ] is appended
     *  </ul>
     */
    public URI toURI() {
        try {
            List<String> queryComponents = new ArrayList<String>();
            for (LocationPathExpression path : selectedPaths)
                queryComponents
                        .add("select=" + URLEncoder.encode(path.toString(), "UTF-8"));

            if (includedStateTypes.size() == 1) {
                if (includedStateTypes.contains(StateType.CONFIG)) {
                    queryComponents.add("config=true");
                } else {
                    queryComponents.add("config=false");
                }
            }

            return new URI(
                    null,
                    null,
                    basePath.toString(),
                    queryComponents.isEmpty() ? null : Joiner.on("&").join(queryComponents),
                    null);

        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result + ((basePath == null) ? 0 : basePath.hashCode());
        result =
                prime *
                        result +
                        ((includedStateTypes == null) ? 0 : includedStateTypes
                                .hashCode());
        result =
                prime *
                        result +
                        ((selectedPaths == null) ? 0 : selectedPaths.hashCode());
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
        Query other = (Query) obj;
        if (basePath == null) {
            if (other.basePath != null)
                return false;
        } else if (!basePath.equals(other.basePath))
            return false;
        if (includedStateTypes == null) {
            if (other.includedStateTypes != null)
                return false;
        } else if (!includedStateTypes.equals(other.includedStateTypes))
            return false;
        if (selectedPaths == null) {
            if (other.selectedPaths != null)
                return false;
        } else if (!selectedPaths.equals(other.selectedPaths))
            return false;
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

}
