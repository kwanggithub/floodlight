package net.bigdb.auth;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import net.bigdb.auth.password.MultiPasswordHasher;
import net.bigdb.auth.password.PasswordHasher;
import net.bigdb.auth.session.FileSessionManager;
import net.bigdb.auth.session.SessionManager;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.rest.auth.AuthContextFactory;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.restlet.security.Authorizer;

import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableSet;

/** This class encapsulates the configuration of the auth subsystem. Parameter
 *  are defined as Generic instances of the local Param.
 *  E.g., the constant
 *  <pre>
 *      public final static Param<Boolean> AUTH_ENABLED =
 *           Param.booleanParam("enabled", "net.bigdb.useAuth");
 *  </pre>
 *   defines a parameter with the logical name "enabled", who has a 
 *   corresponding system property "net.bigdb.useAuth".
 *  <p>
 *  current parameter values can be queried with the getParam method. E.g.,
 *  <pre>
 *      boolean authEnabled = authConfig.getParam(AuthConfig.AUTH_ENABLED)
 *  </pre>
 *  The current value is determined by querying three value sources, in order 
 *  of precedence
 *  <ol>
 *    <li><b>local values locally set by setParam or constructor parameters</b>
 *    <li><b>the values of corresponding system properties</b>
 *    <li><b>default values stored in DEFAULT_VALUES</b>
 *  </ol>
 *
 *  Parameters can also be set by passing a parameter spec String to the
 *  constructor. The call
 *  <code>
 *     new AuthConfig("enabled=true, sessionDir=/var/floodlight/sessions")
 *  </code>
 *     is equivalent to
 *  <code>
 *     new AuthConfig().setParam(AuthConfig.AUTH_ENABLED, true).
 *      setParam(AuthConfig.SESSION_DIR, new File("/var/floodlight/sessions");
 *  </code>
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 *
 */
public class AuthConfig {

    public static final String SYSPROP_USE_AUTH =
        "net.bigdb.useAuth";

    /** enables/disables the aaa sub system */
    public final static Param<Boolean> AUTH_ENABLED =
            Param.booleanParam("enabled", SYSPROP_USE_AUTH);

    /**
     * when set, the password of the global admin account 'admin' will be 
     * reset to the value of this parameter during initialization.
     * Intended as a last resort password recovery mechanism.
     */
    public final static Param<String> RESET_ADMIN_PASSWORD =
            new Param<String>(String.class, "resetAdminPassword");

    /**
     * When set, allow the "admin" user to log in using an empty password when
     * no password is configured for the admin user
     */
    public final static Param<Boolean> ALLOW_EMPTY_ADMIN =
            Param.booleanParam("allowEmptyAdmin");
    
    /** 
     * when enabled, the AuthConfigFactory will accept unauthenticated requests
     * (i.e., requests without a session cookie) to protected resources.
     * in this case, AuthConfigFactory will create a 'null' AuthContext, that
     * must subsequently be handled by the authorizers.
     * Note: the default PlatformAuthorizer rejects null AuthContext, so 
     * to turn off authentication entirely, it must be replaced as well.
     * @see AuthContextFactory
     * @see AuthContext
     */
    public final static Param<Boolean> ENABLE_NULL_AUTHENTICATION =
            Param.booleanParam("enableNullAuthentication");

    /** 
     * the class of the default/platform implementation to be used for query 
     * operations.
     * Defaults to the PlatformAuthorizationHook. Can be changed. To turn off
     * Authorization, e.g., in unit tests, set to the NullAuthorizationHook
     *  @see NullAuthorizer
     *  @see PlatformAuthorizer
     *  @see Authorizer
     */
    public final static Param<Class<? extends AuthorizationHook>> 
        DEFAULT_QUERY_PREAUTHORIZATION_HOOK =
            Param.classParam(AuthorizationHook.class, 
                             "defaultQueryPreauthorizationHook");

    /**
     * the class of the default/platform implementation to be used for query 
     * operations. Defaults to the PlatformAuthorizationHook. Can be changed. 
     * To turn off Authorization, e.g.,in unit tests, set to the 
     * NullAuthorizationHook
     * @see NullAuthorizer
     * @see PlatformAuthorizer
     * @see Authorizer
     */
    public final static Param<Class<? extends AuthorizationHook>> 
        DEFAULT_QUERY_AUTHORIZATION_HOOK =
            Param.classParam(AuthorizationHook.class, 
                             "defaultQueryAuthorizationHook");

