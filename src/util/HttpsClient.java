package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.GZIPInputStream;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import main.Main;
import main.properties.AppConfigurations;
import main.table.Serie;

import org.apache.log4j.Logger;

public class HttpsClient {
	
	private static Logger log = Logger.getLogger(HttpsClient.class);

	private final static String apiKey = "AIzaSyBB8nPkgUthwwwlTdTX5YCWr7EMHO4OU0I";
	private final static String customSearchEngineKey = "000800968141440158892:28g54gwznsc";


	//base url for the search query
	private final static String searchEpisodesURL = "https://www.googleapis.com/customsearch/v1?";

	//base url for pirate bay search - descending by seeds
	private static final int ALL_URLS_DEAD = -2;
	private static final int NOT_INITIALIZED = -1;
	private static int pirateBayUrlIndex = NOT_INITIALIZED; // which url to use
	private static final String[] searchPirateBayConcatChar = {"%20", "+"};
	private static final String[] searchPirateBayUrl = {"https://kickass.to/usearch/", "http://oldpiratebay.org/search.php?q="};
	private static final String[] searchPirateBayAttributes = {
				 "/?field=seeders&sorder=desc",// Descending by Seeds
				 "&iht=8&Torrent_sort=seeders.desc", // Series&TV  / Descending by Seeds
	}; 

	//TOREC search
	public final static String torecDomain 	= "http://www.torec.net"; 
	public final static String torecSearchLink = torecDomain + "/ssearch.asp";
	public final static String torecSerieLink 	= torecDomain + "/series.asp?series_id=";

	public static String getEpisodesGuideGoogleSearchContent(String serieName){
		return read(makeEpisodeGuideSearchString(serieName, 1, 1));
	}

	public static String getPirateBaySearchContent(EpisodeData epData){
		return read(makePirateBaySearchString(epData));		
	}

