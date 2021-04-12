import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Network {

    private DatagramSocket socket;
    private int port;
    private InetAddress remoteIP;
    private InetAddress senderIP;
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
        // set receiver isn to -1, change when we receive initial ack from receiver
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
        // flag = 0 is a TCP ACK, flag = 1 is TCP FIN, flag = 2 is TCP SYN, flag = 3 is NONE
        int mask = flag == 3 ? 0 : 1;
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
    public void sendSegmentSenderSide(byte[] data, int flag, int ack, short checksum, int seqNum, long timestamp) throws IOException{
        outStream.writeInt(seqNum);
        outStream.writeInt(ack);
        outStream.writeLong(timestamp);
        int length = setLength(data.length, flag);
        outStream.writeInt(length);
        outStream.writeShort(checksum);
        outStream.write(data);
        outStream.flush();
        byte[] packetData = byteStream.toByteArray();
        DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length, remoteIP, port);
        Segment segment = new Segment(outgoingPacket, seqNum, timestamp);
        System.out.println( Thread.currentThread().getName() + " FROM: Sender"  +  " Network.java: sendSegmentSenderSide(): SENT SYN= " + seqNum + " SENT ACK= " + ack);
        socket.send(outgoingPacket);
    }
    
    /**
     * Receives a TCP segment
     */
    public DataInputStream receiveSegmentSenderSide() throws IOException{
        byte[] incomingData = new byte[HEADER_SIZE + mtu];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
        DataInputStream din = new DataInputStream(bin);
        buffer.poll();
        return din;
    }

    public DataInputStream receiveSegmentReceiverSide() throws IOException{
        byte[] incomingData = new byte[HEADER_SIZE + mtu];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        this.senderIP = incomingPacket.getAddress();
        ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
        DataInputStream din = new DataInputStream(bin);
        return din;
    }

    public void sendSegmentReceiverSide(byte[] data, int flag, int ack, short checksum, int seqNum, long timestamp) throws IOException{
        outStream.writeInt(seqNum);
        outStream.writeInt(ack);
        outStream.writeLong(timestamp);
        int length = setLength(data.length, flag);
        outStream.writeInt(length);
        outStream.writeShort(checksum);
        outStream.write(data);
        byte[] packetData = byteStream.toByteArray();
        DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length, this.senderIP, port);
        try {
            socket.send(outgoingPacket);
            System.out.println( "FROM: Receiver"  +  " Network.java: sendSegmentReceiverSide(): SENT SYN= " + seqNum + " SENT ACK= " + ack);
        } catch(Exception e){
            System.out.println("Network.java: sendSegmentReceiverSide(): FAILED");
        }

    }
    /**
     * Method to resend a particular segment
     * @param outgoingPacket
     */
    public void resendSegment(DatagramPacket outgoingPacket){
        try {
            socket.send(outgoingPacket);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    /**
     * Method to extract the length and the flag from an int
     * @param length
     * @return
     */
    public int[] extractFlagAndLength(int lengthEntry){
        // first element is length, last element is flag
        int[] res = new int[2];
        int mask = 1;
        int length = 0, flag = 0;
        for(int i = 0; i < 3; i++){
             flag += lengthEntry & mask;
        }
        length = length >> 3;
        res[0] = length;
        res[1] = flag;
        return res;
    }
}
