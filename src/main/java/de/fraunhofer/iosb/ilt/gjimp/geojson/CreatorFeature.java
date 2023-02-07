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
import de.fraunhofer.iosb.ilt.configurable.Utils;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.utils.Caches;
import de.fraunhofer.iosb.ilt.gjimp.utils.EntityCache;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils.ENCODING_GEOJSON;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.geojson.MultiPolygon;
import org.geojson.Point;
import org.geojson.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class CreatorFeature implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorFeature.class.getName());

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

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "If Not Empty", description = "Template that must result in a non-empty value for the Entity to be created, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String ifNotEmptyTemplate;

	private SensorThingsService service;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
	}

	public String generateTestOutput(Feature feature, Caches caches) {
		if (templateName.isEmpty()) {
			return "Feature not configured.\n";
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return "FeatureOfInterest:\n  ifNotEmpty Template is empty.\n";
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
		String featureString;
		try {
			featureString = ObjectMapperFactory.get().writeValueAsString(geometry);
		} catch (JsonProcessingException ex) {
			featureString = "Failed to parse json: " + ex.getMessage();
		}

		FeatureOfInterest newFoi = new FeatureOfInterest(name, description, ENCODING_GEOJSON, geometry);
		newFoi.setProperties(properties);

		String equalsFilter = fillTemplate(templateEqualsFilter, newFoi, true);
		String cacheKey = fillTemplate(caches.getCacheFeatures().getTemplateCacheKey(), newFoi, false);

		StringBuilder output = new StringBuilder("Feature:\n");
		output.append("  name: ").append(name).append('\n')
				.append("  description: ").append(description).append('\n')
				.append("  properties: ").append(propertiesString).append('\n')
				.append("  feature: ").append(featureString).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Cache Key: ").append(cacheKey).append('\n');

		return output.toString();
	}

	public FeatureOfInterest createFeatureOfInterest(Feature feature, FrostUtils frostUtils, Caches caches) throws JsonProcessingException, ServiceFailureException {
		if (templateName.isEmpty()) {
			return null;
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return null;
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		Map<String, Object> properties = Collections.emptyMap();
		if (!Utils.isNullOrEmpty(propertiesString)) {
			properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
		}

		GeoJsonObject geometry = getGeometry(feature);
		FeatureOfInterest newFoi = new FeatureOfInterest(name, description, ENCODING_GEOJSON, geometry);
		newFoi.setProperties(properties);

		EntityCache<FeatureOfInterest> cache = caches.getCacheFeatures();
		FeatureOfInterest cachedFoI = cache.getCachedVersion(newFoi);

		String filter = fillTemplate(templateEqualsFilter, newFoi, true);
		LOGGER.debug("FeatureOfInterest Filter: {}", filter);
		FeatureOfInterest foi = frostUtils.findOrCreateFeature(filter, newFoi, cachedFoI);
		cache.put(foi);

		return foi;
	}

	private GeoJsonObject getGeometry(Feature feature) {
		String crs = fillTemplate(templateCrs, feature, false);
		return convertCrs(feature.getGeometry(), crs);
	}

	private GeoJsonObject convertCrs(GeoJsonObject input, String inputCrs) {
		if (input instanceof Point) {
			return FrostUtils.convertCoordinates((Point) input, inputCrs, numberScale, flipCoords);
		} else if (input instanceof Polygon) {
			return FrostUtils.convertCoordinates((Polygon) input, inputCrs, numberScale, flipCoords);
		} else if (input instanceof MultiPolygon) {
			return FrostUtils.convertCoordinates((MultiPolygon) input, inputCrs, numberScale, flipCoords);
		}

		return input;
	}

}
