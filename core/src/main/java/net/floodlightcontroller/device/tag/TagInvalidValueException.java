package net.floodlightcontroller.device.tag;

public class TagInvalidValueException extends TagException {

    static final long serialVersionUID = 5629989010156160759L;
    
    static private String makeExceptionMessage(String s) {
        String message = "TagInvalidID Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagInvalidValueException() {
        super(makeExceptionMessage(null));
    }
    
    public TagInvalidValueException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagInvalidValueException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
