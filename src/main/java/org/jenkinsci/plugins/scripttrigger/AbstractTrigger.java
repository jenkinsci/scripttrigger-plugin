package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.util.StreamTaskListener;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends Trigger<BuildableItem> implements Serializable {

    private static Logger LOGGER = Logger.getLogger(AbstractTrigger.class.getName());


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
            StreamTaskListener listener;
            try {
                try {
                    listener = new StreamTaskListener(getLogFile(), Charset.forName("UTF-8"));
                } catch (IOException e) {
                    listener = null;
                }
                ScriptTriggerLog scriptTriggerLog = new ScriptTriggerLog(listener);
                if (job instanceof AbstractProject) {
                    AsynchronousTask asynchronousTask = new AsynchronousTask((AbstractProject) job, scriptTriggerLog, getLogFile());
                    executorService.execute(asynchronousTask);
                }
            } catch (Throwable t) {
                executorService.shutdown();
                LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    protected abstract File getLogFile();

    protected class AsynchronousTask implements Runnable, Serializable {

        private AbstractProject project;

        private ScriptTriggerLog scriptTriggerLog;

        private File logFile;

        public AsynchronousTask(AbstractProject project, ScriptTriggerLog scriptTriggerLog, File logFile) {
            this.project = project;
            this.scriptTriggerLog = scriptTriggerLog;
            this.logFile = logFile;
        }

        public void run() {

            try {
                long start = System.currentTimeMillis();
                scriptTriggerLog.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(scriptTriggerLog);
                scriptTriggerLog.info("Polling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    logChanges(scriptTriggerLog);
                    String scriptContent = Util.loadFile(logFile);
                    String cause = extractRootCause(scriptContent);
                    Action[] actions = getScheduledActions(scriptContent);
                    project.scheduleBuild(0, new ScriptTriggerCause(cause), actions);
                } else {
                    logNoChanges(scriptTriggerLog);
                }
            } catch (ScriptTriggerException e) {
                scriptTriggerLog.error("Polling error " + e.getMessage());
            } catch (Throwable e) {
                scriptTriggerLog.error("SEVERE - Polling error " + e.getMessage());
            } finally {
                scriptTriggerLog.closeQuietly();
            }
        }

        private Action[] getScheduledActions(String scriptContent) throws IOException {
            List<Action> actionList = new ArrayList<Action>();
            actionList.addAll(Arrays.asList(getScheduleAction(scriptTriggerLog)));
            String description = extractDescription(scriptContent);
            if (description != null) {
                actionList.add(new ScriptTriggerRunAction(description));
            }
            return actionList.toArray(new Action[actionList.size()]);
        }

        private String extractRootCause(String content) throws IOException {
            return StringUtils.substringBetween(content, "<cause>", "</cause>");
        }

        private String extractDescription(String content) throws IOException {
            return StringUtils.substringBetween(content, "<description>", "</description>");
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

        return checkIfModifiedByExecutingScript(executingNode, log);
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

    protected abstract boolean checkIfModifiedByExecutingScript(Node executingNode, ScriptTriggerLog log);

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

}
