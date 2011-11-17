package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.Callable;
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty;
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo;
import org.jenkinsci.plugins.envinject.EnvInjectLogger;
import org.jenkinsci.plugins.envinject.service.EnvInjectEnvVars;
import org.jenkinsci.plugins.envinject.service.EnvInjectVariableGetter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    protected boolean checkIfModifiedByExecutingScript(Node executingNode, ScriptTriggerLog log) throws ScriptTriggerException {

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


    private boolean checkIfModifiedWithScriptsEvaluation(Node executingNode, int expectedExitCode, ScriptTriggerLog log) throws ScriptTriggerException {

        ScriptTriggerExecutor executor = getScriptTriggerExecutor(log);
        Map<String, String> envVars = prepareAndGetEnvVars(executingNode);

        if (script != null) {
            int exitCode = executor.executeScriptAndGetExitCode(executingNode, job, script, envVars);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        if (scriptFilePath != null) {
            int exitCode = executor.executeScriptPathAndGetExitCode(executingNode, job, scriptFilePath, envVars);
            boolean evaluationSucceed = testExpectedExitCode(exitCode, expectedExitCode, log);
            if (evaluationSucceed) {
                return true;
            }
        }

        return false;
    }

    private Map<String, String> prepareAndGetEnvVars(Node executingNode) {
        Map<String, String> overrides = new HashMap<String, String>();
        if (Hudson.getInstance().getPlugin("envinject") != null) {
            try {
                overrides = prepareEnvironmentWithEnvInjectPlugin(executingNode);
            } catch (IOException e) {
                throw new ScriptTriggerException(e);
            } catch (InterruptedException e) {
                throw new ScriptTriggerException(e);
            }
        }

        Map<String, String> env = new HashMap<String, String>();
        try {
            env = getNodeEnvVars(executingNode, job);
        } catch (IOException e) {
            throw new ScriptTriggerException(e);
        } catch (InterruptedException e) {
            throw new ScriptTriggerException(e);
        }

        env.putAll(overrides);
        return env;
    }

    private Map<String, String> getNodeEnvVars(Node executingNode, final Item job) throws ScriptTriggerException, IOException, InterruptedException {
        Map<String, String> env = new HashMap<String, String>();
        env.putAll(executingNode.getRootPath().act(new Callable<Map<String, String>, ScriptTriggerException>() {
            public Map<String, String> call() throws ScriptTriggerException {
                Map<String, String> env = new HashMap<String, String>();
                env.putAll(EnvVars.masterEnvVars);
                return env;
            }
        }));

        env.put("JENKINS_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey()));
        env.put("HUDSON_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey())); // Legacy compatibility
        env.put("JOB_NAME", job.getFullName());
        env.put("JENKINS_HOME", Hudson.getInstance().getRootDir().getPath());
        env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath());   // legacy compatibility
        env.put("NODE_NAME", executingNode.getNodeName());
        env.put("NODE_LABELS", Util.join(executingNode.getAssignedLabels(), " "));

        //Workspace (maybe not exist yet)
        if (job instanceof TopLevelItem) {
            env.put("WORKSPACE", Hudson.getInstance().getWorkspaceFor((TopLevelItem) job).getRemote());
        }

        return env;
    }

    private Map<String, String> prepareEnvironmentWithEnvInjectPlugin(Node executingNode) throws IOException, InterruptedException {
        EnvInjectVariableGetter variableGetter = new EnvInjectVariableGetter();
        boolean isEnvInjectActivated = variableGetter.isEnvInjectJobPropertyActive((Project) job);
        if (isEnvInjectActivated) {
            EnvInjectJobProperty envInjectJobProperty = variableGetter.getEnvInjectJobProperty((Project) job);
            EnvInjectJobPropertyInfo info = envInjectJobProperty.getInfo();
            Map<String, String> infraEnvVarsMater = new HashMap<String, String>();
            Map<String, String> infraEnvVarsNode = new HashMap<String, String>();
            EnvInjectEnvVars envInjectEnvVarsService = new EnvInjectEnvVars(new EnvInjectLogger(listener));
            Map<String, String> vars = envInjectEnvVarsService.processEnvVars(executingNode.getRootPath(), info, infraEnvVarsMater, infraEnvVarsNode);

            return vars;
        }
        return new HashMap<String, String>();
    }

    private ScriptTriggerExecutor getScriptTriggerExecutor(ScriptTriggerLog log) throws ScriptTriggerException {
        return new ScriptTriggerExecutor(getListener(), log);
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
