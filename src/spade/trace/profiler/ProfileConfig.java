package spade.trace.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Settings;

public class ProfileConfig{

	private static final Logger logger = Logger.getLogger(ProfileConfig.class.getName());
	
	private static final String configPath = Settings.getDefaultConfigFilePath(ProfileConfig.class);
	
	private static int engagement;
	private static File tempDirFile;
	
	static{
		BufferedReader br = null;
		try{
			br = new BufferedReader(new FileReader(new File(configPath)));
			engagement = Integer.parseInt(br.readLine());
			if(engagement < 1 || engagement > 5){
				throw new Exception("Invalid engagement number: " + engagement);
			}
			tempDirFile = new File(br.readLine());
			if(!tempDirFile.exists()){
				if(!tempDirFile.mkdirs()){
					throw new Exception("Failed to create dir at: " + tempDirFile.getAbsolutePath());
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configPath, e);
		}finally{
			if(br != null){
				try{
					br.close();
				}catch(Exception e){
					
				}
			}
		}
	}
	
	public static int getEngagement(){
		return engagement;
	}
	
	public static String getOutFilePath(String name){
		return tempDirFile.getAbsolutePath() + File.separator + name;
	}
}
