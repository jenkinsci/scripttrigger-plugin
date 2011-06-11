package org.jenkinsci.plugins.scripttrigger.groovy;

import hudson.model.Cause;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerCause extends Cause {

    @Override
    public String getShortDescription() {
        return "[ScriptTrigger] - Groovy Expression evaluation to true";
    }
}
