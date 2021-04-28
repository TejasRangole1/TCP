import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
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
    private final int FIN_ACK = 3;

    private final int SYN_ACK = 5;
    // NONE indicates that it segment is not SYN, ACK, or FIN
    // private final int NONE = 3;
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
                    nextByteExpected = incomingSegment.getSeqNum() + incomingSegment.getLength();
                    receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, incomingSegment.getTimestamp(), 0, ACK, incomingSegment.getPayload());
                }
                // received out of order packet, do not send ack immediatley
                else {
                    receiverQueue.add(incomingSegment);
                }
                continue;
            }
            receiverUtility.sendPacket(incomingSegment.getSeqNum(),incomingSegment.getSeqNum() + 1, incomingSegment.getTimestamp(), 0 ,incomingSegment.getFlag(),incomingSegment.getPayload());
            nextByteExpected = 1;
        }
        while(!finished) {
            incomingSegment = receiverUtility.receivePacketReceiver();
            sequenceToSegment.put(incomingSegment.getSeqNum(), incomingSegment);
            // null indicates checksum does not match
            if (incomingSegment == null) {
                continue;
            }

            if (incomingSegment.getFlag() == FIN){ 
                endConnection(incomingSegment.getSeqNum()+1);
                return;
            }
            receiverQueue.add(incomingSegment);
            // received a packet out of order, send ack for last byte contigous byte received
            if(nextByteExpected < incomingSegment.getSeqNum()){
                Segment resend = sequenceToSegment.get(lastSeqAcked);
                receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, resend.getTimestamp(), 0, ACK, resend.getPayload());
                continue;
            } 

            long timestamp = sequenceToSegment.get(lastSeqAcked).getTimestamp();
            
          
            while (!receiverQueue.isEmpty() && receiverQueue.peek().getSeqNum() < nextByteExpected){
                receiverQueue.poll();
            }
            
            // performing cumulative ack
            while(!receiverQueue.isEmpty() && receiverQueue.peek().getSeqNum() == nextByteExpected) {
                Segment current = receiverQueue.poll();
                fs.write(current.getPayload());
                timestamp = current.getTimestamp();
                lastSeqAcked = current.getSeqNum();
                nextByteExpected = nextByteExpected + current.getLength();
            }
            byte[] data = new byte[0];
            System.out.println("nextByteExpected: " + nextByteExpected);
            receiverUtility.sendPacket(byteSequenceNumber, nextByteExpected, timestamp, 0, ACK, data);
        }
    }

    public void endConnection(int ack) throws IOException{
        byte[] data = new byte[0]; 
        long timestamp = System.nanoTime();
        Segment outgoingSegment = new Segment(byteSequenceNumber, ack, timestamp, data.length, FIN_ACK, data);
        receiverUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), outgoingSegment.getTimestamp(), outgoingSegment.getLength(), outgoingSegment.getFlag(), outgoingSegment.getPayload());
        socket.setSoTimeout(5000);
        boolean established = false;
        while(!established) {
            try {
                Segment incomingSegment = receiverUtility.receivePacketSender();
                if (incomingSegment == null){
                    continue;
                } 
                else if(incomingSegment.getFlag() == ACK && incomingSegment.getAck() == byteSequenceNumber + 1)
                    established = true;
            } catch (SocketTimeoutException e) {
                // update timestamp before resending
                outgoingSegment.updateTimestamp();
                receiverUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), timestamp, outgoingSegment.getLength(), outgoingSegment.getFlag(), outgoingSegment.getPayload());
            }
        }

        System.out.println("Connection successfully terminated");

    }

}
   

