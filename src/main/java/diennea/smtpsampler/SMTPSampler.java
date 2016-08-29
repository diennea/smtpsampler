/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package diennea.smtpsampler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import diennea.smtpsampler.collectors.ConsoleResultCollector;

/**
 * Benchs an SMTPServer
 *
 * @author enrico.olivelli
 */
public class SMTPSampler {

    private static void reportFatalError(Throwable error) {
        System.err.println("Fatal error: " + error);
        System.exit(1);
    }

    public static void main(String... args) {
        
        MessageReceiver receiver = null;
        boolean listen = false;
        
        try {
            DefaultParser parser = new DefaultParser();
            Options options = new Options();            
            options.addOption("h", "host", true, "SMTP Server hostname or IP Address, default to localhost");
            options.addOption("p", "port", true, "SMTP Server port, default to 25");
            options.addOption("u", "username", true, "Username");
            options.addOption("pwd", "password", true, "Password");
            options.addOption("a", "auth", false, "Use authentication");
            options.addOption("mf", "file", true, "Use file as message and do not generate a test message");
            
            options.addOption("s", "subject", true, "Subject of the generated email");
            options.addOption("ms", "messagesize", true, "Size of the body of the generated message, defaults to 10 bytes");
            options.addOption("f", "from", true, "Value for the From header of the test message");
            options.addOption("t", "to", true, "Value for the To header of the test message");
            
            options.addOption("n", "nummessages", true, "Number of messages, defaults to 1");
            options.addOption("nc", "nummessagesperconnection", true, "Number of messages per connection, defaults to 1");
            options.addOption("tx", "numthreads", true, "Number of concurrent threads/connections");
            options.addOption("tt", "timeout", true, "Max time for execution of the test, in seconds, defaults to 0, which means 'forever'");
            options.addOption("v", "verbose", false, "Verbose output");
            options.addOption("stls", "starttls", false, "Use STARTTLS");
            options.addOption("d", "javamaildebug", false, "Enable JavaMail Debug");
            
            options.addOption("l", "listen", false, "Listen on a generated inbound SMTP Server for message delivery");
            options.addOption("lh", "listenhost", true, "SMTP Server hostname or IP Address, default to localhost");
            options.addOption("lp", "listenport", true, "SMTP Server port, default to 25");
            
            CommandLine commandLine = parser.parse(options, args);
            if (args.length == 0) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("smtpsampler", options, true);
                return;
            }

            boolean verbose = commandLine.hasOption("verbose");
            String host = commandLine.getOptionValue("host", "localhost");
            int port = Integer.parseInt(commandLine.getOptionValue("port", "25"));
            String username = commandLine.getOptionValue("username", "");
            String password = commandLine.getOptionValue("password", "");
            boolean auth = commandLine.hasOption("auth");
            boolean starttls = commandLine.hasOption("starttls");
            boolean javamaildebug = commandLine.hasOption("javamaildebug");
            String subject = commandLine.getOptionValue("subject", "test");
            int messagesize = Integer.parseInt(commandLine.getOptionValue("messagesize", "10"));
            String from = commandLine.getOptionValue("from", "from@localhost");
            String to = commandLine.getOptionValue("to", "to@localhost");
            
            int numthreads = Integer.parseInt(commandLine.getOptionValue("numthreads", "1"));
            
            int nummessages = Integer.parseInt(commandLine.getOptionValue("nummessages", "1"));
            int nummessagesperconnection = Integer.parseInt(commandLine.getOptionValue("nummessagesperconnection", "1"));
            
            if ( nummessages < numthreads )
                throw new Exception("Number of messages must be greater or equal to thread number");
            
            /* Correct num messages per connection if nummessages is too low (to force all thread use) */
            nummessagesperconnection = Math.min( nummessages / numthreads, nummessagesperconnection );
            
            int timeout_seconds = Integer.parseInt(commandLine.getOptionValue("timeout", "0"));
            boolean hasTimeout = timeout_seconds > 0;
            long timeout_millis = TimeUnit.SECONDS.toMillis(timeout_seconds);
            String file = commandLine.getOptionValue("file", "");
            
            listen = commandLine.hasOption("listen");
            String listenhost = commandLine.getOptionValue("listenhost", "localhost");
            int listenport = Integer.parseInt(commandLine.getOptionValue("listenport", "25"));

            File messagefile = null;
            if (!file.isEmpty()) {
                messagefile = new File(file);
                file = messagefile.getAbsolutePath();
            }
            if (verbose) {
                System.out.println("Options:");
                System.out.println("\thost:" + host);
                System.out.println("\tport:" + port);
                System.out.println("\tstarttls:" + starttls);
                System.out.println("\tauth:" + auth);
                System.out.println("\tusername:" + username);
                System.out.println("\tpassword:" + password);
                System.out.println("\tfile:" + file);
                System.out.println("\tsubject:" + subject);
                System.out.println("\tmessagesize:" + messagesize + " (bytes)");
                System.out.println("\tfrom:" + from);
                System.out.println("\tto:" + to);
                System.out.println("\tnummessages:" + nummessages);
                System.out.println("\tnummessagesperconnection:" + nummessagesperconnection);
                System.out.println("\tnumthreads:" + numthreads);
                System.out.println("\tlisten:" + listen);
                System.out.println("\tlistenhost:" + listenhost);
                System.out.println("\tlistenport:" + listenport);
                System.out.println("\ttimeout:" + timeout_seconds);
                System.out.println("\tverbose:" + verbose);
                System.out.println("\tjavamaildebug:" + javamaildebug);
            }

            if (!auth) {
                username = null;
                password = null;
            } else if (username.trim().isEmpty()) {
                throw new Exception("Username is required with -auth flag");
            }
            
            
            
            Properties props = new Properties();
            props.putAll(System.getProperties());
            if (starttls)
            {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.starttls.required", "true");
            }
            if (javamaildebug)
            {
                props.put("mail.debug", "true");
            }
            
            Session session = Session.getDefaultInstance(props);

            
            ResultCollector collector = new ConsoleResultCollector(verbose,listen);
            
            String messageIDHeader = "X-BENCHMARK-MESSAGE-ID";
            AtomicInteger connectionIDGenerator = new AtomicInteger(0);
            AtomicInteger messageIDGenerator = new AtomicInteger(0);
            
            
            if (listen)
            {
                receiver = new MessageReceiver(collector, nummessages, listenhost, listenport, messageIDHeader);
                receiver.start();
            }
            
            MimeMessage message = buildMessage(session, subject, from, to, messagesize, messagefile);

            collector.start();
            
            ExecutorService service = Executors.newFixedThreadPool(numthreads);
            
            final List<Future<SendMessageTask.Result>> messageIDSendTimesFutures = new ArrayList<>(numthreads);
            
            /* Just for timeout, expressed in seconds we don't need fine grained data */
            long start = System.currentTimeMillis();
            
            int remaining = nummessages; 
            while( remaining > 0 )
            {
                int messageCount = Math.min(remaining, nummessagesperconnection);
                remaining -= messageCount;
                
                messageIDSendTimesFutures.add(
                        service.submit(
                                new SendMessageTask(
                                        collector,
                                        host,
                                        port,
                                        username,
                                        password,
                                        session,
                                        message,
                                        messageCount,
                                        connectionIDGenerator,
                                        messageIDGenerator,
                                        messageIDHeader) ) );
            }
            
            service.shutdown();
            if (hasTimeout)
            {
                boolean finished = service.awaitTermination(timeout_millis, TimeUnit.MILLISECONDS);
                if (!finished)
                    throw new Exception("Test not finished in time");
            } else
            {
                service.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);                
            }
            
