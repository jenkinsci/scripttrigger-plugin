package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends Trigger<BuildableItem> implements Serializable {

    private static Logger LOGGER = Logger.getLogger(AbstractTrigger.class.getName());

    protected transient TaskListener listener;

    public AbstractTrigger(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    @Override
    public abstract AbstractScriptTriggerDescriptor getDescriptor();

    @Override
    public void run() {

        if (!Hudson.getInstance().isQuietingDown() && ((AbstractProject) this.job).isBuildable()) {
            AbstractScriptTriggerDescriptor descriptor = getDescriptor();
            ExecutorService executorService = descriptor.getExecutor();
            try {
                try {
                    listener = new StreamTaskListener(getLogFile(), Charset.forName("UTF-8"));
                } catch (IOException e) {
                    listener = null;
                }
                ScriptTriggerLog log = new ScriptTriggerLog(getListener());
                if (job instanceof AbstractProject) {
                    AsynchronousTask asynchronousTask = new AsynchronousTask((AbstractProject) job, log);
                    executorService.execute(asynchronousTask);
                }

            } catch (Throwable t) {
                executorService.shutdown();
                LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    protected TaskListener getListener() {
        return listener;
    }

    protected abstract File getLogFile();

    protected class AsynchronousTask implements Runnable, Serializable {

        private AbstractProject project;

        private ScriptTriggerLog log;

        private AsynchronousTask(AbstractProject project, ScriptTriggerLog log) {
            this.project = project;
            this.log = log;
        }

        public void run() {

            try {
                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(log);
                log.info("Polling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    logChanges(log);
                    project.scheduleBuild(0, new ScriptTriggerCause(), getScheduleAction(log));
                } else {
                    logNoChanges(log);
                }
            } catch (ScriptTriggerException e) {
                log.error("Polling error " + e.getMessage());
            } catch (Throwable e) {
                log.error("SEVERE - Polling error " + e.getMessage());
            }
        }
    }

    protected abstract void logChanges(ScriptTriggerLog log);

    protected abstract void logNoChanges(ScriptTriggerLog log);

    protected boolean checkIfModified(ScriptTriggerLog log) throws ScriptTriggerException {
        Node executingNode = getActiveNode();
        if (isNodeOff(executingNode)) {
            log.info("The execution node is off.");
            return false;
        }

        assert executingNode != null;
        FilePath executionScriptRootPath = executingNode.getRootPath();
        assert executionScriptRootPath != null;
        log.info("Polling on " + getNodeName(executingNode));

        return checkIfModifiedByExecutingScript(executionScriptRootPath, log);
    }

    private boolean isNodeOff(Node node) {
        if (node == null) {
            return true;
        }
        return false;
    }

    private String getNodeName(Node node) {
        assert node != null;
        String name = node.getNodeName();
        if (name == null || name.length() == 0) {
            name = "master";
        }
        return name;
    }

    protected abstract boolean checkIfModifiedByExecutingScript(FilePath executionScriptRootPath, ScriptTriggerLog log);

    protected Action[] getScheduleAction(ScriptTriggerLog log) throws ScriptTriggerException {
        return new Action[0];
    }

    protected Node getActiveNode() {
        AbstractProject p = (AbstractProject) job;
        Label label = p.getAssignedLabel();
        if (label == null) {
            return Hudson.getInstance();
        } else {
            Set<Node> nodes = label.getNodes();
            Node node;
            for (Iterator<Node> it = nodes.iterator(); it.hasNext();) {
                node = it.next();
                FilePath nodePath = node.getRootPath();
                if (nodePath != null) {
                    return node;
                }
            }
            return null;
        }
    }

//    protected TaskListener getListener() throws ScriptTriggerException {
//        FileOutputStream fos = null;
//        try {
//            fos = new FileOutputStream(getLogFile());
//            TaskListener listener = new StreamBuildListener(fos);
//            return listener;
//        } catch (FileNotFoundException fne) {
//            throw new ScriptTriggerException(fne);
//        } finally {
//            try {
//                if (fos != null) {
//                    fos.close();
//                }
//            } catch (IOException ioe) {
//                throw new ScriptTriggerException(ioe);
//            }
//        }
//    }
}
