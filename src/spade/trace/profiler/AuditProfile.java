package spade.trace.profiler;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

public class AuditProfile{

	public static final int engagement = ProfileConfig.getEngagement();
	
	public static final AuditProfile instance = new AuditProfile();
	
	private final Map<String, ReadWriteCount> unitMemoryStats = new HashMap<String, ReadWriteCount>();
	
	private final Map<String, Perioder> syscallPerioder = new HashMap<String, Perioder>();
	
	private final Histogram vertexHistogram = new Histogram();
	private final Histogram edgeHistogram = new Histogram();
	
	private AuditProfile(){}
	
	public void unitMemoryWrite(String pid, String address){
		String memory = pid+","+address;
		ReadWriteCount c = unitMemoryStats.get(memory);
		if(c == null){
			c = new ReadWriteCount();
			unitMemoryStats.put(memory, c);
		}
		c.w++;
	}
	
	public void unitMemoryRead(String pid, String address){
		String memory = pid+","+address;
		ReadWriteCount c = unitMemoryStats.get(memory);
		if(c == null){
			c = new ReadWriteCount();
			unitMemoryStats.put(memory, c);
		}
		c.r++;
		if(c.w > 0){
			c.d++;
			newEdge("unit dependency");
		}
	}
	
	public void syscallStart(String syscallName){
		syscallName = String.valueOf(syscallName).toLowerCase();
		Perioder perioder = syscallPerioder.get(syscallName);
		if(perioder == null){
			perioder = new Perioder();
			syscallPerioder.put(syscallName, perioder);
		}
		perioder.start();
	}
	
	public void syscallEnd(String syscallName){
		syscallName = String.valueOf(syscallName).toLowerCase();
		Perioder perioder = syscallPerioder.get(syscallName);
		if(perioder != null){
			perioder.end();
		}
	}
	
	public void syscallEndException(String syscallName, Exception e){
		syscallName = String.valueOf(syscallName).toLowerCase();
		Perioder perioder = syscallPerioder.get(syscallName);
		if(perioder != null){
			perioder.end();
		}
	}
	
	private void newVertex(String type){
		vertexHistogram.add(type);
	}
	
	private void newEdge(String operation){
		edgeHistogram.add(operation);
	}
	
	public void newVertexCommon(AbstractVertex vertex){
		switch(engagement){
			case 1: newVertex_tc1(vertex); break;
			case 2: newVertex_tc2(vertex); break;
			case 3: newVertex_tc3(vertex); break;
			case 4: newVertex_tc4(vertex); break;
			case 5: newVertex_tc5(vertex); break;
			default: newVertex_tcUnknown(vertex); break;
		}
	}
	
	public void newEdgeCommon(AbstractEdge edge){
		switch(engagement){
			case 1: newEdge_tc1(edge); break;
			case 2: newEdge_tc2(edge); break;
			case 3: newEdge_tc3(edge); break;
			case 4: newEdge_tc4(edge); break;
			case 5: newEdge_tc5(edge); break;
			default: newEdge_tcUnknown(edge); break;
		}
	}
	
	private void newVertex_tc1(AbstractVertex vertex){
		if(vertex != null){
    		String type = vertex.type();
    		if(type != null){
    			switch(type){
    				case "Process":{
    					String unit = vertex.getAnnotation("unit");
    					if(unit == null){
    						newVertex("Process");
    					}else{
    						switch(unit){
    							case "0": newVertex("Process"); break;
    							default: newVertex("Unit"); break;
    						}
    					}
    				}
    				break;
    				case "Artifact":{
    					String subtype = vertex.getAnnotation("subtype");
    					if(subtype == null){
    						newVertex("null subtype");
    					}else{
    						newVertex(subtype);
    					}
    				}
    				break;
    				default: break;
    			}
    		}else{
    			newVertex("null vertex type");
    		}
    	}else{
    		newVertex("null vertex");
    	}
	}
	
	private void newEdge_tc1(AbstractEdge edge){
		if(edge == null){
			newEdge("null edge");
		}else{
			String operation = edge.getAnnotation("operation");
			if(operation == null){
				String type = edge.type();
				if(type == null){
					newEdge("null edge type");
				}else{
					newEdge(type);
				}
			}else{
				newEdge(operation);
			}
		}
	}
	
	private void newVertex_tc2(AbstractVertex vertex){
		newVertex_tc1(vertex);
	}
	
	private void newEdge_tc2(AbstractEdge edge){
		newEdge_tc1(edge);
	}
	
	private void newVertex_tc3(AbstractVertex vertex){}
	private void newEdge_tc3(AbstractEdge edge){}
	private void newVertex_tc4(AbstractVertex vertex){}
	private void newEdge_tc4(AbstractEdge edge){}
	private void newVertex_tc5(AbstractVertex vertex){}
	private void newEdge_tc5(AbstractEdge edge){}
	
	private void newVertex_tcUnknown(AbstractVertex vertex){}
	private void newEdge_tcUnknown(AbstractEdge edge){}
	
	public void shutdown(){
		String vertexHistogramPath = ProfileConfig.getOutFilePath("audit-vertex-histogram.csv");
		String edgeHistogramPath = ProfileConfig.getOutFilePath("audit-edge-histogram.csv");
		vertexHistogram.toFile(vertexHistogramPath, false, true, String.valueOf(engagement));
		edgeHistogram.toFile(edgeHistogramPath, false, true, String.valueOf(engagement));
		
		for(Map.Entry<String, Perioder> entry : syscallPerioder.entrySet()){
			String key = entry.getKey();
			Perioder perioder = entry.getValue();
			if(key != null && perioder != null){
				String syscallPerioderPath = ProfileConfig.getOutFilePath("audit-syscall-" + key + "-perioder.csv");
				perioder.toFile(syscallPerioderPath);
			}
		}
		
		String unitMemoryPath = ProfileConfig.getOutFilePath("audit-unit-memory.csv");
		PrintWriter writer = null;
		try{
			long depCountOverall = 0;
			long depCountUnique = 0;
			writer = new PrintWriter(unitMemoryPath);
			for(Map.Entry<String, ReadWriteCount> entry : unitMemoryStats.entrySet()){
				String mem = entry.getKey();
				ReadWriteCount c = entry.getValue();
				if(mem != null && c != null){
					depCountOverall += c.d;
					if(c.d > 0){
						depCountUnique++;
					}
					writer.print(mem+",");
					writer.print(c.r + ",");
					writer.print(c.w + ",");
					writer.print(c.d + ",");
					writer.println();
				}
			}
			writer.println("dep count overall, " + depCountOverall);
			writer.println("dep count unique, " + depCountUnique);
		}catch(Exception e){
			Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Failed to write path: " + unitMemoryPath, e);
		}finally{
			if(writer != null){
				try{
					writer.close();
				}catch(Exception e){
					
				}
			}
		}
	}
	
	private static class ReadWriteCount{
		private long r = 0, w = 0, d = 0; // read, write, dep
	}
}
