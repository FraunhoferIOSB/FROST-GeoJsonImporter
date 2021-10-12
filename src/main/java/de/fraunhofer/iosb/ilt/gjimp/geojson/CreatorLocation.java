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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import static de.fraunhofer.iosb.ilt.gjimp.geojson.GeoJsonConverter.fillTemplate;
import de.fraunhofer.iosb.ilt.gjimp.utils.EntityCache;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils.ENCODING_GEOJSON;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.ProgressTracker;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.util.HashMap;
import java.util.Map;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class CreatorLocation implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorLocation.class.getName());

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Crs Template", description = "Template used to generate the crs, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String templateCrs;

	@ConfigurableField(editor = EditorBoolean.class, optional = true,
			label = "Flip Coordinates", description = "Flip lat/lon coordinates")
	@EditorBoolean.EdOptsBool()
	private boolean flipCoords;

	@ConfigurableField(editor = EditorInt.class, optional = true,
			label = "Numeric Scale", description = "The number of significant digits to use in coordinates. Default=6.")
	@EditorInt.EdOptsInt(dflt = 6, max = 15, min = 0, step = 1)
	private Integer numberScale;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Name Template", description = "Template used to generate the name, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Description Template", description = "Template used to generate the description, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 3)
	private String templateDescription;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Properties Template", description = "Template used to generate the properties, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path/to/field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheFilter", description = "Filter used to load the cache.")
	@EditorString.EdOptsString(lines = 1, dflt = "properties/type eq 'NUTS'")
	private String cacheFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheKey", description = "Template used to generate the key used to cache, using {path/to/field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "{properties/type}-{properties/nutsId}")
	private String templateCacheKey;

	private EntityCache<String, Location> cache;
	private SensorThingsService service;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
	}

	public String generateTestOutput(Feature feature) {
		if (templateName.isEmpty()) {
			return "Locations not configured.\n";
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);

		Map<String, Object> properties = null;
		if (!propertiesString.trim().isEmpty()) {
			try {
				properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
				propertiesString = ObjectMapperFactory.get().writeValueAsString(properties);
			} catch (JsonProcessingException ex) {
				propertiesString = "Failed to parse json: " + ex.getMessage();
				properties = new HashMap<>();
			}
		}

		GeoJsonObject geometry = getGeometry(feature);
		String locationString;
		try {
			locationString = ObjectMapperFactory.get().writeValueAsString(geometry);
		} catch (JsonProcessingException ex) {
			locationString = "Failed to parse json: " + ex.getMessage();
		}

		Location newLocation = new Location(name, description, ENCODING_GEOJSON, geometry);
		newLocation.setProperties(properties);

		String equalsFilter = fillTemplate(templateEqualsFilter, newLocation, true);
		String cacheKey = fillTemplate(templateCacheKey, newLocation, false);

		StringBuilder output = new StringBuilder("Location:\n");
		output.append("  name: ").append(name).append('\n')
				.append("  description: ").append(description).append('\n')
				.append("  properties: ").append(propertiesString).append('\n')
				.append("  location: ").append(locationString).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Cache Load Filter: ").append(cacheFilter).append('\n')
				.append("  Cache Key: ").append(cacheKey).append('\n');

		return output.toString();
	}

	public void loadCache(ProgressTracker tracker) {
		if (templateName.isEmpty()) {
			return;
		}
		cache = new EntityCache<>(
				entity -> fillTemplate(templateCacheKey, entity, false),
				entity -> entity.getName());
		try {
			cache.load(service.locations(), cacheFilter, "id,name,description,properties,encodingType,location", "");
		} catch (ServiceFailureException ex) {
			LOGGER.error("Failed to load the Cache.", ex);
		}
	}

	public EntityCache<String, Location> getCache() {
		return cache;
	}

	public Location createLocation(Feature feature, FrostUtils frostUtils) throws JsonProcessingException, ServiceFailureException {
		if (templateName.isEmpty()) {
			return null;
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		Map<String, Object> properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);

		String crs = fillTemplate(templateCrs, feature, false);
		GeoJsonObject geometry = getGeometry(feature);
		Location newLocation = new Location(name, description, ENCODING_GEOJSON, geometry);
		newLocation.setProperties(properties);

		Location cachedLocation = getCachedLocation(newLocation);

		String filter = fillTemplate(templateEqualsFilter, newLocation, true);
		LOGGER.debug("Location Filter: {}", filter);
		Location location = frostUtils.findOrCreateLocation(filter, newLocation, cachedLocation);
		cache.put(location);

		return location;
	}

	private GeoJsonObject getGeometry(Feature feature) {
		String crs = fillTemplate(templateCrs, feature, false);
		return convertCrs(feature.getGeometry(), crs);
	}

	private GeoJsonObject convertCrs(GeoJsonObject input, String inputCrs) {
		if (Utils.isNullOrEmpty(inputCrs)) {
			return input;
		}
		if (input instanceof Point) {
			Point point = (Point) input;
			if (flipCoords) {
				return FrostUtils.convertCoordinates(point.getCoordinates().getLongitude(), point.getCoordinates().getLatitude(), inputCrs, numberScale);
			} else {
				return FrostUtils.convertCoordinates(point.getCoordinates().getLatitude(), point.getCoordinates().getLongitude(), inputCrs, numberScale);
			}
		}
		return input;
	}

	public Location getCachedLocation(String cacheKey) {
		return cache.get(cacheKey);
	}

	public Location getCachedLocation(Location newLocation) {
		String cacheKey = fillTemplate(templateCacheKey, newLocation, false);
		return cache.get(cacheKey);
	}
}
