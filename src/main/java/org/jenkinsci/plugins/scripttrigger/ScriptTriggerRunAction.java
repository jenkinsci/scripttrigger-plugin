package org.jenkinsci.plugins.scripttrigger;

import hudson.model.InvisibleAction;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerRunAction extends InvisibleAction {

    private String description;

    public ScriptTriggerRunAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
