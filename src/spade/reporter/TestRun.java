package spade.reporter;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractFilter;
import spade.core.AbstractReporter;
import spade.core.AbstractStorage;
import spade.core.SpecialBuffer;
import spade.filter.FinalCommitFilter;

public class TestRun{

	private final static Logger logger = Logger.getLogger(TestRun.class.getName());
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception{
		String reporter = null, filter = null, storage = null;
		String reporterArguments = null, filterArguments = null, storageArguments = null;
		boolean error = false;
		for(String arg : args){
			String[] toks = arg.split("=", 2);
			String key = toks[0];
			String value = toks[1];
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
