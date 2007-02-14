package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueuePurgeOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class QueuePurgeHandler implements StateAwareMethodListener<QueuePurgeBody>
{
    private static final QueuePurgeHandler _instance = new QueuePurgeHandler();

    public static QueuePurgeHandler getInstance()
    {
        return _instance;
    }

    private final boolean _failIfNotFound;

    public QueuePurgeHandler()
    {
        this(true);
    }

    public QueuePurgeHandler(boolean failIfNotFound)
    {
        _failIfNotFound = failIfNotFound;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<QueuePurgeBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        QueuePurgeBody body = evt.getMethod();
        AMQQueue queue;
        if(body.queue == null)
        {
            queue = session.getChannel(evt.getChannelId()).getDefaultQueue();
            if(queue == null)
            {
                if(_failIfNotFound)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED.getCode(),"No queue specified.");
                }

            }
        }
        else
        {
            queue = queueRegistry.getQueue(body.queue);
        }

        if(queue == null)
        {
            if(_failIfNotFound)
            {
                throw body.getChannelException(404, "Queue " + body.queue + " does not exist.");
            }
        }
        else
        {
                long purged = queue.clearQueue(session.getChannel(evt.getChannelId()).getStoreContext());


                if(!body.nowait)
                {
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    session.writeFrame(QueuePurgeOkBody.createAMQFrame(evt.getChannelId(),
                        (byte)8, (byte)0,	// AMQP version (major, minor)
                        purged));	// messageCount
                }
        }
    }
}
