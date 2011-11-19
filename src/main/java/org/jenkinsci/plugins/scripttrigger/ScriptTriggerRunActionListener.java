package org.jenkinsci.plugins.scripttrigger;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
@Extension
public class ScriptTriggerRunActionListener extends RunListener<Run> {

    @Override
    public void onStarted(Run run, TaskListener listener) {
        ScriptTriggerRunAction scriptTriggerRunAction = run.getAction(ScriptTriggerRunAction.class);
        if (scriptTriggerRunAction != null) {
            try {
                String description = scriptTriggerRunAction.getDescription();
                if (description != null) {
                    run.setDescription(description);
                }
            } catch (IOException ioe) {
                listener.getLogger().println("[ScriptTrigger] - Error to set the descriptor.");
            }
        }
    }
}
