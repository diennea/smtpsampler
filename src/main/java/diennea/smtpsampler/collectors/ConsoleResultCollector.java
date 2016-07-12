package diennea.smtpsampler.collectors;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics;

import diennea.smtpsampler.ResultCollector;

/**
 * Collects and prints results
 * 
 * @author diego.salvi
 * @author enrico.olivelli
 */
public class ConsoleResultCollector implements ResultCollector
{
    private static final ThreadLocal <DecimalFormat> NUMBER_FORMAT = new ThreadLocal <DecimalFormat>()
    {
        @Override protected DecimalFormat initialValue() { return new DecimalFormat("0.000"); }
    };
    
    
    private final boolean verbose;
    private final boolean receive;

    private final LongAdder connectionCount = new LongAdder();
    private final LongAdder failedConnectionsCount = new LongAdder();
    
    private final LongAdder messageCount = new LongAdder();
    private final LongAdder failedMessageCount = new LongAdder();
    private final LongAdder deliveredMessageCount = new LongAdder();
    
    private final LongAdder receivedMessageCount = new LongAdder();
    
    private final SummaryStatistics connectionTime = new SynchronizedSummaryStatistics();
    private final SummaryStatistics sendTime = new SynchronizedSummaryStatistics();
    private final SummaryStatistics receiveTime = new SynchronizedSummaryStatistics();
    
    private long testStart;
    private long sendEnd;
    private long receiveEnd;
    private long testEnd;
    
    public ConsoleResultCollector(boolean verbose, boolean receive)
    {
        this.verbose = verbose;
        this.receive = receive;
    }
    
    private void write(Object msg)
    {
        if (!verbose) {
            return;
        }
        System.out.println(msg);

    }
    
    @Override
    public void start()
    {
        testStart = System.nanoTime();
    }

    @Override
    public void finishSend()
    {
        sendEnd = System.nanoTime();
    }

    @Override
    public void finishReceive()
    {
        receiveEnd = System.nanoTime();
    }

    
    private String formatNanos(DecimalFormat format, long time, TimeUnit dst)
    {
        double multiplier = dst.toNanos(1);
        
        return format.format( ((double) time) / multiplier );
    }
    
    private String formatNanos(DecimalFormat format, double time, TimeUnit dst)
    {
        double multiplier = dst.toNanos(1);
        
        return format.format( time / multiplier );
    }
    
    private String formatEventNanos(DecimalFormat format, long events, long time, TimeUnit dst)
    {
        double multiplier = dst.toNanos(1);
        
        return format.format( (events * multiplier) /  ((double) time) );
    }
    
    private String formatEventNanos(DecimalFormat format, long events, double time, TimeUnit dst)
    {
        double multiplier = dst.toNanos(1);
        
        return format.format( (events * multiplier) /  time );
    }
    
