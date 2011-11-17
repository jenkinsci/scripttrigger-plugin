package org.jenkinsci.plugins.scripttrigger;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerExecutor implements Serializable {

    protected TaskListener listener;

    protected ScriptTriggerLog log;

    public ScriptTriggerExecutor(TaskListener listener, ScriptTriggerLog log) {
        this.listener = listener;
        this.log = log;
    }

    public int executeScriptAndGetExitCode(Node executingNode, Item job, String scriptContent, Map<String, String> envVars) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("A scriptContent object must be set.");
        }

        String scriptContentResolved = getResolvedContentWithEnvVars(executingNode, job, scriptContent, envVars);
        return executeScript(executingNode, scriptContentResolved, envVars);
    }


    public int executeScriptPathAndGetExitCode(Node executingNode, Item job, String scriptFilePath, Map<String, String> envVars) throws ScriptTriggerException {

        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        if (!existsScript(executingNode, scriptFilePath)) {
            throw new ScriptTriggerException(String.format("The script file path '%s' doesn't exist.", scriptFilePath));
        }

        String scriptContent = getStringContent(executingNode, scriptFilePath);
        return executeScriptAndGetExitCode(executingNode, job, scriptContent, envVars);
    }

    private String getResolvedContentWithEnvVars(Node executingNode, Item job, final String scriptContent, Map<String, String> envVars) throws ScriptTriggerException {
        assert executingNode != null;
        return Util.replaceMacro(scriptContent, envVars);
    }

    protected String getStringContent(Node executingNode, final String filePath) throws ScriptTriggerException {

        assert filePath != null;

        try {
            return executingNode.getRootPath().act(new FilePath.FileCallable<String>() {

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

    private int executeScript(final Node executingNode, final String scriptContent, final Map<String, String> envVars) throws ScriptTriggerException {

        assert scriptContent != null;

        log.info(String.format("Evaluating the script: \n %s", scriptContent));
        try {

            boolean isUnix = executingNode.getRootPath().act(new Callable<Boolean, ScriptTriggerException>() {
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
            FilePath tmpFile = batchRunner.createScriptFile(executingNode.getRootPath());
            final String[] cmd = batchRunner.buildCommandLine(tmpFile);

            final FilePath rootPath = executingNode.getRootPath();
            return rootPath.act(new FilePath.FileCallable<Integer>() {
                public Integer invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    try {
                        return getLocalLauncher(listener).launch().cmds(cmd).envs(envVars).stdout(listener).pwd(rootPath).join();
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

    protected boolean existsScript(Node executingNode, final String path) throws ScriptTriggerException {
        try {
            return executingNode.getRootPath().act(new Callable<Boolean, ScriptTriggerException>() {
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
