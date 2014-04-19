package main;

import javafx.fxml.FXML;
import javafx.scene.text.Text;

public class FXMLAboutController {

	private static final String VERSION = "1.0";
	
	@FXML private Text versionValue;
	
	
	@FXML public void initialize(){
		versionValue.setText(VERSION);
	}
}
