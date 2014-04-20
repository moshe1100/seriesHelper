/*
 * Copyright (c) 2011, 2014 Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package main;

import java.awt.Desktop;
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
import util.Constants;
import util.EpisodeData;
import util.FileUtil;
import util.HttpsClient;
import util.Util;

public class FXMLButtonController{
	private static final Color DEFAULT_ROW_FONT_COLOR = new Color(0.2,0.2,0.2,1);
	@FXML private TableView<Serie> tableView;
	@FXML private TableColumn<Serie, String> lastEpAiredColumn;
	@FXML private TableColumn<Serie, String> nameColumn;
	@FXML private TableColumn<Serie, String> nextEpAirDateColumn;
	@FXML private TableColumn<Serie, String> availableEpForDownloadColumn;
	@FXML private TableColumn<Serie, String> missingSubsEpColumn;

	@FXML private ComboBox<String> comboBox;

	private List<Serie> allData = null;
	
	@FXML private Text statusTime;
	@FXML private Text nextAiredSeries;
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

			private void handleUpcomingEpisodesStatus() {
				List<EpisodeData> nextEpisodesToBeAired = new LinkedList<>();
				// updating the next series to be aired
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
		}));  
		timeline.setCycleCount(Animation.INDEFINITE);  
		timeline.play(); 


	}

	private void initRowsActions() {
		// Adding "Go to Torec"
		missingSubsEpColumn.setCellFactory(
				new Callback<TableColumn<Serie, String>, TableCell<Serie, String>>() {  
					@Override
					public TableCell<Serie, String> call(TableColumn<Serie, String> col) {

						final TableCell<Serie, String> cell = new TableCell<>();
						cell.textProperty().bind(cell.itemProperty());
						cell.itemProperty().addListener(new ChangeListener<String>() {
							@Override
							public void changed(ObservableValue<? extends String> obs, String oldValue, String newValue) {
								if (newValue != null && !newValue.isEmpty()) {
									final ContextMenu cellMenu = new ContextMenu();
									final MenuItem openTorecMenuItem = new MenuItem("Go to Torec");
									openTorecMenuItem.setOnAction(new EventHandler<ActionEvent>(){
										@Override
										public void handle(ActionEvent event) {
											Serie serie = (Serie) cell.getTableRow().getItem();
											
											HttpsClient.openTorecSeriesLink(serie);
										}

									});
									cellMenu.getItems().add(openTorecMenuItem);

									// "Borrow" menu items from table's context menu,
									// if there is one.
									final ContextMenu tableRowMenu = cell.getTableRow().getContextMenu();
									if (tableRowMenu != null) {
										cellMenu.getItems().add(new SeparatorMenuItem());
										cellMenu.getItems().addAll(tableRowMenu.getItems());
									}

									cell.setContextMenu(cellMenu);
								} else {
									cell.setContextMenu(null);
								}
							} 
						});
						return cell;
					}
				});

		// Adding "Go to Episodes Guide"
		nextEpAirDateColumn.setCellFactory(
				new Callback<TableColumn<Serie, String>, TableCell<Serie, String>>() {  
					@Override
					public TableCell<Serie, String> call(TableColumn<Serie, String> col) {

						final TableCell<Serie, String> cell = new TableCell<>();
						cell.textProperty().bind(cell.itemProperty());
						cell.itemProperty().addListener(new ChangeListener<String>() {
							@Override
							public void changed(ObservableValue<? extends String> obs, String oldValue, String newValue) {
								if (newValue != null && !newValue.isEmpty()) {
									final ContextMenu cellMenu = new ContextMenu();
									final MenuItem openEpGuideMenuItem = new MenuItem("Go to Episodes Guide");
									openEpGuideMenuItem.setOnAction(new EventHandler<ActionEvent>(){
										@Override
										public void handle(ActionEvent event) {
											Serie serie = (Serie) cell.getTableRow().getItem();

											HttpsClient.openEpGuideLink(serie);
										}

									});
									cellMenu.getItems().add(openEpGuideMenuItem);

									// "Borrow" menu items from table's context menu,
									// if there is one.
									final ContextMenu tableRowMenu = cell.getTableRow().getContextMenu();
									if (tableRowMenu != null) {
										cellMenu.getItems().add(new SeparatorMenuItem());
										cellMenu.getItems().addAll(tableRowMenu.getItems());
									}

									cell.setContextMenu(cellMenu);
								} else {
									cell.setContextMenu(null);
								}
							} 
						});
						return cell;
					}
				});
		
		// Adding "Download Avaialble Episodes" and "Open in Browser"
		availableEpForDownloadColumn.setCellFactory(
				new Callback<TableColumn<Serie, String>, TableCell<Serie, String>>() {  
					@Override
					public TableCell<Serie, String> call(TableColumn<Serie, String> col) {

						final TableCell<Serie, String> cell = new TableCell<>();
						cell.textProperty().bind(cell.itemProperty());
						cell.itemProperty().addListener(new ChangeListener<String>() {
							@Override
							public void changed(ObservableValue<? extends String> obs, String oldValue, String newValue) {
								if (newValue != null && !newValue.isEmpty()) {
									final ContextMenu cellMenu = new ContextMenu();
									final MenuItem downloadMenuItem = new MenuItem("Download Available Episodes");
									final MenuItem browserMenuItem = new MenuItem("Open In Browser");
									downloadMenuItem.setOnAction(new EventHandler<ActionEvent>(){
										@Override
										public void handle(ActionEvent event) {
											performDownloadAction(cell);
										}

										private void performDownloadAction(final TableCell<Serie, String> cell) {
											Serie serie = (Serie) cell.getTableRow().getItem();
											List<EpisodeData> downloadEpisodesCandidates = serie.getDownloadEpisodesCandidates();
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
									});
									browserMenuItem.setOnAction(new EventHandler<ActionEvent>(){
										@Override
										public void handle(ActionEvent event) {
											Serie serie = (Serie) cell.getTableRow().getItem();
											List<EpisodeData> candidates = serie.getDownloadEpisodesCandidates();
											EpisodeData episodeData = candidates.get(candidates.size()-1);
											String pirateBaySearchLink = HttpsClient.makePirateBaySearchString(episodeData);
											HttpsClient.openURL(pirateBaySearchLink);
										}
									});									
									cellMenu.getItems().add(downloadMenuItem);
									cellMenu.getItems().add(browserMenuItem);

									final ContextMenu tableRowMenu = cell.getTableRow().getContextMenu();
									if (tableRowMenu != null) {
										cellMenu.getItems().add(new SeparatorMenuItem());
										cellMenu.getItems().addAll(tableRowMenu.getItems());
									}

									// "Borrow" menu items from table's context menu,
									// if there is one.
									cell.setContextMenu(cellMenu);
								} else {
									cell.setContextMenu(null);
								}
							} 
						});
						return cell;
					}
				});

		// Adding "Open Series Location"
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
				openItem.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						Serie item = row.getItem();
						String pathOnDisk = item.getLastEpisodeOnDiskPath();
						try {
							Desktop.getDesktop().open(new File(pathOnDisk));
						} catch (IOException e) {
							e.printStackTrace();
						}
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
				rowMenu.getItems().addAll(openItem, ignoreMissingSubsItem, markAsEndedItem);

				// only display context menu for non-null items:
				row.contextMenuProperty().bind(
						Bindings.when(Bindings.isNotNull(row.itemProperty()))
						.then(rowMenu)
						.otherwise((ContextMenu)null));
				return row;
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
					if (Constants.N_A.equals(t) && Constants.N_A.equals(other) ){
						return 0;
					}else if (Constants.N_A.equals(t)){						
						return isAscending ? 1 : -1;
					}else if (Constants.N_A.equals(other)){
						return isAscending ? -1 : 1;
					}
					SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.US);
					Date d1 = dateFormat.parse(t);                
					Date d2 = dateFormat.parse(other);
					return Long.compare(d1.getTime(),d2.getTime());
				}catch(ParseException p){
					p.printStackTrace();
				}
				return -1;

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
							if (serie.hasAvailableNewEpisode()){								
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
		List<File> seasonsList = Arrays.asList(serieFolder.listFiles());
		for (File file : seasonsList) {
			if (file.isDirectory()){
				fillEpisodesList(serieName, file, episodes, subtitleFileNames);
			}else if (FileUtil.isVideoFile(file)){
				EpisodeData epData = new EpisodeData(serieName, file);
				if (epData.getEpisode() > 0){
					episodes.add(epData);
				}
			}else if (FileUtil.isSubtitlesFile(file)){
				// remove suffix
				subtitleFileNames.add(FileUtil.removeFileSuffix(file.getName(), ".HEB").toLowerCase());
			}
		}
	}


}
