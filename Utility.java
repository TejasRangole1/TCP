import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Class to create DatagramPackets
 */
public class Utility {

    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int DATA = 3;

    private final int HEADER_SIZE = 24;
    private int MTU;
    private InetAddress remoteIP;
    private int remotePort;

    public Utility(int mtu, int port) {
        this.MTU = mtu;
        this.remotePort = port;
    }

    public Utility(int mtu, String ip, int port) throws UnknownHostException {
        this.MTU = mtu;
        remoteIP = InetAddress.getByName(ip);
        this.remotePort = port;
    }

    private String getFlagOutput(int flag) {
        if(flag == 4){
            return "SYN";
        }
        else if(flag == 5) {
            return "SYN ACK";
        }
        else if(flag == 1) {
            return "ACK";
        }
        else if(flag == 2) {
            return "FIN";
        }
        return "DATA";
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
        System.out.println("SENT: " + byteSeqNum + " ACKNOWLEDGEMENT: " + ack + " TIMESTAMP: " + timestamp + " LENGTH: " + length + " FLAG: " + getFlagOutput(flag));
        return data;
    }

    public void deserialize(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        int seqeuence = bb.getInt();
        int acknowledgement = bb.getInt();
        long timestamp = bb.getLong();
        int length = bb.getInt();
        int flag = length & ((1 << 3) - 1);
        length >>= 3;
        short checksum = bb.getShort();
        byte[] payload = new byte[length];
        bb.get(payload);
        String flagOutput = getFlagOutput(flag);
        System.out.println("RECEIVED SEQUENCE: " + seqeuence + " ACK: " + acknowledgement + " TIMESTAMP: " + timestamp + " LENGTH: " + length + " FLAG: " + flagOutput);
    }

    public void setIP(InetAddress senderIP) {
        remoteIP = senderIP;
    }

    public DatagramPacket encapsulatePacket(int byteSeqNum, int ack, long timestamp, int length, int flag, short checksum, byte[] payloadData){
        byte[] payload = serialize(byteSeqNum, ack, timestamp, length, flag, checksum, payloadData);
        DatagramPacket outgoingPacket = new DatagramPacket(payload, payload.length, remoteIP, remotePort);
        return outgoingPacket;
    }
}
