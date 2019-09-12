package spade.trace.profiler;

public class CDMProfile{

	public static final int engagement = ProfileConfig.getEngagement();
	
	public static final CDMProfile instance = new CDMProfile();
	private CDMProfile(){}
	
	private final Histogram datumCounts = new Histogram();
	
	private final Perioder putVertexPerioder = new Perioder();
	private final Perioder putEdgePerioder = new Perioder();
	
	public void newDatum(String name){
		datumCounts.add(name);
	}
	
	public void putVertexStart(){
		putVertexPerioder.start();
	}
	
	public void putVertexEnd(){
		putVertexPerioder.end();
	}
	
	public void putEdgeStart(){
		putEdgePerioder.start();
	}
	
	public void putEdgeEnd(){
		putEdgePerioder.end();
	}
	
	public void shutdown(){
		String datumHistogramPath = ProfileConfig.getOutFilePath("cdm-datum-histogram.csv");
		String putVertexPerioderPath = ProfileConfig.getOutFilePath("cdm-putvertex-perioder.csv");
		String putEdgePerioderPath = ProfileConfig.getOutFilePath("cdm-putedge-perioder.csv");
		datumCounts.toFile(datumHistogramPath, false, false, String.valueOf(engagement));
		putVertexPerioder.toFile(putVertexPerioderPath);
		putEdgePerioder.toFile(putEdgePerioderPath);
	}
}
