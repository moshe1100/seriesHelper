package main.properties;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import util.FileUtil;
import util.Util;

public class AppConfigurations {

	private static String launchDir = System.getProperty("user.dir");
	private static String configFileName = "seriesHelper.txt";

	private static final String GENERAL_SECTION = "_____________GENERAL_____________";
	private static final String SERIES_SECTION  = "_____________SERIES______________";
	
	// Properties Keys
	public static final String ROOT_FOLDER_PROPERTY = "SERIES_ROOT_FOLDER";
	public static final String EP_GUIDE_LINK_PROPERTY = "EP_GUIDE_LINK";
	public static final String MAGNET_LINK_APPLICATION_SERIES_PROPERTY = "MAGNET_LINK_APPLICATION_PATH";
	public static final String IGNORE_MISSING_SUBS_SERIES_PROPERTY = "IGNORE_MISSING_SUBS_SERIES";
	public static final String TOREC_SERIES_PROPERTY = "TOREC_SERIES_ID";
	public static final String SERIES_ENDED_PROPERTY = "SERIES_ENDED";
	public static final String SERIES_LAST_EP_WATCHED_PROPERTY = "SERIES_LAST_EP_WATCHED";
	public static final String TABLE_SORT_ORDER = "TABLE_SORT_ORDER";

	private Map<String, Properties> sectionsToProperties = new HashMap<>(); 
	
	private static AppConfigurations instance = new AppConfigurations();
	public static AppConfigurations getInstance() {
		return instance;
	}
	

	private AppConfigurations(){
		init();
	}

	private void init() {

		sectionsToProperties.put(GENERAL_SECTION, new Properties());
		sectionsToProperties.put(SERIES_SECTION, new Properties());

		try {
			File configFile = new File(getConfigFilePath());
			boolean fileExists = configFile.exists();
			if (!fileExists){
				configFile.createNewFile();
			}
			String readFromFile = FileUtil.readFromFile(configFile);
			BufferedReader reader = Util.getStringAsBufferReader(readFromFile);
			String currentSection = null;
			String line;
			while((line = reader.readLine())!=null){
				if (sectionsToProperties.containsKey(line.trim())){
					currentSection = line; 
				}else {
					String[] split = line.split("=");
					sectionsToProperties.get(currentSection).put(split[0], split[1]);
				}

			}	
			
			if (!fileExists){
				saveConfig();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String getConfigFilePath() {
		return launchDir + File.separator + configFileName;
	}

	public String getGeneralProperty(String key){
		return sectionsToProperties.get(GENERAL_SECTION).getProperty(key);
	}
	
	public void setGeneralProperty(String key, String value) {
		sectionsToProperties.get(GENERAL_SECTION).setProperty(key, value);
		saveConfig();
	}
	
	public String getSerieProperty(String serieName, String key){
		return sectionsToProperties.get(SERIES_SECTION).getProperty(getSeriePropertyKey(serieName, key));
	}
	
	public void setSerieProperty(String serieName, String key, String value) {
		if (value == null){
			sectionsToProperties.get(SERIES_SECTION).remove(getSeriePropertyKey(serieName, key));
		}else{			
			sectionsToProperties.get(SERIES_SECTION).setProperty(getSeriePropertyKey(serieName, key), value);		
		}
		saveConfig();
	}



	private String getSeriePropertyKey(String serieName, String key) {
		return serieName + "_" + key;
	}

	private synchronized void saveConfig() {
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(getConfigFilePath()), "utf-8"))) {
			writeSectionToFile(writer, GENERAL_SECTION);
			writeSectionToFile(writer, SERIES_SECTION);
		} catch (IOException ex){
			ex.printStackTrace();
		} 

	}
	
	private void writeSectionToFile(BufferedWriter writer, String section) throws IOException{
		writer.write(section);
	    writer.newLine();
	    Properties properties = sectionsToProperties.get(section);
	    List<Object> keys = new LinkedList<>(properties.keySet());
	    Collections.sort(keys, new Comparator<Object>() {
			@Override
			public int compare(Object o1, Object o2) {				
				return o1.toString().compareTo(o2.toString());
			}
		});
	    for (Object key : keys) {
	    	writer.write(key.toString());
	    	writer.write("=");
	    	writer.write(properties.getProperty(key.toString()));
	    	writer.newLine();
		}
	    
	}


	public String getSeriesRootFolder() {
		return getGeneralProperty(ROOT_FOLDER_PROPERTY);
		
	}

}
