/**
 * 
 */
package net.floodlightcontroller.device.tag;

public class TagInvalidSwitchInterfaceException extends Exception {

    private static final long serialVersionUID = 5404945233581737905L;

    static private String makeExceptionMessage(String s) {
        String message = "TagInvalidSwitchInterface Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public TagInvalidSwitchInterfaceException() {
        super(makeExceptionMessage(null));
    }
    
    public TagInvalidSwitchInterfaceException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public TagInvalidSwitchInterfaceException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
