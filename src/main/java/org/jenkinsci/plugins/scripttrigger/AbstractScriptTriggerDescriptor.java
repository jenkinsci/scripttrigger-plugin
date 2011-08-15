package org.jenkinsci.plugins.scripttrigger;

import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Gregory Boissinot
 */
public abstract class AbstractScriptTriggerDescriptor extends TriggerDescriptor {

    private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

    public ExecutorService getExecutor() {
        return queue.getExecutors();
    }
}
