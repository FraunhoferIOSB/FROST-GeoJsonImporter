/*
 * Copyright (C) 2017 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
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
package de.fraunhofer.iosb.ilt.gjimp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.gjimp.geojson.GeoJsonConverter;
import de.fraunhofer.iosb.ilt.gjimp.utils.ProgressTracker;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.apache.commons.io.FileUtils;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.slf4j.LoggerFactory;

public class GeoJsonImportController implements Initializable {

	/**
	 * The logger for this class.
	 */
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GeoJsonImportController.class);
	@FXML
	private ScrollPane paneConfig;
	@FXML
	private Button buttonLoad;
	@FXML
	private Button buttonNext;
	@FXML
	private Button buttonOpen;
	@FXML
	private Button buttonPrev;
	@FXML
	private Button buttonSave;
	@FXML
	private Button buttonImport;
	@FXML
	private Label labelFile;
	@FXML
	private Label labelShownFeature;
	@FXML
	private TextArea textAreaJson;
	@FXML
	private TextArea textAreaTestOutput;
	@FXML
	private ProgressBar progressBar;

	private EditorClass<SensorThingsService, Void, GeoJsonConverter> editor;

	private FileChooser fileChooser = new FileChooser();

	private ExecutorService executor = Executors.newFixedThreadPool(1);

	private FeatureCollection collection;

	private int shownFeature = 0;

	@FXML
	private void actionLoad(ActionEvent event) throws ConfigurationException {
		loadImporter();
	}

	@FXML
	private void actionOpen(ActionEvent event) throws ConfigurationException {
		loadGeoJson();
	}

	private void loadImporter() {
		JsonElement json = toJsonElement(loadFromFile("Load Importer", null));
		if (json == null) {
			return;
		}

		editor = new EditorClass<>(new SensorThingsService(), null, GeoJsonConverter.class);
		editor.setConfig(json);
		replaceEditor();
	}

	private void loadGeoJson() {
		textAreaJson.setText("Loading File...");
		String json = loadFromFile("Load GeoJson File", labelFile);
		if (json == null) {
			return;
		}
		textAreaJson.setText("Parsing File...");
		try {
			GeoJsonObject geoJsonObject = ObjectMapperFactory.get().readValue(json, GeoJsonObject.class);
			if (geoJsonObject instanceof FeatureCollection) {
				collection = (FeatureCollection) geoJsonObject;
				loadFeature(0);
			} else {
				textAreaJson.setText("File is not a FeatureCollection!");
			}
		} catch (JsonProcessingException ex) {
			textAreaJson.setText("File is not a FeatureCollection!");
		}
	}

	@FXML
	private void actionButtonNext(ActionEvent event) throws ConfigurationException {
		nextFeature();
	}

	@FXML
	private void actionButtonPrev(ActionEvent event) throws ConfigurationException {
		prevFeature();
	}

	private void nextFeature() {
		if (collection == null) {
			return;
		}
		shownFeature++;
		if (shownFeature >= collection.getFeatures().size()) {
			shownFeature = collection.getFeatures().size() - 1;
		}
		loadFeature(shownFeature);
	}

	private void prevFeature() {
		if (collection == null) {
			return;
		}
		shownFeature--;
		if (shownFeature < 0) {
			shownFeature = 0;
		}
		loadFeature(shownFeature);
	}

	private void loadFeature(int nr) {
		if (collection == null) {
			labelShownFeature.setText("0/0");
			return;
		}
		List<Feature> features = collection.getFeatures();
		if (features.isEmpty()) {
			buttonNext.setDisable(true);
			buttonPrev.setDisable(true);
			labelShownFeature.setText("0/0");
			return;
		}
		int count = features.size();
		labelShownFeature.setText("" + nr + "/" + count);
		buttonNext.setDisable(nr >= count);
		buttonPrev.setDisable(nr <= 0);
		Feature feature = features.get(nr);
		setInTextArea(feature);
		textAreaTestOutput.setText(generateTestOutput(feature));
	}

	private String loadFromFile(String title, Label label) {
		try {
			fileChooser.setTitle(title);
			File file = fileChooser.showOpenDialog(paneConfig.getScene().getWindow());
			if (file == null) {
				return null;
			}
			if (label != null) {
				label.setText(file.getAbsolutePath());
			}
			return FileUtils.readFileToString(file, "UTF-8");
		} catch (IOException ex) {
			LOGGER.error("Failed to read file", ex);
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("failed to read file");
			alert.setContentText(ex.getLocalizedMessage());
			alert.showAndWait();
		}
		return null;
	}

	private JsonElement toJsonElement(String string) {
		return JsonParser.parseString(string);
	}

	private void setInTextArea(Object object) {
		try {
			String json = ObjectMapperFactory.get()
					.configure(SerializationFeature.INDENT_OUTPUT, true)
					.writeValueAsString(object);
			textAreaJson.setText(json);
		} catch (JsonProcessingException ex) {
			textAreaJson.setText(ex.getMessage());
		}
	}

	private String generateTestOutput(Feature feature) {
		EditorClass<SensorThingsService, Object, GeoJsonConverter> importer = new EditorClass<>(new SensorThingsService(), null, GeoJsonConverter.class);
		importer.setConfig(editor.getConfig());
		try {
			return importer.getValue().generateTestOutput(feature);
		} catch (ConfigurationException ex) {
			LOGGER.error("Exception", ex);
			return "Failed to create importer: " + ex.getMessage();
		}

	}

	@FXML
	private void actionSave(ActionEvent event) {
		saveImporter();
	}

	private void saveImporter() {
		JsonElement json = editor.getConfig();
		saveToFile(json, "Save Importer");
	}

	private void saveToFile(JsonElement json, String title) {
		String config = new GsonBuilder().setPrettyPrinting().create().toJson(json);
		fileChooser.setTitle(title);
		File file = fileChooser.showSaveDialog(paneConfig.getScene().getWindow());

		try {
			FileUtils.writeStringToFile(file, config, "UTF-8");
		} catch (IOException ex) {
			LOGGER.error("Failed to write file.", ex);
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("failed to write file");
			alert.setContentText(ex.getLocalizedMessage());
			alert.showAndWait();
		}
	}

	@FXML
	private void actionImport(ActionEvent event) throws ConfigurationException {
		buttonImport.setDisable(true);

		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				updateProgress(0, 100);
				try {
					runImport(this::updateProgress);
				} catch (ConfigurationException | RuntimeException ex) {
					LOGGER.error("Failed to import.", ex);
				}
				updateProgress(100, 100);
				importDone();
				return null;
			}
		};
		progressBar.progressProperty().bind(task.progressProperty());
		executor.submit(task);
	}

	private void runImport(ProgressTracker tracker) throws ConfigurationException {
		if (collection == null) {
			return;
		}
		EditorClass<SensorThingsService, Object, GeoJsonConverter> importer = new EditorClass<>(new SensorThingsService(), null, GeoJsonConverter.class);
		importer.setConfig(editor.getConfig());
		importer.getValue().importAll(collection, tracker);
	}

	private void importDone() {
		buttonImport.setDisable(false);
	}

	private void replaceEditor() {
		paneConfig.setContent(editor.getGuiFactoryFx().getNode());
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {
		editor = new EditorClass<>(new SensorThingsService(), null, GeoJsonConverter.class);
		replaceEditor();
	}

	public void close() {
		executor.shutdown();
	}
}
