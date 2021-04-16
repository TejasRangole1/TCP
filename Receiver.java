import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.PriorityQueue;


public class Receiver {
    
    private int port;
    private DatagramSocket socket;
    private int isn = 200;
    private final int MTU;
    private int nextByteExpected;
    private final int HEADER_SIZE = 24; 
    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    // NONE indicates that it segment is not SYN, ACK, or FIN
    private final int NONE = 3;
    private boolean established = false;
    private boolean finished = false;
    private Network network;
    private Utility receiverUtility;
    private int acknowledgement;
    private PriorityQueue<Segment> receiverQueue;

    public Receiver(int remotePort, int mtu) throws SocketException{
        this.port = remotePort;
        this.MTU = mtu;
        socket = new DatagramSocket(port);
        this.network = new Network(socket, port);
        receiverUtility = new Utility(MTU, port, socket);
        receiverQueue = new PriorityQueue<>((a, b) -> a.getSeqNum() - b.getSeqNum()); 
    }
   
    public void startConnection() throws IOException{
        Segment incomingSegment;
        while(!established) {
            incomingSegment = receiverUtility.receivePacketReceiver();
            int incomingFlag = incomingSegment.getFlag();
            if(incomingFlag == ACK) {
                established = true;
                continue;
            }
            int sequence = incomingSegment.getSeqNum();
            acknowledgement = sequence + 1;
            long timestamp = incomingSegment.getTimestamp();
            int flag = SYN_ACK;
            short checksum = incomingSegment.getChecksum();
            byte[] data = new byte[0];
            receiverUtility.sendPacket(sequence, acknowledgement, timestamp, 0, flag, checksum, data);
        }
        nextByteExpected = 1;
        while(!finished) {
            incomingSegment = receiverUtility.receivePacketReceiver();
            receiverQueue.add(incomingSegment);
            long timestamp = 0;
            while(!receiverQueue.isEmpty() && receiverQueue.peek().getSeqNum() == nextByteExpected) {
                Segment top = receiverQueue.poll();
                timestamp = top.getTimestamp();
                nextByteExpected += top.getLength();
            }
            int sequence  = nextByteExpected - 1;
            byte[] data = new byte[0];
            receiverUtility.sendPacket(sequence, nextByteExpected, timestamp, 0, ACK, (short) 0, data);
        }
    }

}
   

