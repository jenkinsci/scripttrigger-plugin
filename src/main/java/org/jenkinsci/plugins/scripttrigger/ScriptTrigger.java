package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.lib.envinject.service.EnvVarsResolver;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class ScriptTrigger extends AbstractTrigger {

    private String script;

    private String scriptFilePath;

    private String exitCode;

    @DataBoundConstructor
    public ScriptTrigger(String cronTabSpec, LabelRestrictionClass labelRestriction, boolean enableConcurrentBuild, String script, String scriptFilePath, String exitCode) throws ANTLRException {
        super(cronTabSpec, (labelRestriction == null) ? false : true, (labelRestriction == null) ? null : labelRestriction.getTriggerLabel(), enableConcurrentBuild);
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

    @SuppressWarnings("unused")
    public String getExitCode() {
        return exitCode;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        ScriptTriggerAction action = new InternalScriptTriggerAction(getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    public final class InternalScriptTriggerAction extends ScriptTriggerAction {

        private transient String actionTitle;

        public InternalScriptTriggerAction(String actionTitle) {
            this.actionTitle = actionTitle;
        }

        @SuppressWarnings("unused")
        public AbstractProject<?, ?> getOwner() {
            return (AbstractProject) job;
        }

        @Override
        public String getDisplayName() {
            return "ScriptTrigger Log";
        }

        @Override
        public String getUrlName() {
            return "scripttriggerPollLog";
        }

        @Override
        public String getIconFileName() {
            return "clipboard.gif";
        }

        @SuppressWarnings("unused")
        public String getLabel() {
            return actionTitle;
        }

        @SuppressWarnings("unused")
        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        @SuppressWarnings("unused")
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<InternalScriptTriggerAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
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
    protected String getDefaultMessageCause() {
        return "The execution script returns the expected exit code";
    }

    @Override
    protected boolean checkIfModified(Node executingNode, XTriggerLog log) throws ScriptTriggerException {

        int expectedExitCode = getExpectedExitCode();
        log.info("The expected script execution code is " + expectedExitCode);

        return checkIfModifiedWithScriptsEvaluation(executingNode, expectedExitCode, log);
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


    private boolean checkIfModifiedWithScriptsEvaluation(Node executingNode, int expectedExitCode, XTriggerLog log) throws ScriptTriggerException {

        ScriptTriggerExecutor executor = getScriptTriggerExecutor(log);

        EnvVarsResolver envVarsResolver = new EnvVarsResolver();
        Map<String, String> envVars;
        try {
            envVars = envVarsResolver.getPollingEnvVars((AbstractProject) job, executingNode);
        } catch (EnvInjectException e) {
            throw new ScriptTriggerException(e);
        }

        if (script != null) {
            int exitCode = executor.executeScriptAndGetExitCode(executingNode, script, envVars);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        if (scriptFilePath != null) {
            int exitCode = executor.executeScriptPathAndGetExitCode(executingNode, scriptFilePath, envVars);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        return false;
    }

    private ScriptTriggerExecutor getScriptTriggerExecutor(XTriggerLog log) throws ScriptTriggerException {
        return new ScriptTriggerExecutor(log);
    }

    private boolean testExpectedExitCode(int exitCode, int expectedExitCode, XTriggerLog log) {
        log.info(String.format("The exit code is '%s'.", exitCode));
        log.info(String.format("Testing if the script execution code returns '%s'.", expectedExitCode));
        return expectedExitCode == exitCode;
    }


    @Extension
    @SuppressWarnings("unused")
    public static class ScriptTriggerDescriptor extends XTriggerDescriptor {

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
