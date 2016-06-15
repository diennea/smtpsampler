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

import diennea.smtpsampler.ResultCollector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Output Results to System.out
 *
 * @author enrico.olivelli
 */
public class ConsoleResultCollector implements ResultCollector {

    private AtomicInteger messageCount = new AtomicInteger();
    private AtomicInteger deliveredMessageCount = new AtomicInteger();
    private AtomicInteger failedMessageCount = new AtomicInteger();
    private LongAdder totalTime = new LongAdder();
    private AtomicInteger failedConnectionsCount = new AtomicInteger();
    private final boolean verbose;
    private long testStartTs;

    private void write(Object msg) {
        if (!verbose) {
            return;
        }
        System.out.println(msg);

    }

    public ConsoleResultCollector(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void fatalErrorOnConnection(int connectionId, Throwable error) {
        write("Connection " + connectionId + " failed: " + error);
        failedConnectionsCount.incrementAndGet();
    }

    @Override
    public void messageSent(int connectionId, int i, long delta, String lastServerResponse, Throwable error) {
        totalTime.add(delta);
        messageCount.incrementAndGet();
        if (lastServerResponse == null) {
            lastServerResponse = "";
        } else {
            lastServerResponse = lastServerResponse.trim();

        }
        if (error != null) {
            failedMessageCount.incrementAndGet();
            write("Message failed: " + connectionId + "/" + i + " " + delta + " ms " + lastServerResponse + ": " + error);
        } else {
            deliveredMessageCount.incrementAndGet();
            write("Message delivered: " + connectionId + "/" + i + " " + delta + " ms " + lastServerResponse);
        }

    }

    @Override
    public void finished() {
        long testFinishTs = System.currentTimeMillis();
        long totalTestTime = testFinishTs - testStartTs;
        System.out.println("Report:");
        System.out.println("Total time: " + (totalTestTime / 1000) + " s (Wall Clock) - (" + ((int)(totalTime.doubleValue()/1000)) + " s total time)");
        System.out.println("Total messages: " + messageCount);
        System.out.println("Delivered messages: " + deliveredMessageCount);
        System.out.println("Failed messages: " + failedMessageCount);
        System.out.println("Failed connections: " + failedConnectionsCount);
        System.out.println("Average message delivery time: " + ((int) ((messageCount.doubleValue() * 1000d) / totalTime.doubleValue())) + " msg/s");
        System.out.println("Total thoughtput: " + ((int) ((messageCount.doubleValue() * 1000d) / totalTestTime)) + " msg/s");
    }

    @Override
    public void start() {
        testStartTs = System.currentTimeMillis();
    }

}
