package org.projectfloodlight.db.auth.application;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.projectfloodlight.db.auth.session.JsonFileDataStore;
import org.projectfloodlight.util.IOUtils;
import org.restlet.Request;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.util.Series;

/** register applications by name, validate them with secret keys
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class JsonApplicationRegistry implements ApplicationRegistry {

    private final Map<String, ApplicationRegistration> applications = new LinkedHashMap<String, ApplicationRegistration>();
    private static final Random random = new Random();

    static void saveJson(File dir, String key, ApplicationRegistration reg) throws IOException {

        JsonFileDataStore<ApplicationRegistration> store;
        store = new JsonFileDataStore<ApplicationRegistration>(dir, ApplicationRegistration.class, Pattern.compile(".+"));

        String tmpNew = key + "-new-" + (random.nextLong() & Long.MAX_VALUE);
        File curFile = new File(dir, key + ".json");
        File newFile = new File(dir, tmpNew + ".json");
        IOUtils.ensureDirectoryExistsAndWritable(dir);
        store.save(tmpNew, reg);
        IOUtils.mvAndOverride(newFile, curFile);
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.auth.application.ApplicationRegistryInterface#getRegistry()
     */
    @Override
    public Iterator<ApplicationRegistration> iterator() {
        return applications.values().iterator();
    }

    // XXX roth -- HACK HACK HACK
    /* (non-Javadoc)
     * @see org.projectfloodlight.db.auth.application.ApplicationRegistryInterface#registerApplication(org.projectfloodlight.db.auth.application.ApplicationRegistration)
     */
    @Override
    public void registerApplication(ApplicationRegistration app) {
        applications.put(app.getName(), app);
    }

    /* (non-Javadoc)
     * @see org.projectfloodlight.db.auth.application.ApplicationRegistryInterface#authenticate(org.restlet.Request)
     */
    @Override
    public ApplicationContext getApplication(Request request) {

        @SuppressWarnings("unchecked")
        Series<Header> headers = (Series<Header>) request.getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
        String appAuth = headers.getFirstValue("Authorization");

        if (appAuth == null)
            return null;

        for (ApplicationRegistration app : this) {
            if (appAuth.equals(app.getBasicAuth()))
                return new ApplicationContext(app.getName());
        }

        return null;
    }
}
