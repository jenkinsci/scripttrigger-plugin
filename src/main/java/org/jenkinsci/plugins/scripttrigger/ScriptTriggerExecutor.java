package org.jenkinsci.plugins.scripttrigger;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;

import java.io.*;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerExecutor implements Serializable {

    protected FilePath executionNodeRootPath;

    protected TaskListener listener;

    protected ScriptTriggerLog log;

    public ScriptTriggerExecutor(FilePath executionNodeRootPath, TaskListener listener, ScriptTriggerLog log) {
        this.executionNodeRootPath = executionNodeRootPath;
        this.listener = listener;
        this.log = log;
    }

    public int executeScriptAndGetExitCode(final String scriptContent) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("A scriptContent object must be set.");
        }

        String scriptContentResolved = getResolvedContentWithEnvVars(scriptContent);
        return executeScript(scriptContentResolved);
    }


    public int executeScriptPathAndGetExitCode(String scriptFilePath) throws ScriptTriggerException {

        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        if (!existsScript(scriptFilePath)) {
            throw new ScriptTriggerException(String.format("The script file path '%s' doesn't exist.", scriptFilePath));
        }

        String scriptContent = getStringContent(scriptFilePath);
        return executeScriptAndGetExitCode(scriptContent);
    }

    private String getResolvedContentWithEnvVars(final String scriptContent) throws ScriptTriggerException {

        assert executionNodeRootPath != null;

        String scriptContentResolved;
        try {
            log.info("Resolving environment variables for script the content.");
            scriptContentResolved =
                    executionNodeRootPath.act(new FilePath.FileCallable<String>() {
                        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                            return Util.replaceMacro(scriptContent, EnvVars.masterEnvVars);
                        }
                    });
        } catch (IOException ioe) {
            throw new ScriptTriggerException("Error to execute the script", ioe);
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException("Error to execute the script", ie);
        }
        return scriptContentResolved;
    }


    protected String getStringContent(final String filePath) throws ScriptTriggerException {

        assert filePath != null;

        try {
            return executionNodeRootPath.act(new FilePath.FileCallable<String>() {

                public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    StringBuffer content = new StringBuffer();
                    FileReader fileReader = new FileReader(filePath);
                    BufferedReader bufferedReader = new BufferedReader(fileReader);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        content.append(line);
                    }
                    return content.toString();
                }
            });
        } catch (IOException ioe) {
            throw new ScriptTriggerException(ioe);
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException(ie);
        }
    }

    private int executeScript(final String scriptContent) throws ScriptTriggerException {

        assert scriptContent != null;

        log.info(String.format("Evaluating the script: \n %s", scriptContent));
        try {


            boolean isUnix = executionNodeRootPath.act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    return File.pathSeparatorChar == ':';
                }
            });

            CommandInterpreter batchRunner;
            if (isUnix) {
                batchRunner = new Shell(scriptContent);
            } else {
                batchRunner = new BatchFile(scriptContent);
            }
            FilePath tmpFile = batchRunner.createScriptFile(executionNodeRootPath);
            final String[] cmd = batchRunner.buildCommandLine(tmpFile);

            return executionNodeRootPath.act(new FilePath.FileCallable<Integer>() {
                public Integer invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    try {
                        return getLocalLauncher(listener).launch().cmds(cmd).stdout(listener).pwd(executionNodeRootPath).join();
                    } catch (InterruptedException ie) {
                        throw new ScriptTriggerException(ie);
                    } catch (IOException ioe) {
                        throw new ScriptTriggerException(ioe);
                    }
                }
            });
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException(ie);
        } catch (IOException ioe) {
            throw new ScriptTriggerException(ioe);
        }
    }

    private Launcher getLocalLauncher(TaskListener listener) throws ScriptTriggerException {
        return new Launcher.LocalLauncher(listener);
    }

    protected boolean existsScript(final String path) throws ScriptTriggerException {

        try {
            return executionNodeRootPath.act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    File f = new File(path);
                    if (!f.exists()) {
                        log.info(String.format("Can't load the file '%s'. It doesn't exist.", f.getPath()));
                        return false;
                    }
                    return true;
                }
            });
        } catch (IOException ioe) {
            throw new ScriptTriggerException(ioe);
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException(ie);
        }
    }

}
