package org.projectfloodlight.db.auth.simpleacl;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.db.query.parser.XPathParserUtils;

import com.google.common.base.Objects;

/**
 * the match class. right now just supports exact matches on BigDBOperation and
 * Query. Wilcards are indicated by 'null' values. Immutable.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
class SimpleACLMatch {
    private final AuthorizationHook.Operation op;
    private final LocationPathExpression path;

    public SimpleACLMatch(AuthorizationHook.Operation op, LocationPathExpression path) {
        super();
        this.op = op;
        this.path = path;
    }

    public boolean matches(AuthorizationHook.Operation op, LocationPathExpression path,
            DataNode dataNode, AuthContext context) {
        return (this.op == null || Objects.equal(this.op, op)) &&
                (this.path == null || Objects.equal(this.path, path));
    }

    public AuthorizationHook.Operation getOp() {
        return op;
    }

    public LocationPathExpression getPath() {
        return path;
    }

    /**
     * parse a match from a line. Wild cards in the match are indicated by the
     * asterisk character '*'.
     *
     * @param line
     * @throws BigDBException
     */
    public static SimpleACLMatch parseLine(String line) throws BigDBException {
        String[] parts = SimpleACLEntry.whitespace.split(line);
        AuthorizationHook.Operation op = null;
        LocationPathExpression path = null;

        if (parts.length > 0)
            op =
                    Objects.equal("*", parts[0]) ? null : AuthorizationHook.Operation
                            .valueOf(parts[0]);

        if (parts.length > 1) {
            if (!Objects.equal("*", parts[1])) {
                path = XPathParserUtils.parseSingleLocationPathExpression(parts[1], null);
            }
        }

        return new SimpleACLMatch(op, path);
    }

    private String valOrStar(Object o) {
        return o != null ? o.toString() : "*";
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(valOrStar(op)).append(' ').append(valOrStar(path));
        return buf.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((op == null) ? 0 : op.hashCode());
        result = prime * result + ((path == null) ? 0 : path.hashCode());
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
        SimpleACLMatch other = (SimpleACLMatch) obj;
        if (op != other.op)
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        return true;
    }

}