package org.projectfloodlight.db.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This annotation is used in Constructors marked with the CreateAuthComponent annotation to declare with AuthConfig parameter should be used
 *  by AuthComponentCreatoir to fill in the value for the parameter.
 *  <p>
 *  E.g., for the parameter
 *  <pre>
 *    @CreateAuthParam("sessionCacheSpec") String cacheSpec, ....
 *  </pre>
 *  the value returned by authConfig.getParam("sessionCacheSpec") will be used.
 *
 * @see CreateAuthComponent
 * @see AuthComponentCreator
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CreateAuthParam {

    String value();
}