	public static void openURL(String url){
		if (url == null) {
			return;
		}
		try {
			java.awt.Desktop.getDesktop().browse(new URI(url));
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static String makePirateBaySearchString(EpisodeData epData){
		setAliveTorrentUrl();
		if (pirateBayUrlIndex == ALL_URLS_DEAD) {
			return null;
		}
		//replace spaces in the serie name with "%20"
		String toSearch = "";
		String keys[] = epData.getSerieName().split("[ ]+");
		int wordCount = 0;
		for(String key:keys){
			wordCount++;
			// if this is the last word (but not first) and it is a number - remove it from search string (i.e. Once upon a time 2011 - remove the 2011)
			if (wordCount > 1 && wordCount == keys.length && key.matches("^[0-9]+$")) {
				continue;
			}
			toSearch += key + searchPirateBayConcatChar[pirateBayUrlIndex]; //append the keywords to the url
		} 
		// add the season and episode
		toSearch += epData.toString();

		toSearch = searchPirateBayUrl[pirateBayUrlIndex] + toSearch + searchPirateBayAttributes[pirateBayUrlIndex];

		return toSearch;
	}

	@SuppressWarnings("unused")
	private static synchronized void setAliveTorrentUrl() {
		if (pirateBayUrlIndex == ALL_URLS_DEAD) {
			return;
		}
		if (pirateBayUrlIndex == NOT_INITIALIZED) {
			while (pirateBayUrlIndex <= searchPirateBayUrl.length) {
				pirateBayUrlIndex++;
				if (searchPirateBayUrl.length <= pirateBayUrlIndex) {
					pirateBayUrlIndex = ALL_URLS_DEAD;
				} else {				
					URL url;
					HttpURLConnection connection = null;
					try {
//						String fullAddress = searchPirateBayUrl[pirateBayUrlIndex];
//						url = new URL(fullAddress);
//						if (searchPirateBayUrl[pirateBayUrlIndex].startsWith("https")) {
//							int pathStart = fullAddress.indexOf("/", 8);
//							url = new URL("https", fullAddress.substring(8, pathStart), fullAddress.substring(pathStart));
//						}
//						connection= (HttpURLConnection)url.openConnection();
//						connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
//						connection.connect();
//						connection.getResponseCode();
//						InputStream inputStream = connection.getInputStream();
//						inputStream.close();
//						connection.disconnect();
						return; // success - found an alive url
					} catch (Exception e) {
						if (connection != null && e.getMessage().equalsIgnoreCase("Already Connected")) {
							connection.disconnect();
							return; // success - found an alive url
						} else {							
							log.error("Couldn't connect to " + searchPirateBayUrl[pirateBayUrlIndex], e);
						}
					}
				}
			}
		}
	}
	
	public static String getFirstMagnetLink(String pirateBaySearchContent) {
		String link = null;
		if (pirateBayUrlIndex == 0) {
			// regular pirate bay parse
			String firstString = "Torrent magnet link\"";
			int indexOf = pirateBaySearchContent.indexOf(firstString);
			if (indexOf != -1){
				pirateBaySearchContent = pirateBaySearchContent.substring(indexOf + firstString.length());
				indexOf = pirateBaySearchContent.indexOf("magnet");
				if (indexOf != -1){
					int endIndex = pirateBaySearchContent.indexOf("\"", indexOf);
					link = pirateBaySearchContent.substring(indexOf, endIndex).trim();
				}
			}
		} else {
			// old pirate bay parse
			int indexOf = pirateBaySearchContent.indexOf("<td class=\"title-row\">");
			if (indexOf != -1){
				pirateBaySearchContent = pirateBaySearchContent.substring(indexOf);
				indexOf = pirateBaySearchContent.indexOf("magnet");
				if (indexOf != -1){
					int endIndex = pirateBaySearchContent.indexOf("' title='MAGNET LINK'", indexOf);
					link = pirateBaySearchContent.substring(indexOf, endIndex).trim();
				}
			}
		}
		return link;
	}

	private static String makeEpisodeGuideSearchString(String qSearch,int start,int numOfResults)
	{
		String toSearch = searchEpisodesURL + "key=" + apiKey + "&cx=" + customSearchEngineKey+"&q=";

		//replace spaces in the search query with +
		String keys[] = qSearch.split("[ ]+");
		for(String key:keys){
			toSearch += key +"+"; //append the keywords to the url
		}        

		//specify response format as json
		toSearch+="&alt=json";

		//specify starting result number
		toSearch+="&start="+start;

		//specify the number of results you need from the starting position
		toSearch+="&num="+numOfResults;

		log.info("searchEpisodesURL = " + toSearch);
		
		return toSearch;
	}


	private static String read(String pUrl)
	{
		if (pUrl == null) {
			return null;
		}
		String newLine = System.getProperty("line.separator");
		//pUrl is the URL we created in previous step
		try
		{
			URL url=new URL(pUrl);
			HttpURLConnection connection=(HttpURLConnection)url.openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
			BufferedReader br;
			String headerField = connection.getHeaderField("Content-Encoding");
			InputStream inputStream = connection.getInputStream();
			if (headerField != null && headerField.equals("gzip")){
				br = new BufferedReader(new InputStreamReader(new GZIPInputStream(inputStream)));            
	        } else {
				br = new BufferedReader(new InputStreamReader(inputStream));            
	        }  
			String line;
			StringBuffer buffer=new StringBuffer();
			while((line=br.readLine())!=null){
				buffer.append(line).append(newLine);
			}
			connection.disconnect();
			return buffer.toString();
		}catch(Exception e){
			log.error(e.getMessage(), e);
		}
		return null;
	}
	
	public static void openEpGuideLink(Serie serie) {
		String link = AppConfigurations.getInstance().getSerieProperty(serie.getName(), AppConfigurations.EP_GUIDE_LINK_PROPERTY);
		if (link != null){
			HttpsClient.openURL(link);
			return;
		}
	}
	
	public enum TorecStage{
		SEARCH_PAGE,
		SEARCH_RESULTS_PAGE,
		SERIES_PAGE,
		DONE;
	}
	
	

	// For keeping hard reference to objects
	public static Queue<TorecWebEngineListener> webEngineQueue = new LinkedList<>();
	private static boolean webEngineInProgress = false;
	
	public static void openTorecSeriesLink(Serie serie, boolean forceOpenWeb) {

		String startLink = torecSerieLink;
		TorecStage startTorecStage = TorecStage.SEARCH_PAGE;
		
		if (!forceOpenWeb && serie.getMissingSubsEpisodesList().isEmpty()) {
			return;
		}
		
		String torecSerieId = AppConfigurations.getInstance().getSerieProperty(serie.getName(), AppConfigurations.TOREC_SERIES_PROPERTY);
		if (torecSerieId != null){
			startLink = torecSerieLink + torecSerieId;
			startTorecStage = TorecStage.SERIES_PAGE;
		}
		
        
		Platform.runLater(new Runnable() {
		    @Override
		    public void run() {
		        Main.mainStage.getScene().setCursor(Cursor.WAIT);
		    }
		});
		
		try {
			WebView webview = new WebView();
			WebEngine webEngine = webview.getEngine();
			webEngine.setJavaScriptEnabled(true);
			
			TorecWebEngineListener torecWebEngineListener = new TorecWebEngineListener(webview, startTorecStage, serie, forceOpenWeb, startLink);
			webEngine.getLoadWorker().stateProperty().addListener(
					torecWebEngineListener);
			log.info("Adding to queue for series: " + serie.getName());
			webEngineQueue.add(torecWebEngineListener);
			
			if (!webEngineInProgress) {						
				startNextWebEngineLoad();
			}
			

		} catch (Exception ex) {
			System.err.print("error " + ex.getMessage());
			ex.printStackTrace();
		}

	}
	
	public static void setWebEngineInProgress(boolean webEngineInProgress) {
		HttpsClient.webEngineInProgress = webEngineInProgress;
	}

	public static void startNextWebEngineLoad() {
		if (!webEngineQueue.isEmpty()) {		
			webEngineInProgress = true;
			TorecWebEngineListener poll = webEngineQueue.poll();
			log.info("Starting web engine for serie: " + poll.getSerie().getName());
			String startLink = poll.getStartLink();
			Main.setStatusTextOverride("Loading " + startLink);
			poll.getWebEngine().load(startLink);
		}
	}

}