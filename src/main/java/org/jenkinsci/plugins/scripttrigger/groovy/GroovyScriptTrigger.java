package org.jenkinsci.plugins.scripttrigger.groovy;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.lib.envinject.service.EnvVarsResolver;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.scripttrigger.AbstractTrigger;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;


/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTrigger extends AbstractTrigger {

    private String groovyExpression;

    private String groovyFilePath;

    private String propertiesFilePath;
    
    private boolean groovySystemScript;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public GroovyScriptTrigger(String cronTabSpec, String groovyExpression, String groovyFilePath, String propertiesFilePath, boolean groovySystemScript) throws ANTLRException {
        super(cronTabSpec);
        this.groovyExpression = Util.fixEmpty(groovyExpression);
        this.groovyFilePath = Util.fixEmpty(groovyFilePath);
        this.propertiesFilePath = Util.fixEmpty(propertiesFilePath);
        this.groovySystemScript = groovySystemScript;
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
    
    public boolean isGroovySystemScript() {
        return groovySystemScript;
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
        final Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {

            GroovyScriptTriggerExecutor executor = getGroovyScriptTriggerExecutor(log);
            
            EnvVarsResolver envVarsResolver = new EnvVarsResolver();
            Map<String, String> envVars;
            try {
                envVars = envVarsResolver.getPollingEnvVars((AbstractProject) job, pollingNode);
            } catch (EnvInjectException e) {
                throw new ScriptTriggerException(e);
            }
    
            if (groovyExpression != null) {
                boolean evaluationSucceed = executor.evaluateGroovyScript(pollingNode, job, getGroovyExpression(), envVars, groovySystemScript);
                if (evaluationSucceed) {
                    return true;
                }
            }
    
            if (groovyFilePath != null) {
                boolean evaluationSucceed = executor.evaluateGroovyScriptFilePath(pollingNode, job, groovyFilePath, envVars, groovySystemScript);
                if (evaluationSucceed) {
                    return true;
                }
            }
    
            return false;
        }
        finally {
            SecurityContextHolder.getContext().setAuthentication(existingAuth);
        }
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
