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

public class Sender {

    
    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int NONE = 3;


    private long timeout;
    ReentrantLock TOLock;

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

    private Map<Integer, Segment> ackedSegments;
    private ConcurrentLinkedQueue<Segment> buffer;
    private PriorityQueue<Segment> senderQueue;
    private Thread senderThread;
    private Thread receiveThread;
    private Thread timeoutThread;
    private DatagramSocket socket;
    private Network network;

    private DatagramPacket resendSegment;

    private class SenderTimeout implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            while(true) {
                for(Segment segment : buffer){
                    if(System.nanoTime() - segment.getTimestamp() >= timeout) {
                        System.out.println("SenderTimeout: run(): SEGMENT " + segment.getSeqNum() + " TIMED OUT");
                        segment.incrementTransmissions();
                        resendSegment = segment.getPacket();
                        senderThread.interrupt();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        
    }

    private class SendingThread implements Runnable {

        Path path = Paths.get(filepath);
        byte[] fileBytes;

        public void startConnection() throws IOException{
            byte[] data = new byte[MTU];
            long timestamp = System.nanoTime();
            DatagramPacket outgoingPacket = network.createSegment(data, SYN, 0, (short) 0, seqNum, timestamp);
            network.sendSegmentSenderSide(outgoingPacket, seqNum, 0);
        }
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
            /*
            while(!established) {
                System.out.println("Sender.java: " + Thread.currentThread().getName() + " ESTABLISHED: " + established);
            }
            System.out.println("Sender.java: " + Thread.currentThread().getName() + " ESTABLISHED: " + established);
            long timestamp = System.nanoTime();
            byte[] data = new byte[0];
            DatagramPacket outgoingPacket = network.createSegment(data, ACK, ISN, (short) 0, seqNum, timestamp);
            System.out.println("Sender.java: " + Thread.currentThread().getName() + "SENDING  ACK: " + ISN);
            network.sendSegmentSenderSide(outgoingPacket, seqNum, ISN);
            */
            fileBytes = Files.readAllBytes(path);
            boolean init = false; // indicates whether we are sending the first byte of data, in which case we should send an ACK
            while(lastByteAcked != fileBytes.length) {
                while(lastByteSent - lastByteAcked == sws || senderQueue.isEmpty()){
                    byte[] data = writeData();
                    DatagramPacket outgoingPacket;
                    long timestamp = System.nanoTime();
                    if(!init) {
                        // first data packet to be sent must have an ACK
                        outgoingPacket = network.createSegment(data, ACK, data.length, (short) 0, ISN, timestamp);
                        init = true;
                    }
                    else {
                        outgoingPacket = network.createSegment(data, NONE, data.length, (short) 0, seqNum, timestamp);
                    }
                    Segment segment = new Segment(outgoingPacket, seqNum, timestamp);
                    senderQueue.add(segment);
                    seqNum++;
                }
                Segment outgoingSegment = senderQueue.poll();
                int seq = outgoingSegment.getSeqNum(), ack = seq + 1;
                DatagramPacket outgoingPacket = outgoingSegment.getPacket();
                network.sendSegmentSenderSide(outgoingPacket, seq, ack);                      
            }
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            try {
                //startConnection();
                System.out.println("Sender.java: " + Thread.currentThread().getName() + " ESTABLISHED: " + established);
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
            DatagramPacket outgoingPacket = network.createSegment(data, SYN, 0, (short) 0, seqNum, timestamp);
            network.sendSegmentSenderSide(outgoingPacket, seqNum, 0);
            socket.setSoTimeout(5000);
            while(true) {
                try {
                    DataInputStream is = network.receiveSegmentSenderSide();
                    ISN = is.readInt();
                    int ack = is.readInt();
                    System.out.println("Sender.java: " + Thread.currentThread().getName() + " RECEIVED: " + ISN + " ACK: " + ack);
                    seqNum++;
                    ISN++;
                    break;
                } catch (SocketTimeoutException e) {
                    network.sendSegmentSenderSide(outgoingPacket, seqNum, 0);
                    continue;
                }
            }
            established = true;
            senderThread.start();
        }

        public void dataTransfer() throws IOException{
            while(!finished) {
                DataInputStream is = network.receiveSegmentSenderSide();
                senderQueue.poll();
                int seq = is.readInt(), ack = is.readInt();
                System.out.println("Receiver.java: " + Thread.currentThread().getName() + " dataTransfer(): " + " RECEIVED SEGMENT: " + seq + " ACK: " + ack);
            }            
        }

        public void run(){
           try {
            startConnection();
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
        this.sws = windowSize;
        this.buffer = new ConcurrentLinkedQueue<Segment>();
        this.ackedSegments = new HashMap<>();
        this.socket = new DatagramSocket(remotePort);
        this.network = new Network(socket, remotePort, remoteIp, mtu, ackedSegments, buffer);
        senderQueue = new PriorityQueue<Segment>((a, b) -> a.getSeqNum() - b.getSeqNum());
        Runnable senderRunnable = new SendingThread();
        Runnable receiverRunnable = new ReceiveThread();
        SenderTimeout senderTimeout = new SenderTimeout();
        senderThread = new Thread(senderRunnable, "Sender Thread");
        receiveThread = new Thread(receiverRunnable, "Receiver Thread");
        timeoutThread = new Thread(senderTimeout, "Timeout Thread");
        //senderThread.start();
        receiveThread.start();
        //timeoutThread.start();
    }
    
}
