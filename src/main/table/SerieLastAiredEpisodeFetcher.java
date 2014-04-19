package main.table;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import main.properties.AppConfigurations;
import util.EpisodeData;
import util.FileUtil;
import util.HttpsClient;
import util.Util;
import util.json.EpisodeGuideJSON;
import util.json.Item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SerieLastAiredEpisodeFetcher implements Runnable {
	
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MMM/yy", Locale.US);
	
	private Serie serie;

	public SerieLastAiredEpisodeFetcher(Serie serie){
		this.serie = serie;
	}

	@Override
	public void run() {
		String name = serie.getName();
		
		if (serie.isEnded()){
			return; // no need to check for new episodes
		}
			
		try {
			String link = getEpisodeGuideLink(name);
			
			String readFromUrl = FileUtil.readFromUrl(link);
			// Getting only the texts between "<div id="eplist">" and "</div>"
			int indexOf = readFromUrl.indexOf("<div id=\"eplist\">");
			int endIndex = readFromUrl.indexOf("</div>", indexOf);
			readFromUrl = readFromUrl.substring(indexOf, endIndex);
			
			// Now starting after the row that has "Season 1"
			indexOf = readFromUrl.indexOf("Season 1");
			readFromUrl = readFromUrl.substring(indexOf + 8);
			
			// Getting all lines that starts with a number
			BufferedReader reader = Util.getStringAsBufferReader(readFromUrl);
			String line;
			while((line = reader.readLine()) != null){
				if (line.trim().length() == 0){
					continue;
				}
				dateFormat.format(new Date());
				// Ignore whitespace
				String[] split = line.split("\\s+");
				if ( Util.isNumeric(split[0])){
					String seasonAndEpisode = split[1]; // i.e. 2-01
					int separatorIndex = seasonAndEpisode.indexOf("-");
					int season = Integer.parseInt(seasonAndEpisode.substring(0, separatorIndex));
					int epNumber = Integer.parseInt(seasonAndEpisode.substring(separatorIndex+1));
					String airDateStr = split[3].contains("/") ? split[3] : split[2];
					if (!Util.isDigit(airDateStr.charAt(0)) ){ // probably no day (i.e. Sep/14) - adding "01/"
						airDateStr = "01/" + airDateStr;
					}
					Date airDate = dateFormat.parse(airDateStr); // skip production number					
					serie.addEpisode(season, epNumber, airDate);
				}
			}
			
			serie.calculateLastAired();
			
			// getting the available episodes for download (torrents)
			checkForAvaialbleTorents(serie);
			
		} catch (ConnectException e){
			// try again
			System.out.println("Timeout for serie: " + name + ", trying again");
			run();
		
		} catch (Exception e) {
			System.out.println("Error for serie: " + name);
			e.printStackTrace();
			serie.setLastEpAired(e.getMessage());
		}
		
	}

	private void checkForAvaialbleTorents(Serie serie) {
		if (!serie.hasAvailableNewEpisode()){
			return;
		}
		
		List<EpisodeData> candidates = serie.getDownloadEpisodesCandidates();
		for (EpisodeData episodeData : candidates) {			
			String pirateBaySearchContent = HttpsClient.getPirateBaySearchContent(episodeData);
			String magnetLink = getFirstMagnetLink(pirateBaySearchContent);
			episodeData.setMagnetLink(magnetLink);
		}
		
		serie.updateAvailableEpisodesForDownload();
		
	}

	private String getFirstMagnetLink(String pirateBaySearchContent) {		
		int indexOf = pirateBaySearchContent.indexOf("<div class=\"detName\">");
		String link = null;
		if (indexOf != -1){
			pirateBaySearchContent = pirateBaySearchContent.substring(indexOf);
			indexOf = pirateBaySearchContent.indexOf("magnet");
			if (indexOf != -1){
				int endIndex = pirateBaySearchContent.indexOf("\"", indexOf);
				link = pirateBaySearchContent.substring(indexOf, endIndex).trim();
			}
		}
		return link;
	}

	private String getEpisodeGuideLink(String serieName) {
		String link = AppConfigurations.getInstance().getSerieProperty(serieName, AppConfigurations.EP_GUIDE_LINK_PROPERTY);
		if (link != null){
			return link;
		}
		// Getting the link for this serie in episode guide by searching the site using google
		String readFromUrl = HttpsClient.getEpisodesGuideGoogleSearchContent(serieName);
		
		BufferedReader reader = new BufferedReader(new StringReader(readFromUrl));
		Gson gson=new GsonBuilder().create();
		
		//Get the root object for the response
		EpisodeGuideJSON ex = gson.fromJson(reader, EpisodeGuideJSON.class);
		Item firstResult = ex.getItems().iterator().next();
		link = firstResult.getLink();
		AppConfigurations.getInstance().setSerieProperty(serieName, AppConfigurations.EP_GUIDE_LINK_PROPERTY, link);
		return link;
	}

}