            if (hasTimeout)
            {
                long now = System.currentTimeMillis();
                
                timeout_millis = Math.max(0, timeout_millis - (now - start));
            }
            
            collector.finishSend();
            
            
            if( listen )
            {
                if (hasTimeout)
                {
                    boolean finished = receiver.awaitTermination(timeout_millis, TimeUnit.MILLISECONDS);
                    if (!finished)
                        throw new Exception("Test not finished in time");
                } else
                {
                    receiver.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);                
                }
                
                collector.finishReceive();
                
                final Map<Integer,Long> messageIDBeforeSendTimes = new HashMap<Integer,Long>();
                final Map<Integer,Long> messageIDAfterSendTimes = new HashMap<Integer,Long>();
                for( Future<SendMessageTask.Result> future : messageIDSendTimesFutures )
                {
                    future.get().getMessageIDBeforeSendTimes().entrySet().stream().forEach(entry -> messageIDBeforeSendTimes.put(entry.getKey(), entry.getValue()));
                    future.get().getMessageIDAfterSendTimes() .entrySet().stream().forEach(entry -> messageIDAfterSendTimes.put(entry.getKey(), entry.getValue()));
                }
                
                receiver.flushResults(messageIDBeforeSendTimes,messageIDAfterSendTimes);
            }
            
            
            collector.finished();
            
        } catch (Exception ex)
        {
            reportFatalError(ex);
            
        } finally
        {
            if ( listen )
                receiver.stop();
        }

    }

    private static MimeMessage buildMessage(Session session, String subject, String from, String to, int messagesize, File messagefile) throws MessagingException, IOException {
        if (messagefile != null) {
            byte[] content = Files.readAllBytes(messagefile.getAbsoluteFile().toPath());
            return new MimeMessage(session, new ByteArrayInputStream(content));
        } else {
            MimeMessage msg = new MimeMessage(session);
            msg.setSubject(subject);
            msg.setFrom(from);
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < messagesize; i++) {
                content.append("x");
            }
            msg.setText(content.toString(), "utf-8", "plain");
            msg.saveChanges();
            return msg;
        }
    }
    
}
