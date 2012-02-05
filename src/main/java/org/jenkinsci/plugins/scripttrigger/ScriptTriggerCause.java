package org.jenkinsci.plugins.scripttrigger;

import hudson.model.Cause;

/**
 * @author Gregory Boissinot
 */
@Deprecated
public class ScriptTriggerCause extends Cause {

    public static final String DEFAULT_MESSAGE = "The execution script returns the expected exit code";

    private String cause;

    public ScriptTriggerCause(String cause) {
        if (cause != null) {
            this.cause = cause;
        } else {
            this.cause = DEFAULT_MESSAGE;
        }
    }

    @Override
    public String getShortDescription() {
        return "[ScriptTrigger] - " + cause;
    }
}
