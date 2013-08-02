package net.bigdb.hook.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.hook.FilterHook;
import net.bigdb.hook.HookRegistry;
import net.bigdb.hook.ValidationHook;
import net.bigdb.hook.WatchHook;
import net.bigdb.util.Path;

import com.google.common.collect.ImmutableList;

public class HookRegistryImpl implements HookRegistry {

    private static final class AuthorizationHookInfo {

        final AuthorizationHook.Operation operation;
        final AuthorizationHook.Stage stage;

        AuthorizationHookInfo(AuthorizationHook.Operation operation,
                AuthorizationHook.Stage stage) {
            this.operation = operation;
            this.stage = stage;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result =
                    prime * result +
                            ((operation == null) ? 0 : operation.hashCode());
            result = prime * result + ((stage == null) ? 0 : stage.hashCode());
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
            AuthorizationHookInfo other = (AuthorizationHookInfo) obj;
            if (operation != other.operation)
                return false;
            if (stage != other.stage)
                return false;
            return true;
        }
    }

    public static class HookInfo {
        List<FilterHook> filterHooks;
        Map<AuthorizationHookInfo, List<AuthorizationHook>> authorizationHookMap;
        List<ValidationHook> validationHooks;
        List<WatchHook> watchHooks;
    }

    public static class RegistrationPath {

        final Path path;
        final boolean registerOnList;

        RegistrationPath(Path path, boolean registerOnList) {
            this.path = path;
            this.registerOnList = registerOnList;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (registerOnList ? 1231 : 1237);
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
            RegistrationPath other = (RegistrationPath) obj;
            if (registerOnList != other.registerOnList)
                return false;
            if (path == null) {
                if (other.path != null)
                    return false;
            } else if (!path.equals(other.path))
                return false;
            return true;
        }
    }

    private final Map<AuthorizationHookInfo, List<AuthorizationHook>> globalAuthorizationHookMap =
            new HashMap<AuthorizationHookInfo, List<AuthorizationHook>>();

    private final Map<RegistrationPath, HookInfo> hookMap =
            new HashMap<RegistrationPath, HookInfo>();

    private static final String SIMPLE_REGISTRATION_PATH_ERROR_MESSAGE =
            "The registration path for a hook cannot have any predicates: %s";
    private HookInfo getHookInfo(LocationPathExpression path,
            boolean registerOnList, boolean create) {
        Path simplePath = path.getSimplePath();
        RegistrationPath registrationPath =
                new RegistrationPath(simplePath, registerOnList);
        HookInfo hookInfo = hookMap.get(registrationPath);
        if ((hookInfo == null) && create) {
            hookInfo = new HookInfo();
            hookMap.put(registrationPath, hookInfo);
        }
        return hookInfo;
    }

    private void checkSimpleLocationPath(LocationPathExpression path)
            throws BigDBException {
        if ((path != null) && !path.isSimple()) {
            throw new BigDBException(String.format(
                    SIMPLE_REGISTRATION_PATH_ERROR_MESSAGE, path));
        }
    }

