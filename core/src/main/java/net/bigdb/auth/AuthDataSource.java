package net.bigdb.auth;

import net.bigdb.BigDBException;
import net.bigdb.data.ServerDataSource;
import net.bigdb.schema.Schema;

/** a BigDB datasource implementation for AAA that provides the authentication specific runtime data
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthDataSource extends ServerDataSource {

    public AuthDataSource(Schema schema) throws BigDBException {
        super("aaa-data-source", schema);
    }

}
