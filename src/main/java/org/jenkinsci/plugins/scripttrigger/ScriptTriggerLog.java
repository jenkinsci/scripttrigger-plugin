package org.jenkinsci.plugins.scripttrigger;

import hudson.util.StreamTaskListener;

import java.io.Serializable;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerLog implements Serializable {

    private StreamTaskListener listener;

    public ScriptTriggerLog(StreamTaskListener listener) {
        this.listener = listener;
    }

    public void info(String message) {
        listener.getLogger().println(message);
    }

    public void error(String message) {
        listener.getLogger().println("[ERROR] - " + message);
    }

    public StreamTaskListener getListener() {
        return listener;
    }

    public void closeQuietly() {
        if (listener != null) {
            listener.closeQuietly();
        }
    }
}
