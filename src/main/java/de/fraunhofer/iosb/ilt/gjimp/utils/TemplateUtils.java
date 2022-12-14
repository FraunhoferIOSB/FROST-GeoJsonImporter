/*
 * Copyright (C) 2022 Fraunhofer IOSB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.gjimp.utils;

import de.fraunhofer.iosb.ilt.sta.Utils;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
public class TemplateUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateUtils.class.getName());
	private static final Pattern PLACE_HOLDER_PATTERN = Pattern.compile("\\{([^|{}]+)(\\|([^}]*))?\\}");

	private TemplateUtils() {
		// Utility class
	}

	public static String fillTemplate(String template, Object feature, boolean forUrl) {
		Matcher matcher = PLACE_HOLDER_PATTERN.matcher(template);
		matcher.reset();
		StringBuilder result = new StringBuilder();
		int pos = 0;
		while (matcher.find()) {
			int start = matcher.start();
			result.append(template.substring(pos, start));
			result.append(findMatch(matcher.group(1), matcher.group(3), feature, forUrl));
			pos = matcher.end();
		}
		result.append(template.substring(pos));
		return result.toString();
	}

	private static String findMatch(final String path, final String deflt, final Object source, final boolean forUrl) {
		boolean numeric = false;
		final String realPath;
		if (path.startsWith("N:")) {
			numeric = true;
			realPath = path.substring(2);
		} else {
			realPath = path;
		}
		String[] parts = StringUtils.split(realPath, '/');
		Object value = source;
		for (String part : parts) {
			part = JsonUtils.DecodeJsonPointer(part);
			value = getFrom(part, value);
			if (value == null) {
				return deflt;
			}
		}
		if (value instanceof Map || value instanceof List || value == null) {
			return deflt;
		}
		if (Utils.isNullOrEmpty(value.toString())) {
			return deflt;
		}
		if (forUrl) {
			return Utils.escapeForStringConstant(value.toString());
		}
		String result = StringUtils.replace(value.toString(), "\"", "\\\"");
		result = StringUtils.replace(result, "\n", "\\n");
		if (numeric) {
			result = UnitConverter.convertDecimalSeparator(result);
		}
		return result;
	}

	public static Object getFrom(String field, Object source) {
		if (source instanceof Map) {
			Map map = (Map) source;
			return map.get(field);
		} else if (source instanceof List) {
			List list = (List) source;
			try {
				Integer idx = Integer.valueOf(field);
				return list.get(idx);
			} catch (NumberFormatException ex) {
				return null;
			}
		}
		String getterName = "get" + field.substring(0, 1).toUpperCase() + field.substring(1);
		try {
			return MethodUtils.invokeMethod(source, getterName);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
			LOGGER.trace("Failed to execute getter {} on {}", getterName, source, ex);
			return null;
		}
	}
}
