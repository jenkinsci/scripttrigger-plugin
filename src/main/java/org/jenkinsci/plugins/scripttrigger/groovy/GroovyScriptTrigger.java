package org.jenkinsci.plugins.scripttrigger.groovy;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.scripttrigger.AbstractTrigger;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
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
    protected String getDefaultMessageCause() {
        return "Groovy Expression evaluation to true.";
    }

    @Override
    protected Action[] getScheduledActions(Node pollingNode, final XTriggerLog log) throws ScriptTriggerException {

        if (propertiesFilePath != null) {
            try {
                return pollingNode.getRootPath()
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
    protected boolean checkIfModified(Node pollingNode, XTriggerLog log) throws ScriptTriggerException {

        GroovyScriptTriggerExecutor executor = getGroovyScriptTriggerExecutor(log);

        if (groovyExpression != null) {
            boolean evaluationSucceed = executor.evaluateGroovyScript(pollingNode, getGroovyExpression());
            if (evaluationSucceed) {
                return true;
            }
        }

        if (groovyFilePath != null) {
            boolean evaluationSucceed = executor.evaluateGroovyScriptFilePath(pollingNode, groovyFilePath);
            if (evaluationSucceed) {
                return true;
            }
        }

        return false;
    }

    private GroovyScriptTriggerExecutor getGroovyScriptTriggerExecutor(XTriggerLog log) throws ScriptTriggerException {
        return new GroovyScriptTriggerExecutor(log);
    }

    @Extension
    @SuppressWarnings("unused")
    public static class GroovyScriptTriggerDescriptor extends XTriggerDescriptor {

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
