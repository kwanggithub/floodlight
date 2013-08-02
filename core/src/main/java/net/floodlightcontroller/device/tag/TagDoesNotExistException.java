package net.floodlightcontroller.device.tag;

public class TagDoesNotExistException extends TagException {

    static final long serialVersionUID = 5629989010156158790L;
    
    static private String makeExceptionMessage(String s) {
        String message = "TagDoesNotExist Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagDoesNotExistException() {
        super(makeExceptionMessage(null));
    }
    
    public TagDoesNotExistException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagDoesNotExistException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
