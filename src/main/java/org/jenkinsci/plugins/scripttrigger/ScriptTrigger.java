package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.*;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
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

    private String scriptFilePath;

    private String exitCode;

    @DataBoundConstructor
    public ScriptTrigger(String cronTabSpec, String script, String scriptFilePath, String exitCode) throws ANTLRException {
        super(cronTabSpec);
        this.script = Util.fixEmpty(script);
        this.scriptFilePath = Util.fixEmpty(scriptFilePath);
        this.exitCode = Util.fixEmpty(exitCode);
    }

    @SuppressWarnings("unused")
    public String getScript() {
        return script;
    }

    @SuppressWarnings("unused")
    public String getScriptFilePath() {
        return scriptFilePath;
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

    private boolean checkIfModified(final ScriptTriggerLog log) throws ScriptTriggerException {
        FilePath executionPath = getOneRootNode();
        if (executionPath == null) {
            //No trigger the job
            return false;
        }

        try {

            boolean evaluationResult = false;
            int expectedExitCode;
            if (exitCode == null) {
                expectedExitCode = 0;
            } else {
                try {
                    expectedExitCode = Integer.parseInt(exitCode);
                } catch (NumberFormatException nfe) {
                    log.info(String.format("The given exit code must be a numeric value. The given value is '%s'.", exitCode));
                    return false;
                }
            }

            TaskListener listener = new StreamBuildListener(new FileOutputStream(getLogFile()));
            final Launcher launcher = Hudson.getInstance().createLauncher(listener);
            CommandInterpreter batchRunner;

            if (script != null) {
                final String scriptContentResolved = Util.replaceMacro(script, EnvVars.masterEnvVars);
                log.info(String.format("Evaluating the script: \n %s", script));
                if (launcher.isUnix()) {
                    batchRunner = new Shell(scriptContentResolved);
                } else {
                    batchRunner = new BatchFile(scriptContentResolved);
                }
                FilePath tmpFile = batchRunner.createScriptFile(executionPath);
                int cmdCode = launcher.launch().cmds(batchRunner.buildCommandLine(tmpFile)).stdout(listener).pwd(executionPath).join();
                log.info(String.format("The exit code is '%s'.", cmdCode));
                log.info(String.format("Testing if the script execution code returns : '%s'.", expectedExitCode));
                if (expectedExitCode == cmdCode) {
                    evaluationResult = true;
                }
            }

            if (!evaluationResult && (scriptFilePath != null)) {

                log.info(String.format("Evaluating the script file path '%s'.", scriptFilePath));
                boolean isFileExist = executionPath.act(new Callable<Boolean, ScriptTriggerException>() {
                    public Boolean call() throws ScriptTriggerException {
                        File f = new File(scriptFilePath);
                        if (!f.exists()) {
                            log.info(String.format("Can't load the file '%s'. It doesn't exist.", f.getPath()));
                            return false;
                        }
                        return true;
                    }
                });

                if (isFileExist) {

                    //Get the file Content
                    StringBuffer content = new StringBuffer();
                    try {
                        FileReader fileReader = new FileReader(scriptFilePath);
                        BufferedReader bufferedReader = new BufferedReader(fileReader);
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            content.append(line);
                        }
                    } catch (FileNotFoundException fne) {
                        throw new ScriptTriggerException(fne);
                    } catch (IOException ioe) {
                        throw new ScriptTriggerException(ioe);
                    }

                    if (launcher.isUnix()) {
                        batchRunner = new Shell(content.toString());
                    } else {
                        batchRunner = new BatchFile(content.toString());
                    }
                    FilePath tmpFile = batchRunner.createScriptFile(executionPath);
                    int cmdCode = launcher.launch().cmds(batchRunner.buildCommandLine(tmpFile)).stdout(listener).pwd(executionPath).join();
                    log.info(String.format("The exit code is '%s'.", cmdCode));
                    log.info(String.format("Testing if the script execution code returns : '%s'.", expectedExitCode));
                    if (expectedExitCode == cmdCode) {
                        evaluationResult = true;
                    }
                }
            }

            return evaluationResult;

        } catch (Exception e) {
            throw new ScriptTriggerException(e);
        }
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
                executorService.shutdown();
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

        @Override
        public String getHelpFile() {
            return "/plugin/scripttrigger/help-script.html";
        }
    }

}
