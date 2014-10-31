package main;

import java.util.Arrays;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import main.table.Serie;
import util.EpisodeData;

public class LastEpisodeWatchedController {

	@FXML 
	private ComboBox<Integer> seasonBox;
	@FXML 
	private ComboBox<Integer> episodeBox;
	
	@FXML
	private Button cancelButton;
	@FXML
	private Button okButton;
	
	private Serie series;
	
	@FXML public void initialize(){
		seasonBox.getItems().addAll(Arrays.asList(1,2,3,4,5,6,7,8,9,10,11,12,13));
		episodeBox.getItems().addAll(Arrays.asList(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28));
	}

	public void init(Serie series) {
		this.series = series;
		EpisodeData epData = series.getLastEpisodeWatched() == null ? series.getLastEpOnDisk() : series.getLastEpisodeWatched();
		if (epData != null) {
			if (epData.getSeason() > 0) {				
				seasonBox.setValue(epData.getSeason());
			}
			if (epData.getEpisode() > 0) {				
				episodeBox.setValue(epData.getEpisode());
			}
		}
	}
	
	@FXML protected void doOK(ActionEvent event) {		
		if (series != null) {
			series.setLastEpisodeWatched(new EpisodeData(series.getName(), seasonBox.getValue(), episodeBox.getValue()));
		}
		Stage stage = (Stage) okButton.getScene().getWindow();
	    stage.close();
	}
	
	@FXML protected void doCancel(ActionEvent event) {		
		// get a handle to the stage
	    Stage stage = (Stage) cancelButton.getScene().getWindow();
	    stage.close();
	}
}
