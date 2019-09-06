package spade.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class ProcessFilter extends AbstractFilter{

	private final static Logger logger = Logger.getLogger(ProcessFilter.class.getName());
	
	private boolean descendents;
	private boolean skeleton;
	private boolean strip; // TODO
	
	private String matchKey, matchValue;
	private OpHandler opHandler;
	
	private static final Map<String, OpHandler> opHandlers = new HashMap<String, OpHandler>();
	static{
		opHandlers.put("=", new EqualHandler());
		opHandlers.put("!=", new NotEqualHandler());
		opHandlers.put("<", new LesserHandler());
		opHandlers.put("<=", new LesserEqualHandler());
		opHandlers.put(">", new GreaterHandler());
		opHandlers.put(">=", new GreaterEqualsHandler());
		opHandlers.put("~", new RegexHandler());
	}
	
//	private Map<String, AbstractVertex> originalProcessHashToNewProcessVertex = null;
	
	private Set<AbstractVertex> verticesPut = new HashSet<AbstractVertex>();
	
	private Set<AbstractVertex> relevantProcesses = new HashSet<AbstractVertex>();
	
	private static String mustGetNonEmptyValue(String arguments, Map<String, String> map, String key) throws Exception{
		if(map == null){
			logger.log(Level.SEVERE, "NULL map for arguments: " + arguments);
			throw new Exception();
		}else{
			String value = map.get(key);
			if(value == null){
				logger.log(Level.SEVERE, "NULL value for key '" + key + "' in arguments: " + arguments);
				throw new Exception();
			}else{
				value = value.trim();
				if(value.isEmpty()){
					logger.log(Level.SEVERE, "Empty value for key '" + key + "' in arguments: " + arguments);
					throw new Exception();
				}else{
					return value;
				}
			}
		}
	}
	
	private static boolean mustParseBoolean(String key, String value) throws Exception{
		if(value == null){
			logger.log(Level.SEVERE, "NULL value for key '"+key+"'");
			throw new Exception();
		}else{
			value = value.toLowerCase();
			switch(value){
				case "0": case "off": case "false": case "no": return false;
				case "1": case "on": case "true": case "yes": return true;
				default: logger.log(Level.SEVERE, "Invalid boolean '"+value+"' for key '"+key+"'"); throw new Exception();
			}
		}
	}
	
	private static OpHandler mustParseOp(String arguments, Map<String, String> map, String key) throws Exception{
		String opString = mustGetNonEmptyValue(arguments, map, key);
		OpHandler opHandler = opHandlers.get(opString);
		if(opHandler == null){
			logger.log(Level.SEVERE, "NULL operation handler for '"+opString+"'");
			throw new Exception();
		}else{
			return opHandler;
		}
	}
	
	@Override
	public boolean initialize(String arguments){
		try{
			Map<String, String> map = CommonFunctions.parseKeyValPairs(arguments);
			
			String subtreeString = mustGetNonEmptyValue(arguments, map, "descendents");
			this.descendents = mustParseBoolean("descendents", subtreeString);
			
			String skeletonString = mustGetNonEmptyValue(arguments, map, "skeleton");
			this.skeleton = mustParseBoolean("skeleton", skeletonString);
			
			String stripString = mustGetNonEmptyValue(arguments, map, "strip");
			this.strip = mustParseBoolean("strip", stripString);
			
			this.matchKey = mustGetNonEmptyValue(arguments, map, "key");
			this.matchValue = mustGetNonEmptyValue(arguments, map, "value");
			this.opHandler = mustParseOp(arguments, map, "op");
			
			if(opHandler instanceof RegexHandler){
				((RegexHandler)opHandler).init(matchValue);
			}
			
			return true;
		}catch(Exception e){
			return false;
		}
	}
	
	@Override
	public boolean shutdown(){
		return true;
	}
	
	private boolean isProcess(AbstractVertex vertex){
		if(vertex == null){
			return false;
		}else{
			String type = vertex.type();
			if(type == null){
				return false;
			}else{
				return type.equals(OPMConstants.PROCESS);
			}
		}
	}
	
	private boolean isProcessMatch(AbstractVertex vertex){
		if(vertex == null){
			return false;
		}else{
			String value = vertex.getAnnotation(matchKey);
			return opHandler.match(value, matchValue);
		}
	}
	
	private boolean isRelevantProcessVertex(AbstractVertex vertex){
		return (isProcess(vertex) && isProcessMatch(vertex));
	}
	
	private String getHash(AbstractVertex vertex){
		if(vertex == null){
			return null;
		}else{
			return vertex.bigHashCode();
		}
	}
	
	private AbstractVertex stripArtifact(AbstractVertex vertex){
		AbstractVertex artifact = new Artifact();
		artifact.addAnnotations(vertex.getAnnotations());
		artifact.removeAnnotation(OPMConstants.ARTIFACT_VERSION);
		artifact.removeAnnotation(OPMConstants.ARTIFACT_EPOCH);
		artifact.removeAnnotation(OPMConstants.ARTIFACT_PERMISSIONS);
		return artifact;
	}
	
	private AbstractVertex stripProcess(AbstractVertex vertex){
		AbstractVertex process = new Process();
		process.addAnnotations(vertex.getAnnotations());
		process.removeAnnotation(OPMConstants.PROCESS_CWD);
		process.removeAnnotation(OPMConstants.AGENT_GID);
		process.removeAnnotation(OPMConstants.AGENT_EGID);
//		process.removeAnnotation(OPMConstants.AGENT_UID);
		process.removeAnnotation(OPMConstants.AGENT_EUID);
		return process;
	}
	
	@Override
	public void putVertex(AbstractVertex vertex){}
	
	@Override
	public void putEdge(AbstractEdge edge){
		try{
			if(edge != null){
				String edgeType = edge.type();
				if(edgeType != null){
					switch(edgeType){
						case OPMConstants.WAS_TRIGGERED_BY:{
							AbstractVertex newProcess = edge.getChildVertex();
							AbstractVertex oldProcess = edge.getParentVertex();
							boolean put = false;
							boolean isOldProcessRelevant = isRelevantProcessVertex(oldProcess);
							if(isOldProcessRelevant){
								// Parent is the matched one so child becomes relevant automatically
								put = true;
								relevantProcesses.add(oldProcess);
								relevantProcesses.add(newProcess);
							}else{
								boolean isNewProcessRelevant = isRelevantProcessVertex(newProcess);
								if(isNewProcessRelevant){
									// Parent is not relevant
									put = true;
									relevantProcesses.add(newProcess);
								}else{
									if(relevantProcesses.contains(oldProcess)){
										put = true;
										relevantProcesses.add(newProcess);
									}else{
										if(relevantProcesses.contains(newProcess)){
											put = true;
										}
									}
								}
							}
							if(put){
								if(verticesPut.add(oldProcess)){ super.putInNextFilter(oldProcess); }
								if(verticesPut.add(newProcess)){ super.putInNextFilter(newProcess); }
								super.putInNextFilter(edge);
							}
						}
						break;
						case OPMConstants.WAS_GENERATED_BY:{
							if(!skeleton){
								AbstractVertex process = edge.getParentVertex();
								AbstractVertex artifact = edge.getChildVertex();
								boolean put = false;
								if(relevantProcesses.contains(process)){
									put = true;
								}else{
									boolean isProcessRelevant = isRelevantProcessVertex(process);
									if(isProcessRelevant){
										put = true;
										relevantProcesses.add(process);
									}
								}
								if(put){
									if(verticesPut.add(process)){ super.putInNextFilter(process); }
									if(verticesPut.add(artifact)){ super.putInNextFilter(artifact); }
									super.putInNextFilter(edge);
								}
							}
						}
						break;
						case OPMConstants.USED:{
							if(!skeleton){
								AbstractVertex process = edge.getChildVertex();
								AbstractVertex artifact = edge.getParentVertex();
								boolean put = false;
								if(relevantProcesses.contains(process)){
									put = true;
								}else{
									boolean isProcessRelevant = isRelevantProcessVertex(process);
									if(isProcessRelevant){
										put = true;
										relevantProcesses.add(process);
									}
								}
								if(put){
									if(verticesPut.add(process)){ super.putInNextFilter(process); }
									if(verticesPut.add(artifact)){ super.putInNextFilter(artifact); }
									super.putInNextFilter(edge);
								}
							}
						}
						break;
						default: break;
					}
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to put edge: " + edge, e);
		}
	}
	
	private static interface OpHandler{
		public boolean match(String valueToCheck, String valueToMatch);
	}
	
	private static class EqualHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToMatch == "null"){
				return valueToCheck == null;
			}else{
				if(valueToCheck == null && valueToMatch == null){
					return true;
				}else if(valueToCheck != null && valueToMatch == null){
					return false;
				}else if(valueToCheck == null && valueToMatch != null){
					return false;
				}else{
					return valueToCheck.equals(valueToMatch);
				}
			}
		}
	}
	
	private static class NotEqualHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToMatch == "null"){
				return valueToCheck != null;
			}else{
				if(valueToCheck == null && valueToMatch == null){
					return false;
				}else if(valueToCheck != null && valueToMatch == null){
					return true;
				}else if(valueToCheck == null && valueToMatch != null){
					return true;
				}else{
					return !valueToCheck.equals(valueToMatch);
				}
			}
		}
	}
	
	private static class GreaterHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToCheck == null && valueToMatch == null){
				return false;
			}else if(valueToCheck != null && valueToMatch == null){
				return false;
			}else if(valueToCheck == null && valueToMatch != null){
				return false;
			}else{
				try{
					double doubleToCheck = Double.parseDouble(valueToCheck);
					double doubleToMatch = Double.parseDouble(valueToMatch);
					return doubleToCheck > doubleToMatch;
				}catch(Exception e){
					return false;
				}
			}
		}
	}
	
	private static class GreaterEqualsHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToCheck == null && valueToMatch == null){
				return false;
			}else if(valueToCheck != null && valueToMatch == null){
				return false;
			}else if(valueToCheck == null && valueToMatch != null){
				return false;
			}else{
				try{
					double doubleToCheck = Double.parseDouble(valueToCheck);
					double doubleToMatch = Double.parseDouble(valueToMatch);
					return doubleToCheck >= doubleToMatch;
				}catch(Exception e){
					return false;
				}
			}
		}
	}
	
	private static class LesserHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToCheck == null && valueToMatch == null){
				return false;
			}else if(valueToCheck != null && valueToMatch == null){
				return false;
			}else if(valueToCheck == null && valueToMatch != null){
				return false;
			}else{
				try{
					double doubleToCheck = Double.parseDouble(valueToCheck);
					double doubleToMatch = Double.parseDouble(valueToMatch);
					return doubleToCheck < doubleToMatch;
				}catch(Exception e){
					return false;
				}
			}
		}
	}
	
	private static class LesserEqualHandler implements OpHandler{
		public boolean match(String valueToCheck, String valueToMatch){
			if(valueToCheck == null && valueToMatch == null){
				return false;
			}else if(valueToCheck != null && valueToMatch == null){
				return false;
			}else if(valueToCheck == null && valueToMatch != null){
				return false;
			}else{
				try{
					double doubleToCheck = Double.parseDouble(valueToCheck);
					double doubleToMatch = Double.parseDouble(valueToMatch);
					return doubleToCheck <= doubleToMatch;
				}catch(Exception e){
					return false;
				}
			}
		}
	}
	
	private static class RegexHandler implements OpHandler{
		private Pattern pattern = null;
		public void init(String valueToMatch) throws Exception{
			try{
				pattern = Pattern.compile(valueToMatch);
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to initialize pattern: " + valueToMatch, e);
				throw e;
			}
		}
		public boolean match(String valueToCheck, String valueToMatch){
			if(pattern == null){
				logger.log(Level.SEVERE, "Uninitialized pattern");
				return false;
			}else{
				if(valueToCheck == null){
					return false;
				}else{
					return pattern.matcher(valueToCheck).matches();
				}
			}
		}
	}
}
