package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.scene.Cursor;
import javafx.scene.web.WebEngine;
import main.Main;
import main.properties.AppConfigurations;
import main.table.Serie;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLInputElement;

import util.HttpsClient.TorecStage;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class TorecWebEngineListener implements ChangeListener<State> {
		private final WebEngine webEngine;
		private TorecStage torecStage;
		private Serie serie;

		public TorecWebEngineListener(WebEngine webEngine, TorecStage torecStage, Serie serie) {
			this.webEngine = webEngine;
			this.torecStage = torecStage;
			this.serie = serie;
		}

		@SuppressWarnings("rawtypes")
		public void changed(ObservableValue ov, State oldState, State newState) {							
			if (newState == State.SUCCEEDED) {				
				if (torecStage == TorecStage.DONE){
					return;
				}
				Document doc = webEngine.getDocument();
				if (torecStage == TorecStage.SEARCH_PAGE){		
					handleSearchPage(doc);
				}else {					
					StringWriter stringOut = extractHtmlContent(doc);
					
					if (torecStage == TorecStage.SEARCH_RESULTS_PAGE){									
						handleSearchResultPage(stringOut);
					} else if (torecStage == TorecStage.SERIES_PAGE){
						handleSeriesPage(stringOut);
					} else if (torecStage == TorecStage.DONE){								
						handleDone();
					}
				}
				
			} else if (newState == State.FAILED){
				Platform.runLater(new Runnable() {
				    @Override
				    public void run() {
				    	Main.mainStage.getScene().setCursor(Cursor.DEFAULT);
				    }
				});
			}
		}

		private void handleDone() {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					Main.mainStage.getScene().setCursor(Cursor.DEFAULT);
				}
			});
		}

		private void handleSeriesPage(StringWriter stringOut) {
			
			String torecSerieId = AppConfigurations.getInstance().getSerieProperty(serie.getName(), AppConfigurations.TOREC_SERIES_PROPERTY);
			if (serie.getMissingSubsEpisodesList().size() > 3){
				// just open the series page instead of opening for each
				if (torecSerieId != null){
					torecStage = TorecStage.DONE;
					HttpsClient.openURL(HttpsClient.torecSerieLink + torecSerieId);
					return;
				}
			}
			
			Map<Integer, Map<Integer, String>> episodeToLinkMap = new HashMap<>();
			// creating a map from each Season to a map of episode -> Link
			String htmlContent = stringOut.toString();
			int seasonTableIndex = htmlContent.indexOf("season_table");
			int season = 0;
			if (seasonTableIndex == -1){
				System.out.println("Warning - didn't find episodes in series torec page");
			} else{				
				while (seasonTableIndex != -1){
					season++;			     
					Map<Integer, String> episodesMap = new HashMap<>();
					htmlContent = htmlContent.substring(seasonTableIndex);
					int seasonEndIndex = htmlContent.indexOf("button_season_download");
					String seasonContent = htmlContent.substring(0, seasonEndIndex);
					BufferedReader reader = Util.getStringAsBufferReader(seasonContent);
					String line;
					try {
						while((line = reader.readLine())!=null){
							if (line.contains("href")){
								String[] split = line.split("\"");
								int episodeNumber = -1;
								String episodeLink = HttpsClient.torecDomain;
								for (String splitLine : split) {
									if (splitLine.contains("sub_id")){
										episodeLink += splitLine;
									}else if (splitLine.contains("���")){
										episodeNumber = Util.getOnlyDigitCharsAsNumber(splitLine);
									}
								}
								if (episodeNumber > 0){
									episodesMap.put(episodeNumber, episodeLink);
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					episodeToLinkMap.put(season, episodesMap);
					// next season
					seasonTableIndex = htmlContent.indexOf("season_table", seasonEndIndex);
				}
				
				boolean atleastOneEpisodeFound = false;
				List<EpisodeData> missingSubsEpisodesList = serie.getMissingSubsEpisodesList();
				for (EpisodeData episodeData : missingSubsEpisodesList) {
					Map<Integer, String> episodesLinksMap = episodeToLinkMap.get(episodeData.getSeason());
					if (episodesLinksMap != null && episodesLinksMap.containsKey(episodeData.getEpisode())){
						atleastOneEpisodeFound = true;
						HttpsClient.openURL(episodesLinksMap.get(episodeData.getEpisode()));
					}
				}
				if (!atleastOneEpisodeFound){
					HttpsClient.openURL(HttpsClient.torecSerieLink + torecSerieId);
				}
			}
			
			
			torecStage = TorecStage.DONE;
			handleDone();
		}

		private void handleSearchPage(Document doc) {
			// First request - sending the search value
			NodeList elementsByTagName = doc.getElementsByTagName("INPUT");
			HTMLInputElement item = (HTMLInputElement) elementsByTagName.item(0);
			item.setValue(serie.getName());
			webEngine.executeScript("$('#srchForm').submit();");
			torecStage = TorecStage.DONE;
		}

		private void handleSearchResultPage(StringWriter stringOut) {
			// Getting the series Id in Torec
			String htmlContent = stringOut.toString();
			int indexOf = htmlContent.indexOf("series_id=");
			
			String link = HttpsClient.torecSearchLink;
			if (indexOf != -1){
				torecStage = TorecStage.SERIES_PAGE;
				htmlContent = htmlContent.substring(indexOf + 10);
				int endIndex = htmlContent.indexOf("\">");
				String seriesId = htmlContent.substring(0, endIndex);
				link = HttpsClient.torecSerieLink + seriesId;
				
				// Saving for future
				AppConfigurations.getInstance().setSerieProperty(serie.getName(), AppConfigurations.TOREC_SERIES_PROPERTY, seriesId);
				
				webEngine.load(link);
				
			}
		}

		private StringWriter extractHtmlContent(Document doc) {
			//Serialize DOM
			OutputFormat format    = new OutputFormat (doc); 
			// as a String
			StringWriter stringOut = new StringWriter ();    
			XMLSerializer serial   = new XMLSerializer (stringOut, format);
			try {
				serial.serialize(doc);
			} catch (IOException e) {
				e.printStackTrace();
			}
			// Display the XML		                        
			//System.out.println(stringOut.toString());
			return stringOut;
		}
	}