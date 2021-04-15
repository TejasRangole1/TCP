import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Class to create DatagramPackets
 */
public class Utility {

    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int FIN_ACK = 3;

    private final int HEADER_SIZE = 24;
    private int MTU;
    private InetAddress remoteIP;
    private int remotePort;
    private DatagramSocket socket;

    public Utility(int mtu, int port, DatagramSocket dataSocket) {
        this.MTU = mtu;
        this.remotePort = port;
        this.socket = dataSocket;
    }

    public Utility(int mtu, String ip, int port, DatagramSocket dataSocket) throws UnknownHostException {
        this.MTU = mtu;
        this.socket = dataSocket;
        remoteIP = InetAddress.getByName(ip);
        this.remotePort = port;
    }

    private String getFlagOutput(int flag) {
        if(flag == 4){
            return "S";
        }
        else if(flag == 5) {
            return "S A";
        }
        else if(flag == 1) {
            return "A";
        }
        else if(flag == 2) {
            return "F";
        }
        return "F A";
    }

    private byte[] serialize(int byteSeqNum, int ack, long timestamp, int length, int flag, short checksum, byte[] payloadData) {
        byte[] data = new byte[HEADER_SIZE + MTU];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putInt(byteSeqNum);
        bb.putInt(ack);
        bb.putLong(timestamp);
        length  = (length << 3) | flag;
        bb.putInt(length);
        bb.putShort(checksum);
        bb.put(payloadData);
        return data;
    }

    public Segment deserialize(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        int sequence = bb.getInt();
        int acknowledgement = bb.getInt();
        long timestamp = bb.getLong();
        int length = bb.getInt();
        int flag = length & ((1 << 3) - 1);
        length >>= 3;
        short checksum = bb.getShort();
        byte[] payload = new byte[length];
        bb.get(payload);
        String flagOutput = getFlagOutput(flag);
        // Use an array to store the sequence number received as well as the flag of the incoming packet
        Segment incomingSegment = new Segment(sequence, acknowledgement, timestamp, length, flag, checksum, payload);
        long timestampOutput = TimeUnit.NANOSECONDS.toMillis(timestamp);
        System.out.println("rcv " + timestampOutput + " " + flagOutput + " " + sequence + " " + length + " " + acknowledgement);
        return incomingSegment;
    }

    public void sendPacket(int byteSeqNum, int ack, long timestamp, int length, int flag, short checksum, byte[] payloadData) throws IOException{
        byte[] payload = serialize(byteSeqNum, ack, timestamp, length, flag, checksum, payloadData);
        DatagramPacket outgoingPacket = new DatagramPacket(payload, payload.length, remoteIP, remotePort);
        socket.send(outgoingPacket);
        String flagOutput = getFlagOutput(flag);
        long timestampOutput = TimeUnit.NANOSECONDS.toMillis(timestamp);
        System.out.println("snd " + timestampOutput + " " + flagOutput + " " + byteSeqNum + " " + length + " " + ack);
    }

    public Segment receivePacketSender() throws IOException{
        byte[] incomingData = new byte[HEADER_SIZE + MTU];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        return deserialize(incomingPacket.getData());
    }

    public Segment receivePacketReceiver() throws IOException {
        byte[] incomingData = new byte[HEADER_SIZE + MTU];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        remoteIP = incomingPacket.getAddress();
        return deserialize(incomingPacket.getData());
    }



}