    /**
     * the class of the default/platform implementation to be used for 
     * mutation operations.  Defaults to the PlatformAuthorizationHook. Can
     * be changed. To turn off Authorization, e.g., in unit tests, set to the 
     * NullAuthorizationHook
     * @see NullAuthorizer
     * @see PlatformAuthorizer
     * @see Authorizer
     */
    public final static Param<Class<? extends AuthorizationHook>> 
        DEFAULT_MUTATION_PREAUTHORIZATION_HOOK =
            Param.classParam(AuthorizationHook.class, 
                             "defaultMutationPreauthorizationHook");

    /**
     * the class of the default/platform implementation to be used for mutation
     * operations.  Defaults to the PlatformAuthorizationHook. Can be changed.
     * To turn off Authorization, e.g., in unit tests, set to the
     * NullAuthorizationHook
     * @see NullAuthorizer
     * @see PlatformAuthorizer
     * @see Authorizer
     */
    public final static Param<Class<? extends AuthorizationHook>> 
        DEFAULT_MUTATION_AUTHORIZATION_HOOK =
            Param.classParam(AuthorizationHook.class, 
                             "defaultMutationAuthorizationHook");

    /** Whitelist ACL file to be used by the platform authorizer for restricted
     *  users.
     *  @see RestrictedAuthorizer
     */
    public static final Param<String> RESTRICTED_POLICY_FILE =
            Param.stringParam("restrictedPolicyFile");

    /** Whether Restricted Authorizer should go into policy recording mode. If
     *  set to true, the restrictedAuthorizer always returns its default
     *  decision, and writes all queries into the restrictedPolicyFile.
     *
     *  @see RestrictedAuthorizer
     */
    public static final Param<Boolean> RESTRICTED_POLICY_RECORD =
            Param.booleanParam( "restrictedPolicyRecord");

    /** When RestrictedAuthorizer is policy replay mode, this result is
     *  returned if no entry in the ACL matches When RestrictedAuthorizer is
     *  policy recording mode, this result is always returned (and new ACL
     *  entries are recorded).
     *
     *  @see RestrictedAuthorizer
     */
    public static final Param<AuthorizationHook.Result> 
        RESTRICTED_POLICY_DEFAULT_RESULT =
            new Param<AuthorizationHook.Result>(AuthorizationHook.Result.class, 
                                                "restrictedPolicyDefaultResult");

    /** SessionManager implementation to use. Defaults to the
     * FileSessionManager
     *
     *  @see FileSessionManager
     */
    public static final Param<Class<? extends SessionManager>> SESSION_MANAGER =
            Param.classParam(SessionManager.class, "sessionManager");

    /** Expiry/maximum cache size settings for the session manager.  Uses the
     * Google Guava CacheBuilderSpec syntax.
     *  <p>E.g., <code>'maximumsize=10, expireAfterAccess=1h'</code>
     * limits the number of concurrent sessions to 10, and session expire after
     * 1 h.
     *
     *  @see CacheBuilderSpec
     */
    public static final Param<String> SESSION_CACHE_SPEC =
            Param.stringParam("sessionCacheSpec");

    /** for the FileSystemManager, the directory where sessions are persisted
     *  as JSON files.  Defaults to <code>$HOME/.floodlight-sessions</code>
     */
    public static final Param<File> SESSION_DIR = Param.fileParam("sessionDir");

    /** Local repository for application keys (JSON descriptions).
     *  Defaults to <code>$HOME/.floodlight-applications</code> for 'normal' users and
     *  <code>/opt/bigswitch/run/applications</code> for the user 'bsn'.
     */
    public static final Param<String> APPLICATIONS = Param.stringParam("applications");
    public static final Param<File> APPLICATION_DIR = Param.fileParam("applicationDir");

    /** SessionManager implementation to use. Defaults to the FileSessionManager
    *
    *  @see FileSessionManager
    */
    public static final Param<Class<? extends PasswordHasher>> PASSWORD_HASHER =
            Param.classParam(PasswordHasher.class, "passwordHasher");

    /** for the UnixUserValidationHook, the path to the /etc/passwd file.
     *  Defaults to <code>/etc/passwd</code> for UNIX systems.
     *  Set to an empty string (empty abstract pathname) to disable this hook.
     */
    public static final Param<File> PASSWD_FILE = Param.fileParam("passwdFile");

    /** comma-separated list of IP addresses that are allowed to proxy
     * connections to the controller
     */
    public static final Param<Set<? extends String>> PROXY_WHITELIST =
            Param.setParam(String.class, "proxyWhitelist");

    /** map that contains the default values to be used as a last resort */
    private final static ParamMap DEFAULT_VALUES = initDefaultValues();

