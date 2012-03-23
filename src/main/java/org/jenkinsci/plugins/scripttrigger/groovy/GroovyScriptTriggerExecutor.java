package org.jenkinsci.plugins.scripttrigger.groovy;

import groovy.lang.GroovyShell;
import hudson.Util;
import hudson.PluginManager;
import hudson.model.Item;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.remoting.Callable;

import java.io.IOException;
import java.util.Map;

import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerExecutor;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerExecutor extends ScriptTriggerExecutor {

    public GroovyScriptTriggerExecutor(XTriggerLog log) {
        super(log);
    }

    public boolean evaluateGroovyScript(Node executingNode, final Item job, final String scriptContent, final Map<String, String> envVars, boolean groovySystemScript) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("The script content object must be set.");
        }
        try {
            if (groovySystemScript) {
                log.info("Running as system script");
                return evaluateGroovyScript(job, scriptContent, envVars);
            }
            
            return executingNode.getRootPath().act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    log.info("Running as node script");
                    return evaluateGroovyScript(null, scriptContent, envVars);
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

    private boolean evaluateGroovyScript(final Item job, final String scriptContent, final Map<String, String> envVars) {
        final StringBuilder envDebug = new StringBuilder("Replacing script vars using:");
        for (final Map.Entry<String, String> envEntry : envVars.entrySet()) {
            envDebug.append("\n\t").append(envEntry.getKey()).append("=").append(envEntry.getValue());
        }
        log.info(envDebug.toString());
        
        final String groovyExpressionResolved = Util.replaceMacro(scriptContent, envVars);
        log.info(String.format("Evaluating the groovy script: \n----------------------------------------\n%s\n----------------------------------------\n\n", groovyExpressionResolved));
        
        final ClassLoader cl = getClassLoader();
        
        GroovyShell shell = new GroovyShell(cl);
        
        shell.setVariable("log", log);
        shell.setVariable("out", log.getListener().getLogger());
        if (job != null) {
            shell.setVariable("job", job);
        }
        
        //Evaluate the new script content
        Object result = shell.evaluate(groovyExpressionResolved);
        //Return the evaluated result
        return Boolean.valueOf(String.valueOf(result));
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

    public boolean evaluateGroovyScriptFilePath(Node executingNode, Item job, String scriptFilePath, Map<String, String> envVars, boolean groovySystemScript) throws ScriptTriggerException {
        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        if (!existsScript(executingNode, scriptFilePath)) {
            return false;
        }

        String scriptContent = getStringContent(executingNode, scriptFilePath);
        return evaluateGroovyScript(executingNode, job, scriptContent, envVars, groovySystemScript);
    }

}
