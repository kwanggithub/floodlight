package org.projectfloodlight.db.auth.simpleacl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

/**
 * Simple ACL list implementation. Contains a List of SimpleACLEntries. This is
 * pretty much just a wrapper around that list, and I/O operations Threadsafe to
 * modify through the use of a CopyOnWriteArrayList.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 **/
class SimpleACL {
    private final static Logger logger = LoggerFactory
            .getLogger(SimpleACL.class);

    private final List<SimpleACLEntry> entries;

    public SimpleACL() {
        this.entries = new CopyOnWriteArrayList<SimpleACLEntry>();
    }

    public SimpleACL(Collection<SimpleACLEntry> entries) {
        this.entries = new CopyOnWriteArrayList<SimpleACLEntry>(entries);
    }

    /**
     * read an ACL from an Input Stream. Return the resulting objects. NOTE:
     * Malformed lines are ignored in the input, a warning message is printed to
     * the log.
     *
     * @param is
     * @return
     * @throws IOException
     */
    public static SimpleACL fromStream(InputStream is) throws IOException {
        List<SimpleACLEntry> entries = new ArrayList<SimpleACLEntry>();

        BufferedReader br =
                new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
        String l;

        while ((l = br.readLine()) != null) {
            try {
                SimpleACLEntry entry = SimpleACLEntry.parseLine(l);
                entries.add(entry);
            } catch (Exception e) {
                logger.warn("Exception parsing. Ignoring malformatted line: " +
                        l, e);

            }
        }
        return new SimpleACL(entries);
    }

    /** write the ACL out to an output stream. closes the stream */
    public void writeTo(OutputStream os) {
        PrintWriter bw =
                new PrintWriter(new OutputStreamWriter(os, Charsets.UTF_8));
        for (SimpleACLEntry e : entries) {
            bw.println(e.toString());
        }
        bw.close();
    }

    /**
     * return the list of Entries. Changes to the list directly affect the
     * behavior of the ACL. The used list is thread-safe to manipulate, but
     * changes are expensive (Copy-On-Write) O(n).
     *
     * @return the list wrapped by this ACL list;
     */
    public List<SimpleACLEntry> getEntries() {
        return entries;
    }

    /**
     * add an entry to this ACL list. Takes effect immediately. Threadsafe, but
     * expensive (O(n)).
     *
     * @param op
     * @param query
     * @param result
     */
    public void addEntry(AuthorizationHook.Operation op, LocationPathExpression path,
            AuthorizationHook.Result result) {
        getEntries().add(
                new SimpleACLEntry(new SimpleACLMatch(op, path), result));
    }
}