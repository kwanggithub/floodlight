package org.projectfloodlight.device.tag;

public class TagException extends Exception {

    static final long serialVersionUID = 289534771027891748L;
    
    static private String makeExceptionMessage(String s) {
        String message = "Tag Manager Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagException() {
        super(makeExceptionMessage(null));
    }
    
    public TagException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
