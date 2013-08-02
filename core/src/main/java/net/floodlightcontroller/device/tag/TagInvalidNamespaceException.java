package net.floodlightcontroller.device.tag;

public class TagInvalidNamespaceException extends TagException {

    static final long serialVersionUID = 5629989010156158760L;
    
    static private String makeExceptionMessage(String s) {
        String message = "InvalidNamespace Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagInvalidNamespaceException() {
        super(makeExceptionMessage(null));
    }
    
    public TagInvalidNamespaceException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagInvalidNamespaceException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
