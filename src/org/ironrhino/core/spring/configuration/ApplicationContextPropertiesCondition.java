package org.ironrhino.core.spring.configuration;

import org.ironrhino.core.util.AppInfo;

class ApplicationContextPropertiesCondition extends SimpleCondition<ApplicationContextPropertiesConditional> {

	@Override
	public boolean matches(ApplicationContextPropertiesConditional annotation) {
		return matches(annotation.key(), annotation.value(), annotation.negated());
	}

	public static boolean matches(String key, String value, boolean negated) {
		boolean matched = value.equals(AppInfo.getApplicationContextProperties().getProperty(key));
		if (negated)
			matched = !matched;
		return matched;
	}

}