    @Override
    public void finished()
    {
        testEnd = System.nanoTime();
        
        final DecimalFormat format = NUMBER_FORMAT.get();
        
        long totalTestTime    = testEnd - testStart;
        long totalSendTime    = sendEnd - testStart;
        long totalReceiveTime = receiveEnd - testStart;
        
        System.out.println("Report:");
        
        System.out.println("\n  Wall Clock:            " + formatNanos( format, totalTestTime, TimeUnit.SECONDS ) + " s");
        System.out.println("  Wall Send Time:        " + formatNanos( format, totalSendTime, TimeUnit.SECONDS ) + " s");
        System.out.println("  Real Send Time:        " + formatNanos( format, sendTime.getSum(), TimeUnit.SECONDS ) + " s");
        System.out.println("  Real Connection Time:  " + formatNanos( format, connectionTime.getSum(), TimeUnit.SECONDS ) + " s");
       
        if (receive)
        {
            System.out.println("  Wall Round Trip Time:  " + formatNanos( format, totalReceiveTime, TimeUnit.SECONDS ) + " s");
            System.out.println("  Real Round Trip Time:  " + formatNanos( format, receiveTime.getSum(), TimeUnit.SECONDS ) + " s");
        }
        
        System.out.println("\n  Failed connections:    " + failedConnectionsCount);
        System.out.println("  Total messages:        " + messageCount);
        System.out.println("  Delivered messages:    " + deliveredMessageCount);
        System.out.println("  Failed messages:       " + failedMessageCount);
        
        if (receive)
            System.out.println("  Received messages:     " + receivedMessageCount);
        
        double significance = 0.05;
        
        if (connectionCount.intValue() > 0)
        {
            
            System.out.println("\n  Connection time");
            
            System.out.println("    Average:             " + formatNanos( format, totalSendTime, TimeUnit.MILLISECONDS ) + " ms (on wall send time)");
            System.out.println("                         " + formatNanos( format, connectionTime.getMean(), TimeUnit.MILLISECONDS ) + " ms (on real connection time)");
            System.out.println("    Minimum:             " + formatNanos( format, connectionTime.getMin(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Maximum:             " + formatNanos( format, connectionTime.getMax(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Standard deviation:  " + formatNanos( format, connectionTime.getStandardDeviation(), TimeUnit.MILLISECONDS ));
            
            boolean distribution = connectionTime.getN() > 1;
            if ( distribution )
            {
                final TDistribution td = new TDistribution(connectionTime.getN() -1);
                final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                final double confidence =  inversetd * connectionTime.getStandardDeviation() / Math.sqrt(connectionTime.getN());
                
                System.out.println("    Evaluation:          " + formatNanos( format, connectionTime.getMean(), TimeUnit.MILLISECONDS )
                    + " ms +-"  + formatNanos( format, confidence, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
            } else
            {
                System.out.println("    Evaluation:          no evaluation");
            }
        }
        
        if (messageCount.intValue() > 0)
        {
            
            System.out.println("\n  Message delivery time");
            
            System.out.println("    Average:             " + formatNanos( format, totalSendTime, TimeUnit.MILLISECONDS ) + " ms (on wall send time)");
            System.out.println("                         " + formatNanos( format, sendTime.getMean(), TimeUnit.MILLISECONDS ) + " ms (on real send time)");
            System.out.println("    Minimum:             " + formatNanos( format, sendTime.getMin(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Maximum:             " + formatNanos( format, sendTime.getMax(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Standard deviation:  " + formatNanos( format, sendTime.getStandardDeviation(), TimeUnit.MILLISECONDS ));
            
            boolean distribution = sendTime.getN() > 1;
            if ( distribution )
            {
                final TDistribution td = new TDistribution(sendTime.getN() -1);
                final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                final double confidence =  inversetd * sendTime.getStandardDeviation() / Math.sqrt(sendTime.getN());
                
                System.out.println("    Evaluation:          " + formatNanos( format, sendTime.getMean(), TimeUnit.MILLISECONDS )
                    + " ms +-"  + formatNanos( format, confidence, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
            } else
            {
                System.out.println("    Evaluation:          no evaluation");
            }
            
            System.out.println("\n  Message delivery speed");
            System.out.println("    Average:             " + formatEventNanos( format, messageCount.longValue(), totalSendTime, TimeUnit.SECONDS ) + " msg/s (on wall send time)");
            System.out.println("                         " + formatEventNanos( format, messageCount.longValue(), sendTime.getSum(), TimeUnit.SECONDS ) + " msg/s (on real send time)");
        }
        
        if (receive && receivedMessageCount.intValue() > 0)
        {
            System.out.println("\n  Message round trip time");
            
            System.out.println("    Average:             " + formatNanos( format, totalSendTime, TimeUnit.MILLISECONDS ) + " ms (on wall round trip time)");
            System.out.println("                         " + formatNanos( format, receiveTime.getMean(), TimeUnit.MILLISECONDS ) + " ms (on pure round trip time)");
            System.out.println("    Minimum:             " + formatNanos( format, receiveTime.getMin(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Maximum:             " + formatNanos( format, receiveTime.getMax(), TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Standard deviation:  " + formatNanos( format, receiveTime.getStandardDeviation(), TimeUnit.MILLISECONDS ));
            
            boolean distribution = receiveTime.getN() > 1;
            if ( distribution )
            {
                final TDistribution td = new TDistribution(receiveTime.getN() -1);
                final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                final double confidence =  inversetd * receiveTime.getStandardDeviation() / Math.sqrt(receiveTime.getN());
                
                System.out.println("    Evaluation:          " + formatNanos( format, receiveTime.getMean(), TimeUnit.MILLISECONDS )
                + " ms +-"  + formatNanos( format, confidence, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
            } else
            {
                System.out.println("    Evaluation:          no evaluation");
            }
            
            System.out.println("\n  Message round trip speed");
            System.out.println("    Average:             " + formatEventNanos( format, messageCount.longValue(), totalReceiveTime, TimeUnit.SECONDS ) + " msg/s (on wall round trip time)");
            System.out.println("                         " + formatEventNanos( format, messageCount.longValue(), receiveTime.getSum(), TimeUnit.SECONDS ) + " msg/s (on real round trip time)");
        }
        
        if (messageCount.intValue() > 0)
            System.out.println("\n  Total thoughtput:      " + formatEventNanos( format, messageCount.longValue(), totalTestTime, TimeUnit.SECONDS ) + " msg/s (on wall test time)");
        
    }
    
    @Override
    public void connectionHandled(int connectionId, long nanoseconds, Throwable error)
    {
        if ( error != null )
        {
            failedConnectionsCount.increment();
            
            write("Connection failed: " + formatNanos(NUMBER_FORMAT.get(), nanoseconds, TimeUnit.MILLISECONDS) + " ms error: " + error);
            
        } else
        {
            write("Connection handled: " + formatNanos(NUMBER_FORMAT.get(), nanoseconds, TimeUnit.MILLISECONDS) + " ms");
        }
        
        
        connectionCount.increment();
        connectionTime.addValue(nanoseconds);
    }
    
    @Override
    public void messageSent(int connectionId, int messageNumber, long nanoseconds, String lastServerResponse, Throwable error)
    {
        sendTime.addValue(nanoseconds);
        
        messageCount.increment();
        
        lastServerResponse = lastServerResponse == null ? "" : lastServerResponse.trim();
        
        if (error != null)
        {
            failedMessageCount.increment();
            write("Message failed: " + connectionId + "/" + messageNumber + " " + formatNanos(NUMBER_FORMAT.get(), nanoseconds, TimeUnit.MILLISECONDS) + " ms " + lastServerResponse + ": " + error);
        } else
        {
            deliveredMessageCount.increment();
            write("Message delivered: " + connectionId + "/" + messageNumber + " " + formatNanos(NUMBER_FORMAT.get(), nanoseconds, TimeUnit.MILLISECONDS) + " ms " + lastServerResponse);
        }
    }
    
    @Override
    public void messageReceived(long nanoseconds)
    {
        receiveTime.addValue(nanoseconds);
        
        receivedMessageCount.increment();
        
        write("Message received: " + formatNanos(NUMBER_FORMAT.get(), nanoseconds, TimeUnit.MILLISECONDS) + " ms");
    }

}
