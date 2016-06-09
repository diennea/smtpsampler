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

import com.sun.mail.smtp.SMTPTransport;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 * Procedue which actually delivers the message
 *
 * @author enrico.olivelli
 */
public class SendMessageTask implements Runnable {

    final Session session;
    final ResultCollector collector;
    final MimeMessage msg;
    final String host;
    final int port;
    final String username;
    final String password;
    final int numMessages;
    final int connectionId;

    public SendMessageTask(int connectionId, Session session, ResultCollector collector, MimeMessage msg,
            final String host,
            final int port,
            final String username,
            final String password,
            final int numMessages) {
        this.connectionId = connectionId;
        this.session = session;
        this.collector = collector;
        this.msg = msg;
        this.numMessages = numMessages;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run() {
        try {
            SMTPTransport transport = (SMTPTransport) session.getTransport("smtp");
            try {
                transport.connect(host, port, username, password);
                for (int i = 0; i < numMessages; i++) {
                    long _msgStart = System.currentTimeMillis();
                    try {
                        transport.sendMessage(msg, msg.getAllRecipients());
                        long _msgEnd = System.currentTimeMillis();
                        collector.messageSent(connectionId, i, _msgEnd - _msgStart, transport.getLastServerResponse(), null);
                    } catch (Exception err) {
                        long _msgEnd = System.currentTimeMillis();
                        collector.messageSent(connectionId, i, _msgEnd - _msgStart, transport.getLastServerResponse(), err);
                        break;
                    }
                }
            } finally {
                transport.close();
            }
        } catch (Throwable error) {
            collector.fatalErrorOnConnection(connectionId, error);
        }
    }

}
