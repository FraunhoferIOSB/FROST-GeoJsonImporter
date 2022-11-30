/*
 * Copyright (C) 2021 Fraunhofer IOSB
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

import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.Utils;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.UnitConverter;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.Point;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class CsvLoaderOptions implements AnnotatedConfigurable<SensorThingsService, Object> {

	@ConfigurableField(editor = EditorBoolean.class, optional = false,
			label = "Is CSV", description = "Check if the source file is CSV")
	@EditorBoolean.EdOptsBool()
	private boolean sourceIsCsv;

	@ConfigurableField(editor = EditorInt.class, optional = true,
			label = "Row Skip", description = "The number of rows to skip when reading the file (0=none).")
	@EditorInt.EdOptsInt(dflt = 0, max = Integer.MAX_VALUE, min = 0, step = 1)
	private Integer rowSkip;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Delimiter", description = "The character to use as delimiter ('\\t' for tab, default ',').")
	@EditorString.EdOptsString(dflt = ",")
	private String delimiter;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Comment Marker", description = "Lines starting with this character are ignored.")
	@EditorString.EdOptsString(dflt = "")
	private String commentMarker;

	@ConfigurableField(editor = EditorBoolean.class, optional = true,
			label = "Tab Delimited", description = "Is the TAB character a delimeter?")
	@EditorBoolean.EdOptsBool()
	private boolean tabIsDelimeter;

	@ConfigurableField(editor = EditorBoolean.class, optional = true,
			label = "Has Header", description = "Check if the CSV file has a header line.")
	@EditorBoolean.EdOptsBool()
	private boolean hasHeader;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Axis One", description = "Name of the First-Axis column. Axis oder must be according to the used CRS.")
	@EditorString.EdOptsString(dflt = "")
	private String colAxisOne;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Axis Two", description = "Name of the Second-Axis column. Axis oder must be according to the used CRS.")
	@EditorString.EdOptsString(dflt = "")
	private String colAxisTwo;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "CRS", description = "The CRS of the imput data.")
	@EditorString.EdOptsString(dflt = "")
	private String colCrs;

	@ConfigurableField(editor = EditorInt.class, optional = true,
			label = "Numeric Scale", description = "The number of significant digits to use in coordinates. Default=6.")
	@EditorInt.EdOptsInt(dflt = 6, max = 15, min = 0, step = 1)
	private Integer numberScale;

	public CSVParser parseData(String data) throws IOException {
		char finalDelimiter = delimiter.charAt(0);
		if (tabIsDelimeter) {
			finalDelimiter = '\t';
		}
		CSVFormat.Builder formatBuilder = CSVFormat.Builder.create()
				.setDelimiter(finalDelimiter);
		if (hasHeader) {
			formatBuilder.setHeader().setSkipHeaderRecord(hasHeader);
		}
		if (!Utils.isNullOrEmpty(commentMarker)) {
			formatBuilder.setCommentMarker(commentMarker.charAt(0));
		}
		CSVFormat format = formatBuilder.build();
		return CSVParser.parse(data, format);
	}

	public FeatureCollection loadGeoJson(String data) throws IOException {
		CSVParser parser = parseData(data);
		FeatureCollection collection = new FeatureCollection();
		int row = 0;
		for (CSVRecord record : parser) {
			row++;
			if (row < rowSkip) {
				continue;
			}
			Feature feature = new Feature();
			if (record.isMapped(colAxisOne) && record.isMapped(colAxisTwo)) {
				BigDecimal axisOneValue = UnitConverter.stringToBigDecimal(record.get(colAxisOne));
				BigDecimal axisTwoValue = UnitConverter.stringToBigDecimal(record.get(colAxisTwo));
				Point point = new Point(FrostUtils.convertCoordinates(axisOneValue.doubleValue(), axisTwoValue.doubleValue(), getOrName(record, colCrs), numberScale));
				feature.setGeometry(point);
			}
			Map properties = record.toMap();
			if (properties.isEmpty()) {
				properties = feature.getProperties();
				int i = 0;
				for (String item : record) {
					properties.put(i++, item);
				}
			} else {
				feature.setProperties(properties);
			}
			collection.add(feature);
		}
		return collection;
	}

	private static String getOrName(CSVRecord record, String columnName) {
		if (record.isMapped(columnName)) {
			return record.get(columnName);
		}
		return columnName;
	}

	public boolean isSourceCsv() {
		return sourceIsCsv;
	}

}