    @Override
    public synchronized void registerFilterHook(LocationPathExpression path,
            FilterHook filterHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, false, true);
        if (hookInfo.filterHooks == null)
            hookInfo.filterHooks = new CopyOnWriteArrayList<FilterHook>();
        hookInfo.filterHooks.add(filterHook);
    }

    @Override
    public synchronized void unregisterFilterHook(LocationPathExpression path,
            FilterHook filterHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, false, true);
        if (hookInfo.filterHooks != null)
            hookInfo.filterHooks.remove(filterHook);
        // Could clean up the hook info map here but probably not worth it.
        // At this point we don't expect apps to change their hooks a lot.
    }

    @Override
    public synchronized List<FilterHook> getFilterHooks(
            LocationPathExpression path) {
        LocationPathExpression simplePath = path.getSimpleLocationPath();
        HookInfo hookInfo = getHookInfo(simplePath, false, false);
        return (hookInfo != null) && (hookInfo.filterHooks != null)
                ? hookInfo.filterHooks : Collections.<FilterHook>emptyList();
    }

    private List<AuthorizationHook>
            getAuthorizationHooksInternal(LocationPathExpression path,
                    AuthorizationHook.Operation operation,
                    AuthorizationHook.Stage stage, boolean authorizeList,
                    boolean create) {

        // Construct info object that's the key to the authorization hook maps.
        AuthorizationHookInfo authHookInfo =
                new AuthorizationHookInfo(operation, stage);

        Map<AuthorizationHookInfo, List<AuthorizationHook>> authHookMap;
        if (path == null) {
            authHookMap = globalAuthorizationHookMap;
        } else {
            HookInfo hookInfo = getHookInfo(path, authorizeList, create);
            if (hookInfo == null)
                return null;

            if (hookInfo.authorizationHookMap == null) {
                if (!create)
                    return null;
                hookInfo.authorizationHookMap =
                        new HashMap<AuthorizationHookInfo, List<AuthorizationHook>>();
            }
            authHookMap = hookInfo.authorizationHookMap;
        }
        List<AuthorizationHook> authHooks =
                authHookMap.get(authHookInfo);
        if (authHooks == null) {
            if (!create)
                return null;
            authHooks = new CopyOnWriteArrayList<AuthorizationHook>();
            authHookMap.put(authHookInfo, authHooks);
        }

        return authHooks;
    }

    @Override
    public synchronized void registerAuthorizationHook(
            LocationPathExpression path, AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList,
            AuthorizationHook authorizationHook)
            throws BigDBException {
        checkSimpleLocationPath(path);
        List<AuthorizationHook> authHooks =
                getAuthorizationHooksInternal(path, operation, stage,
                        authorizeList, true);
        authHooks.add(authorizationHook);
    }

    @Override
    public synchronized void unregisterAuthorizationHook(
            LocationPathExpression path, AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList,
            AuthorizationHook authorizationHook)
            throws BigDBException {
        checkSimpleLocationPath(path);
        List<AuthorizationHook> authHooks =
                getAuthorizationHooksInternal(path, operation, stage,
                        authorizeList, false);
        if (authHooks != null)
            authHooks.remove(authorizationHook);
        // Could clean up the hook info map here but probably not worth it.
        // At this point we don't expect apps to change their hooks a lot.
    }

    @Override
    public synchronized List<AuthorizationHook> getAuthorizationHooks(
            LocationPathExpression path, AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList) {
        // Lookup up both the per-node and global auth hooks
        List<AuthorizationHook> authHooks;
        if (path != null) {
            LocationPathExpression simplePath = path.getSimpleLocationPath();
            authHooks = getAuthorizationHooksInternal(simplePath, operation,
                    stage, authorizeList, false);
        } else {
            authHooks = null;
        }
        List<AuthorizationHook> globalAuthHooks =
                getAuthorizationHooksInternal(null, operation, stage,
                        authorizeList, false);

        // If there are no installed hooks, then return an empty list
        if (authHooks == null && globalAuthHooks == null)
            return Collections.<AuthorizationHook> emptyList();

        // Otherwise build the union of the per-node and global hooks
        ImmutableList.Builder<AuthorizationHook> builder =
                new ImmutableList.Builder<AuthorizationHook>();
        if (authHooks != null)
            builder.addAll(authHooks);
        if (globalAuthHooks != null)
            builder.addAll(globalAuthHooks);

        return builder.build();
    }

    public synchronized void registerValidationHook(
            LocationPathExpression path, boolean validateList,
            ValidationHook validationHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, validateList, true);
        if (hookInfo.validationHooks == null)
            hookInfo.validationHooks =
                    new CopyOnWriteArrayList<ValidationHook>();
        hookInfo.validationHooks.add(validationHook);
    }

    public synchronized void unregisterValidationHook(
            LocationPathExpression path, boolean validateList,
            ValidationHook validationHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, validateList, true);
        if (hookInfo.validationHooks != null)
            hookInfo.validationHooks.remove(validationHook);
        // Could clean up the hook info map here but probably not worth it.
        // At this point we don't expect apps to change their hooks a lot.
    }

    public synchronized List<ValidationHook> getValidationHooks(
            LocationPathExpression path, boolean validateList) {
        LocationPathExpression simplePath = path.getSimpleLocationPath();
        HookInfo hookInfo = getHookInfo(simplePath, validateList, false);
        return (hookInfo != null) && (hookInfo.validationHooks != null)
                ? hookInfo.validationHooks
                : Collections.<ValidationHook>emptyList();
    }

    @Override
    public void registerWatchHook(LocationPathExpression path,
            boolean watchList, WatchHook watchHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, watchList, true);
        if (hookInfo.watchHooks == null)
            hookInfo.watchHooks = new CopyOnWriteArrayList<WatchHook>();
        hookInfo.watchHooks.add(watchHook);
    }

    @Override
    public void unregisterWatchHook(LocationPathExpression path,
            boolean watchList, WatchHook watchHook) throws BigDBException {
        checkSimpleLocationPath(path);
        HookInfo hookInfo = getHookInfo(path, watchList, true);
        if (hookInfo.watchHooks != null)
            hookInfo.watchHooks.remove(watchHook);
        // Could clean up the hook info map here but probably not worth it.
        // At this point we don't expect apps to change their hooks a lot.
    }

    @Override
    public List<WatchHook> getWatchHooks(LocationPathExpression path,
            boolean watchList) {
        LocationPathExpression simplePath = path.getSimpleLocationPath();
        HookInfo hookInfo = getHookInfo(simplePath, watchList, false);
        return (hookInfo != null) && (hookInfo.watchHooks != null)
                ? hookInfo.watchHooks
                : Collections.<WatchHook>emptyList();
    }
}
