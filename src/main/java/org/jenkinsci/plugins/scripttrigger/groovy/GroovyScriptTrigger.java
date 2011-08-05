package org.jenkinsci.plugins.scripttrigger.groovy;

import antlr.ANTLRException;
import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.scripttrigger.AbstractTrigger;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerLog;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTrigger extends AbstractTrigger {

    private static Logger LOGGER = Logger.getLogger(GroovyScriptTrigger.class.getName());

    private String groovyExpression;

    private String groovyFilePath;

    private String propertiesFilePath;

    @DataBoundConstructor
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
    public File getLogFile() {
        return new File(job.getRootDir(), "groovyScriptTrigger-polling.log");
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        GroovyScriptTriggerAction action = new GroovyScriptTriggerAction((AbstractProject) job, getLogFile(), getDescriptor().getDisplayName());
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
                        project.scheduleBuild(0, new GroovyScriptTriggerCause(), parametersAction);
                    } else {
                        project.scheduleBuild(new GroovyScriptTriggerCause());
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

    private boolean checkIfModified(final ScriptTriggerLog log) throws ScriptTriggerException {
        FilePath executionPath = getOneRootNode();
        if (executionPath == null) {
            //No trigger the job
            return false;
        }
        Boolean evaluationResult = false;
        if (groovyExpression != null) {
            try {
                final String groovyExpressionResolved = Util.replaceMacro(groovyExpression, EnvVars.masterEnvVars);
                log.info(String.format("Evaluating the groovy script: \n %s", groovyExpression));
                evaluationResult = executionPath.act(new Callable<Boolean, ScriptTriggerException>() {
                    public Boolean call() throws ScriptTriggerException {
                        GroovyShell shell = new GroovyShell();
                        //Evaluate the new script content
                        Object result = shell.evaluate(groovyExpressionResolved);
                        //Return the evaluated result
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

        if (!evaluationResult && (groovyFilePath != null)) {
            try {
                boolean isGroovyFileExist = executionPath.act(new Callable<Boolean, ScriptTriggerException>() {
                    public Boolean call() throws ScriptTriggerException {
                        File f = new File(groovyFilePath);
                        if (!f.exists()) {
                            log.info(String.format("Can't load the groovy file '%s'. It doesn't exist.", f.getPath()));
                            return false;
                        }
                        return true;
                    }
                });

                if (isGroovyFileExist) {
                    log.info(String.format("Evaluating the groovy script file path '%s'", groovyFilePath));
                    evaluationResult = executionPath.act(new Callable<Boolean, ScriptTriggerException>() {
                        public Boolean call() throws ScriptTriggerException {
                            GroovyShell shell = new GroovyShell();
                            Object result;

                            //Get the file Content
                            StringBuffer content = new StringBuffer();
                            try {
                                FileReader fileReader = new FileReader(groovyFilePath);
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

                            //Replace vars
                            String contentVars = Util.replaceMacro(content.toString(), EnvVars.masterEnvVars);
                            //Evaluate the new script content
                            result = shell.evaluate(contentVars);
                            //Return the evaluated result
                            return Boolean.valueOf(String.valueOf(result));
                        }
                    });
                }
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


    @Override
    public void run() {

        if (!Hudson.getInstance().isQuietingDown() && ((AbstractProject) this.job).isBuildable()) {
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
                executorService.shutdown();
                LOGGER.log(Level.SEVERE, "Severe Error during the trigger execution " + t.getMessage());
                t.printStackTrace();
            }
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
            return "[ScriptTrigger] - Poll with a Groovy script";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/scripttrigger/help-groovyScript.html";
        }
    }


}
