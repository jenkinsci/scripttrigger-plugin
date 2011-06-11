package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Gregory Boissinot
 */
public class ScriptTrigger extends AbstractTrigger {

    private static Logger LOGGER = Logger.getLogger(ScriptTrigger.class.getName());

    private String script;

    private String exitCode;

    @DataBoundConstructor
    public ScriptTrigger(String cronTabSpec, String script, String exitCode) throws ANTLRException {
        super(cronTabSpec);
        this.script = script;
        this.exitCode = Util.fixEmpty(exitCode);
    }

    @SuppressWarnings("unused")
    public String getScript() {
        return script;
    }

    @Override
    public File getLogFile() {
        return new File(job.getRootDir(), "scriptTrigger-polling.log");
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        ScriptTriggerAction action = new ScriptTriggerAction((AbstractProject) job, getLogFile(), getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    /**
     * Asynchronous task
     */
    protected class Runner implements Runnable, Serializable {

        private AbstractProject project;

        private ScriptTriggerLog log;

        Runner(AbstractProject project, ScriptTriggerLog log) {
            this.project = project;
            this.log = log;
        }

        public void run() {

            try {
                long start = System.currentTimeMillis();
                log.info("Polling started on " + DateFormat.getDateTimeInstance().format(new Date(start)));
                boolean changed = checkIfModified(log);
                log.info(String.format("Evaluating the  script: \n %s", script));
                log.info("Polling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    log.info("The script returns the expected code. Scheduling a build.");
                    project.scheduleBuild(new ScriptTriggerCause());
                } else {
                    log.info("The script doesn't return the expected code.");
                }
            } catch (ScriptTriggerException e) {
                log.error("Polling error " + e.getMessage());
            } catch (Throwable e) {
                log.error("SEVERE - Polling error " + e.getMessage());
            }
        }
    }

    private boolean checkIfModified(ScriptTriggerLog log) throws ScriptTriggerException {
        FilePath executionPath = getOneRootNode();
        if (executionPath == null) {
            //No trigger the job
            return false;
        }

        try {
            TaskListener listener = new StreamBuildListener(new FileOutputStream(getLogFile()));
            final Launcher launcher = Hudson.getInstance().createLauncher(listener);
            CommandInterpreter batchRunner;
            if (launcher.isUnix()) {
                batchRunner = new Shell(script);
            } else {
                batchRunner = new BatchFile(script);
            }
            FilePath tmpFile = batchRunner.createScriptFile(executionPath);
            int cmdCode = launcher.launch().cmds(batchRunner.buildCommandLine(tmpFile)).stdout(listener).pwd(executionPath).join();
            log.info(String.format("The exit code is: %s", cmdCode));

            if (exitCode == null) {
                return cmdCode == 0;
            }

            log.info(String.format("Testing the expected exit code: '%s'", exitCode));
            if (this.exitCode.equals(cmdCode)) {
                return true;
            }

        } catch (Exception e) {
            throw new ScriptTriggerException(e);
        }

        return false;
    }

    @Override
    public void run() {

        if (!Hudson.getInstance().isQuietingDown() && ((AbstractProject) this.job).isBuildable()) {
            ScriptTriggerDescriptor descriptor = getDescriptor();
            ExecutorService executorService = descriptor.getExecutor();
            StreamTaskListener listener;
            try {
                listener = new StreamTaskListener(getLogFile());
                ScriptTriggerLog log = new ScriptTriggerLog(listener);
                if (job instanceof AbstractProject) {
                    Runner runner = new Runner((AbstractProject) job, log);
                    executorService.execute(runner);
                }

            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
                t.printStackTrace();
            }
        }
    }


    @Override
    public ScriptTriggerDescriptor getDescriptor() {
        return (ScriptTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    @SuppressWarnings("unused")
    public static class ScriptTriggerDescriptor extends TriggerDescriptor {

        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        public ExecutorService getExecutor() {
            return queue.getExecutors();
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "[ScriptTrigger] - Poll with a shell or batch script";
        }
    }

}
