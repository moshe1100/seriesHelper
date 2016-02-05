package main;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.log4j.Logger;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import main.properties.AppConfigurations;
import main.table.Serie;
import main.table.SerieLastAiredEpisodeFetcher;
import util.EpisodeData;
import util.FileUtil;
import util.HttpsClient;
import util.Util;

public class FXMLButtonController{
	@SuppressWarnings("unused")
	private static Logger log = Logger.getLogger(FXMLButtonController.class);
	
	private static final Color DEFAULT_ROW_FONT_COLOR = new Color(0.2,0.2,0.2,1);
	@FXML private TableView<Serie> tableView;
	@FXML private TableColumn<Serie, String> lastEpAiredColumn;
	@FXML private TableColumn<Serie, String> nameColumn;
	@FXML private TableColumn<Serie, String> nextEpAirDateColumn;
	@FXML private TableColumn<Serie, String> availableEpForDownloadColumn;

	@FXML private ComboBox<String> comboBox;
	@FXML private HBox filterHBox;

	private List<Serie> allData = null;
	
	@FXML private Text statusTime;
	@FXML private Text nextAiredSeries;
	private String overrideStatusText = null;
	@FXML private HBox statusBar;
	
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US);
	
	@FXML public void initialize(){
		initCellRenderers();

		initDefaultSort();

		initColumnsComparators();

		// automatically adjust width of columns depending on their content
		tableView.setColumnResizePolicy((param) -> true );

		initRowsActions();
		
		// No extra column 
		tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

		// init table data
		initTable(true);

		// Showing "Not Ended" shows by default
		comboBox.getSelectionModel().select("Not Ended");
		initStatusBar();

	}
	
	public void overrideStatusText(String text){
		overrideStatusText = text;
		if (text == null){
			handleUpcomingEpisodesStatus();
		}else{			
			nextAiredSeries.setText(text);
		}
	}

	private void initStatusBar() {

		statusBar.setStyle("-fx-background-color: lightgray");

		// Setting the timer for the clock
		final Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), new EventHandler<ActionEvent>() {  
			@Override  
			public void handle(ActionEvent event) {  
				final Calendar cal = Calendar.getInstance();  
				statusTime.setText(dateFormat.format(cal.getTime()));
				
				if (nextAiredSeries.getText().isEmpty() || cal.get(Calendar.SECOND) == 30){ // every minute or first time
					handleUpcomingEpisodesStatus();
				}
			}

		}));  
		timeline.setCycleCount(Animation.INDEFINITE);  
		timeline.play(); 


	}
	
	private void handleUpcomingEpisodesStatus() {
		if (overrideStatusText != null){
			nextAiredSeries.setText(overrideStatusText);
			return;
		}
		List<EpisodeData> nextEpisodesToBeAired = new LinkedList<>();
		// updating the next series to be aired
		if (allData != null) {			
			for (Serie serie : allData){
				EpisodeData nextEpisodeToBeAiredForSerie = serie.getNextEpisodeToBeAired();
				if (nextEpisodeToBeAiredForSerie != null){
					if (nextEpisodesToBeAired.isEmpty()){
						nextEpisodesToBeAired.add(nextEpisodeToBeAiredForSerie);
					} else {
						EpisodeData first = nextEpisodesToBeAired.iterator().next();
						if (first.getAirDate().compareTo(nextEpisodeToBeAiredForSerie.getAirDate()) == 0){
							// same date - add it to list
							nextEpisodesToBeAired.add(nextEpisodeToBeAiredForSerie);
						}else if (first.getAirDate().after(nextEpisodeToBeAiredForSerie.getAirDate())){
							// clear the list - found earlier 
							nextEpisodesToBeAired.clear();
							nextEpisodesToBeAired.add(nextEpisodeToBeAiredForSerie);
						}
					}
				}
			}
		}
		if (!nextEpisodesToBeAired.isEmpty()){	
			EpisodeData first = nextEpisodesToBeAired.iterator().next();
			SimpleDateFormat format = new SimpleDateFormat("dd/MM/yy", Locale.US);
			String nextToBeAiredStatus = "Upcoming episodes (" + format.format(first.getAirDate()) + "): ";
			for (EpisodeData episodeData : nextEpisodesToBeAired) {
				nextToBeAiredStatus += episodeData.getSerieName() + " (" + episodeData.toString() + "), ";
			}
			nextToBeAiredStatus = nextToBeAiredStatus.substring(0, nextToBeAiredStatus.length()-2);
			nextAiredSeries.setText(nextToBeAiredStatus);
		}
	}  

	private void initRowsActions() {

		// Adding Actions
		tableView.setRowFactory(new Callback<TableView<Serie>, TableRow<Serie>>() {
			@Override
			public TableRow<Serie> call(TableView<Serie> tableView) {
				final TableRow<Serie> row = new TableRow<Serie>(){
					@Override
					protected void updateItem(Serie serie, boolean empty) {
						super.updateItem(serie, empty);						
						this.getStyleClass().remove("endedSeriesRow");
						if (!empty && serie.isEnded()){
							this.getStyleClass().add("endedSeriesRow");
						}
					}
				};
				final ContextMenu rowMenu = new ContextMenu();
				MenuItem openItem = new MenuItem("Open Series Location");
				MenuItem ignoreMissingSubsItem = new MenuItem("Ignore Missing Subs");
				MenuItem markAsEndedItem = new MenuItem("Mark/Unmark as Ended");
				MenuItem setLastEpWatchedItem = new MenuItem("Set Last Episode Watched");
				
				// Adding "Go to Torec"
				addGoToTorecAction(row, rowMenu);
				// Adding "Go to Episodes Guide"
				addGoToEpGuideAction(row, rowMenu);
				// Adding "Download Available Episodes" and "Open in Browser"
				addDownloadActions(row, rowMenu);
				
				openItem.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						Serie item = row.getItem();
						item.openLastEpisodeLocationOnDisk();
					}
				});
				ignoreMissingSubsItem.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						Serie item = row.getItem();
						item.setMissingSubsEpisodes("");
						AppConfigurations.getInstance().setSerieProperty(item.getName(), AppConfigurations.IGNORE_MISSING_SUBS_SERIES_PROPERTY, "true");
					}
				});
				markAsEndedItem.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						Serie item = row.getItem();
						item.setSeriesEnded(!item.isEnded());
						handleComboBoxAction();
					}
				});
				rowMenu.getItems().addAll(openItem, ignoreMissingSubsItem, markAsEndedItem, setLastEpWatchedItem);

				// only display context menu for non-null items:
				row.contextMenuProperty().bind(  
                        Bindings.when(row.emptyProperty())  
                        .then((ContextMenu)null)  
                        .otherwise(rowMenu)  
                ); 
				return row;
			}

			private void addGoToTorecAction(final TableRow<Serie> row,
					final ContextMenu rowMenu) {
				final MenuItem openTorecMenuItem = new MenuItem("Go to Torec");
				openTorecMenuItem.setOnAction(new EventHandler<ActionEvent>(){
					@Override
					public void handle(ActionEvent event) {
						Serie serie = row.getItem();
						
						HttpsClient.openTorecSeriesLink(serie, true);
					}
					
				});
				rowMenu.getItems().add(openTorecMenuItem);
				rowMenu.getItems().add(new SeparatorMenuItem());
			}
			
			private void addGoToEpGuideAction(final TableRow<Serie> row, final ContextMenu rowMenu) {
				final MenuItem openEpGuideMenuItem = new MenuItem("Go to Episodes Guide");
				openEpGuideMenuItem.setOnAction(new EventHandler<ActionEvent>(){
					@Override
					public void handle(ActionEvent event) {
						Serie serie = row.getItem();
						
						HttpsClient.openEpGuideLink(serie);
					}
					
				});
				rowMenu.getItems().add(openEpGuideMenuItem);
				rowMenu.getItems().add(new SeparatorMenuItem());
			}
			
			private void addDownloadActions(final TableRow<Serie> row, final ContextMenu rowMenu) {
				final MenuItem downloadMenuItem = new MenuItem("Download Available Episodes");
				final MenuItem browserMenuItem = new MenuItem("Open In Browser");
				downloadMenuItem.setOnAction(new EventHandler<ActionEvent>(){
					@Override
					public void handle(ActionEvent event) {
						Serie serie = row.getItem();
						downloadAllSeriesCandidates(serie);
					}
				});
				browserMenuItem.setOnAction(new EventHandler<ActionEvent>(){
					@Override
					public void handle(ActionEvent event) {
						Serie serie = row.getItem();
						List<EpisodeData> candidates = serie.getDownloadEpisodesCandidates();
						EpisodeData episodeData = null;
						if (candidates.isEmpty()) {
							episodeData = serie.getNextEpisodeToBeAired();
						} else {											
							episodeData = candidates.get(candidates.size()-1);
						}
						if (episodeData != null) {											
							String pirateBaySearchLink = HttpsClient.makePirateBaySearchString(episodeData);
							HttpsClient.openURL(pirateBaySearchLink);
						}
					}
				});									
				rowMenu.getItems().add(downloadMenuItem);
				rowMenu.getItems().add(browserMenuItem);
				rowMenu.getItems().add(new SeparatorMenuItem());
			}
		});
	}

	private void initColumnsComparators() {
		// override comparator for date column
		nextEpAirDateColumn.setComparator(new Comparator<String>() {

			@Override 
			public int compare(String t, String other) {
				try{
					boolean isAscending = nextEpAirDateColumn.getSortType().equals(SortType.ASCENDING);
					if (isDate(t) && isDate(other)){
						// both dates
						SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.US);
						Date d1 = dateFormat.parse(t);                
						Date d2 = dateFormat.parse(other);
						return Long.compare(d1.getTime(),d2.getTime());
					}
					if (isDate(t)){
						return isAscending ? -1 : 1;
					}
					if (isDate(other)){
						return isAscending ? 1 : -1;
					}
					return t.compareTo(other);
				}catch(ParseException p){
					p.printStackTrace();
				}
				return -1;

			}
			
			private boolean isDate(String str){
				int indexOf = str.indexOf("/");
				if (indexOf != -1){
					// date should have 2 "/"
					return str.indexOf("/", indexOf+1) != -1;
				}
				return false;
			}

		});
	}

	private void initDefaultSort() {
		// Sorting by name by default
		tableView.getSortOrder().clear();
		
		String sortOrder = AppConfigurations.getInstance().getGeneralProperty(AppConfigurations.TABLE_SORT_ORDER);
		if (Util.isEmpty(sortOrder)){			
			tableView.getSortOrder().add(nameColumn);
		}else{
			String[] split = sortOrder.split(",");
			for (String columnName : split) {
				TableColumn<Serie, ?> column = getColumnByName(tableView, columnName);
				if (column != null){
					tableView.getSortOrder().add(column);
				}
			}
		}
		
		// Saving the sort in file
		tableView.getItems().addListener(new ListChangeListener<Serie>(){
			private List<String> lastSortColumnsOrder;

			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends Serie> paramChange) {
				ObservableList<TableColumn<Serie, ?>> sortOrder = tableView.getSortOrder();
				if (sortOrder.size() == 0){
					return; // do nothing if no sort
				}
				List<String> columnsSortOrder = new LinkedList<>();
				Iterator<TableColumn<Serie, ?>> iterator = sortOrder.iterator();
				while (iterator.hasNext()){
					TableColumn<Serie, ?> next = iterator.next();
					columnsSortOrder.add(next.getText());
				}
				if (lastSortColumnsOrder == null || !Arrays.deepEquals(lastSortColumnsOrder.toArray(), columnsSortOrder.toArray()) ){
					lastSortColumnsOrder = columnsSortOrder;
					AppConfigurations.getInstance().setGeneralProperty(AppConfigurations.TABLE_SORT_ORDER, Util.getCommaSeparated(lastSortColumnsOrder));
				}
			}
		});		
		
	}

	private TableColumn<Serie, ?> getColumnByName(TableView<Serie> tableView, String columnName) {
		if (columnName != null){
			for (TableColumn<Serie, ?> tableColumn : tableView.getColumns()) {
				if (columnName.equals(tableColumn.getText())){
					return tableColumn;
				}
			}
		}
		return null;
	}

	private void initCellRenderers() {
		// paint in blue if series has available new episode
		lastEpAiredColumn.setCellFactory(new Callback<TableColumn<Serie, String>, TableCell<Serie, String>>() {

			@Override
			public TableCell<Serie, String> call(TableColumn<Serie, String> p) {
				return new TableCell<Serie, String>() {

					@Override
					public void updateItem(String item, boolean empty) {
						super.updateItem(item, empty);
						if (!isEmpty()) {
							Serie serie = (Serie) this.getTableRow().getItem();
							if (serie != null && serie.hasAvailableNewEpisode()){								
								this.setTextFill(Color.BLUE);
							}else {
								this.setTextFill(DEFAULT_ROW_FONT_COLOR);
							}
							setText(item);
						}else{
							setText("");
						}
					}
				};

			}
		});
	}

	@FXML protected void handleSubmitButtonAction(ActionEvent event) {
		initTable(false);
	}
	
	@FXML protected void handleCloseButtonAction(ActionEvent event) {
		System.exit(0);
	}
	
	@FXML protected void handleRefreshButtonAction(ActionEvent event) {		
		initTable(true);
		handleComboBoxAction();
	}
	
	@FXML protected void handleDownloadAllButtonAction(ActionEvent event) {		
		ObservableList<Serie> items = tableView.getItems();
		for (Serie serie : items) {
			downloadAllSeriesCandidates(serie);
		}
	}
	
	@FXML protected void handleDownloadAvailableSubsButton(ActionEvent event) {		
		ObservableList<Serie> items = tableView.getItems();
		for (Serie serie : items) {
			HttpsClient.openTorecSeriesLink(serie, false);
		}
	}
	
	@FXML protected void handleReaarageFoldersButton(ActionEvent evnt) {
		ObservableList<Serie> items = tableView.getItems();
		for (Serie serie : items) {
			serie.rearrangeFoldersAndFilenames();			
		}
	}

	@FXML protected void handleAboutButtonAction(ActionEvent event) {
		Parent root;
		try {
			root = FXMLLoader.load(getClass().getResource("about.fxml"));
			Stage stage = new Stage();
			stage.setResizable(false);
			stage.setTitle("About");
			stage.setScene(new Scene(root));
			stage.show();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@FXML
	private void handleComboBoxAction() {
		String selectedItem = comboBox.getSelectionModel().getSelectedItem();
		if (allData == null){
			ObservableList<Serie> items = tableView.getItems();
			// saving original data
			allData = new LinkedList<>();
			for (Serie serie : items) {
				allData.add(serie);
			}
		}
		
		if ("All".equals(selectedItem)){
			tableView.getItems().clear();
			tableView.getItems().setAll(allData);
		} else if ("Not Ended".equals(selectedItem)){

			List<Serie> filteredItems = new LinkedList<>();
			for (Serie serie : allData) {
				if (!serie.isEnded()){
					filteredItems.add(serie);
				}
			}

			tableView.getItems().clear();
			tableView.getItems().setAll(filteredItems);
		} else if ("Has Available".equals(selectedItem)){
			List<Serie> filteredItems = new LinkedList<>();
			for (Serie serie : allData) {
				if (!serie.availableEpForDownloadProperty().get().isEmpty()){
					filteredItems.add(serie);
				}
			}

			tableView.getItems().clear();
			tableView.getItems().setAll(filteredItems);
		}

	}

	private void initTable(boolean silent) {
		allData =  null;
		List<File> folders = FileUtil.getAllSeriesFolders(silent);
		if (folders == null){
			return;
		}

		ObservableList<Serie> data = tableView.getItems();
		data.clear();
		for (File file : folders) {   
			List<EpisodeData> serieEpisodesOnDisk = getSerieEpisodesOnDisk(file);
			Serie serie = new Serie(file.getName(), serieEpisodesOnDisk, file.getPath());
			data.add(serie);
			
			// resort after column values updated
			serie.nextEpAirDateProperty().addListener(new ChangeListener<String>(){

				@Override
				public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
					Platform.runLater(new Runnable() {
				        @Override
				        public void run() {
				        	List<TableColumn<Serie, ?>> sortOrder = new ArrayList<>(tableView.getSortOrder());
				        	tableView.getSortOrder().clear();
				        	tableView.getSortOrder().addAll(sortOrder);
				        }
				   });
				}
				
			});

			
			serie.updateMissingSubs();
		}

		setLastAiredEpisode(data);
		
		

	}

	private void setLastAiredEpisode(ObservableList<Serie> data) {
		ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(15, 
				new ThreadFactory() {
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setDaemon(true);
				return t;
			}
		});
		for (Serie serie : data) {
			newFixedThreadPool.execute(new SerieLastAiredEpisodeFetcher(serie));
		}


	}

	private List<EpisodeData> getSerieEpisodesOnDisk(File serieFolder) {
		List<EpisodeData> episodes = new ArrayList<>();
		Set<String> subtitleFileNames = new HashSet<>();
		fillEpisodesList(serieFolder.getName(), serieFolder, episodes, subtitleFileNames);

		if (episodes.size() > 0){			
			Collections.sort(episodes);
		}
		// Checking if episode has subtitle (same name of file)
		String serieProperty = AppConfigurations.getInstance().getSerieProperty(serieFolder.getName(), AppConfigurations.IGNORE_MISSING_SUBS_SERIES_PROPERTY);
		boolean ignoreMissingSubs = serieProperty != null && Boolean.valueOf(serieProperty);
		for (EpisodeData epOnDisk : episodes) {
			if (ignoreMissingSubs || subtitleFileNames.contains(epOnDisk.getFileNameWithoutSuffix().toLowerCase())){
				epOnDisk.setHasSubtitle(true);
			}
		}
		return episodes;

	}

	private void fillEpisodesList(String serieName, File serieFolder, List<EpisodeData> episodes, Set<String> subtitleFileNames) {
		log.info("Init Serie episodes from disk for: " + serieName);
		List<File> seasonsList = Arrays.asList(serieFolder.listFiles());
		for (File file : seasonsList) {
			if (file.isDirectory()){
				fillEpisodesList(serieName, file, episodes, subtitleFileNames);
			}else if (FileUtil.isVideoFile(file) && isFileSizeInMBAtLeast(file, 50)){
				EpisodeData epData = new EpisodeData(serieName, file);
				if (epData.getEpisode() > 0){
					episodes.add(epData);
				}
			}else if (FileUtil.isSubtitlesFile(file)){
				// remove suffix
				subtitleFileNames.add(FileUtil.removeFileSuffix(file.getName(), ".HEB", ".he").toLowerCase());
			}
		}
	}

	// Returns true if this file size is at least the given size in MB
	// used to skip the sample files
	private boolean isFileSizeInMBAtLeast(File file, int desiredSizeInMB) {
		// Get the number of bytes in the file
		long sizeInBytes = file.length();
		//transform in MB
		long sizeInMb = sizeInBytes / (1024 * 1024);
		return sizeInMb > desiredSizeInMB;
	}

	private void downloadAllSeriesCandidates(Serie serie) {		
		List<EpisodeData> downloadEpisodesCandidates = serie.getDownloadEpisodesCandidates();
		if (downloadEpisodesCandidates.isEmpty()) {
			return;
		}
		String appPath = AppConfigurations.getInstance().getGeneralProperty(AppConfigurations.MAGNET_LINK_APPLICATION_SERIES_PROPERTY);
		if (appPath == null){
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Locate Bittorrent Application");
			File showOpenDialog = fileChooser.showOpenDialog(Main.mainStage);
			if (showOpenDialog == null){
				return;
			}
			appPath = showOpenDialog.getPath();
			AppConfigurations.getInstance().setGeneralProperty(AppConfigurations.MAGNET_LINK_APPLICATION_SERIES_PROPERTY, appPath);
		}
		for (EpisodeData episodeData : downloadEpisodesCandidates) {
			String[] command = {appPath, "\"" + episodeData.getMagnetLink() + "\""};
			try {
				Runtime.getRuntime().exec(command);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


}
