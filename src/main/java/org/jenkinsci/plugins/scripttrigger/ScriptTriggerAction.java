package org.jenkinsci.plugins.scripttrigger;

import hudson.model.AbstractProject;

import java.io.File;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerAction extends AbstractTriggerAction {

    public ScriptTriggerAction(AbstractProject<?, ?> job, File logFile, String label) {
        super(job, logFile, label);
    }

    @SuppressWarnings("unused")
    public String getIconFileName() {
        return "clipboard.gif";
    }

    public String getDisplayName() {
        return "ScriptTrigger Log";
    }

    @SuppressWarnings("unused")
    public String getUrlName() {
        return "scripttriggerPollLog";
    }

}