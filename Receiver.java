import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.PriorityQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashMap;

public class Receiver {
    
    private int port;
    private DatagramSocket socket;
    private final int MTU;
    private int nextByteExpected;
    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    // NONE indicates that it segment is not SYN, ACK, or FIN
    private final int NONE = 3;
    private boolean established = false;
    private boolean finished = false;
    private Utility receiverUtility;
    private PriorityQueue<Segment> receiverQueue;
    private File file;
    private FileOutputStream fs;
    int byteSequenceNumber = 0;
    int lastSeqAcked;
    // maps sequence numbers to segments
    Map<Integer, Segment> sequenceToSegment;

    public Receiver(int remotePort, int mtu, String outputFile) throws SocketException{
        this.port = remotePort;
        this.MTU = mtu;
        socket = new DatagramSocket(port);
        file = new File(outputFile);
        try {
            fs = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        receiverUtility = new Utility(MTU, port, socket);
        sequenceToSegment = new HashMap<>();
        receiverQueue = new PriorityQueue<>((a, b) -> (a.getSeqNum() != b.getSeqNum()) ? a.getSeqNum() - b.getSeqNum() : a.getLength() - b.getLength()); 
    }
   
    public void startConnection() throws IOException{
        Segment incomingSegment;
        while(!established) {
            incomingSegment = receiverUtility.receivePacketReceiver();
            sequenceToSegment.put(incomingSegment.getSeqNum(), incomingSegment);
            // null indicates that checksum does not match
            if(incomingSegment == null) {
                continue;
            }
            int incomingFlag =incomingSegment.getFlag();
            // sender has received SYN_ACK move to data transfer state
            if(incomingFlag != SYN) {
                established = true;
                // received an ack from sender
                if(incomingSegment.getSeqNum() == 1) {
                    byteSequenceNumber = 1;
                    nextByteExpected = incomingSegment.getSeqNum() +incomingSegment.getLength();
                    receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, incomingSegment.getTimestamp(), 0, ACK, incomingSegment.getPayload());
                }
                else {
                    receiverQueue.add(incomingSegment);
                }
                continue;
            }
            receiverUtility.sendPacket(incomingSegment.getSeqNum(),incomingSegment.getSeqNum() + 1, incomingSegment.getTimestamp(), 0 ,incomingSegment.getFlag(),incomingSegment.getPayload());
        }
        while(!finished) {
            incomingSegment = receiverUtility.receivePacketReceiver();
            sequenceToSegment.put(incomingSegment.getSeqNum(), incomingSegment);
            // null indicates checksum does not match
            if (incomingSegment == null) {
                continue;
            }
            receiverQueue.add(incomingSegment);
            // received a packet out of order, send ack for last byte contigous byte received
            if(nextByteExpected < incomingSegment.getSeqNum()){
                Segment resend = sequenceToSegment.get(lastSeqAcked);
                // Segment resend = sequenceToSegment.get(nextByteExpected-1);

                receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, resend.getTimestamp(), 0, ACK, resend.getPayload());
                continue;
            }
            long timestamp = 0;
            // performing cumulative ack
            while(!receiverQueue.isEmpty() && receiverQueue.peek().getSeqNum() == nextByteExpected) {
                Segment current = receiverQueue.poll();
                fs.write(current.getPayload());
                timestamp = current.getTimestamp();
                lastSeqAcked = current.getSeqNum();
                nextByteExpected = (nextByteExpected > 0) ? nextByteExpected + current.getLength() : 1;
            }
            byte[] data = new byte[0];
            receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, timestamp, 0, ACK, data);
        }
    }

}
   

