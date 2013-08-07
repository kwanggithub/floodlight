package org.projectfloodlight.db.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** When this annotation is present at a constructor, it tells AuthComponentCreator that this is the preferred way to construct the components.
 *  All parameters of said constructor must have CreateAuthParam annotations detailling the authConfig parameters to be used for supplying the
 *  parameter values.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface CreateAuthComponent {

}
