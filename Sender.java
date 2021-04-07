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
import java.net.SocketTimeoutException;
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

    private Map<Integer, Segment> ackedSegments;
    private ConcurrentLinkedQueue<Segment> buffer;
    private Thread timeoutThread;
    private Thread current = Thread.currentThread();
    private DatagramSocket socket;
    private Network network;

    private class SegmentTimeout implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            for(Segment segment : buffer){
                if(System.nanoTime() - segment.getTimestamp() >= timeout){
                    current.interrupt();
                }
            }
        }
        
    }

    public Sender(int port, int remotePort, String remoteIp, int mtu) throws SocketException, UnknownHostException{
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.timeout = 5000000000L;
        this.mtu = mtu;
        this.seqNum = 0;
        this.ackNumber = 0;
        this.buffer = new ConcurrentLinkedQueue<Segment>();
        this.ackedSegments = new HashMap<>();
        this.socket = new DatagramSocket(port);
        this.network = new Network(socket, remotePort, remoteIp, mtu, ackedSegments, buffer);
    }

    public void startConnection() throws Exception {
        boolean established = false;
        byte[] nothing = new byte[0];
        while(!established){
            network.sendSegment(nothing, SYN, 0, (short) 0, seqNum, 1, "Sender", null);
            try {
                socket.setSoTimeout(5000);
                network.receiveSegment("Sender");
            } catch (SocketTimeoutException e){
                continue;
            }
            established = true;
        }
        System.out.println("Sender.java: startConnection(): Connection Established!");
        socket.close();
    }

    
}