    private static ParamMap initDefaultValues() {
        ParamMap defaults = new ParamMap();
        defaults.set(AUTH_ENABLED,               Boolean.FALSE);
        // temporarily reset admin password by default
        // bigdb currently runs an in-memory data source and loses config on every restart
        defaults.set(RESET_ADMIN_PASSWORD,       null);
        defaults.set(ALLOW_EMPTY_ADMIN,          Boolean.FALSE);
        defaults.set(ENABLE_NULL_AUTHENTICATION, Boolean.FALSE);
        defaults.set(DEFAULT_QUERY_AUTHORIZATION_HOOK, PlatformAuthorizationHook.class);
        defaults.set(DEFAULT_QUERY_PREAUTHORIZATION_HOOK, PlatformPreauthorizationHook.class);
        defaults.set(DEFAULT_MUTATION_AUTHORIZATION_HOOK, PlatformAuthorizationHook.class);
        defaults.set(DEFAULT_MUTATION_PREAUTHORIZATION_HOOK, PlatformPreauthorizationHook.class);
        defaults.set(RESTRICTED_POLICY_FILE,     "/restricted.acl");
        defaults.set(RESTRICTED_POLICY_RECORD,   Boolean.FALSE);
        defaults.set(RESTRICTED_POLICY_DEFAULT_RESULT,   AuthorizationHook.Result.UNDECIDED);
        defaults.set(SESSION_MANAGER,            FileSessionManager.class);
        defaults.set(SESSION_CACHE_SPEC,         "maximumSize=100000,expireAfterAccess=2h");
        defaults.set(SESSION_DIR,                defaultSessionDir());
        defaults.set(APPLICATIONS,               null);
        defaults.set(APPLICATION_DIR,            defaultApplicationDir());
        defaults.set(PASSWORD_HASHER,            MultiPasswordHasher.class);
        defaults.set(PASSWD_FILE,                UnixUserValidationHook.PASSWD);
        defaults.set(PROXY_WHITELIST,            ImmutableSet.of("127.0.0.1"));
        return defaults.freeze();
    }

    private static File defaultSessionDir() {
        return new File(System.getProperty("user.home"), ".floodlight-sessions");
    }

    private static File defaultApplicationDir() {
        return new File(System.getProperty("user.home"), ".floodlight-applications");
    }

    private final ParamMap customValues;

    public AuthConfig() {
        customValues = new ParamMap();
    }

    /**
     * initialize an auth config with a comma separated string of
     * key-value-pairs, primarily for testing keys are the logical names of
     * Auth Parameters values are the values to be set
     *
     * @param spec, e.g., "auth=enabled,resetAdminPassword=admin2,enableNullAuth=true"
     */
    public AuthConfig(String spec) {
       this();
       Pattern commaPattern = Pattern.compile("\\s*,\\s*");
       Pattern equalsPattern = Pattern.compile("\\s*=\\s*");
       for(String keyVal : commaPattern.split(spec)) {
           String[] keyValArray = equalsPattern.split(keyVal);
           if(keyValArray.length != 2) {
               throw new IllegalArgumentException("Expected: key=value pair");
           }
           setParam(keyValArray[0], keyValArray[1]);
       }
    }

    /** programatically set a parameter by its logical name */
    public void setParam(String paramName, String val) {
        @SuppressWarnings("unchecked")
        Param<Object> param = (Param<Object>) DEFAULT_VALUES.getParamByName(paramName);
        if(param == null) {
            throw new IllegalArgumentException("Unknown parameter "+paramName);
        }
        customValues.set(param, param.getValueFromString(val));
    }

    /** programatically set a parameter by Param instance. */
    public <T> AuthConfig setParam(Param<T> param, T value) {
        customValues.set(param, value);
        return this;
    }

    /** programatically unset a parameter by Param instance. This will make the
     *  system property configuration / default values 'shine through'.
     **/
    public <T> T unsetParam(Param<T> param) {
        return customValues.remove(param);
    }

    /** get the current value of this parameter.  Parameters are queried
     * locally, in the system property, from the DEFAULT_VALUES.
     *
     * @param param
     * @return
     */
    public <T> T getParam(Param<T> param) {
        if (customValues.containsKey(param)) {
            return customValues.get(param);
        }

        String sysPropString = System.getProperty(param.getSysProp());
        if (sysPropString != null) {
            return param.getValueFromString(sysPropString);
        }

        if (DEFAULT_VALUES.containsKey(param))
            return DEFAULT_VALUES.get(param);

        throw new IllegalStateException("No default value for parameter " + param);
    }

    /** get the value of a parameter based on its logical name */
    public Object getParam(String paramKey) {
        Param<?> p = DEFAULT_VALUES.getParamByName(paramKey);
        if(p == null) {
            throw new IllegalArgumentException("Unknown parameter: "+paramKey);
        }

        return getParam(p);
    }

