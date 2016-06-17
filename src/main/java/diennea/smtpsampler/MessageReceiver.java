package diennea.smtpsampler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.server.SMTPServer;

public class MessageReceiver
{
    private final ResultCollector resultCollector;
    private final CountDownLatch countDown;
    
    private final SMTPServer server;
    
    public MessageReceiver( ResultCollector resultCollector, int messages, String host, int port ) throws UnknownHostException
    {
        server = new SMTPServer( new MessageHandlerFactory()
        {
            @Override
            public MessageHandler create(MessageContext ctx)
            {
                return new CountingHandler();
            }
        });
        
//        server.setMaxConnections(1000);
        
        server.setBindAddress(InetAddress.getByName(host) );
        server.setPort(port);
        
        this.resultCollector = resultCollector;
        this.countDown = new CountDownLatch(messages);
    }
    
    public void start()
    {
        server.start();
    }
    
    public void stop()
    {
        server.stop();
    }
    
    public void await()
    {
        boolean complete = false;
        
        
        while ( !complete )
            try
            {
                countDown.await();
                complete = true;
            } catch (InterruptedException e) {}
        
    }

    private final class CountingHandler implements MessageHandler
    {   
        private long start;
        private long originalStart;
        private long end;
        
        private boolean dataRead;
        
        public CountingHandler()
        {
            super();
            
            this.start = System.nanoTime();
            this.originalStart = -1;
            this.end = -1;
            
            this.dataRead = false;
        }

        
        @Override
        public void from(String from) throws RejectException {}

        @Override
        public void recipient(String recipient) throws RejectException {}

        @Override
        public void data(InputStream data) throws RejectException, TooMuchDataException, IOException
        {
            try
            {
                
                MimeMessage message = new MimeMessage(null,data);
                
                String start = message.getHeader("X-START-BENCHMARK",null);
                
                if ( start != null )
                    originalStart = Long.valueOf(start);
                
            } catch (MessagingException e)
            {
                throw new IOException( e );
            }
            
            dataRead = true;
        }

        @Override
        public void done()
        {
            end = System.nanoTime();
            
            if (dataRead)
                resultCollector.messageReceived(end-start, originalStart > -1 ? end-originalStart : -1, null);
            else
                resultCollector.messageReceived(end-start, originalStart > -1 ? end-originalStart : -1, new RuntimeException("No received data"));
            
            countDown.countDown();
        }
        
    }
    
}
