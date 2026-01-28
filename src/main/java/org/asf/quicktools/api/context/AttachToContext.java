package org.asf.quicktools.api.context;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({ TYPE })
@Repeatable(AttachToContexts.class)
public @interface AttachToContext {
	public Class<? extends BaseContext> value();
}
