package org.minima.system.network.p2p.messages;

import lombok.*;
import org.minima.objects.base.MiniData;
import org.minima.system.network.p2p.Traceable;
import org.minima.system.network.p2p.event.EventPublisher;
import org.minima.utils.Streamable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import static lombok.AccessLevel.PRIVATE;

/**
 * P2P message suggesting that a node is not accepting messages
 */

@NoArgsConstructor(access = PRIVATE)
@Getter
@Setter(PRIVATE)
public class P2PMsgNodeNotAccepting implements Streamable, Traceable {

    private MiniData traceId = MiniData.getRandomData(8);
    /**
     * Node broadcasting the message
     */
    private InetSocketAddress broadcaster;

    public P2PMsgNodeNotAccepting(InetSocketAddress broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void writeDataStream(DataOutputStream zOut) throws IOException {
        traceId.writeDataStream(zOut);
        InetSocketAddressIO.writeAddress(broadcaster, zOut);
        EventPublisher.publishWrittenStream(this);
    }

    @Override
    public void readDataStream(DataInputStream zIn) throws IOException {
        setTraceId(MiniData.ReadFromStream(zIn));
        broadcaster = InetSocketAddressIO.readAddress(zIn);
        EventPublisher.publishReadStream(this);
    }

    public static P2PMsgNodeNotAccepting ReadFromStream(DataInputStream zIn) throws IOException {
    P2PMsgNodeNotAccepting data = new P2PMsgNodeNotAccepting();
        data.readDataStream(zIn);
        return data;
    }

    @Override
    public String getTraceId() {
        return traceId.to0xString();
    }
}
