import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class Sender {

    
    private final int SYN = 2;
    private final int FIN = 1;
    private final int ACK = 0;
    private final int SYN_ACK = 5;


    private long timeout;
    ReentrantLock TOLock;

    private int port;
    private int remotePort;
    private String remoteIp;
    private int sws;
    private int mtu;
    private File file;
    private int seqNum;
    private int ackNumber;

    private final int HEADER_SIZE = 24;

    private Map<Integer, TCPacket> ackedSegments;
    private Queue<TCPacket> buffer;
    private Thread timeoutThread;
    private Thread ackThread;
    private DatagramSocket socket;
    private ByteArrayOutputStream byteStream;
    private DataOutputStream outStream;

    public Sender(int port, int remotePort, String remoteIp, int mtu){
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.timeout = 5000000000L;
        this.mtu = mtu;
        seqNum = 0;
        this.ackNumber = 0;
        buffer = new ConcurrentLinkedQueue<TCPacket>();
        ackedSegments = new HashMap<>();
        byteStream = new ByteArrayOutputStream();
        outStream = new DataOutputStream(byteStream);
    }

    public void startConnection() throws Exception {
        try {
            socket = new DatagramSocket(port);
            String test = "Connection Started";
            DatagramPacket outgoingPacket = createPacket(test.getBytes(), SYN, 0, (short) 0);            
            seqNum++;
            System.out.println("Sender.java: startConnection() sent segment= " + (seqNum - 1));
            socket.send(outgoingPacket);
            byte[] incomingData = new byte[HEADER_SIZE + mtu];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
            DataInputStream din = new DataInputStream(bin);
            int segment = din.readInt();
            int ackNumber = din.readInt();
            System.out.println("Sender.java: startConnection() receieved ACK= " + din.readInt() + " with Receiver ISN= " + ackNumber);
            socket.close();
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public int setLength(int length, int flag){
        // shifting length left by 3 bits to make room for tcp flags in length field
        length <<= 3;
        // flag = 0 is a TCP ACK, flag = 1 is TCP FIN, flag = 2 is TCP SYN
        int mask = 1;
        length = length | (mask << flag);
        return length;
    }

    public DatagramPacket createPacket(byte[] data, int flag, int ack, short checksum) throws Exception {
        InetAddress dst = InetAddress.getByName(remoteIp);
        outStream.writeInt(seqNum);
        outStream.writeInt(ack);
        outStream.writeLong(System.currentTimeMillis());
        int length = setLength(data.length, flag);
        outStream.writeInt(length);
        outStream.writeShort(checksum);
        outStream.write(data);
        byte[] packetData = byteStream.toByteArray();
        InetAddress ip = InetAddress.getByName(remoteIp);
        DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length, ip, remotePort);
        return outgoingPacket;
    }

    
}