    public static class Param<T> {
        private final String name;
        private final Class<T> clazz;
        private final String sysProp;

        private Param(Class<T> clazz, String name, String sysProp) {
            this.clazz = clazz;
            this.name = name;
            this.sysProp = sysProp;
        }

        Param(Class<T> clazz, String name) {
            this(clazz, name, "net.bigdb.auth."+name);
        }

        public static Param<Boolean> booleanParam(String name, String sysprop) {
            return new Param<Boolean>(Boolean.class, name, sysprop);
        }

        public static Param<String> stringParam(String name) {
            return new Param<String>(String.class, name);
        }

        public static Param<Boolean> booleanParam(String name) {
            return new Param<Boolean>(Boolean.class, name);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static <T> Param<Class<? extends T>> classParam(Class<T> clazz, 
                                                               String name) {
            // this is ugly
            return new Param(Class.class, name);
        }

        public static Param<File> fileParam(String name) {
            return new Param<File>(File.class, name);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public static <T> Param<Set<? extends T>> setParam(Class<T> clazz, String name) {
            return new Param(Set.class, name);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        /** convert a parameter from a string to the Parameter target
         *  type. E.g., for evaluating system properties
         *
         * @param string representation of the value, e.g., (a) "true" (b)
         * "/var/log/messages" (c) "net.bigdb.authAuthorizer
         * @return instantiated representation of the value in the target type
         * (a) Boolean.TRUE (b) java.io.File("/var/log/messages") (c)
         * Class.forName("net.bigdb.authAuthoizer")
         */
        public T getValueFromString(String s) {
            if (s == null)
                return null;

            if (clazz == String.class)
                return (T) String.valueOf(s);
            else if (clazz == Boolean.class)
                return (T) Boolean.valueOf(s);
            else if (clazz == Integer.class)
                return (T) Integer.valueOf(s);
            else if (clazz == Double.class)
                return (T) Double.valueOf(s);
            else if (clazz == File.class)
                return (T) new File(s);
            else if (clazz == Set.class)
                return (T) ImmutableSet.of(s.split(",\\s*"));
            else if (Enum.class.isAssignableFrom(clazz)) {
                 return (T) Enum.valueOf((Class<Enum>) clazz, s);
            } else if (Class.class.isAssignableFrom(clazz)) {
                try {
                    return (T) Class.forName(s);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Could not find class " + s, e);
                }
            } else
                throw new IllegalArgumentException(
                        "don't know how to convert parameter of type " + clazz);
        }

        public String getName() {
            return name;
        }

        public String getSysProp() {
            return sysProp;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    /** keeps a map of parameter. threadsafe */
    static class ParamMap {
        private final ConcurrentMap<Param<?>, Object> map =
                new ConcurrentHashMap<Param<?>, Object>();

        public final static Object NULL_VALUE_MARKER = new Object();

        private volatile boolean frozen;

        public <T> void set(Param<T> param, T value) {
            if (frozen)
                throw new IllegalStateException("Config map frozen");
            map.put(param, value != null ? value : NULL_VALUE_MARKER);
        }

        @SuppressWarnings("unchecked")
        public <T> T remove(Param<T> param) {
            if (frozen)
                throw new IllegalStateException("Config map frozen");
            return (T) map.remove(param);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(Param<T> param) {
            T result = (T) map.get(param);
            return result != NULL_VALUE_MARKER ? result : null;
        }

        public Param<?> getParamByName(String name) {
            name = underscoreToCamelCase(name);
            for(Param<?> p: map.keySet() ) {
                if(name.equals(p.getName()))
                    return p;
            }
            return null;
        }

        private final static Pattern underscorePattern = Pattern.compile("_");
        private String underscoreToCamelCase(String name) {
            if(name.indexOf('_') < 0)
                return name;

            boolean first = true;
            StringBuilder buf = new StringBuilder();
            for(String s : underscorePattern.split(name)) {
                if(first) {
                    buf.append(s);
                    first = false;
                } else {
                    if(s.length() > 0) {
                        buf.append(Character.toUpperCase(s.charAt(0)));
                        buf.append(s.substring(1));
                    }
                }
            }
            return buf.toString();
        }

        boolean containsKey(Param<?> param) {
            return map.containsKey(param);
        }

        public ParamMap freeze() {
            frozen = true;
            return this;
        }

        @Override
        public String toString() {
            return "ParamMap [map=" + map + "]";
        }
    }

    private final static class InstanceHolder {
        private final static AuthConfig INSTANCE = new AuthConfig();
    }
    public static AuthConfig getDefault() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String toString() {
        return "AuthConfig [customValues=" + customValues + "]";
    }

}
