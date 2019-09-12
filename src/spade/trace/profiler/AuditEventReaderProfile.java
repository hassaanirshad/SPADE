package spade.trace.profiler;

public class AuditEventReaderProfile{
	
	private final Perioder eventConstructionPerioder = new Perioder();
	private final Perioder recordReadPerioder = new Perioder();
	private final Perioder recordParsePerioder = new Perioder();
	
	public void eventConstructionStart(){
		eventConstructionPerioder.start();
	}
	
	public void eventConstructionEnd(){
		eventConstructionPerioder.end();
	}
	
	public void recordReadStart(){
		recordReadPerioder.start();
	}
	
	public void recordReadEnd(){
		recordReadPerioder.end();
	}
	
	public void recordParseStart(){
		recordParsePerioder.start();
	}
	
	public void recordParseEnd(){
		recordParsePerioder.end();
	}
	
	public void shutdown(){
		String eventConstructionFile = ProfileConfig.getOutFilePath("aue-event-constuction-perioder.csv");
		String recordReadFile = ProfileConfig.getOutFilePath("aue-record-read-perioder.csv");
		String recordParseFile = ProfileConfig.getOutFilePath("aue-record-parse-perioder.csv");
		eventConstructionPerioder.toFile(eventConstructionFile);
		recordReadPerioder.toFile(recordReadFile);
		recordParsePerioder.toFile(recordParseFile);
	}
}
