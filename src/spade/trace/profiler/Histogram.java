package spade.trace.profiler;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Histogram{

	private final Map<String, Long> counts = new HashMap<String, Long>();
	
	public Histogram(Collection<String> fixedKeys){
		for(String fixedKey : fixedKeys){
			counts.put(fixedKey, 0L);
		}
	}
	
	public Histogram(){}
	
	public void add(String id){
		Long count = counts.get(id);
		if(count == null){
			count = 0L;
		}
		counts.put(id, ++count);
	}
	
	public void clear(){
		for(String x : counts.keySet()){
			counts.put(x, 0L);
		}
	}
	
	public String toString(){
		return entriesToString(getSortedEntriesByValue(counts));
	}
	
	public void toFile(String path, boolean append, boolean printHeading, String firstColumn){
		PrintWriter writer = null;
		try{
			writer = new PrintWriter(new FileWriter(path, append));
			List<Map.Entry<String, Long>> entries = getSortedEntriesByKey(counts);
			String keyString = "";
			String valueString = "";
			if(firstColumn != null){
				keyString += firstColumn + ",";
				valueString += firstColumn + ",";
			}
			for(Map.Entry<String, Long> entry : entries){
				if(entry != null){
					keyString += entry.getKey() + ",";
					valueString += ((entry.getValue() == null) ? 0 : entry.getValue()) + ",";
				}
			}
			if(printHeading){
				writer.println(keyString);
			}
			writer.println(valueString);
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
	
	private String entriesToString(List<Map.Entry<String, Long>> entries){
		if(entries == null){
			return String.valueOf(entries);
		}else{
			String nl = System.lineSeparator();
			String str = "";
			for(Map.Entry<String, Long> entry : entries){
				if(entry == null){
					str += String.valueOf(entry) + nl;
				}else{
					String o = entry.getKey();
					Long l = entry.getValue();
					str += o + "=" + l + nl;
				}
			}
			return str;
		}
	}
	
	private List<Map.Entry<String, Long>> getSortedEntriesByValue(Map<String, Long> map){
		List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(map.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String, Long>>(){
			public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b){
				if(a == null && b == null){
					return 0;
				}else if(a == null && b != null){
					return -1;
				}else if(a != null && b == null){
					return 1;
				}else{
					Long al = a.getValue();
					Long bl = b.getValue();
					if(al == null && bl == null){
						return 0;
					}else if(al == null && bl != null){
						return -1;
					}else if(al != null && bl == null){
						return 1;
					}else{
						if(al < bl){
							return -1;
						}else if(al > bl){
							return 1;
						}else{
							return 0;
						}
					}
				}
				
			}
		});
		return entries;
	}
	
	private List<Map.Entry<String, Long>> getSortedEntriesByKey(Map<String, Long> map){
		List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(map.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String, Long>>(){
			public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b){
				if(a == null && b == null){
					return 0;
				}else if(a == null && b != null){
					return -1;
				}else if(a != null && b == null){
					return 1;
				}else{
					String ao = a.getKey();
					String bo = b.getKey();
					if(ao == null && bo == null){
						return 0;
					}else if(ao == null && bo != null){
						return -1;
					}else if(ao != null && bo == null){
						return 1;
					}else{
						return ao.compareTo(bo);
					}
				}
				
			}
		});
		return entries;
	}
	
	public static void main(String[] args) throws Exception{
		String es[] = {"a", "b", "c", "d", "a", "d"};
		Histogram hist = new Histogram();
		for(String e : es){
			hist.add(e);
		}
		System.out.println(hist);
	}
}
