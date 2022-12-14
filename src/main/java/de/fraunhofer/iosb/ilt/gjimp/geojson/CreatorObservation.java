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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.utils.Caches;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class CreatorObservation implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorObservation.class.getName());

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Result Template", description = "Template used to generate the result, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateResult;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Phenomenon Time Template", description = "Template used to generate the phenomenon time, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templatePhenomenonTime;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Parameters Template", description = "Template used to generate the parameters, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path.to.field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Datastream Key Template", description = "Template used to generate the key to find the Datastream, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "{name}")
	private String dsCacheTemplate;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Feature Key Template", description = "Template used to generate the key to find the FeatureOfInterest, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String featureCacheTemplate;

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
		if (templateResult.isEmpty()) {
			return "Observation not configured.\n";
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return "Observation:\n  ifNotEmpty Template is empty.\n";
		}

		String resultString = fillTemplate(templateResult, feature, false);
		String phenTimeString = fillTemplate(templatePhenomenonTime, feature, false);
		String parametersString = fillTemplate(templateProperties, feature, false);
		String dsKey = fillTemplate(dsCacheTemplate, feature, false);
		String foiKey = "";
		if (!featureCacheTemplate.isEmpty()) {
			foiKey = fillTemplate(featureCacheTemplate, feature, false);
		}

		Object result = null;
		try {
			result = ObjectMapperFactory.get().readValue(resultString, Object.class);
		} catch (JsonProcessingException ex) {
			resultString = "Failed to parse result: " + ex.getMessage();
		}

		TimeObject phenTime;
		try {
			phenTime = TimeObject.parse(phenTimeString);
		} catch (DateTimeParseException ex) {
			phenTimeString = "Failed to parse '" + phenTimeString + "' as Time Object.";
			phenTime = new TimeObject(ZonedDateTime.now());
		}

		Map<String, Object> parameters = null;
		if (!parametersString.trim().isEmpty()) {
			try {
				parameters = ObjectMapperFactory.get().readValue(parametersString, JsonUtils.TYPE_MAP_STRING_OBJECT);
				parametersString = ObjectMapperFactory.get().writeValueAsString(parameters);
			} catch (JsonProcessingException ex) {
				parametersString = "Failed to parse json: " + ex.getMessage();
			}
		}

		Observation observation;
		if (phenTime.isInterval()) {
			observation = new Observation(result, phenTime.getAsInterval());
		} else {
			observation = new Observation(result, phenTime.getAsDateTime());
		}
		observation.setParameters(parameters);

		String equalsFilter = fillTemplate(templateEqualsFilter, observation, true);

		StringBuilder output = new StringBuilder("Observation:\n");
		output.append("  Result: ").append(resultString).append('\n')
				.append("  PhenomenonTime: ").append(phenTimeString).append('\n')
				.append("  Parameters: ").append(parametersString).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Datastream Cache Key: ").append(dsKey).append('\n')
				.append("  Feature Cache Key: ").append(foiKey).append('\n');

		return output.toString();
	}

	public Observation createObservation(Feature feature, FrostUtils frostUtils, Caches caches) throws JsonProcessingException, ServiceFailureException {
		if (templateResult.isEmpty()) {
			return null;
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return null;
		}

		String resultString = fillTemplate(templateResult, feature, false);
		String phenTimeString = fillTemplate(templatePhenomenonTime, feature, false);
		String parametersString = fillTemplate(templateProperties, feature, false);

		Object result = ObjectMapperFactory.get().readValue(resultString, Object.class);
		TimeObject phenTime = TimeObject.parse(phenTimeString);

		Map<String, Object> parameters = null;
		if (!Utils.isNullOrEmpty(parametersString)) {
			parameters = ObjectMapperFactory.get().readValue(parametersString, JsonUtils.TYPE_MAP_STRING_OBJECT);
		}

		Observation observation;
		if (phenTime.isInterval()) {
			observation = new Observation(result, phenTime.getAsInterval());
		} else {
			observation = new Observation(result, phenTime.getAsDateTime().withZoneSameInstant(ZoneOffset.UTC));
		}
		observation.setParameters(parameters);

		String dsKey = fillTemplate(dsCacheTemplate, feature, false);
		observation.setDatastream(caches.getCacheDatastreams().get(dsKey));

		if (!featureCacheTemplate.isEmpty()) {
			String foiKey = fillTemplate(featureCacheTemplate, feature, false);
			observation.setFeatureOfInterest(caches.getCacheFeatures().get(foiKey));
		}

		return observation;
	}

}
