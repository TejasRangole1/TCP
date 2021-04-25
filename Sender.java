import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.text.Segment;

import java.util.Map;
import java.util.HashMap;

public class Sender {
    
    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int FIN_ACK = 3;
    private final int DATA = 0;

    private long timeout = 5000000000L;
    private long eRTT;
    private long eDev;

    private int sws;
    private int MTU;

    private String filepath;
    private int lastByteSent = 0;
    private int lastByteAcked = 0;
    private int lastByteRead = 0;
    private byte[] fileBytes;

    private boolean established = false;
    private boolean finished = false;
    // min-heap of byte sequence numbers representing packets that have been written but not sent
    private PriorityQueue<Segment> senderQueue;
    // queue storing the packets that were sent
    private ConcurrentLinkedDeque<Segment> sentPackets;
    private Thread senderThread;
    private Thread receiveThread;
    private Thread timeoutThread;
    private DatagramSocket socket;
    private Segment lastSegmentAcked;
    private ReentrantLock lock;
    private Utility senderUtility;
    // maps sequence numbers to segments
    private Map<Integer, Segment> sequenceToSegment;
    // keeps track of the number of acks for a particular ackNum
    private int totalAcks;

    private int ackNum = 0;

    private class SenderTimeout implements Runnable {
        
        int localTimeout = timeout;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            while(!finished) {
                Thread.sleep(localTimeout);
                try {
                    lock.lock();
                    if(!sentPackets.isEmpty()) {
                        // check if the oldest sent packet has timed out
                        if(System.nanoTime() - sentPackets.peek().getTimestamp() >= timeout) {
                            int timedOutSequence = sentPackets.peek().getSeqNum();
                            while(sentPackets.peek().getSeqNum() >= timedOutSequence) {
                                senderQueue.add(sentPackets.poll());
                            }
                            // resetting timeout since queue is empty
                            localTimeout = timeout;
                        } else {
                            // computing new time to sleep
                            localTimeout = timeout - (System.nanoTime() - sentPackets.peek().getTimestamp());
                        }
                    }
                    else {
                        localTimeout = timeout;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        
    }

    private class SendingThread implements Runnable {
        /**
         * Method to write bytes of file into an array of bytes
         */
        public byte[] writeData(){
            int endIndex = lastByteRead;
            if(sws - (lastByteSent - lastByteAcked) < MTU && sws - (lastByteSent - lastByteAcked) > 0) {
                // create as much data as fits in the sliding window
                endIndex += sws - (lastByteSent - lastByteAcked);
            }
            else {
                // create a packet with one MTU
                endIndex += MTU;
            }
            // If there is less than one MTU left or less than sws number of bytes, then get the rest of the bytes in the file
            endIndex = (endIndex >= fileBytes.length) ? fileBytes.length : endIndex;
            byte[] data = Arrays.copyOfRange(fileBytes, lastByteRead, endIndex);
            lastByteRead += data.length;
            return data; 
        }

        /**
         * Method to send bytes of file 
         * @throws IOException
         */
        public void dataTransfer() throws IOException {
            byte[] payload = new byte[0];
            long timestamp = System.nanoTime();
            Segment outgoingSegment = new Segment(0, 0, timestamp, payload.length, ACK, (short) 0, payload);
            senderQueue.add(outgoingSegment);
            while(lastByteAcked < fileBytes.length) {
                // while there is no room to send packet, read data from file and add it to the queue
                // we also add to queue if there are no packets in the sliding window
                while((lastByteSent - lastByteAcked >= sws || senderQueue.isEmpty()) && lastByteRead < fileBytes.length) {
                    if(!senderQueue.isEmpty()) {
                        Segment top = senderQueue.peek();
                        // This indicates that a previously sent packet must be resent
                        if(top.getSeqNum() + top.getLength() - 1 < lastByteSent) {
                            lastByteSent -= top.getLength();
                            continue;
                        }
                    }
                    byte[] data = writeData();
                    timestamp = System.nanoTime();
                    int sequence = lastByteRead - data.length + 1;
                    Segment segment = new Segment(sequence, 1, timestamp, data.length, DATA, (short) 0, data);
                    senderQueue.add(segment);
                }
                // send packet
                
                if(!senderQueue.isEmpty() && lastByteSent - lastByteAcked < sws) {
                    Segment toSend = senderQueue.poll();
                    toSend.incrementTransmissions();
                    toSend.updateTimestamp();
                    sequenceToSegment.put(toSend.getSeqNum(), toSend);
                    lastByteSent += toSend.getLength();
                    sentPackets.add(toSend);
                    senderUtility.sendPacket(toSend.getSeqNum(), toSend.getAck(), toSend.getTimestamp(), toSend.getLength(), toSend.getFlag(),
                    toSend.getChecksum(), toSend.getPayload());
                }
            }
        }

        @Override
        public void run() {
            try {
                dataTransfer();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
    }

    private class ReceiveThread implements Runnable {
        /**
         * Method to calculate timeout
         * @param sequence
         * @param timestamp
         */
        public void updateTimeout(int sequence, long timestamp) {
            if(sequence == 0) {
                eRTT = System.nanoTime() - timestamp;
                eDev = 0;
                timeout =  2 * eRTT;
            }
            else {
                long sRTT = System.nanoTime() - timestamp;
                long sDev = sRTT - eRTT;
                eRTT = (long) (0.875 * eRTT) +  (long) (1 - 0.875) * sRTT;
                eDev = (long) (0.75 * eDev) + (long) (1 - 0.75) * sDev;
                timeout = eRTT + 4 * eDev;
            }
        }

        public void startConnection() throws IOException{
            byte[] data = new byte[0]; 
            long timestamp = System.nanoTime();
            Segment outgoingSegment = new Segment(0, 0, timestamp, data.length, SYN, (short) 0, data);
            senderUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), outgoingSegment.getTimestamp(), outgoingSegment.getLength(), outgoingSegment.getFlag(), 
            outgoingSegment.getChecksum(), outgoingSegment.getPayload());
            socket.setSoTimeout(5000);
            while(!established) {
                try {
                    Segment incomingSegment = senderUtility.receivePacketSender();
                    // checksums dont match
                    if (incomingSegment == null) {
                        continue;
                    }
                    updateTimeout(0, incomingSegment.getTimestamp());
                    established = true;
                } catch (SocketTimeoutException e) {
                    // update timestamp before resending
                    outgoingSegment.updateTimestamp();
                    senderUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), timestamp, outgoingSegment.getLength(), outgoingSegment.getFlag(), 
            outgoingSegment.getChecksum(), outgoingSegment.getPayload());
                }
            }
            socket.setSoTimeout(0);
            senderThread.start();
            timeoutThread.start();
        }

        public void dataTransfer() throws IOException{
            while(!finished) {
                lastSegmentAcked = senderUtility.receivePacketSender();
                // indicates that the checksum does not match and therefore we drop the packet
                if(lastSegmentAcked == null) {
                    continue;
                }
                // received an ack for a packet that has already been acked 
                else if(ackNum > lastSegmentAcked.getAckNum()){
                    continue;
                }
                // received a duplicate ack
                else if(ackNum == lastSegmentAcked.getAckNum()) {
                    totalAcks++;
                    // three-duplicate acks, add segment to be resent
                    if(totalAcks >= 3) {
                        totalAcks = 0;
                        try {
                            lock.lock();
                            Segment segmentToResend = sequenceToSegment.get(ackNum);
                            sentPackets.remove(segmentToResend);
                            senderQueue.add(segmentToResend);
                        } finally {
                            lock.unlock();
                        }
                        continue;
                    }
                }
                // received an ack for a new segment
                else {
                    ackNum = lastSegmentAcked.getAckNum();
                    totalAcks = 1;
                }
                try {
                    lock.lock();
                    // removing all acked segments from queue
                    Iterator<Segment> it = sentPackets.iterator();
                    while(it.hasNext()) {
                        Segment current = it.next();
                        if(current.getSeqNum() < ackNum) {
                            it.remove();
                        }
                    }
                } finally {
                    lock.unlock();
                }
                lastByteAcked = ackNum - 1;
                updateTimeout(ackNum, lastSegmentAcked.getTimestamp());
                if(lastByteAcked == fileBytes.length) {
                    finished = true;
                }
            }
        }

        public void run(){
            try {
                startConnection();
                dataTransfer();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    public Sender(int port, int remotePort, String remoteIp, int mtu, int windowSize, String filename) throws SocketException, UnknownHostException, IOException{
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.filepath = filename;
        this.timeout = 5000000000L;
        this.MTU = mtu;
        this.sws = windowSize * MTU;
        this.socket = new DatagramSocket(remotePort);
        this.lock = new ReentrantLock(true);
        senderUtility = new Utility(MTU, remoteIp, remotePort, socket);
        senderQueue = new PriorityQueue<>((a, b) -> (a.getSeqNum() != b.getSeqNum()) ? a.getSeqNum() - b.getSeqNum() : a.getLength() - b.getLength());
        sentPackets = new ConcurrentLinkedDeque<>();
        sequenceToSegment = new HashMap<>();
        Runnable senderRunnable = new SendingThread();
        Runnable receiverRunnable = new ReceiveThread();
        SenderTimeout senderTimeout = new SenderTimeout();
        Path path = Paths.get(filepath);
        fileBytes = Files.readAllBytes(path);
        senderThread = new Thread(senderRunnable, "Sender Thread");
        receiveThread = new Thread(receiverRunnable, "Receiver Thread");
        timeoutThread = new Thread(senderTimeout, "Timeout Thread");
        receiveThread.start();
    }

    
}
