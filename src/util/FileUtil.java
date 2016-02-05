package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.stage.DirectoryChooser;
import main.Main;
import main.properties.AppConfigurations;


public class FileUtil {
	
	public static Set<String> movieSuffix = new HashSet<>();
	static{
		movieSuffix.add("avi");
		movieSuffix.add("mp4");
		movieSuffix.add("mkv");
		movieSuffix.add("mov");
		movieSuffix.add("wmv");
	}
	
	public static Set<String> subsSuffix = new HashSet<>();
	static{
		subsSuffix.add("srt");
		subsSuffix.add("sub");
	}

	public static List<File> getAllSeriesFolders(boolean silent) {
		List<File> folders = new ArrayList<>();
		File rootFolder = null;
		if (silent){
			String seriesRootFolderPath = AppConfigurations.getInstance().getSeriesRootFolder();
			if (seriesRootFolderPath != null){
				rootFolder = new File(seriesRootFolderPath);
			}
		}else{
			DirectoryChooser chooser = new DirectoryChooser();			
			chooser.setTitle("Choose Series Root Folder");
			rootFolder = chooser.showDialog(Main.mainStage);
			if (rootFolder == null){
				return null;
			}
		}
		if (rootFolder != null){
			AppConfigurations.getInstance().setGeneralProperty(AppConfigurations.ROOT_FOLDER_PROPERTY, rootFolder.getPath());
			addSeriesFolder(folders, rootFolder);
		}
		return folders;
		
	}
	
	public static String readFromUrl(String urlStr) throws Exception {
		String newline = System.getProperty("line.separator"); 
		StringBuilder builder = new StringBuilder();
		URL url = new URL(urlStr);
		URLConnection uc = url.openConnection();
        uc.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

        uc.connect();
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		
		String inputLine;
		while ((inputLine = in.readLine()) != null)
			builder.append(inputLine).append(newline);
		in.close();
		
		return builder.toString();
	}
	
	public static String readFromFile(File file){		
		try(BufferedReader br = new BufferedReader(new FileReader(file))) {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();
			
			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
			}
			String everything = sb.toString();
			
			return everything;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean isVideoFile(File file) {
		String suffix = getFileSuffix(file);
		return movieSuffix.contains(suffix.toLowerCase());
	}
	
	public static boolean isSubtitlesFile(File file) {
		String suffix = getFileSuffix(file);
		return subsSuffix.contains(suffix);
	}
	
	public static String removeFileSuffix(String name, String... additonalSuffixToRemove) {
		int indexOf = name.lastIndexOf(".");
		String newName = name;
		if (indexOf != -1){			
			newName = name.substring(0, indexOf);
		}
		if (additonalSuffixToRemove != null){				
			for (String suffix : additonalSuffixToRemove) {				
				newName = newName.replace(suffix, "");
				newName = newName.replace(suffix.toLowerCase(), "");
			}
		}
		return newName;
	}
	
	public static boolean deleteDir(File dir) 
	{ 
		if (dir.isDirectory()) 
		{ 
			String[] children = dir.list(); 
			for (int i=0; i<children.length; i++)
			{ 
				boolean success = deleteDir(new File(dir, children[i])); 
				if (!success) 
				{  
					return false; 
				} 
			} 
		}  
		// The directory is now empty or this is a file so delete it 
		return dir.delete(); 
	} 
	
	//////////////// PRIVATE Methods //////////////////////////////////////////

	private static void addSeriesFolder(List<File> folders, File rootFolder) {
		File[] listFiles = rootFolder.listFiles(new DirectoryFilter());
		for (File folder : listFiles) {
			File[] filesInFolder = folder.listFiles();
			if (isSerieFolder(filesInFolder)){
				folders.add(folder);
			}else {
				addSeriesFolder(folders, folder);
			}
		}
	}

	private static boolean isSerieFolder(File[] filesInFolder) {
		for (File file : filesInFolder) {
			if (isVideoFile(file) || 
					(file.isDirectory() && file.getName().toLowerCase().startsWith("season")) ){
				return true;
			}
		}
		return false;
	}

	private static String getFileSuffix(File file) {
		return getFileSuffix(file.getName());
	}
	
	public static String getFileSuffix(String name) {
		int indexOf = name.lastIndexOf(".");
		if (indexOf > 0){
			return name.substring(indexOf+1);
		}
		return "";
	}

	////////////////////////////////////////////////////////////////

	public static class DirectoryFilter implements FileFilter {

		@Override
		public boolean accept(File file) {
			return file.isDirectory();
		}

	}


}
