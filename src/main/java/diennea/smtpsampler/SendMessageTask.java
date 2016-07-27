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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import com.sun.mail.smtp.SMTPTransport;

import diennea.smtpsampler.SendMessageTask.Result;

/**
 * Procedure deliver messages.
 * 
 * @author diego.salvi
 * @author enrico.olivelli
 */
public class SendMessageTask implements Callable<Result>
{
    public static final class Result
    {
        private final Map<Integer,Long> messageIDBeforeSendNanos;
        private final Map<Integer,Long> messageIDAfterSendNanos;
        
        private Result( int size )
        {
            messageIDBeforeSendNanos = new HashMap<>(size);
            messageIDAfterSendNanos = new HashMap<>(size);
        }

        public Map<Integer, Long> getMessageIDBeforeSendNanos()
        {
            return messageIDBeforeSendNanos;
        }

        public Map<Integer, Long> getMessageIDAfterSendNanos()
        {
            return messageIDAfterSendNanos;
        }
    }
    
    private final ResultCollector collector;
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    
    private final Session session;
    private final MimeMessage message;
    
    private final int messageCount;
    
    private final int connectionID;
    private final AtomicInteger messageIDGenerator;
    private final String messageIDHeader;
    
    public SendMessageTask(
            ResultCollector collector,
            String host,
            int port,
            String username,
            String password,
            Session session,
            MimeMessage message,
            int messageCount,
            AtomicInteger connectionIDGenerator,
            AtomicInteger messageIDGenerator,
            String messageIDHeader)
    {
        super();
        
        this.collector = collector;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.session = session;
        
        try
        {
            /* We need a copy because its headers will be modified */
            this.message = new MimeMessage(message);
            
        } catch (MessagingException e)
        {
            /* Should never occur */
            throw new RuntimeException("Cannot copy message");
        }
        
        this.messageCount = messageCount;
        this.connectionID = connectionIDGenerator.getAndIncrement();
        this.messageIDGenerator = messageIDGenerator;
        this.messageIDHeader = messageIDHeader;
    }
    
    @Override
    public Result call() throws Exception
    {
        final Result result = new Result(messageCount);
//        Map<Integer,Long> result = new HashMap<>(messageCount);
        
        long mtime = 0;
        long stime = 0;        
        long cstart = System.nanoTime();
        
        try
        {
            SMTPTransport transport = (SMTPTransport) session.getTransport("smtp");
            
            try
            {
                transport.connect(host, port, username, password);
                
                for (int i = 0; i < messageCount; i++)
                {
                    
                    long mstart = -1, mend = -1;
                    
                    mstart = System.nanoTime();
                    
                    /*
                     * Generate a message id and add it to the message, it will
                     * be needed to recognize received messages.
                     */
                    int messageID = messageIDGenerator.getAndIncrement();
                    
                    message.setHeader(messageIDHeader, Integer.toString(messageID));
                    message.saveChanges();
                    
                    mend = System.nanoTime();
                    
                    mtime += mend - mstart;
                    
                    long before = mend;
                    long after;
                    try
                    {
                        transport.sendMessage(message, message.getAllRecipients());
                        
                        after = System.nanoTime();
                        long cstime = after - before;
                        stime += cstime;
                        
                        collector.messageSent(connectionID, i, cstime, transport.getLastServerResponse(), null);
                        
                    } catch (Exception err)
                    {
                        after = System.nanoTime();
                        long cstime = after - before;
                        stime += cstime;
                        
                        collector.messageSent(connectionID, i, cstime, transport.getLastServerResponse(), err);
                        break;
                    }
                    
                    /*
                     * Adds message id start time after message send to not
                     * account map time into message send time
                     */
                    result.messageIDBeforeSendNanos.put(messageID, before);
                    result.messageIDAfterSendNanos.put(messageID, after);
                }
                
            } finally
            {
                transport.close();
            }
            
            long cend = System.nanoTime();
            
            collector.connectionHandled(connectionID, cend - cstart - mtime - stime, null);
            
        } catch (Throwable error)
        {
            long cend = System.nanoTime();
            
            collector.connectionHandled(connectionID, cend - cstart - mtime - stime, error);
        }
        
        return result;
    }

}
