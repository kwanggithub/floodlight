package net.bigdb.auth.simpleacl;

import java.util.Arrays;
import java.util.regex.Pattern;

import net.bigdb.BigDBException;
import net.bigdb.hook.AuthorizationHook;

import com.google.common.base.Joiner;

/**
 * An entry in the ACL list. Immutable.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
class SimpleACLEntry {
    final static Pattern whitespace = Pattern.compile("\\s+");

    private final SimpleACLMatch match;
    private final AuthorizationHook.Result result;

    public SimpleACLEntry(SimpleACLMatch match, AuthorizationHook.Result result) {
        super();
        this.match = match;
        this.result = result;
    }

    public SimpleACLMatch getMatch() {
        return match;
    }

    public AuthorizationHook.Result getResult() {
        return result;
    }

    /**
     * parse a line into an SimpleACLEntry. Will throw an Exception if something
     * goes wrong (Don't rely on it being a BigDBException)
     *
     * @param line
     * @return
     * @throws BigDBException
     */
    public static SimpleACLEntry parseLine(String line) throws BigDBException {
        String[] parts = whitespace.split(line);
        // todo this parsing is pretty awful but will do for now
        SimpleACLMatch match =
                SimpleACLMatch.parseLine(Joiner.on(" ").join(
                        Arrays.copyOfRange(parts, 0, parts.length - 1)));
        AuthorizationHook.Decision decision =
                AuthorizationHook.Decision.valueOf(parts[parts.length - 1]);
        AuthorizationHook.Result result = AuthorizationHook.Result.of(decision);
        return new SimpleACLEntry(match, result);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(match.toString()).append(' ').append(result.toString());
        return buf.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result =
                prime * result +
                        ((this.result == null) ? 0 : this.result.hashCode());
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
        SimpleACLEntry other = (SimpleACLEntry) obj;
        if (match == null) {
            if (other.match != null)
                return false;
        } else if (!match.equals(other.match))
            return false;
        if (result != other.result)
            return false;
        return true;
    }

}