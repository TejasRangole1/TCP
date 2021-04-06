import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Network {

    private DatagramSocket socket;
    private int port;
    private InetAddress remoteIP;
    private ByteArrayOutputStream byteStream;
    private DataOutputStream outStream;
    private int mtu;
    private final int HEADER_SIZE = 24;
    private Map<Integer, Segment> ackedSegments;
    private Queue<Segment> buffer;
    /**
     * Constructor used by Sender
     * @param networkSocket
     * @param remotePort
     * @param ip
     * @param mtu
     * @param acked
     * @param window
     * @throws UnknownHostException
     */
    public Network(DatagramSocket networkSocket, int remotePort, String ip, int mtu, Map<Integer, Segment> acked, ConcurrentLinkedQueue<Segment> window) throws UnknownHostException{
        this.socket = networkSocket;
        this.port = remotePort;
        this.byteStream = new ByteArrayOutputStream();
        this.outStream = new DataOutputStream(byteStream); 
        this.mtu = mtu;
        this.remoteIP = InetAddress.getByName(ip);
        this.ackedSegments = acked;
        this.buffer = window;
    }
    /**
     * Constructor used by Receiver
     */
    public Network(DatagramSocket networkSocket, int remotePort){
        this.socket = networkSocket;
        this.port = remotePort;
        this.byteStream = new ByteArrayOutputStream();
        this.outStream = new DataOutputStream(byteStream); 
    }
    /**
     * Creates the length field of the TCP header, setting the appropriate flags
     * @param length
     * @param flag
     * @return
     */
    public int setLength(int length, int flag){
        // shifting length left by 3 bits to make room for tcp flags in length field
        length <<= 3;
        // flag = 0 is a TCP ACK, flag = 1 is TCP FIN, flag = 2 is TCP SYN
        int mask = 1;
        length = length | (mask << flag);
        return length;
    }
    /**
     * Creates a TCP segment and sends it
     * @param data
     * @param flag
     * @param ack
     * @param checksum
     * @param seqNum
     * @throws IOException
     */
    public void sendSegment(byte[] data, int flag, int ack, short checksum, int seqNum, int numTransmissions, String whoami) throws IOException{
        outStream.writeInt(seqNum);
        outStream.writeInt(ack);
        long timestamp = System.nanoTime();
        outStream.writeLong(timestamp);
        int length = setLength(data.length, flag);
        outStream.writeInt(length);
        outStream.writeShort(checksum);
        outStream.write(data);
        byte[] packetData = byteStream.toByteArray();
        DatagramPacket outgoingPacket = (whoami.equals("Sender")) ? new DatagramPacket(packetData, packetData.length, remoteIP, port) :
        new DatagramPacket(packetData, packetData.length, socket.getInetAddress(), port);
        Segment segment = new Segment(outgoingPacket, seqNum, timestamp);
        System.out.println( "FROM: " + whoami +  " Network.java: sendSegment(): SENT SYN= " + seqNum + " SENT ACK= " + ack);
        if(whoami.equals("Sender")) buffer.add(segment);
        socket.send(outgoingPacket);
    }
    /**
     * Receives a TCP segment
     */
    public DataInputStream receiveSegment(String whoami) throws IOException{
        byte[] incomingData = new byte[HEADER_SIZE + mtu];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
        DataInputStream din = new DataInputStream(bin);
        System.out.println("FROM: " +  whoami + " Network.java: receiveSegment(): RECEIVED SYN= " + din.readInt() + " RECEIVED ACK= " + din.readInt());
        return din;
    }
}
