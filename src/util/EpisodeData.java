package util;

import java.io.File;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EpisodeData implements Comparable<EpisodeData>{

	// handle single episode: i.e. Serie.Name.S08E14.HDTV.XviD-LOL
	private static Pattern singleEpisodePattern = Pattern.compile("(.*?)[.\\s][sS](\\d{2})[eE](\\d{2}).*");
	// handle double episode: i.e. Serie.Name.S08E14E15.HDTV.XviD-LOL
	private static Pattern doubleEpisodePattern = Pattern.compile("(.*?)[.\\s][sS](\\d{2})[eE](\\d{2})[eE](\\d{2}).*");
	// handle special episode without a number: i.e. Once.Upon.a.Time.S03.Special-Journey.to.Neverland
	private static Pattern specialEpisodePattern = Pattern.compile("(.*?)[.\\s][sS](\\d{2})[.].*");
	
	private String serieName;
	private int season;
	private int episode; // the ep number (or the first one if double)
	private int doubleEpisode; // the second episode number for double episode
	
	private File episodeFile;
	
	private Date airDate;
	
	// The link to torrent from PirateBay
	private String magnetLink;
	private boolean hasSubtitleFile;
	
	public EpisodeData(String serieName, File file) {
		this(serieName, -1,-1);		
		this.episodeFile = file;
		this.airDate = new Date(file.lastModified());
		
		String fileName = file.getName();
		fileName = fileName.replace("-", "");
		
		Matcher singleEpisodeMatcher = singleEpisodePattern.matcher(fileName);
		Matcher doubleEpisodeMatcher = doubleEpisodePattern.matcher(fileName);
		Matcher specialEpisodeMatcher = specialEpisodePattern.matcher(fileName);
		if (doubleEpisodeMatcher.matches()){
			season  = Integer.parseInt(doubleEpisodeMatcher.group(2));
	        episode = Integer.parseInt(doubleEpisodeMatcher.group(3));
	        doubleEpisode = Integer.parseInt(doubleEpisodeMatcher.group(4));
		} else if (singleEpisodeMatcher.matches()){
	    	season  = Integer.parseInt(singleEpisodeMatcher.group(2));
	        episode = Integer.parseInt(singleEpisodeMatcher.group(3));
		} else if (specialEpisodeMatcher.matches()){
	    	season  = Integer.parseInt(specialEpisodeMatcher.group(2));
	        episode = 0;
	    }else {
	    	// Try to get season/episode from 1x02 form
	    	String currentText = "";
	    	boolean setEpisode = false;
	    	for (int i=0; i < fileName.length(); i++){
	    		char charAt = fileName.charAt(i);
				if (Util.isDigit(charAt)){
	    			currentText += charAt; // gathering number
	    		} else if ((charAt == 'x' || charAt == 'X') && currentText.length() > 0) {
	    			setEpisode = true; // reached the "x" between season and episode
	    			season = Integer.parseInt(currentText); // setting season
	    			currentText = ""; // init current text
	    		} else if (currentText.length() > 0 && setEpisode){
	    			// setting episode (current char is not digit and we got episode number
	    			episode = Integer.parseInt(currentText);
	    			break;
	    		} else if (setEpisode && currentText.isEmpty()){
	    			// false alarm - reset
	    			setEpisode = false;
	    			currentText = "";
	    			season = -1;
	    		}
	    	}
	    }
	    	
	}

	public EpisodeData(String serieName, String season, String episode) {
		this(serieName, Integer.parseInt(season), Integer.parseInt(episode));
	}
	
	public EpisodeData(String serieName, int season, int episode) {
		this(serieName, season, episode, null);
	}
	
	public EpisodeData(String serieName, int season, int episode, Date airDate) {
		this.serieName = serieName;
		this.season = season;
		this.episode = episode;
		this.doubleEpisode = -1;
		this.airDate = airDate;
	}
	
	public String getSerieName() {
		return serieName;
	}

	public int getEpisode() {
		return episode;
	}
	
	public int getDoubleEpisode() {
		return doubleEpisode;
	}
	
	public int getLastEpisodeNumber(){
		if (getDoubleEpisode() > 0){
			return getDoubleEpisode();
		}
		return getEpisode();
	}
	
	public int getSeason() {
		return season;
	}
	
	public void setAirDate(Date airDate) {
		this.airDate = airDate;
	}
	public Date getAirDate() {
		return airDate;
	}

	@Override
	public int compareTo(EpisodeData o) {
		if (o == null){
			return 1;
		}
		if (getSeason() > o.getSeason()){
			return 1;
		}else if (getSeason() < o.getSeason()){
			return -1;
		}
		return Integer.compare(getLastEpisodeNumber(), o.getLastEpisodeNumber());
	}
	
	@Override
	public String toString() {		
		if (doubleEpisode > 0){
			return String.format("S%02dE%02dE%02d", season, episode, doubleEpisode);
		}
		return String.format("S%02dE%02d", season, episode);
	}

	public void setMagnetLink(String magnetLink) {
		this.magnetLink = magnetLink;
	}
	
	public String getMagnetLink() {
		return magnetLink;
	}
	
	public String getFileNameWithoutSuffix() {
		if (episodeFile != null){
			return FileUtil.removeFileSuffix(episodeFile.getName());
		}
		return null;
	}
	
	public File getEpisodeFile() {
		return episodeFile;
	}

	public void setHasSubtitle(boolean hasSubtitle) {
		this.hasSubtitleFile = hasSubtitle;
	}
	
	public boolean hasSubtitleFile() {
		return hasSubtitleFile;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + doubleEpisode;
		result = prime * result + episode;
		result = prime * result + season;
		result = prime * result	+ ((serieName == null) ? 0 : serieName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EpisodeData other = (EpisodeData) obj;
		if (doubleEpisode != other.doubleEpisode)
			return false;
		if (episode != other.episode)
			return false;
		if (season != other.season)
			return false;
		if (serieName == null) {
			if (other.serieName != null)
				return false;
		} else if (!serieName.equals(other.serieName))
			return false;
		return true;
	}

	
	
}
