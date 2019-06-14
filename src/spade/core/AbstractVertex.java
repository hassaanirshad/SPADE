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
package spade.core;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.codec.digest.DigestUtils;

import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;

/**
 * This is the class from which other vertex classes (e.g., OPM vertices) are
 * derived.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractVertex implements Serializable
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 4766085487390172973L;
	/**
     * A map containing the annotations for this vertex.
     */
    protected Map<String, String> annotations = new TreeMap<>();

    /**
     * An integer indicating the depth of the vertex in the graph
     */
    private int depth;

    /**
     * String big hash to be returned by bigHashCode function only if not null.
     * If null then big hash computed using the annotations map.
     */
    private final String bigHashCode;

    /**
     * Create a vertex without a fixed big hash.
     */
    public AbstractVertex(){
    	this(null);
    }

    /**
     * Create a vertex with a fixed big hash.
     * 
     * @param bigHashCode String
     */
    public AbstractVertex(String bigHashCode){
    	this.bigHashCode = bigHashCode;
    }

    public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}

	/**
     * Checks if vertex is empty
     *
     * @return Returns true if vertex contains no annotation
     */
    public final boolean isEmpty()
    {
        return annotations.size() == 0;
    }

    /**
     * Returns the map containing the annotations for this vertex.
     *
     * @return The map containing the annotations.
     */
    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Adds an annotation.
     *
     * @param key The annotation key.
     * @param value The annotation value.
     */
    public final void addAnnotation(String key, String value)
    {
        if(!CommonFunctions.isNullOrEmpty(key))
        {
            if(value == null)
            {
                value = "";
            }
            annotations.put(key, value);
        }
    }

    /**
     * Adds a map of annotation.
     *
     * @param newAnnotations New annotations to be added.
     */
    public final void addAnnotations(Map<String, String> newAnnotations)
    {
        for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet())
        {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            if(!CommonFunctions.isNullOrEmpty(key))
            {
                if(value == null)
                {
                    value = "";
                }
                addAnnotation(key, value);
            }
        }
    }

    /**
     * Removes an annotation.
     *
     * @param key The annotation key to be removed.
     * @return The annotation that is removed, or null of no such annotation key
     * existed.
     */
    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    /**
     * Gets an annotation.
     *
     * @param key The annotation key.
     * @return The value of the annotation corresponding to the key.
     */
    public final String getAnnotation(String key) {
        return annotations.get(key);
    }

    /**
     * Gets the type of this vertex.
     *
     * @return A string indicating the type of this vertex.
     */
    public final String type() {
        return annotations.get(OPMConstants.TYPE);
    }

    /**
     * Returns true if the vertex has a fixed big hash otherwise false
     * 
     * @return true/false
     */
    public final boolean isReferenceVertex(){
    	return bigHashCode != null;
    }
    
    /**
     * Computes MD5 hash of annotations in the vertex if fixed hash field is null 
     * else returns the fixed hash field.
     *
     @return String
     */
    public final String bigHashCode()
    {
    	if(bigHashCode == null){
    		return DigestUtils.md5Hex(this.toString());
    	}else{
    		return bigHashCode;
    	}
    }

    /**
     * Computes MD5 hash of annotations in the vertex if fixed hash field is null
     * else returns the fixed hash field bytes.
     * 
     * @return bytes array
     */
    public final byte[] bigHashCodeBytes()
    {
    	if(bigHashCode == null){
    		return DigestUtils.md5(this.toString());
    	}else{
    		return bigHashCode.getBytes();
    	}
    }

    public boolean isCompleteNetworkVertex()
    {
        String subtype = this.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
        String source = this.getAnnotation(OPMConstants.SOURCE);
        if(subtype != null && subtype.equalsIgnoreCase(OPMConstants.SUBTYPE_NETWORK_SOCKET)
                && source.equalsIgnoreCase(OPMConstants.SOURCE_AUDIT_NETFILTER))
        {
            return true;
        }

        return false;
    }

    public boolean isNetworkVertex()
    {
        String subtype = this.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
        if(subtype != null && subtype.equalsIgnoreCase(OPMConstants.SUBTYPE_NETWORK_SOCKET))
        {
            return true;
        }

        return false;
    }
    
    /**
     * Computes a function of the annotations in the vertex.
     *
     * This takes less time to compute than bigHashCode() but is less collision-resistant.
     *
     * @return An integer-valued hash code.
     */
    @Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((annotations == null) ? 0 : annotations.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		AbstractVertex other = (AbstractVertex) obj;
		if(annotations == null){
			if(other.annotations != null)
				return false;
		}else if(!annotations.equals(other.annotations))
			return false;
		return true;
	}
	
    @Override
    public String toString()
    {
        return "AbstractVertex{" +
                "annotations=" + annotations +
                '}';
    }
}
