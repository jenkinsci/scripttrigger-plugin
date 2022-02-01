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

import groovy.lang.GroovyShell;
import hudson.PluginManager;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.util.IOUtils;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerExecutor extends ScriptTriggerExecutor {

    public GroovyScriptTriggerExecutor(XTriggerLog log) {
        super(log);
    }

    public boolean evaluateGroovyScript(Node executingNode, final AbstractProject proj, final String scriptContent, final Map<String, String> envVars, final Properties parameters, boolean groovySystemScript) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("The script content object must be set.");
        }
        try {
            if (groovySystemScript) {
                log.info("Running as system script");
                return evaluateGroovyScript(proj, scriptContent, envVars, parameters);
            }

            return executingNode.getRootPath().act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    log.info("Running as node script");
                    return evaluateGroovyScript(null, scriptContent, envVars, parameters);
                }
            });
        } catch (IOException ioe) {
            log.info("Script execition failed: " + ioe.getClass().getName());
            ioe.printStackTrace(log.getListener().getLogger());
            throw new ScriptTriggerException(ioe);
        } catch (InterruptedException ie) {
            log.info("Script execition failed: " + ie.getClass().getName());
            ie.printStackTrace(log.getListener().getLogger());
            throw new ScriptTriggerException(ie);
        } catch (RuntimeException e) {
            log.info("Script execition failed: " + e.getClass().getName());
            e.printStackTrace(log.getListener().getLogger());
            throw e;
        }
    }

    private boolean evaluateGroovyScript(final AbstractProject proj, final String scriptContent, final Map<String, String> envVars, final Properties parameters) {
        if (envVars != null) {
            final StringBuilder envDebug = new StringBuilder("Replacing script vars using:");
            for (final Map.Entry<String, String> envEntry : envVars.entrySet()) {
                envDebug.append("\n\t").append(envEntry.getKey()).append("=").append(envEntry.getValue());
            }
            log.info(envDebug.toString());
        } else {
            log.info("No environment variables available.");
        }

        log.info("Evaluating the groovy script:");
        log.info("---------- Base Script -----------------");
        log.info(scriptContent);

        String groovyExpressionResolved = Util.replaceMacro(scriptContent, envVars);
        groovyExpressionResolved = processPath(groovyExpressionResolved);

        log.info("---------- Resolved Script -------------");
        log.info(groovyExpressionResolved);
        log.info("----------------------------------------\n");

        final ClassLoader cl = getClassLoader();

        GroovyShell shell = new GroovyShell(cl);

        shell.setVariable("log", log);
        shell.setVariable("out", log.getListener().getLogger());
        shell.setVariable("parameters", parameters);
        if (proj != null) {
            shell.setVariable("project", proj);
        }

        //Evaluate the new script content
        Object result = shell.evaluate(groovyExpressionResolved);
        //Return the evaluated result
        return Boolean.valueOf(String.valueOf(result));
    }

    private String processPath(String content) {
        if (content == null) {
            return null;
        }
        return content.replace("\\", "\\\\");
    }

    protected ClassLoader getClassLoader() {
        final Hudson instance = Hudson.getInstance();
        if (instance == null) {
            log.info("No Hudson Instance available, returning thread context classloader");
            return Thread.currentThread().getContextClassLoader();

        }

        final PluginManager pluginManager = instance.getPluginManager();
        if (pluginManager == null) {
            log.info("No PluginManager available, returning thread context classloader");
            return Thread.currentThread().getContextClassLoader();
        }

        final ClassLoader cl = pluginManager.uberClassLoader;
        if (cl == null) {
            log.info("No uberClassLoader available, returning thread context classloader");
            return Thread.currentThread().getContextClassLoader();
        }

        return cl;
    }

    public boolean evaluateGroovyScriptFilePath(Node executingNode, AbstractProject proj, String scriptFilePath, Map<String, String> envVars, final Properties parameters, boolean groovySystemScript) throws ScriptTriggerException {

        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        final String scriptContent;
        if (groovySystemScript) {
            String expandedScriptFile = Util.replaceMacro(scriptFilePath, envVars);

            final File file = new File(expandedScriptFile);
            final String scriptPath = file.getAbsolutePath();

            if (!file.exists()) {
                log.info(String.format("Can't load the file '%s'. It doesn't exist.", scriptPath));
                return false;
            }

            log.info("Reading script from: " + file.getAbsolutePath());
            try {
                final FileInputStream fis = new FileInputStream(file);
                try {
                    scriptContent = IOUtils.toString(fis);
                } finally {
                    fis.close();
                }
                log.info("Read " + scriptContent.length() + " character long script from: " + scriptPath);
            } catch (IOException e) {
                final String msg = "Failed to read system groovy script file '" + scriptFilePath + "' from '" + scriptPath + "'";
                log.info(msg);
                e.printStackTrace(log.getListener().getLogger());
                throw new RuntimeException(msg, e);
            }
        } else {
            if (!existsScript(executingNode, scriptFilePath)) {
                return false;
            }

            scriptContent = getStringContent(executingNode, scriptFilePath);
        }

        return evaluateGroovyScript(executingNode, proj, scriptContent, envVars, parameters, groovySystemScript);
    }

}
