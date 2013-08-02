package net.bigdb.rest.auth;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Set;

import net.bigdb.auth.AuthConfig;

import org.restlet.Request;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.util.Series;

import com.google.common.net.InetAddresses;

/** Miscellaneous cruft for dealing with proxy connections
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class ProxyUtils {

    /** Compute the IP address of the peer.
     *
     * If there is a valid X-Forwarded-For header,
     * and if the peer is allowed to proxy requests,
     * then compute the starting client address.
     *
     * @param authConfig
     * @param request
     * @return
     */
    static public String getUpstreamAddress(AuthConfig authConfig, Request request) {
        String peer = request.getClientInfo().getAddress();

        Set<? extends String> peers = authConfig.getParam(AuthConfig.PROXY_WHITELIST);
        if (!peers.contains(peer))
            return peer;

        @SuppressWarnings("unchecked")
        Series<Header> headers = (Series<Header>) request.getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
        String xff = headers.getFirstValue("X-Forwarded-For");
        if (xff == null)
            return peer;

        String[] hops = xff.split(",");
        if (hops.length > 0) {
            String hop = hops[0].trim();
            if (InetAddresses.isInetAddress(hop)) {
                InetAddress a = InetAddresses.forString(hop);
                if (a instanceof Inet4Address)
                    return hop;
            }
        }

        return peer;
    }
}
