package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTrigger extends Trigger<BuildableItem> implements Serializable {

    private static Logger LOGGER = Logger.getLogger(GroovyScriptTrigger.class.getName());

    private String groovyExpression;

    private String propertiesFilePath;

    @DataBoundConstructor
    public GroovyScriptTrigger(String cronTabSpec, String groovyExpression, String propertiesFilePath) throws ANTLRException {
        super(cronTabSpec);
        this.groovyExpression = Util.fixEmpty(groovyExpression);
        this.propertiesFilePath = Util.fixEmpty(propertiesFilePath);
    }

    @Override
    public void start(BuildableItem project, boolean newInstance) {
        super.start(project, newInstance);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @SuppressWarnings("unused")
    public String getGroovyExpression() {
        return groovyExpression;
    }

    @SuppressWarnings("unused")
    public String getPropertiesFilePath() {
        return propertiesFilePath;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        ScriptTriggerAction action = new ScriptTriggerAction((AbstractProject) job, getLogFile(), this.getDescriptor().getDisplayName());
        return Collections.singleton(action);
    }

    private FilePath getOneRootNode() {

        AbstractProject p = (AbstractProject) job;
        Label label = p.getAssignedLabel();
        if (label == null) {

            return Hudson.getInstance().getRootPath();
        } else {
            Set<Node> nodes = label.getNodes();
            Node node;
            for (Iterator<Node> it = nodes.iterator(); it.hasNext();) {
                node = it.next();
                FilePath nodePath = node.getRootPath();
                if (nodePath != null) {
                    return nodePath;
                }
            }
            return null;
        }
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
                log.info(String.format("Evaluating the groovy script: \n %s", groovyExpression));
                log.info("Polling complete. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (changed) {
                    log.info("Expression evaluation returns true. Scheduling a build.");

                    //Process the properties file
                    if (propertiesFilePath != null) {
                        File f = new File(propertiesFilePath);
                        if (!f.exists()) {
                            log.info(String.format("Can't load the properties file '%s'. It doesn't exist.", f.getPath()));
                            return;
                        }

                        Properties properties = new Properties();
                        FileInputStream fis = new FileInputStream(f);
                        properties.load(fis);
                        fis.close();

                        List<ParameterValue> parameterValueList = new ArrayList<ParameterValue>();
                        for (Map.Entry property : properties.entrySet()) {
                            ParameterValue parameterValue = new StringParameterValue(
                                    String.valueOf(property.getKey()),
                                    String.valueOf(property.getValue()));
                            parameterValueList.add(parameterValue);
                        }
                        ParametersAction parametersAction = new ParametersAction(parameterValueList);
                        project.scheduleBuild(0, new ScriptTriggerCause(), parametersAction);
                    } else {
                        project.scheduleBuild(new ScriptTriggerCause());
                    }
                } else {
                    log.info("Expression evaluation returns false.");
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
        Boolean evaluationResult = false;
        if (groovyExpression != null) {
            try {
                evaluationResult = executionPath.act(new Callable<Boolean, ScriptTriggerException>() {
                    public Boolean call() throws ScriptTriggerException {
                        GroovyShell shell = new GroovyShell();
                        Object result = shell.evaluate(groovyExpression);
                        return Boolean.valueOf(String.valueOf(result));
                    }
                });
            } catch (IOException ioe) {
                throw new ScriptTriggerException(ioe);
            } catch (InterruptedException ie) {
                throw new ScriptTriggerException(ie);
            } catch (ScriptTriggerException ge) {
                throw new ScriptTriggerException(ge);
            }
        }

        return evaluationResult;
    }

    /**
     * Gets the triggering log file
     *
     * @return the trigger log
     */
    private File getLogFile() {
        return new File(job.getRootDir(), "trigger-script-polling.log");
    }


    @Override
    public void run() {

        GroovyScriptTriggerDescriptor descriptor = getDescriptor();
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


    @Override
    public GroovyScriptTriggerDescriptor getDescriptor() {
        return (GroovyScriptTriggerDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    @SuppressWarnings("unused")
    public static class GroovyScriptTriggerDescriptor extends TriggerDescriptor {

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
            return "Poll with a Groovy script";
        }
    }


}
