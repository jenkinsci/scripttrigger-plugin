package org.jenkinsci.plugins.scripttrigger;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerException extends Exception {

    public ScriptTriggerException(String message) {
        super(message);
    }

    public ScriptTriggerException(String messsage, Throwable throwable) {
        super(messsage, throwable);
    }

    public ScriptTriggerException(Throwable throwable) {
        super(throwable);
    }

}
