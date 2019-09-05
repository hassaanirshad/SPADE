package spade.utility;

import java.io.IOException;
import java.io.File;

import java.util.Date;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Handler;

import java.util.Map;
import java.util.HashMap;

import spade.core.AbstractFilter;
import spade.core.AbstractReporter;
import spade.core.AbstractStorage;
import spade.core.Settings;
import spade.core.SpecialBuffer;
import spade.filter.FinalCommitFilter;
import spade.utility.FileUtility;

public class TestRun{

	static{
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%6$s%n");
	}

	private static Logger logger;

	private static void setupLogging(){
		try{
			String logFilename = System.getProperty("spade.log");
			if(logFilename == null){
				//System.err.println("Must specify -Dspade.log=<log path>");
				//System.exit(1);

				new File("log").mkdirs();
				Date currentTime = new Date(System.currentTimeMillis());
				String logStartTime = new java.text.SimpleDateFormat("MM.dd.yyyy-H.mm.ss").format(currentTime);
				logFilename = "log" + File.separator + "TestRun_" + logStartTime + ".log";

			}//else{
				final Handler logFileHandler = new FileHandler(logFilename);
				logFileHandler.setFormatter(new SimpleFormatter());
				logFileHandler.setLevel(Level.parse(Settings.getProperty("logger_level")));
				Logger.getLogger("").addHandler(logFileHandler);

				logger = Logger.getLogger(TestRun.class.getName());
				Logger parentLog= logger.getParent();
				if(parentLog!=null && parentLog.getHandlers().length > 0){ parentLog.removeHandler(parentLog.getHandlers()[0]); }
			//}
	       	}catch(IOException | SecurityException exception){
            		System.err.println("Error initializing exception logger");
			System.exit(1);
        	}
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception{
		setupLogging();
		String configFile = Settings.getDefaultConfigFilePath(TestRun.class);
		Map<String, String> configMap = new HashMap<String, String>();
		try{
			Map<String, String> result = FileUtility.readConfigFileAsKeyValueMap(configFile, "=");
			configMap.putAll(result);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configFile, e);
			return;
		}

		String reporter = null, filter = null, storage = null;
		String reporterArguments = null, filterArguments = null, storageArguments = null;
		boolean error = false;
		//for(String arg : args){
			//String[] toks = arg.split("=", 2);
		for(Map.Entry<String, String> entry : configMap.entrySet()){
			String key = entry.getKey();
			String value = entry.getValue();
			switch(key){
				case "reporter":
					if(reporter == null){
						reporter = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'reporter' arg"); error = true; break;
					}
				case "filter":
					if(filter == null){
						filter = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'filter' arg"); error = true; break;
					}
				case "storage":
					if(storage == null){
						storage = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'storage' arg"); error = true; break;
					}
				case "reporterArgs":
					if(reporterArguments == null){
						reporterArguments = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'reporterArgs' arg"); error = true; break;
					}
				case "filterArgs":
					if(filterArguments == null){
						filterArguments = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'filterArgs' arg"); error = true; break;
					}
				case "storageArgs":
					if(storageArguments == null){
						storageArguments = value; break;
					}else{
						logger.log(Level.SEVERE, "Redefinition of 'storageArgs' arg"); error = true; break;
					}
				default: logger.log(Level.SEVERE, "Unexpected arg '"+key+"'"); break;
			}
		}

		if(!error){
			if(reporter == null){ logger.log(Level.SEVERE, "Undefined 'reporter' flag"); error = true; }
			if(filter == null){ logger.log(Level.SEVERE, "Undefined 'filter' flag"); error = true; }
			if(storage == null){ logger.log(Level.SEVERE, "Undefined 'storage' flag"); error = true; }
			if(reporterArguments == null){ logger.log(Level.SEVERE, "Undefined 'reporterArgs' flag"); error = true; }
			if(filterArguments == null){ logger.log(Level.SEVERE, "Undefined 'filterArgs' flag"); error = true; }
			if(storageArguments == null){ logger.log(Level.SEVERE, "Undefined 'storageArgs' flag"); error = true; }

			logger.log(Level.INFO, String.format("%s=%s, %s=%s, %s=%s, %s=%s, %s=%s, %s=%s", 
					"reporter", reporter,
					"filter", filter,
					"storage", storage,
					"reporterArgs", reporterArguments,
					"filterArgs", filterArguments,
					"storageArgs", storageArguments));

			if(!error){
				try{
					Class<? extends AbstractReporter> reporterClass = 
							(Class<? extends AbstractReporter>)Class.forName("spade.reporter." + reporter);
					try{
						Class<? extends AbstractFilter> filterClass = 
								(Class<? extends AbstractFilter>)Class.forName("spade.filter." + filter);
						try{
							Class<? extends AbstractStorage> storageClass = 
									(Class<? extends AbstractStorage>)Class.forName("spade.storage." + storage);
							try{
								AbstractReporter reporterObject = 
										reporterClass.getConstructor().newInstance();
								try{
									AbstractFilter filterObject = 
											filterClass.getConstructor().newInstance();
									try{
										AbstractStorage storageObject = 
												storageClass.getConstructor().newInstance();
										FinalCommitFilter finalCommitFilter = new FinalCommitFilter();
										finalCommitFilter.storages.add(storageObject);
										filterObject.setNextFilter(finalCommitFilter);
										try{
											if(storageObject.initialize(storageArguments)){
												try{
													if(filterObject.initialize(filterArguments)){
														reporterObject.setBuffer(new SpecialBuffer(filterObject));
														try{
															if(reporterObject.launch(reporterArguments)){
																while(reporterObject.isRunning()){}
																reporterObject.shutdown();
																filterObject.shutdown();
																storageObject.shutdown();
															}else{
																logger.log(Level.SEVERE, "Failed to launch reporter");
															}
														}catch(Exception e){
															logger.log(Level.SEVERE, "Failed to launch reporter", e);
														}
													}else{
														logger.log(Level.SEVERE, "Failed to launch filter");
													}
												}catch(Exception e){
													logger.log(Level.SEVERE, "Failed to launch filter", e);
												}
											}else{
												logger.log(Level.SEVERE, "Failed to launch storage");
											}
										}catch(Exception e){
											logger.log(Level.SEVERE, "Failed to launch reporter", e);
										}
									}catch(Exception e){
										logger.log(Level.SEVERE, "Failed to instantiate storage", e);
									}
								}catch(Exception e){
									logger.log(Level.SEVERE, "Failed to instantiate filter", e);
								}
							}catch(Exception e){
								logger.log(Level.SEVERE, "Failed to instantiate reporter", e);
							}
						}catch(Exception e){
							logger.log(Level.SEVERE, "Invalid storage class", e);
						}
					}catch(Exception e){
						logger.log(Level.SEVERE, "Invalid filter class", e);
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, "Invalid reporter class", e);
				}
			}
		}
	}
	
	public static void sleepSafe(long millis){
		try{ Thread.sleep(millis); }catch(Exception e){}
	}
}
