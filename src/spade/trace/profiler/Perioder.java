package spade.trace.profiler;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Perioder implements Iterable<Long>{

	private final List<Long> timestamps = new ArrayList<Long>();
	
	private int started = 0;
	
	public synchronized void start(){
		switch(started){
			case 1: timestamps.set(timestamps.size() - 1, System.currentTimeMillis()); break;
			case 0: timestamps.add(System.currentTimeMillis()); started = 1; break;
			default: break;
		}
	}
	
	public synchronized void end(){
		switch(started){
			case 1: timestamps.add(System.currentTimeMillis()); started = 0; break;
			default: break;
		}
	}
	
	public int size(){
		return timestamps.size() / 2;
	}
	
	public synchronized String toString(){
		String str = "";
		for(Long diff : this){
			str += diff + ", ";
		}
		long n = size();
		double sum = getSum(iterator(), n);
		double mean = getMean((long)sum, n);
		double stdDev = getStdDev(mean, n, iterator());
		str += n + ", " + sum + ", " + mean + ", " + stdDev;
		return str;
	}
	
	public synchronized void toFile(String path){
		PrintWriter writer = null;
		try{
			writer = new PrintWriter(path);
			for(Long diff : this){
				writer.print(diff + ", ");
			}
			long n = size();
			double sum = getSum(iterator(), n);
			double mean = getMean((long)sum, n);
			double stdDev = getStdDev(mean, n, iterator());
			writer.print(n + ", " + sum + ", " + mean + ", " + stdDev);
		}catch(Exception e){
			Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Failed to write to path: " + path, e);
		}finally{
			if(writer != null){
				try{
					writer.close();
				}catch(Exception e){
					
				}
			}
		}
	}
	
	public static double getSum(Iterator<Long> iterator, long n){
		double sum = 0;
		long a = 0;
		while(iterator.hasNext()){
			if(a >= n){
				break;
			}
			a++;
			sum += iterator.next();
		}
		return sum;
	}
	
	public static double getMean(long sum, long n){
		return (double)sum / (double)n;
	}
	
	public static double getStdDev(double mean, long n, Iterator<Long> iterator){
		double sumSquared = 0;
		long a = 0;
		while(iterator.hasNext()){
			if(a >= n){
				break;
			}
			a++;
			long next = iterator.next();
			double diff = mean - (double)next;
			sumSquared += (diff * diff);
		}
		return Math.pow((sumSquared / (double)n), 0.5);
	}
	
	public static void sleepSafe(long millis){
		try{ Thread.sleep(millis); }catch(Exception e){}
	}
	
	public static void main(String[] args){
		Perioder p = new Perioder();
		for(int i = 0; i < 10; i++){
			p.start();
			sleepSafe(30 + (long)(Math.random() * 170));
			p.end();
			sleepSafe(2);
		}
		
		p.toFile("/Users/hassaanirshad/Documents/vmshare/tc-logs/xy");
	}

	@Override
	public Iterator<Long> iterator(){
		return new MyIterator();
	}
	
	private class MyIterator implements Iterator<Long>{
		private int position = 0;
		@Override
		public boolean hasNext(){
			return !(position + 2 > timestamps.size()); 
		}
		@Override
		public Long next(){
			long start = timestamps.get(position++);
			long end = timestamps.get(position++);
			long diff = end - start;
			return diff;
		}
	}
}
