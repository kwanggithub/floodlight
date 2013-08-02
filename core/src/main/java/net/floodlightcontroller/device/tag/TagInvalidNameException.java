package net.floodlightcontroller.device.tag;

public class TagInvalidNameException extends TagException {

    static final long serialVersionUID = 5629989010156160759L;
    
    static private String makeExceptionMessage(String s) {
        String message = "TagInvalidID Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagInvalidNameException() {
        super(makeExceptionMessage(null));
    }
    
    public TagInvalidNameException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagInvalidNameException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
