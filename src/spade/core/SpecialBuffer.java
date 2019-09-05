package spade.core;

public class SpecialBuffer extends Buffer{

	private final AbstractFilter filter;
	
	public SpecialBuffer(AbstractFilter filter){
		this.filter = filter;
	}
	
	@Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (incomingVertex == null) {
            return false;
        } else {
        	filter.putVertex(incomingVertex);
        	return true;
        }
    }

	@Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        if ((incomingEdge == null)
                || (incomingEdge.getChildVertex() == null)
                || (incomingEdge.getParentVertex() == null)) {
            return false;
        } else {
        	filter.putEdge(incomingEdge);
            return true;
        }
    }
}
