package main.table;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import main.properties.AppConfigurations;
import util.Constants;
import util.EpisodeData;
import util.FileUtil;
import util.HttpsClient;
import util.Util;
import util.json.EpisodeGuideJSON;
import util.json.Item;

public class SerieLastAiredEpisodeFetcher implements Runnable {
	private static Logger log = Logger.getLogger(SerieLastAiredEpisodeFetcher.class);
	
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
			if (link == null) {
				return;
			}
			
			String readFromUrl = FileUtil.readFromUrl(link);
			
			boolean ended = false;
			// checking if show has ended
			int statusIndex = readFromUrl.indexOf("class='status'>");
			if (statusIndex != -1){
				int statusEndIndex = readFromUrl.indexOf("</span>", statusIndex);
				String status = readFromUrl.substring(statusIndex + "class='status'>".length(), statusEndIndex);
				if (status.toLowerCase().contains("ended")){
					ended = true;
				}
			}
			
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
					int dateStartIndex = 3;
					String airDateStr = split[dateStartIndex].contains("/") ? split[dateStartIndex] /*3*/: split[--dateStartIndex] /*2*/;
					if (!Util.isDigit(airDateStr.charAt(0)) ){ // probably no day (i.e. Sep/14) - adding "01/"
						airDateStr = "01/" + airDateStr;
					} else if (!airDateStr.contains("/")) { // probably date is splitted to 3 cells i.e. 24 Mar 08
						airDateStr = airDateStr + "/" + split[dateStartIndex+1] + "/" + split[dateStartIndex+2];
					}
					Date airDate = null;
					try{
						airDate = dateFormat.parse(airDateStr); // skip production number											
					}catch (Exception ex){
						airDate = null;
					}
					serie.addEpisode(season, epNumber, airDate);
				}
			}
			
			serie.calculateLastAired();
			if (ended){				
				serie.setNextAirDate(Constants.ENDED);
			}
			
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
			if (pirateBaySearchContent == null) {
				continue;
			}
			String magnetLink = HttpsClient.getFirstMagnetLink(pirateBaySearchContent);
			episodeData.setMagnetLink(magnetLink);
		}
		
		serie.updateAvailableEpisodesForDownload();
		
	}

	private String getEpisodeGuideLink(String serieName) {
		String link = AppConfigurations.getInstance().getSerieProperty(serieName, AppConfigurations.EP_GUIDE_LINK_PROPERTY);
		if (link != null){
			return link;
		}
		// Getting the link for this serie in episode guide by searching the site using google
		String readFromUrl = HttpsClient.getEpisodesGuideGoogleSearchContent(serieName);

		if (readFromUrl == null) {
			return null;
		}
		log.info("Read from EpGuide Search: " + readFromUrl);
		 
		
		BufferedReader reader = new BufferedReader(new StringReader(readFromUrl));
		Gson gson=new GsonBuilder().create();
		
		//Get the root object for the response
		EpisodeGuideJSON ex = gson.fromJson(reader, EpisodeGuideJSON.class);
		Item firstResult = ex.getItems().isEmpty() ? null : ex.getItems().iterator().next();
		if (firstResult != null) {			
			link = firstResult.getLink();
			AppConfigurations.getInstance().setSerieProperty(serieName, AppConfigurations.EP_GUIDE_LINK_PROPERTY, link);
		}
		return link;
	}

}
