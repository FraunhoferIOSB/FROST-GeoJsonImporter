/*
 * Copyright (C) 2020 Fraunhofer IOSB
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
package de.fraunhofer.iosb.ilt.gjimp.geojson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.gjimp.StaService;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.ProgressTracker;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class GeoJsonConverter implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(GeoJsonConverter.class.getName());

	private static final Pattern PLACE_HOLDER_PATTERN = Pattern.compile("\\{([^|{}]+)(\\|([^}]+))?\\}");

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "SensorThingsService", description = "The STA service to upload the Locations & Things to")
	@EditorClass.EdOptsClass(clazz = StaService.class)
	private StaService uploader;

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "Locations", description = "The definition of how to create Locations.")
	@EditorClass.EdOptsClass(clazz = CreatorLocation.class)
	private CreatorLocation creatorLocations;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Things", description = "The definition of how to create Things.")
	@EditorClass.EdOptsClass(clazz = CreatorThing.class)
	private CreatorThing creatorThings;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "ObsProps", description = "The definition of how to create ObservedProperties.")
	@EditorClass.EdOptsClass(clazz = CreatorObservedProperty.class)
	private CreatorObservedProperty creatorObservedProperties;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "CSV Loader", description = "The definition of if and how to load CSV.")
	@EditorClass.EdOptsClass(clazz = CsvLoaderOptions.class)
	private CsvLoaderOptions csvLoader;

	private SensorThingsService service;
	private FrostUtils frostUtils;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
		frostUtils = new FrostUtils(service);
	}

	public CsvLoaderOptions getCsvLoader() {
		return csvLoader;
	}

	public String generateTestOutput(Feature feature) {
		return new StringBuilder()
				.append(creatorLocations.generateTestOutput(feature))
				.append('\n')
				.append(creatorThings.generateTestOutput(feature))
				.append('\n')
				.append(creatorObservedProperties.generateTestOutput(feature))
				.toString();
	}

	public void importAll(FeatureCollection collection, ProgressTracker tracker) {
		creatorLocations.loadCache(tracker);
		creatorThings.loadCache(tracker);
		creatorObservedProperties.loadCache(tracker);

		List<Feature> features = collection.getFeatures();
		int total = features.size();
		int count = 0;
		tracker.updateProgress(count, total);
		for (Feature feature : features) {
			try {
				importFeature(feature);
				tracker.updateProgress(++count, total);
			} catch (JsonProcessingException | ServiceFailureException ex) {
			}
		}
	}

	public void importFeature(Feature feature) throws JsonProcessingException, ServiceFailureException {
		Location location = creatorLocations.createLocation(feature, frostUtils);
		creatorThings.createThing(feature, location, frostUtils);
		creatorObservedProperties.createObservedProperty(feature, location, frostUtils);
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

	private static String findMatch(String path, String deflt, Object source, boolean forUrl) {
		String[] parts = StringUtils.split(path, '/');
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
		if (forUrl) {
			return Utils.escapeForStringConstant(value.toString());
		}
		String result = StringUtils.replace(value.toString(), "\"", "\\\"");
		result = StringUtils.replace(result, "\n", "\\n");
		return result;
	}

	private static Object getFrom(String field, Object source) {
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
