package org.jenkinsci.plugins.scripttrigger.groovy;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.plugins.scripttrigger.AbstractScriptTriggerDescriptor;
import org.jenkinsci.plugins.scripttrigger.AbstractTrigger;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerLog;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;


/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTrigger extends AbstractTrigger {

    private String groovyExpression;

    private String groovyFilePath;

    private String propertiesFilePath;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public GroovyScriptTrigger(String cronTabSpec, String groovyExpression, String groovyFilePath, String propertiesFilePath) throws ANTLRException {
        super(cronTabSpec);
        this.groovyExpression = Util.fixEmpty(groovyExpression);
        this.groovyFilePath = Util.fixEmpty(groovyFilePath);
        this.propertiesFilePath = Util.fixEmpty(propertiesFilePath);
    }

    @SuppressWarnings("unused")
    public String getGroovyExpression() {
        return groovyExpression;
    }

    @SuppressWarnings("unused")
    public String getGroovyFilePath() {
        return groovyFilePath;
    }

    @SuppressWarnings("unused")
    public String getPropertiesFilePath() {
        return propertiesFilePath;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        GroovyScriptTriggerAction action = new GroovyScriptTriggerAction((AbstractProject) job, getLogFile(), getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    @Override
    protected File getLogFile() {
        return new File(job.getRootDir(), "groovyScriptTrigger-polling.log");
    }

    @Override
    protected void logChanges(ScriptTriggerLog log) {
        log.info("Expression evaluation returns true. Scheduling a build.");
    }

    @Override
    protected void logNoChanges(ScriptTriggerLog log) {
        log.info("Expression evaluation returns false.");
    }

    @Override
    protected Action[] getScheduleAction(final ScriptTriggerLog log) throws ScriptTriggerException {

        if (propertiesFilePath != null) {
            try {

                Node activeNode = getActiveNode();
                if (activeNode == null) {
                    log.info("No active node for the execution");
                    return null;
                }

                return activeNode.getRootPath()
                        .act(new FilePath.FileCallable<Action[]>() {

                            public Action[] invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                                File propFile = new File(propertiesFilePath);
                                if (!propFile.exists()) {
                                    log.info(String.format("Can't load the properties file '%s'. It doesn't exist.", f.getPath()));
                                    return null;
                                }

                                Properties properties = new Properties();
                                FileInputStream fis = new FileInputStream(propFile);
                                properties.load(fis);
                                fis.close();

                                List<ParameterValue> parameterValueList = new ArrayList<ParameterValue>();
                                for (Map.Entry property : properties.entrySet()) {
                                    ParameterValue parameterValue = new StringParameterValue(
                                            String.valueOf(property.getKey()),
                                            String.valueOf(property.getValue()));
                                    parameterValueList.add(parameterValue);
                                }
                                return new Action[]{new ParametersAction(parameterValueList)};
                            }
                        });
            } catch (IOException ioe) {
                throw new ScriptTriggerException(ioe);
            } catch (InterruptedException ie) {
                throw new ScriptTriggerException(ie);
            }
        }
        return new Action[0];
    }


    @Override
    protected boolean checkIfModifiedByExecutingScript(FilePath executionScriptRootPath, ScriptTriggerLog log) throws ScriptTriggerException {

        GroovyScriptTriggerExecutor executor = getGroovyScriptTriggerExecutor(executionScriptRootPath, log);

        if (groovyExpression != null) {
            boolean evaluationSucceed = executor.evaluateGroovyScript(getGroovyExpression());
            if (evaluationSucceed) {
                return true;
            }
        }

        if (groovyFilePath != null) {
            boolean evaluationSucceed = executor.evaluateGroovyScriptFilePath(groovyFilePath);
            if (evaluationSucceed) {
                return true;
            }
        }

        return false;
    }

    private GroovyScriptTriggerExecutor getGroovyScriptTriggerExecutor(FilePath rootPathExecution, ScriptTriggerLog log) throws ScriptTriggerException {
        return new GroovyScriptTriggerExecutor(rootPathExecution, getListener(), log);
    }


    @Override
    public GroovyScriptTriggerDescriptor getDescriptor() {
        return (GroovyScriptTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    @SuppressWarnings("unused")
    public static class GroovyScriptTriggerDescriptor extends AbstractScriptTriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "[ScriptTrigger] - Poll with a Groovy script";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/scripttrigger/help-groovyScript.html";
        }
    }
}
