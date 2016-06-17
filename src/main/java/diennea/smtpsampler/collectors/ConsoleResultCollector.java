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
package diennea.smtpsampler.collectors;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import diennea.smtpsampler.ResultCollector;

/**
 * Output Results to System.out
 *
 * @author enrico.olivelli
 */
public class ConsoleResultCollector implements ResultCollector {

    private AtomicInteger messageCount = new AtomicInteger();
    private AtomicInteger deliveredMessageCount = new AtomicInteger();
    private AtomicInteger failedMessageCount = new AtomicInteger();
    private LongAdder sendTime = new LongAdder();
    private LongAdder receiveTime = new LongAdder();
    private LongAdder originalReceiveTime = new LongAdder();
    private LongAdder originalReceiveCount = new LongAdder();
    private LongAdder receiveCount = new LongAdder();
    private AtomicInteger failedMessageReceivedCount = new AtomicInteger();
    private AtomicInteger failedConnectionsCount = new AtomicInteger();
    private long testStartTs;
    
    private long testEndSend;
    private long testEndReceive;
    
    private final boolean verbose;
    private final boolean receive;

    private void write(Object msg) {
        if (!verbose) {
            return;
        }
        System.out.println(msg);

    }

    public ConsoleResultCollector(boolean verbose, boolean receive) {
        this.verbose = verbose;
        this.receive = receive;
    }

    @Override
    public void fatalErrorOnConnection(int connectionId, Throwable error) {
        write("Connection " + connectionId + " failed: " + error);
        failedConnectionsCount.incrementAndGet();
    }

    @Override
    public void messageSent(int connectionId, int i, long delta, String lastServerResponse, Throwable error) {
        sendTime.add(delta);
        messageCount.incrementAndGet();
        if (lastServerResponse == null) {
            lastServerResponse = "";
        } else {
            lastServerResponse = lastServerResponse.trim();

        }
        if (error != null) {
            failedMessageCount.incrementAndGet();
            write("Message failed: " + connectionId + "/" + i + " " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms " + lastServerResponse + ": " + error);
        } else {
            deliveredMessageCount.incrementAndGet();
            write("Message delivered: " + connectionId + "/" + i + " " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms " + lastServerResponse);
        }

    }
    
    @Override
    public void messageReceived(long delta , long originaldelta, Throwable error)
    {
        receiveTime.add(delta);
        
        if ( originaldelta > -1 )
        {
            originalReceiveTime.add(originaldelta);
            originalReceiveCount.add(1L);
        }
        
        if (error != null) {
            failedMessageReceivedCount.incrementAndGet();
            
            if ( originaldelta > -1 )
                write("Received failure: " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms (" + TimeUnit.NANOSECONDS.toMillis(originaldelta) + " from original send) : " + error);
            else
                write("Received failure: " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms : " + error);
        } else
        {
            receiveCount.add(1L);
            
            if ( originaldelta > -1 )
                write("Message received: " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms (" + TimeUnit.NANOSECONDS.toMillis(originaldelta) + " from original send)" );
            else
                write("Message received: " + TimeUnit.NANOSECONDS.toMillis(delta) + " ms " );
        }
    }
    
    @Override
    public void finishSend()
    {
        testEndSend = System.nanoTime();
    }
    
    @Override
    public void finishReceive()
    {
        testEndReceive = System.nanoTime();
    }
    
    @Override
    public void finished()
    {
        long testFinishTs = System.nanoTime();
        
        long totalTestTime = testFinishTs - testStartTs;
        long totalSendTime = testEndSend - testStartTs;
        long totalReceiveTime = testEndReceive - testStartTs;
        
        DecimalFormat format = new DecimalFormat("0.00");
        
        System.out.println("Report:");
        
        System.out.println("\t" + TimeUnit.NANOSECONDS.toSeconds(totalTestTime) + " s (Wall Clock)");
        System.out.println("\t" + TimeUnit.NANOSECONDS.toSeconds(sendTime.longValue()) + " s (total send time)");
        
        if (receive)
        {
            System.out.println("\t" + TimeUnit.NANOSECONDS.toSeconds(receiveTime.longValue()) + " s (total receive read time)");
            System.out.println("\t" + TimeUnit.NANOSECONDS.toSeconds(originalReceiveTime.longValue()) + " s (total round trip time)");
        }
        
        
        System.out.println("Total messages: " + messageCount);
        System.out.println("Delivered messages: " + deliveredMessageCount);
        
        if (receive)
        {
            System.out.println("Received messages: " + receiveCount);
            System.out.println("Received messages recognized: " + originalReceiveCount);
        }
        
        System.out.println("Failed messages: " + failedMessageCount);
        System.out.println("Failed connections: " + failedConnectionsCount);
        System.out.println("Failed received message: " + failedMessageReceivedCount);
        System.out.println("Average message delivery time: " + TimeUnit.NANOSECONDS.toMillis((long)(sendTime.doubleValue() / messageCount.doubleValue())) + " ms");
        System.out.println("Average message delivery speed: " + format.format((messageCount.doubleValue() * 1000_000_000d) / totalSendTime) + " msg/s " +
                " (" + format.format((messageCount.doubleValue() * 1000_000_000d) / sendTime.doubleValue()) + " msg/s on total send time)");
        
        if (receive)
        {
            
            System.out.println("Average message round trip time: " + TimeUnit.NANOSECONDS.toMillis((long)(originalReceiveTime.doubleValue() / originalReceiveCount.doubleValue())) + " ms");
            System.out.println("Average message round trip speed: " + format.format((originalReceiveCount.doubleValue() * 1000_000_000d) / totalReceiveTime) + " msg/s " +
                    " (" + format.format((originalReceiveCount.doubleValue() * 1000_000_000d) / originalReceiveTime.doubleValue()) + " msg/s on total round trip time)");
        }
        
        System.out.println("Total thoughtput: " + ((int) ((messageCount.doubleValue() * 1000_000_000d) / totalTestTime)) + " msg/s");
    }

    @Override
    public void start() {
        testStartTs = System.nanoTime();
    }

}
