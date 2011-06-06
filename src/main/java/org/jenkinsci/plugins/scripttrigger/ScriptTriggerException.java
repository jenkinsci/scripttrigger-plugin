package org.jenkinsci.plugins.scripttrigger;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerException extends Exception {

    public ScriptTriggerException() {
    }

    public ScriptTriggerException(String s) {
        super(s);
    }

    public ScriptTriggerException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ScriptTriggerException(Throwable throwable) {
        super(throwable);
    }
}
