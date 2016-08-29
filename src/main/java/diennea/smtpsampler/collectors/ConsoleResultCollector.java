package diennea.smtpsampler.collectors;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

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
    private final SummaryStatistics sendAndReceiveTime = new SynchronizedSummaryStatistics();
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
    
    private void write(Supplier<Object> supplier)
    {
        if (!verbose)
            return;
        
        /* Produce output only if needed */
        System.out.println(supplier.get());
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

    
    
    private String format(DecimalFormat format, long time, TimeUnit src, TimeUnit dst)
    {
        /* Avoid round up conversion of time unit */
        
        double multiplier = src.convert(1, dst);
        
        return format.format( ((double) time) / multiplier );
    }
    
    private String format(DecimalFormat format, double time, TimeUnit src, TimeUnit dst)
    {
        /* Avoid round up conversion of time unit */
        
        double multiplier = src.convert(1, dst);
        
        return format.format( time / multiplier );
    }
    
    private String formatEvent(DecimalFormat format, long events, long time, TimeUnit src, TimeUnit dst)
    {
        /* Avoid round up conversion of time unit */
        
        double multiplier = src.convert(1, dst);
        
        return format.format( (events * multiplier) /  ((double) time) );
    }
    
    private String formatEvent(DecimalFormat format, long events, double time, TimeUnit src, TimeUnit dst)
    {
        /* Avoid round up conversion of time unit */
        
        double multiplier = src.convert(1, dst);
        
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
        
        System.out.println("\n  Wall Clock:            " + format( format, totalTestTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
        System.out.println("  Wall Send Time:        " + format( format, totalSendTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
        System.out.println("  Real Send Time:        " + format( format, sendTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
        System.out.println("  Real Connection Time:  " + format( format, connectionTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
       
        if (receive)
        {
            System.out.println("  Wall Round Trip Time:  " + format( format, totalReceiveTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
            System.out.println("  Real Round Trip Time:  " + format( format, sendAndReceiveTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
            
            System.out.println("  Real Receive Time:     " + format( format, receiveTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " s");
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
            
            System.out.println("    Average:             " + format( format, totalSendTime, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on wall send time)");
            System.out.println("                         " + format( format, connectionTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on real connection time)");
            System.out.println("    Minimum:             " + format( format, connectionTime.getMin(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Maximum:             " + format( format, connectionTime.getMax(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Standard deviation:  " + format( format, connectionTime.getStandardDeviation(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ));
            
            boolean distribution = connectionTime.getN() > 1;
            if ( distribution )
            {
                final TDistribution td = new TDistribution(connectionTime.getN() -1);
                final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                final double confidence =  inversetd * connectionTime.getStandardDeviation() / Math.sqrt(connectionTime.getN());
                
                System.out.println("    Evaluation:          " + format( format, connectionTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS )
                    + " ms +-"  + format( format, confidence, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
            } else
            {
                System.out.println("    Evaluation:          no evaluation");
            }
        }
        
        if (messageCount.intValue() > 0)
        {
            
            System.out.println("\n  Message delivery time");
            
            System.out.println("    Average:             " + format( format, totalSendTime, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on wall send time)");
            System.out.println("                         " + format( format, sendTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on real send time)");
            System.out.println("    Minimum:             " + format( format, sendTime.getMin(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Maximum:             " + format( format, sendTime.getMax(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
            System.out.println("    Standard deviation:  " + format( format, sendTime.getStandardDeviation(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ));
            
            boolean distribution = sendTime.getN() > 1;
            if ( distribution )
            {
                final TDistribution td = new TDistribution(sendTime.getN() -1);
                final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                final double confidence =  inversetd * sendTime.getStandardDeviation() / Math.sqrt(sendTime.getN());
                
                System.out.println("    Evaluation:          " + format( format, sendTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS )
                    + " ms +-"  + format( format, confidence, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
            } else
            {
                System.out.println("    Evaluation:          no evaluation");
            }
            
            System.out.println("\n  Message delivery speed");
            System.out.println("    Average:             " + formatEvent( format, messageCount.longValue(), totalSendTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on wall send time)");
            System.out.println("                         " + formatEvent( format, messageCount.longValue(), sendTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on real send time)");
        }
        
        if (receive && receivedMessageCount.intValue() > 0)
        {
            {
                System.out.println("\n  Message receive time");
                
                System.out.println("    Average:             " + format( format, totalReceiveTime, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on wall round trip time)");
                System.out.println("                         " + format( format, receiveTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on real receive time)");
                System.out.println("    Minimum:             " + format( format, receiveTime.getMin(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
                System.out.println("    Maximum:             " + format( format, receiveTime.getMax(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
                System.out.println("    Standard deviation:  " + format( format, receiveTime.getStandardDeviation(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ));
                
                boolean distribution = receiveTime.getN() > 1;
                if ( distribution )
                {
                    final TDistribution td = new TDistribution(receiveTime.getN() -1);
                    final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                    final double confidence =  inversetd * receiveTime.getStandardDeviation() / Math.sqrt(receiveTime.getN());
                    
                    System.out.println("    Evaluation:          " + format( format, receiveTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS )
                    + " ms +-"  + format( format, confidence, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
                } else
                {
                    System.out.println("    Evaluation:          no evaluation");
                }
                
                System.out.println("\n  Message receive speed");
                System.out.println("    Average:             " + formatEvent( format, messageCount.longValue(), totalReceiveTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on wall round trip time)");
                System.out.println("                         " + formatEvent( format, messageCount.longValue(), receiveTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on real receive time)");
            }
            
            {
                System.out.println("\n  Message round trip time");
                
                System.out.println("    Average:             " + format( format, totalReceiveTime, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on wall round trip time)");
                System.out.println("                         " + format( format, sendAndReceiveTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms (on real round trip time)");
                System.out.println("    Minimum:             " + format( format, sendAndReceiveTime.getMin(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
                System.out.println("    Maximum:             " + format( format, sendAndReceiveTime.getMax(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms");
                System.out.println("    Standard deviation:  " + format( format, sendAndReceiveTime.getStandardDeviation(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ));
                
                boolean distribution = sendAndReceiveTime.getN() > 1;
                if ( distribution )
                {
                    final TDistribution td = new TDistribution(sendAndReceiveTime.getN() -1);
                    final double inversetd = td.inverseCumulativeProbability(1.0 - significance / 2);
                    final double confidence =  inversetd * sendAndReceiveTime.getStandardDeviation() / Math.sqrt(sendAndReceiveTime.getN());
                    
                    System.out.println("    Evaluation:          " + format( format, sendAndReceiveTime.getMean(), TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS )
                    + " ms +-"  + format( format, confidence, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS ) + " ms at " + (int) ((1.0 - significance) * 100) + "%");
                } else
                {
                    System.out.println("    Evaluation:          no evaluation");
                }
                
                System.out.println("\n  Message round trip speed");
                System.out.println("    Average:             " + formatEvent( format, messageCount.longValue(), totalReceiveTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on wall round trip time)");
                System.out.println("                         " + formatEvent( format, messageCount.longValue(), sendAndReceiveTime.getSum(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on real round trip time)");
            }
        }
        
        if (messageCount.intValue() > 0)
            System.out.println("\n  Total thoughtput:      " + formatEvent( format, messageCount.longValue(), totalTestTime, TimeUnit.NANOSECONDS, TimeUnit.SECONDS ) + " msg/s (on wall test time)");
        
    }
    
    @Override
    public void connectionHandled(int connectionId, long time, Throwable error)
    {
        if ( error != null )
        {
            failedConnectionsCount.increment();
            
            write(() -> "Connection failed: " + format(NUMBER_FORMAT.get(), time, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS) + " ms error: " + error);
            
        } else
        {
            write(() -> "Connection handled: " + format(NUMBER_FORMAT.get(), time, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS) + " ms");
        }
        
        
        connectionCount.increment();
        connectionTime.addValue(time);
    }
    
    @Override
    public void messageSent(int connectionId, int messageNumber, long time, String lastServerResponse, Throwable error)
    {
        sendTime.addValue(time);
        
        messageCount.increment();
        
        final String trimmedResponse = lastServerResponse == null ? "" : lastServerResponse.trim();
        
        if (error != null)
        {
            failedMessageCount.increment();
            write(() -> "Message failed: " + connectionId + "/" + messageNumber + " " + format(NUMBER_FORMAT.get(), time, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS) + " ms " + trimmedResponse + ": " + error);
        } else
        {
            deliveredMessageCount.increment();
            write(() -> "Message delivered: " + connectionId + "/" + messageNumber + " " + format(NUMBER_FORMAT.get(), time, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS) + " ms " + trimmedResponse);
        }
    }
    
    @Override
    public void messageReceived(long receive, long before, long after)
    {
        final long sendAndReceive = receive-before; 
        sendAndReceiveTime.addValue(sendAndReceive);
        receiveTime.addValue(receive-after);
        
        receivedMessageCount.increment();
        
        write(() -> "Message received: " + format(NUMBER_FORMAT.get(), sendAndReceive, TimeUnit.NANOSECONDS, TimeUnit.MILLISECONDS) + " ms");
    }

}
