package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory Boissinot
 */
public class ScriptTrigger extends AbstractTrigger {

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
    public Collection<? extends Action> getProjectActions() {
        ScriptTriggerAction action = new ScriptTriggerAction((AbstractProject) job, getLogFile(), getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    @Override
    public ScriptTrigger.ScriptTriggerDescriptor getDescriptor() {
        return (ScriptTrigger.ScriptTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Override
    protected File getLogFile() {
        return new File(job.getRootDir(), "scriptTrigger-polling.log");
    }

    @Override
    protected void logChanges(ScriptTriggerLog log) {
        log.info("The script returns the expected code. Scheduling a build.");
    }

    @Override
    protected void logNoChanges(ScriptTriggerLog log) {
        log.info("No changes. The script doesn't return the expected code or it can't be evaluated.");
    }


    @Override
    protected boolean checkIfModified(ScriptTriggerLog log) throws ScriptTriggerException {

        FilePath executionScriptRootPath = getExecutionNodeRootPath();
        if (isNodeOff(executionScriptRootPath)) {
            log.info("The execution node is off.");
            return false;
        }

        assert executionScriptRootPath != null;

        int expectedExitCode = getExpectedExitCode();
        log.info("The expected script execution code will be " + expectedExitCode);

        return checkIfModifiedWithScriptsEvaluation(executionScriptRootPath, expectedExitCode, log);
    }

    private boolean isNodeOff(FilePath executionScriptRootPath) {
        //if the node is off, the value is null, return no modification
        if (executionScriptRootPath == null) {
            return true;
        }
        return false;
    }

    private int getExpectedExitCode() throws ScriptTriggerException {

        //not set
        if (exitCode == null) {
            return 0;
        }

        try {
            return Integer.parseInt(exitCode);
        } catch (NumberFormatException nfe) {
            throw new ScriptTriggerException(String.format("The given exit code must be a numeric value. The given value is '%s'.", exitCode));
        }
    }


    private boolean checkIfModifiedWithScriptsEvaluation(FilePath rootPathExecution, int expectedExitCode, ScriptTriggerLog log) throws ScriptTriggerException {

        TaskListener listener = getListener();
        ScriptTriggerExecutor executor = getScriptTriggerExecutor(rootPathExecution, listener, log);

        if (script != null) {
            int exitCode = executor.executeScriptAndGetExitCode(script);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        if (scriptFilePath != null) {
            int exitCode = executor.executeScriptPathAndGetExitCode(scriptFilePath);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        return false;
    }

    private ScriptTriggerExecutor getScriptTriggerExecutor(FilePath rootPathExecution, TaskListener listener, ScriptTriggerLog log) throws ScriptTriggerException {
        return new ScriptTriggerExecutor(rootPathExecution, listener, log);
    }

    private boolean testExpectedExitCode(int exitCode, int expectedExitCode, ScriptTriggerLog log) {
        log.info(String.format("The exit code is '%s'.", exitCode));
        log.info(String.format("Testing if the script execution code returns '%s'.", expectedExitCode));
        return expectedExitCode == exitCode;
    }


    @Extension
    @SuppressWarnings("unused")
    public static class ScriptTriggerDescriptor extends AbstractScriptTriggerDescriptor {

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
