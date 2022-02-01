/**
 * The MIT License
 * Copyright (c) 2015 Gregory Boissinot and all contributors
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.scripttrigger.groovy;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.lib.envinject.service.EnvVarsResolver;
import org.jenkinsci.lib.xtrigger.XTriggerDescriptor;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.parameterizedscheduler.ParameterizedStaplerRequest;
import org.jenkinsci.plugins.scripttrigger.AbstractTrigger;
import org.jenkinsci.plugins.scripttrigger.LabelRestrictionClass;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import jenkins.model.Jenkins;


/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTrigger extends AbstractTrigger {

    private final String groovyExpression;

    private final String groovyFilePath;

    private final String propertiesFilePath;

    private final boolean groovySystemScript;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public GroovyScriptTrigger(String cronTabSpec, LabelRestrictionClass labelRestriction, boolean enableConcurrentBuild, String groovyExpression, String groovyFilePath, String propertiesFilePath, boolean groovySystemScript) throws ANTLRException {
        super(cronTabSpec, (labelRestriction == null) ? false : true, (labelRestriction == null) ? null : labelRestriction.getTriggerLabel(), enableConcurrentBuild);
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
        GroovyScriptTriggerAction action = new InternalGroovyScriptTriggerAction(getDescriptor().getDisplayName());
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
        Action[] fileParametersActions = new Action[0];
        Action[] cronTabParametersActions = new Action[0];

        if (propertiesFilePath != null) {
            try {
                FilePath rootPath = null;
                if (this.isGroovySystemScript()) {
                    log.info("System script.");
                    // In the case of a system script we run on the master all the time...
                    rootPath = Jenkins.getInstance().getRootPath();
                } else {
                    log.info("Script executed on node.");
                    rootPath = pollingNode.getRootPath();
                }
                if (rootPath == null) {
                    throw new ScriptTriggerException("The node is offline.");
                }
                fileParametersActions = rootPath.act(new FilePath.FileCallable<Action[]>() {

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

                        return getParametersActions(properties);
                    }
                });
            } catch (IOException ioe) {
                throw new ScriptTriggerException(ioe);
            } catch (InterruptedException ie) {
                throw new ScriptTriggerException(ie);
            }
        }

        Properties cronTabParameters = new Properties();
        try {
            cronTabParameters.putAll(
                new ParameterParser().findParameters(getSpec(), new GregorianCalendar()));
        } catch (ANTLRException e) {
            throw new ScriptTriggerException(e);
        }

        if (! cronTabParameters.isEmpty()) {
            cronTabParametersActions = getParametersActions(cronTabParameters);
        }

        if (fileParametersActions.length > 0 && cronTabParametersActions.length > 0) {
            ParametersAction fileParametersAction = (ParametersAction) fileParametersActions[0];
            ParametersAction cronTabParametersAction = (ParametersAction) cronTabParametersActions[0];
            // Cron Tab parameters overrides File parameters.
            return new Action[]{fileParametersAction.merge(cronTabParametersAction)};
        } else if (fileParametersActions.length > 0) {
            return fileParametersActions;
        } else if (cronTabParametersActions.length > 0) {
            return cronTabParametersActions;
        }

        return new Action[0];
    }

    @Override
    protected boolean checkIfModified(Node pollingNode, XTriggerLog log) throws ScriptTriggerException {
        final Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {

            Properties parameters =
                new ParameterParser().findParameters(getSpec(), new GregorianCalendar());
            log.info("[debug] Parameters: " + parameters);

            GroovyScriptTriggerExecutor executor = getGroovyScriptTriggerExecutor(log);
            final AbstractProject proj = (AbstractProject) job;

            EnvVarsResolver envVarsResolver = new EnvVarsResolver();
            Map<String, String> envVars;
            try {
                envVars = envVarsResolver.getPollingEnvVars(proj, pollingNode);
            } catch (EnvInjectException e) {
                throw new ScriptTriggerException(e);
            }

            if (groovyExpression != null) {
                boolean evaluationSucceed = executor.evaluateGroovyScript(pollingNode, proj, getGroovyExpression(), envVars, parameters, groovySystemScript);
                if (evaluationSucceed) {
                    return true;
                }
            }

            if (groovyFilePath != null) {
                boolean evaluationSucceed = executor.evaluateGroovyScriptFilePath(pollingNode, proj, Util.replaceMacro(groovyFilePath, envVars), envVars, parameters, groovySystemScript);
                if (evaluationSucceed) {
                    return true;
                }
            }

            return false;
        } catch (ANTLRException e) {
            throw new ScriptTriggerException(e);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(existingAuth);
        }
    }

    protected Action[] getParametersActions(Properties properties) {
        assert job != null : "job must not be null if this was 'started'";
        ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) ((Job) job)
            .getProperty(ParametersDefinitionProperty.class);
        List<ParameterValue> parameterValueList = new ArrayList<ParameterValue>();
        /* Scan for all parameter with an associated default values */
        for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
            ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

            if (properties.containsKey(paramDefinition.getName())) {
                ParameterizedStaplerRequest request = new ParameterizedStaplerRequest(
                        String.valueOf(properties.get(paramDefinition.getName())));
                parameterValueList.add(paramDefinition.createValue(request));
            } else if (defaultValue != null)
                parameterValueList.add(defaultValue);
        }
        return new Action[]{new ParametersAction(parameterValueList)};
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

    public final class InternalGroovyScriptTriggerAction extends GroovyScriptTriggerAction {

        private transient String actionTitle;

        public InternalGroovyScriptTriggerAction(String actionTitle) {
            this.actionTitle = actionTitle;
        }

        @SuppressWarnings("unused")
        public AbstractProject<?, ?> getOwner() {
            return (AbstractProject) job;
        }

        @Override
        public String getDisplayName() {
            return "GroovyScriptTrigger Log";
        }

        @Override
        public String getUrlName() {
            return "groovyScripttriggerPollLog";
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
            new AnnotatedLargeText<InternalGroovyScriptTriggerAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
    }
}
