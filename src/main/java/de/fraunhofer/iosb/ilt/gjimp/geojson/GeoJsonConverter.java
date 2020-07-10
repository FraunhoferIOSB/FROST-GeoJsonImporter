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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.ObservationUploader;
import de.fraunhofer.iosb.ilt.gjimp.utils.EntityCache;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils.ENCODING_GEOJSON;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.ProgressTracker;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.HashMap;
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

	private static final Pattern PLACE_HOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_0-9.-]+)(\\|([^}]+))?\\}");

	@ConfigurableField(editor = EditorBoolean.class, optional = false,
			label = "Copy to Thing", description = "Create a Thing for each Location that has all the properties of the Location")
	@EditorBoolean.EdOptsBool(dflt = true)
	private boolean mirrorToThing;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Name Template", description = "Template used to generate the name, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Description Template", description = "Template used to generate the description, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 3)
	private String templateDescription;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Properties Template", description = "Template used to generate the properties, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "SensorThingsService", description = "The STA service to upload the Locations & Things to"
	)
	@EditorClass.EdOptsClass(clazz = ObservationUploader.class)
	private ObservationUploader uploader;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path.to.field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheFilter", description = "Filter used to load the cache.")
	@EditorString.EdOptsString(lines = 1, dflt = "properties/type eq 'NUTS'")
	private String cacheFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheKey", description = "Template used to generate the key used to cache, using {path.to.field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "{properties/type}-{properties/nutsId}")
	private String templateCacheKey;

	private SensorThingsService service;
	private FrostUtils frostUtils;
	private EntityCache<String, Thing> cacheThings;
	private EntityCache<String, Location> cacheLocations;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
		frostUtils = new FrostUtils(service);
	}

	public String generateTestOutput(Feature feature) {
		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);

		Map<String, Object> properties;
		try {
			properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
			propertiesString = ObjectMapperFactory.get().writeValueAsString(properties);
		} catch (JsonProcessingException ex) {
			propertiesString = "Failed to parse json: " + ex.getMessage();
			properties = new HashMap<>();
		}

		Location newLocation = new Location(name, description, ENCODING_GEOJSON, feature.getGeometry());
		newLocation.setProperties(properties);

		String equalsFilter = fillTemplate(templateEqualsFilter, newLocation, true);
		String cacheKey = fillTemplate(templateCacheKey, newLocation, false);

		StringBuilder output = new StringBuilder();
		output.append("Name: ").append(name).append('\n')
				.append("Description: ").append(description).append('\n')
				.append("Properties: ").append(propertiesString).append('\n')
				.append('\n')
				.append("Equals Filter: ").append(equalsFilter).append('\n')
				.append("Cache Key: ").append(cacheKey).append('\n');

		return output.toString();
	}

	public void importAll(FeatureCollection collection, ProgressTracker tracker) {
		cacheThings = new EntityCache<>(
				entity -> fillTemplate(templateCacheKey, entity, false),
				entity -> entity.getName());
		cacheLocations = new EntityCache<>(
				entity -> fillTemplate(templateCacheKey, entity, false),
				entity -> entity.getName());
		try {
			cacheLocations.load(service.locations(), cacheFilter, "id,name,description,properties,encodingType,location", "");
			cacheThings.load(service.things(), cacheFilter, "id,name,description,properties", "Locations($select=id)");
		} catch (ServiceFailureException ex) {
			LOGGER.error("Failed to load the Cache.", ex);
		}
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
		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		Map<String, Object> properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);

		Location newLocation = new Location(name, description, ENCODING_GEOJSON, feature.getGeometry());
		newLocation.setProperties(properties);

		String cacheKey = fillTemplate(templateCacheKey, newLocation, false);
		Location cachedLocation = cacheLocations.get(cacheKey);

		String filter = fillTemplate(templateEqualsFilter, newLocation, true);
		LOGGER.debug("Filter: {}", filter);
		Location location = frostUtils.findOrCreateLocation(filter, newLocation, cachedLocation);
		if (mirrorToThing) {
			Thing cachedThing = cacheThings.get(cacheKey);
			Thing newThing = FrostUtils.buildThing(name, description, properties, location);
			frostUtils.findOrCreateThing(filter, newThing, cachedThing);
		}
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
		String[] parts = StringUtils.split(path, '.');
		Object value = source;
		for (String part : parts) {
			part = URLDecoder.decode(part, JsonUtils.UTF_8);
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
