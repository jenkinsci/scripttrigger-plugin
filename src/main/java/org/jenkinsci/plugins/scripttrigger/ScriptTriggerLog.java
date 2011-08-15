package org.jenkinsci.plugins.scripttrigger;

import hudson.model.TaskListener;

import java.io.Serializable;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerLog implements Serializable {

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
