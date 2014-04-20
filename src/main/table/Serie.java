package main.table;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import main.properties.AppConfigurations;
import util.Constants;
import util.EpisodeData;

public class Serie {
	private final SimpleStringProperty name = new SimpleStringProperty("");
	private final SimpleStringProperty lastEpOnDisk = new SimpleStringProperty("");
	private final SimpleStringProperty lastEpAired = new SimpleStringProperty("");
	private final SimpleStringProperty nextEpAirDate = new SimpleStringProperty("");
	private final SimpleStringProperty availableEpForDownload = new SimpleStringProperty("");
	private final SimpleStringProperty missingSubsEpisodes = new SimpleStringProperty("");
	private final SimpleBooleanProperty seriesEnded = new SimpleBooleanProperty(false);
	
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.US);
	
	private EpisodeData lastEpisodeOnDisk;
	private EpisodeData lastEpisodeAired;
	private List<EpisodeData> episodesList;
	
	private List<EpisodeData> missingSubsEpisodesList;
	
	private String pathOnDisk;
	private String lastEpisodeOnDiskPath;
	private EpisodeData nextEpisodeToBeAired;

	public Serie(String name, List<EpisodeData> episodesList, String pathOnDisk) {
		setName(name);
		this.pathOnDisk = pathOnDisk;
		this.episodesList = episodesList;
		
		this.lastEpisodeOnDisk = episodesList.get(episodesList.size()-1);
		lastEpisodeOnDiskPath = lastEpisodeOnDisk == null ? pathOnDisk : lastEpisodeOnDisk.getEpisodeFile().getParent();
		setLastEpOnDisk(lastEpisodeOnDisk == null ? Constants.N_A : lastEpisodeOnDisk.toString());
		setLastEpAired(Constants.N_A);
		setNextAirDate(Constants.N_A);
		
		String serieProperty = AppConfigurations.getInstance().getSerieProperty(getName(), AppConfigurations.SERIES_ENDED_PROPERTY);
		this.seriesEnded.set(Boolean.parseBoolean(serieProperty));
	}

	public String getName() {
		return name.get();
	}

	public void setName(String name) {
		this.name.set(name);
	}

	public String getLastEpOnDisk() {
		return lastEpOnDisk.get();
	}

	public void setLastEpOnDisk(String lastEpOnDisk) {
		this.lastEpOnDisk.set(lastEpOnDisk);
	}

	public String getLastEpAired() {
		return lastEpAired.get();
	}

	public void setLastEpAired(String lastEpAired) {
		this.lastEpAired.set(lastEpAired);
	}
	
	public List<EpisodeData> getMissingSubsEpisodesList() {
		return missingSubsEpisodesList;
	}
	
	public EpisodeData getNextEpisodeToBeAired() {
		return nextEpisodeToBeAired;
	}
	
	public void setNextAirDate(String nextAirDate) {
		this.nextEpAirDate.set(nextAirDate);
	}
	
	public void setAvailableEpForDownload(String availableEpisodes){
		this.availableEpForDownload.set(availableEpisodes);
	}
	
	public void setMissingSubsEpisodes(String missingSubsEpisodes){
		this.missingSubsEpisodes.set(missingSubsEpisodes);
	}
	
	public void setSeriesEnded(boolean hasEnded){
		this.seriesEnded.set(hasEnded);
		String propertyValue = hasEnded ? "true" : null;
		AppConfigurations.getInstance().setSerieProperty(getName(), AppConfigurations.SERIES_ENDED_PROPERTY, propertyValue);		

	}
	
	public boolean isEnded() {
		return seriesEnded.get();
	}

	
	/////////////// Properties expose for JAVAFX ///////////////
	
	public SimpleStringProperty nameProperty() {
        return name;
    }

    public SimpleStringProperty lastEpOnDiskProperty() {
        return lastEpOnDisk;
    }

    public SimpleStringProperty lastEpAiredProperty() {
        return lastEpAired;
    }
    
    public SimpleStringProperty nextEpAirDateProperty() {
        return nextEpAirDate;
    }
    
    public SimpleStringProperty availableEpForDownloadProperty(){
    	return availableEpForDownload;
    }
    
    public SimpleStringProperty missingSubsEpisodesProperty(){
    	return missingSubsEpisodes;
    }
    
    public SimpleBooleanProperty seriesEndedProperty(){
    	return seriesEnded;
    }
    
    //////////////////////////////////////////////////////////
	
    public synchronized void addEpisode(int season, int episode, Date airDate){
    	if (episodesList == null){
    		episodesList = new ArrayList<>();
    	}
    	
    	boolean found = false;
    	for (EpisodeData episodeData : episodesList) {
			if (episodeData.getSeason() == season && 
					(episodeData.getEpisode() == episode || episodeData.getDoubleEpisode() == episode) ){
				episodeData.setAirDate(airDate);
				found = true;
			}
		}
    	
    	if (!found){
    		EpisodeData e = new EpisodeData(getName(), season, episode, airDate);
    		episodesList.add(e);        	
    	}
    }

	public synchronized void calculateLastAired() {
		String lastEpAired = Constants.N_A;
		String lastAirDate = Constants.N_A;
		if (episodesList != null){
			Collections.sort(episodesList);
			
			Date today = new Date();
			for (EpisodeData epData : episodesList) {
				String airDate = dateFormat.format(epData.getAirDate());
				if (epData.getAirDate().before(today)){
					this.lastEpisodeAired = epData;
					lastEpAired = String.format("%s (%s)", epData.toString(), airDate) ;
				}else{
					lastAirDate = airDate;
					this.nextEpisodeToBeAired = epData;
					break;
				}
			}			
		}
		
		setLastEpAired(lastEpAired);
		setNextAirDate(lastAirDate);		
	}

	public boolean hasAvailableNewEpisode() {
		if (lastEpisodeAired != null && lastEpisodeOnDisk != null){
			return lastEpisodeOnDisk.compareTo(lastEpisodeAired) < 0;
		}
		return false;
	}

	public List<EpisodeData> getDownloadEpisodesCandidates() {
		List<EpisodeData> candidates = new ArrayList<>();
		if (lastEpisodeAired != null){
			for (EpisodeData epData : episodesList) {
				if (epData.compareTo(lastEpisodeOnDisk) > 0 && epData.compareTo(lastEpisodeAired) <= 0){
					candidates.add(epData);
				}
			}	
		}
		return candidates;
	}

	public void updateAvailableEpisodesForDownload() {
		String avaialbleEpisodesForDownload = "";
		for (EpisodeData epData : episodesList) {
			if (epData.getMagnetLink() != null){
				avaialbleEpisodesForDownload += epData.toString() + ", ";
			}
		}
		if (!avaialbleEpisodesForDownload.isEmpty()){
			avaialbleEpisodesForDownload = avaialbleEpisodesForDownload.substring(0, avaialbleEpisodesForDownload.length()-2);
			setAvailableEpForDownload(avaialbleEpisodesForDownload);
		}
		
	}
	
	public String getPathOnDisk() {
		return pathOnDisk;
	}
	
	public String getLastEpisodeOnDiskPath() {
		return lastEpisodeOnDiskPath;
	}
	
	@Override
	public String toString() {	
		return getName();
	}

	public void updateMissingSubs() {
		String missingSubsEpisodes = "";
		missingSubsEpisodesList = new LinkedList<>();
		for (EpisodeData epData : episodesList) {
			if (epData.getEpisodeFile() != null && !epData.hasSubtitleFile()){
				missingSubsEpisodes += epData.toString() + ", ";
				missingSubsEpisodesList.add(epData);
			}
		}
		
		if (!missingSubsEpisodes.isEmpty()){
			missingSubsEpisodes = missingSubsEpisodes.substring(0, missingSubsEpisodes.length()-2);
		}
		setMissingSubsEpisodes(missingSubsEpisodes);
	}


}