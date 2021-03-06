/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.test;

import hudson.util.RingBufferLogHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

/**
 * A test rule which allows you to easily enable one or more loggers for the duration of a test.
 * Call {@link #record(Class, Level)} or another overload for the rule to take effect.
 * <p>By default messages are merely printed to test output.
 * If you also want to examine them, call {@link #capture}.
 * <p>To print and/or capture messages during Jenkins startup,
 * you may compose this with a {@link JenkinsRule} using a {@link RuleChain};
 * or use as a {@link ClassRule}.
 */
public class LoggerRule extends ExternalResource {

    private final Handler consoleHandler = new ConsoleHandler();
    private final Map<Logger,Level> loggers = new HashMap<Logger,Level>();
    // initialized iff capture is called:
    private RingBufferLogHandler ringHandler;
    private List<String> messages;

    /**
     * Initializes the rule, by default not recording anything.
     */
    public LoggerRule() {
        consoleHandler.setFormatter(new SupportLogFormatter());
        consoleHandler.setLevel(Level.ALL);
    }

    /**
     * Initializes log record capture, in addition to merely printing it.
     * This allows you to call {@link #getRecords} and/or {@link #getMessages} later.
     * @param maximum the maximum number of records to keep (any further will be discarded)
     * @return this rule, for convenience
     */
    public LoggerRule capture(int maximum) {
        messages = new ArrayList<String>();
        ringHandler = new RingBufferLogHandler(maximum) {
            final Formatter f = new SimpleFormatter(); // placeholder instance for what should have been a static method perhaps
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                String message = f.formatMessage(record);
                Throwable x = record.getThrown();
                messages.add(message == null && x != null ? x.toString() : message);
            }
        };
        ringHandler.setLevel(Level.ALL);
        for (Logger logger : loggers.keySet()) {
            logger.addHandler(ringHandler);
        }
        return this;
    }

    /**
     * Start listening to a logger.
     * Might be called in a {@link Rule} initializer, to apply to all test cases in a suite;
     * or only at the start of selected test cases.
     * @param logger some logger
     * @param level something between {@link Level#CONFIG} and {@link Level#ALL};
     *              using {@link Level#INFO} or above is typically senseless,
     *              since Java will by default log everything at such levels anyway;
     *              unless you wish to inspect visible {@link #getRecords},
     *              or wish to <em>suppress</em> console log output for some logger
     * @return this rule, for convenience
     */
    public LoggerRule record(Logger logger, Level level) {
        loggers.put(logger, logger.getLevel());
        logger.setLevel(level);
        logger.addHandler(consoleHandler);
        if (ringHandler != null) {
            logger.addHandler(ringHandler);
        }
        return this;
    }

    /**
     * Same as {@link #record(Logger, Level)} but calls {@link Logger#getLogger(String)} for you first.
     */
    public LoggerRule record(String name, Level level) {
        return record(Logger.getLogger(name), level);
    }
    
    /**
     * Same as {@link #record(String, Level)} but calls {@link Class#getName()} for you first.
     */
    public LoggerRule record(Class<?> clazz, Level level) {
        return record(clazz.getName(), level);
    }

    /**
     * Obtains all log records collected so far during this test case.
     * You must have first called {@link #capture}.
     * If more than the maximum number of records were captured, older ones will have been discarded.
     */
    public List<LogRecord> getRecords() {
        return ringHandler.getView();
    }

    /**
     * {@link Formatter#formatMessage} applied to {@link #getRecords} at the time of logging.
     * However, if the message is null, but there is an exception, {@link Throwable#toString} will be used.
     * Does not include logger names, stack traces, times, etc. (these will appear in the test console anyway).
     */
    public List<String> getMessages() {
        return messages;
    }

    @Override
    protected void after() {
        for (Map.Entry<Logger,Level> entry : loggers.entrySet()) {
            Logger logger = entry.getKey();
            logger.setLevel(entry.getValue());
            logger.removeHandler(consoleHandler);
            if (ringHandler != null) {
                logger.removeHandler(ringHandler);
            }
        }
        loggers.clear();
        if (ringHandler != null) {
            ringHandler.clear();
            messages.clear();
        }
    }

}
