package net.floodlightcontroller.device.tag;

public class TagInvalidHostMacException extends TagException {

    static final long serialVersionUID = 5629989010156161564L;
    
    static private String makeExceptionMessage(String s) {
        String message = "TagInvalidHostMac Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagInvalidHostMacException() {
        super(makeExceptionMessage(null));
    }
    
    public TagInvalidHostMacException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagInvalidHostMacException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
