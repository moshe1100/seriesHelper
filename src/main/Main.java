/*
 * Copyright (c) 2011, 2014 Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package main;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import main.properties.AppConfigurations;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

public class Main extends Application {
	private static Logger log = Logger.getLogger(Main.class);
	
	public static Stage mainStage;
	private static FXMLButtonController controller;
	
    @Override
    public void start(Stage stage) throws Exception {
    	initLog();
    	
    	mainStage = stage;
    	AppConfigurations.getInstance();
    	
    	FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main.fxml"));
    	Parent root = fxmlLoader.load();
    	controller = fxmlLoader.getController();
//      Parent root = FXMLLoader.load(getClass().getResource("main.fxml"));
        
        // Set Icon
        stage.getIcons().add(new Image(getClass().getResourceAsStream( "icon.png" ))); 
    
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                try {
					stop();
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
        });
        
        stage.setTitle("Series Helper");
        stage.setScene(new Scene(root));
        stage.show();
    }
    
    private void initLog() {
    	//DOMConfigurator is used to configure logger from xml configuration file
        DOMConfigurator.configure("log4j.xml");
 
        //Log in console in and log file
        log.info("Log4j appender configuration is successful !!");
		
	}

	public static void setStatusTextOverride(String text){
    	if (controller != null){
    		controller.overrideStatusText(text);
    	}
    }
    
    
	public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
