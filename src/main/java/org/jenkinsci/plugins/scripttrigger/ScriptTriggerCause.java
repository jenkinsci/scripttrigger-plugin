package org.jenkinsci.plugins.scripttrigger;

import hudson.model.Cause;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerCause extends Cause {

    @Override
    public String getShortDescription() {
        return "[ScriptTrigger] - The execution script returns the expected exit code";
    }
}
