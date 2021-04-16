import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.text.Segment;

public class Sender {

    
    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int FIN_ACK = 3;
    private long timeout;

    private int port;
    private int remotePort;
    private String remoteIp;
    private int sws;
    private int MTU;

    private String filepath;
    private int seqNum;
    private int lastByteSent = 0;
    private int lastByteAcked = 0;
    private int lastByteWritten = 0;
    private int ISN;

    private final int HEADER_SIZE = 24;
    private boolean established = false;
    private boolean finished = false;

    private PriorityQueue<Segment> senderQueue;
    private Thread senderThread;
    private Thread receiveThread;
    private Thread timeoutThread;
    private DatagramSocket socket;
    private Segment lastSegmentAcked;

    private Utility senderUtility;

    private DatagramPacket resendSegment;

    private class SenderTimeout implements Runnable {
        
        @Override
        public void run() {
            // TODO Auto-generated method stub
            
        }
        
    }

    private class SendingThread implements Runnable {

        Path path = Paths.get(filepath);
        byte[] fileBytes;
        
        /**
         * Method to write bytes of file into an array of bytes
         */
        public byte[] writeData(){
            int endIndex = lastByteWritten;
            // Get the amount of bytes that fit into the sliding window, if the window is full, then get 1 MTU of data
            endIndex = (lastByteSent - lastByteAcked < sws) ? lastByteWritten + (lastByteSent - lastByteAcked) + 1 : lastByteWritten + MTU + 1;
            // If there is less than one MTU left or less than sws number of bytes, then get the rest of the bytes in the file
            endIndex = (endIndex >= fileBytes.length) ? fileBytes.length : endIndex;
            byte[] data = Arrays.copyOfRange(fileBytes, lastByteWritten, endIndex);
            lastByteWritten += data.length;
            return data; 
        }

        /**
         * Method to send bytes of file 
         * @throws IOException
         */
        public void dataTransfer() throws IOException {
            fileBytes = Files.readAllBytes(path);
            byte[] payload = new byte[0];
            long timestamp = System.nanoTime();
            Segment outgoingSegment = new Segment(seqNum, seqNum, timestamp, payload.length, ACK, (short) 0, payload);
            // senderUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), outgoingSegment.getTimestamp(), outgoingSegment.getLength(), outgoingSegment.getFlag(), 
            // outgoingSegment.getChecksum(), outgoingSegment.getPayload());
            senderQueue.add(outgoingSegment);
            boolean init = true; // indicates that the first data packet should be sent with sequence number 1
            while(lastByteAcked < fileBytes.length) {
                while(lastByteSent - lastByteAcked == sws || senderQueue.isEmpty()) {
                    byte[] data = writeData();
                    timestamp = System.nanoTime();
                    int sequence = (init == true) ? 1 : seqNum + data.length;
                    init = (init == true) ? false : init;
                    Segment segment = new Segment(sequence, sequence, timestamp, data.length, DATA, (short) 0, data);
                    senderQueue.add(segment);
                    seqNum += data.length;
                }
                Segment toSend = senderQueue.poll();
                senderUtility.sendPacket(toSend.getSeqNum(), toSend.getAck(), toSend.getTimestamp(), toSend.getLength(), toSend.getFlag(),
                toSend.getChecksum(), toSend.getPayload()); 
                lastByteSent += toSend.getLength();
            }
            finished = true;
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

        public void startConnection() throws IOException{
            byte[] data = new byte[0];
            long timestamp = System.nanoTime();
            Segment outgoingSegment = new Segment(seqNum, seqNum, timestamp, data.length, SYN, (short) 0, data);
            senderUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), outgoingSegment.getTimestamp(), outgoingSegment.getLength(), outgoingSegment.getFlag(), 
            outgoingSegment.getChecksum(), outgoingSegment.getPayload());
            socket.setSoTimeout(5000);
            while(!established) {
                try {
                    senderUtility.receivePacketSender();
                    established = true;
                    seqNum++;
                } catch (SocketTimeoutException e) {
                    senderUtility.sendPacket(outgoingSegment.getSeqNum(), outgoingSegment.getAck(), outgoingSegment.getTimestamp(), outgoingSegment.getLength(), outgoingSegment.getFlag(), 
            outgoingSegment.getChecksum(), outgoingSegment.getPayload());
                }
            }
            senderThread.start();
        }

        public void dataTransfer() throws IOException{
            while(!finished) {
                lastSegmentAcked = senderUtility.receivePacketSender();
                lastSegmentAcked.incrementAcks();
                lastByteAcked = lastSegmentAcked.getAck();
                while(!senderQueue.isEmpty() && lastByteAcked == senderQueue.peek().getSeqNum() + 1) {
                    Segment top = senderQueue.poll();
                    lastByteAcked += top.getLength();
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


    public Sender(int port, int remotePort, String remoteIp, int mtu, int windowSize, String filename) throws SocketException, UnknownHostException{
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.filepath = filename;
        this.timeout = 5000000000L;
        this.MTU = mtu;
        this.seqNum = 0;
        this.sws = windowSize * MTU;
        this.socket = new DatagramSocket(remotePort);
        senderUtility = new Utility(MTU, remoteIp, remotePort, socket);
        senderQueue = new PriorityQueue<Segment>((a, b) -> a.getSeqNum() - b.getSeqNum());
        Runnable senderRunnable = new SendingThread();
        Runnable receiverRunnable = new ReceiveThread();
        SenderTimeout senderTimeout = new SenderTimeout();
        senderThread = new Thread(senderRunnable, "Sender Thread");
        receiveThread = new Thread(receiverRunnable, "Receiver Thread");
        timeoutThread = new Thread(senderTimeout, "Timeout Thread");
        receiveThread.start();
    }

    
}
