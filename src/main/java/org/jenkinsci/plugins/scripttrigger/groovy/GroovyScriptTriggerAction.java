package org.jenkinsci.plugins.scripttrigger.groovy;

import hudson.model.AbstractProject;
import org.jenkinsci.plugins.scripttrigger.AbstractTriggerAction;

import java.io.File;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerAction extends AbstractTriggerAction {

    public GroovyScriptTriggerAction(AbstractProject<?, ?> job, File logFile, String label) {
        super(job, logFile, label);
    }

    @SuppressWarnings("unused")
    public String getIconFileName() {
        return "clipboard.gif";
    }

    public String getDisplayName() {
        return "GroovyScriptTrigger Log";
    }

    @SuppressWarnings("unused")
    public String getUrlName() {
        return "groovyScripttriggerPollLog";
    }

}