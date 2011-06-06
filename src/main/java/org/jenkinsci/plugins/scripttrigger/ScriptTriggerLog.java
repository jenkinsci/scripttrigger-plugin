package org.jenkinsci.plugins.scripttrigger;

import hudson.model.TaskListener;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerLog {

    private TaskListener listener;

    public ScriptTriggerLog(TaskListener listener) {
        this.listener = listener;
    }

    public void info(String message) {
        listener.getLogger().println(message);
    }

    public void error(String message) {
        listener.getLogger().println("[ERROR] - " + message);
    }

}
