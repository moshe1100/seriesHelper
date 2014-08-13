package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
	private final static String searchPirateBayUrl = "http://thepiratebay.se/search/";
	private final static String searchPirateBayAttributes = "/0/7/200"; // first page / Descending by Seeds / Videos

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
		try {
			java.awt.Desktop.getDesktop().browse(new URI(url));
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static String makePirateBaySearchString(EpisodeData epData){
		//replace spaces in the serie name with "%20"
		String toSearch = "";
		String keys[] = epData.getSerieName().split("[ ]+");
		for(String key:keys){
			toSearch += key +"%20"; //append the keywords to the url
		} 
		// add the season and episode
		toSearch += epData.toString();

		toSearch = searchPirateBayUrl + toSearch + searchPirateBayAttributes;

		return toSearch;
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
		String newLine = System.getProperty("line.separator");
		//pUrl is the URL we created in previous step
		try
		{
			URL url=new URL(pUrl);
			HttpURLConnection connection=(HttpURLConnection)url.openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
			BufferedReader br;
			String headerField = connection.getHeaderField("Content-Encoding");
			if (headerField != null && headerField.equals("gzip")){
				br = new BufferedReader(new InputStreamReader(new GZIPInputStream(connection.getInputStream())));            
	        } else {
	        	br = new BufferedReader(new InputStreamReader(connection.getInputStream()));            
	        }  
			String line;
			StringBuffer buffer=new StringBuffer();
			while((line=br.readLine())!=null){
				buffer.append(line).append(newLine);
			}
			return buffer.toString();
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	private static WebView webview;
	private static TorecWebEngineListener torecWebEngineListener;
	
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
	
	public static void openTorecSeriesLink(Serie serie) {

		String startLink = torecSerieLink;
		TorecStage startTorecStage = TorecStage.SEARCH_PAGE;
		
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
			WebEngine webEngine = null;			
			if (webview == null){
				webview = new WebView();
				webEngine = webview.getEngine();
				webEngine.setJavaScriptEnabled(true);
				
				torecWebEngineListener = new TorecWebEngineListener(webEngine, startTorecStage, serie);
				webEngine.getLoadWorker().stateProperty().addListener(
						torecWebEngineListener);
			}else{
				webEngine = webview.getEngine();
				torecWebEngineListener.init(startTorecStage, serie);
			}
			Main.setStatusTextOverride("Loading " + startLink);
			webEngine.load(startLink);

		} catch (Exception ex) {
			System.err.print("error " + ex.getMessage());
			ex.printStackTrace();
		}

	}

}