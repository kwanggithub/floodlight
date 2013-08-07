package org.projectfloodlight.db.hook;

import java.util.List;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;

/**
 * The hook registry manages all of the different hooks that are registered with
 * BigDB to customize the behavior of query/mutation operations.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface HookRegistry {

    /**
     * Register a filter hook at the specified path in the BigDB tree.
     *
     * @param path
     *            Currently this must be a simple path, i.e. no predicates for
     *            list nodes
     * @param filterHook
     *            the hook to invoke
     */
    public void registerFilterHook(LocationPathExpression path,
            FilterHook filterHook) throws BigDBException;

    /**
     * Unregister a filter hook. If the specified hook is not registered at the
     * specified path, the call is a no-op.
     *
     * @param path
     *            Path that was specified when the hook was registered
     * @param filterHook
     */
    public void unregisterFilterHook(LocationPathExpression path,
            FilterHook filterHook) throws BigDBException;

    /**
     * Retrieve all of the filter hooks registered at the specified path.
     *
     * @param path
     * @return
     */
    public List<FilterHook> getFilterHooks(LocationPathExpression path);

    /**
     * Register a authorization hook at the specified path in the BigDB tree.
     *
     * @param path
     *            Currently this must be a simple path, i.e. no predicates for
     *            list nodes
     * @param operation
     *            the operation to be authorized
     * @param stage
     *            the stage of the authorization process to be invoked
     * @param authorizeList
     *            only used if the path specifies a list node; if true then
     *            invoke the authorization hook for the list data node.
     *            Otherwise invoke it for each list element.
     * @param authorizationHook
     *            the hook to invoke
     */
    public void registerAuthorizationHook(LocationPathExpression path,
            AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList,
            AuthorizationHook authorizationHook) throws BigDBException;

    /**
     * Unregister a authorization hook. If the specified hook is not registered
     * at the specified path, the call is a no-op.
     *
     * @param path
     *            path that was specified when the hook was registered
     * @param operation
     *            operation that was specified when the hook was registered
     * @param stage
     *            stage that was specified when the hook was registered
     * @param authorizeList
     *            authorizeList that was specified when the hook was registered
     * @param authorizationHook
     *            the hook to unregister
     * @throws BigDBException
     */
    public void unregisterAuthorizationHook(LocationPathExpression path,
            AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList,
            AuthorizationHook authorizationHook) throws BigDBException;

    /**
     * Retrieve all of the authorization hooks registered at the specified path.
     *
     * @param path
     *            path for which authorization hooks were registered
     * @param operation
     *            operation for which authorization hooks were registered
     * @param stage
     *            stage for which authorization hooks were registered
     * @param authorizeList
     *            authorizeList for which authorization hooks were registered
     * @return
     * @throws BigDBException
     */
    public List<AuthorizationHook> getAuthorizationHooks(
            LocationPathExpression path, AuthorizationHook.Operation operation,
            AuthorizationHook.Stage stage, boolean authorizeList);

    /**
     * Register a validation hook at the specified path in the BigDB tree.
     *
     * @param path
     *            Currently this must be a simple path, i.e. no predicates for
     *            list nodes
     * @param validateList
     *            only used if the path specifies a list node; if true then
     *            invoke the validation hook for the list data node.
     *            Otherwise invoke it for each list element.
     * @param validationHook
     *            the hook to invoke
     */
    public void registerValidationHook(LocationPathExpression path,
            boolean validateList, ValidationHook validationHook)
                    throws BigDBException;

    /**
     * Unregister a validation hook. If the specified hook is not registered at
     * the specified path, the call is a no-op.
     *
     * @param path
     *            Path that was specified when the hook was registered
     * @param validateList
     *            validateList that was specified when the hook was registered
     * @param validationHook
     */
    public void unregisterValidationHook(LocationPathExpression path,
            boolean validateList, ValidationHook validationHook)
                    throws BigDBException;

    /**
     * Retrieve all of the validation hooks registered at the specified path.
     *
     * @param path
     *            path for which validation hooks were registered
     * @param validateList
     *            validateList for which validation hooks were registered
     * @return
     * @throws BigDBException
     */
    public List<ValidationHook> getValidationHooks(LocationPathExpression path,
            boolean validateList);

    /**
     * Register a watch hook at the specified path in the BigDB tree.
     *
     * @param path
     *            Path at which to register the watch hook. Currently this must
     *            be a simple path, i.e. no predicates for list nodes
     * @param watchList
     *            only used if the path specifies a list node; if true then
     *            invoke the watch hook for the list data node. Otherwise invoke
     *            it for each list element.
     * @param watchHook
     *            the hook to invoke
     */
    public void registerWatchHook(LocationPathExpression path,
            boolean watchList, WatchHook watchHook) throws BigDBException;

    /**
     * Unregister a watch hook. If the specified hook is not registered at the
     * specified path, the call is a no-op.
     *
     * @param path
     *            path that was specified when the hook was registered
     * @param watchList
     *            watchList that was specified when the hook was registered
     * @param watchHook
     *            the hook to unregister
     * @throws BigDBException
     */
    public void unregisterWatchHook(LocationPathExpression path,
            boolean watchList, WatchHook watchHook) throws BigDBException;

    /**
     * Retrieve all of the watch hooks registered at the specified path.
     *
     * @param path
     *            path for which watch hooks were registered
     * @param watchList
     *            watchList for which watch hooks were registered
     * @return the watch hooks registered at the specified path
     * @throws BigDBException
     */
    public List<WatchHook> getWatchHooks(LocationPathExpression path,
            boolean watchList);
}
