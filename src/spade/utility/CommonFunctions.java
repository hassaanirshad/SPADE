/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.utility;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;

import spade.core.Settings;

public class CommonFunctions {

	private static final Logger logger = Logger.getLogger(CommonFunctions.class.getName());
	// Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
	
    /**
     * Converts a string of the format [a="b" c=d e=f] into a map of key values
     * Any portions of the string not matching the pattern [a="b"] or [c=d] are ignored
     * 
     * Uses the pattern {@link #pattern_key_value pattern_key_value}
     * 
     * @param messageData string to parse
     * @return a hashmap
     */
    public static Map<String, String> parseKeyValPairs(String messageData) {
    	/*
         * Takes a string with keyvalue pairs and returns a Map Input e.g.
         * "key1=val1 key2=val2" etc. Input string validation is callee's
         * responsibility
         */
    	Map<String, String> keyValPairs = new HashMap<String, String>();
    	if(messageData == null || messageData.trim().isEmpty()){
    		return keyValPairs;
    	}
        Matcher key_value_matcher = pattern_key_value.matcher(messageData);
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1).trim(), key_value_matcher.group(2).trim());
        }
        return keyValPairs;
    }
    
    /**
	 * Input: a=b c='d' e="f f"
	 * Output: Map{a=b, c=d, e=f f}
	 * 
	 * If empty string then empty hashmap returned
	 * 
	 * @param str string to parsee
	 * @return HashMap in result or error
	 */
	public static Result<HashMap<String, String>> parseKeysValuesInString(String str){
		if(str == null){
			return Result.failed("NULL string to parse keys-values from");
		}else{
			HashMap<String, String> map = new HashMap<String, String>();
			String trimmed = str.trim();
			int startFrom = 0;
			while(startFrom < trimmed.length()){
				int a = trimmed.indexOf('=', startFrom);
				if(a < 0){
					// No other key
					break;
				}else{
					String key = trimmed.substring(startFrom, a).trim();
					int b = a+1;
					if(b >= trimmed.length()){
						String value = "";
						map.put(key, value);
						break; // length exceeded
					}else{
						char z = trimmed.charAt(b);
						boolean quotes = false;
						String valueEndsWith = null;
						if(z == '\''){
							valueEndsWith = "'"; quotes = true;
						}else if(z == '"'){
							valueEndsWith = "\""; quotes = true;
						}
						if(quotes){
							int c = b+1;
							if(c >= trimmed.length()){
								// Malformed because of missing ending quote and premature string end
								return Result.failed("No ending quote in str='"+str+"'");
							}else{
								int d = trimmed.indexOf(valueEndsWith, c);
								if(d < 0){
									// Malformed because of missing ending quote
									return Result.failed("No ending quote in str='"+str+"'");
								}else{
									int e = d+1;
									String value = trimmed.substring(b, e).trim();
									value = value.substring(1, value.length() - 1);
									map.put(key, value);
									startFrom = e;
								}
							}
						}else{
							int e = trimmed.indexOf(' ', b);
							if(e < 0){
								e = trimmed.length();
							}
							String value = trimmed.substring(b, e).trim();
							map.put(key, value);
							startFrom = e;
						}
					}
				}
			}
			return Result.successful(map);
		}
	}
    
    /**
     * Gets the default config file path by class.
     * Reads the config file as key value map (if the file exists)
     * Overwrites the config key values by key value specified in arguments
     * Returns the final map if success
     * 
     * @param clazz the class to get the default config file for
     * @param arguments arguments as key value (can be null or empty)
     * @return map
     * @throws Exception 1) Failed to read config file, 2) Failed to check if file exists, 3) Failed to check if file is regular, 4) Failed to create file object
     */
    public static Map<String, String> getGlobalsMapFromConfigAndArguments(Class<?> clazz, String arguments) throws Exception{
		Map<String, String> map = new HashMap<String, String>();
		String configFilePath = Settings.getDefaultConfigFilePath(clazz);
		try{
			File configFile = new File(configFilePath);
			try{
				if(configFile.exists() && configFile.isFile()){
					try{
						map.putAll(FileUtility.readConfigFileAsKeyValueMap(configFilePath, "="));
					}catch(Exception e){
						throw new Exception("Failed to read config file: " + configFilePath, e);
					}
				}
			}catch(Exception e){
				throw new Exception("Failed to check if file exists, and is regular file: " + configFilePath, e);
			}
		}catch(Exception e){
			throw new Exception("Failed to create file object: " + configFilePath, e);
		}
		
		if(arguments != null){
			map.putAll(parseKeyValPairs(arguments));
		}
		
		return map;
	}
    
    /**
     * Convenience wrapper function for Integer.parseInt. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param string string to parse
     * @param defaultValue value to return in case of exception
     * @return integer representation of the string
     */
    public static Integer parseInt(String string, Integer defaultValue){
    	try{
    		return Integer.parseInt(string);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
     * Convenience wrapper function for Long.parseLong. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param str string to parse
     * @param defaultValue value to return in case of exception
     * @return long representation of the string
     */
    public static Long parseLong(String str, Long defaultValue){
    	try{
    		return Long.parseLong(str);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
     * Convenience wrapper function for Double.parseDouble. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param str string to parse
     * @param defaultValue value to return in case of exception
     * @return double representation of the string
     */
    public static Double parseDouble(String str, Double defaultValue){
    	try{
    		return Double.parseDouble(str);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
	 * Parse string to boolean
	 * 
	 * @param str string with value to parse
	 * @return Result with boolean or error
	 */
	public static Result<Boolean> parseBoolean(String str){
		if(str == null){
			return Result.failed("Not a boolean: NULL");
		}else{
			String formattedStr = str.trim().toLowerCase();
			if(formattedStr.isEmpty()){
				return Result.failed("Not a boolean: '"+str+"'");
			}else{
				switch(formattedStr){
					case "true":
					case "1":
					case "on":
					case "yes":
						return Result.successful(true);
					case "false":
					case "0":
					case "off":
					case "no":
						return Result.successful(false);
					default:
						return Result.failed("Not a boolean: '"+str+"'");
				}
			}
		}
	}
	
	/**
	 * Returns true if the radix is in the correct range otherwise false
	 * 
	 * @param radix
	 * @return true/false
	 */
	public static boolean validateNumberRadix(int radix){
		return radix >= (int)Character.MIN_RADIX && radix <= (int)Character.MAX_RADIX;
	}
	
	/**
	 * Parse string to double in the range [min-max]
	 * 
	 * @param str value
	 * @param min minimum value
	 * @param max maximum value
	 * @return Result with double or error
	 */
	public static Result<Double> parseDouble(String str, double min, double max){
		if(str == null){
			return Result.failed("Not a double: NULL");
		}else{
			try{
				Double d = Double.parseDouble(str);
				if(d >= min && d <= max){
					return Result.successful(d);
				}else{
					return Result.failed("Double '"+d+"' not in range: ["+min+"-"+max+"]");
				}
			}catch(Exception e){
				return Result.failed("Not a double: '"+str+"'");
			}
		}
	}
	
	/**
	 * Parse string to long in the range [min-max] with the given radix
	 * 
	 * @param str value
	 * @param radix base to parse number with
	 * @param min minimum value
	 * @param max maximum value
	 * @return Result with long or error
	 */
	public static Result<Long> parseLong(String str, int radix, long min, long max){
		if(str == null){
			return Result.failed("Not a long: NULL");
		}else if(!validateNumberRadix(radix)){
			return Result.failed("Not a valid radix '"+radix+"' for long: '"+str+"'");
		}else{
			try{
				long l = Long.parseLong(str, radix);
				if(l >= min && l <= max){
					return Result.successful(l);
				}else{
					return Result.failed("Long '"+l+"' not in range: ["+min+"-"+max+"]");
				}
			}catch(NumberFormatException nfe){
				return Result.failed("Not a long with radix '"+radix+"': '"+str+"'");
			}
		}
	}
    
    /**
     * Convenience function to get a map with keys converted to lowercase.
     * 
     * Responsibility of the caller to make sure that different keys don't 
     * become the same after converted to lowercase.
     * 
     * Throws NPE if the map is the null
     * 
     * @param map map to convert
     * @return a hashmap
     */
    public static <T> Map<String, T> makeKeysLowerCase(Map<String, T> map){
    	Map<String, T> resultMap = new HashMap<String, T>();
    	for(Map.Entry<String, T> entry : map.entrySet()){
    		resultMap.put(entry.getKey().toLowerCase(), entry.getValue());
    	}
    	return resultMap;
    }
    
    /**
     * Convenience function to check if a string is null or is empty
     * 
     * The str argument is trimmed before the empty check
     * 
     * @param str string to check
     * @return true if null or empty, otherwise false
     */
    public static boolean isNullOrEmpty(String str){
    	return (str == null || str.trim().isEmpty());
    }
    
    /**
     * Decodes the hex string. Null if failed.
     * 
     * @param hexString string in hex format
     * @return converted ascii string
     */
    public static String decodeHex(String hexString){
		if(hexString == null){
			return null;
		}else{
			try{
				return new String(Hex.decodeHex(hexString.toCharArray()));
			}catch(Exception e){
				// ignore
				return null;
			}
		}
	}
    
    /**
     * Create hex string from ascii
     * 
     * @param string
     * @return converted hex string
     */
    public static String encodeHex(String string){
    	return Hex.encodeHexString(String.valueOf(string).getBytes());
    }

    public static List<String> mapToLines(Map<String, String> map, String keyValueSeparator){
    	if(map == null){
    		return null;
    	}else{
    		List<String> lines = new ArrayList<String>();
    		for(Map.Entry<String, String> entry : map.entrySet()){
    			String key = entry.getKey();
    			String value = entry.getValue();
    			lines.add(key + keyValueSeparator + value);
    		}
    		return lines;
    	}
    }
    
    /**
     * Returns hostname gotten by executing the command 'uname -n'
     * 
     * If failed to get the hostname then error message printed and null returned.
     * If succeeded then always returns a non-null value.
     * 
     * @return null/hostname
     */
    public static String getHostNameByUname(){
    	String command = "uname -n";
		try{
			Execute.Output output = Execute.getOutput(command);
			if(output.hasError()){
				output.log();
			}else{
				List<String> stdOutLines = output.getStdOut();
				if(stdOutLines == null || stdOutLines.isEmpty()){
					logger.log(Level.SEVERE, "NULL/Empty '" + command + "' output.");
				}else{
					String hostName = stdOutLines.get(0);
					if(hostName != null){
						return hostName;
					}else{
						logger.log(Level.SEVERE, "NULL '" + command + "' output line.");
					}
				}
			}
		}catch(Throwable e){
			logger.log(Level.SEVERE, "Failed to execute command '" + command + "'", e);
		}
		return null;
    }
    
    /**
	 * Converts the string value to enum object
	 * 
	 * @param <X> enum type
	 * @param clazz class of enum
	 * @param value string value
	 * @return matching enum instance for the value
	 */
	public static <X extends Enum<X>> Result<X> parseEnumValue(Class<X> clazz, String value){
		if(clazz == null){
			return Result.failed("NULL enum class");
		}else if(CommonFunctions.isNullOrEmpty(value)){
			return Result.failed("NULL/Empty enum value for class: '"+clazz.getName()+"'");
		}else{
			for(X x : clazz.getEnumConstants()){
				if(x.name().equals(value)){
					return Result.successful(x);
				}
			}
			return Result.failed("Value '"+value+"' not defined for enum '"+clazz.getName()+"'");
		}
	}
	
	
	
	/**
	 * Convenience function to avoid NULL check when checking if two objects are equal.
	 * 
	 * The object passed must have implement the equals method correctly.
	 * 
	 * Do NOT use for arrays.
	 * 
	 * @param o1
	 * @param o2
	 * @return true/false
	 */
	public static <T> boolean objectsEqual(T o1, T o2){
		if(o1 == null && o2 == null){
			return true;
		}else if((o1 == null && o2 != null) || (o1 != null && o2 == null)){
			return false;
		}else{
			return o1.equals(o2);
		}
	}
	
	public static <T> boolean bigIntegerEquals(BigInteger o1, BigInteger o2){
		if(o1 == null && o2 == null){
			return true;
		}else if((o1 == null && o2 != null) || (o1 != null && o2 == null)){
			return false;
		}else{
			return o1.equals(o2);
		}
	}
	
	/**
	 * @param e Exception
	 * @return formatted exception stacktrace string
	 */
	public static String formatExceptionStackTrace(Exception e){
		if(e == null){
			return "(null)";
		}else{
			StringWriter buffer = new StringWriter();
			PrintWriter writer = new PrintWriter(buffer);
			e.printStackTrace(writer);
			writer.close();
			return buffer.toString();
		}		
	}
}
