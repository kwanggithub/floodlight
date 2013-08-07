package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.schema.Schema;

/** a BigDB datasource implementation for AAA that provides the authentication specific runtime data
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthDataSource extends ServerDataSource {

    public AuthDataSource(Schema schema) throws BigDBException {
        super("aaa-data-source", schema);
    }

}
