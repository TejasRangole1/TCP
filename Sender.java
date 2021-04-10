import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
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
    private int ackNum;

    private final int HEADER_SIZE = 24;
    private boolean established = false;

    private Map<Integer, Segment> ackedSegments;
    private ConcurrentLinkedQueue<Segment> buffer;
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

        public void startConnection() throws IOException{
            byte[] data = new byte[4];
            network.sendSegmentSenderSide(data, SYN, 0, (short) 0, seqNum);
            while(!established){
                if(senderThread.isInterrupted()){
                    network.resendSegment(resendSegment);
                }
            }
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            try {
                startConnection();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }            
        }
        
    }

    private class ReceiveThread implements Runnable {

        public void startConnection() throws IOException{
            while(!established) {
                /*
                try {
                    socket.setSoTimeout(5000);
                    System.out.println(receiveThread.getName() + " : timeout set");
                    network.receiveSegmentSenderSide();
                    established = true;
                } catch (SocketException e){
                    senderThread.interrupt();
                    continue;
                }
                */
                DataInputStream response = network.receiveSegmentSenderSide();
                int ackNum = response.readInt() + 1;
                seqNum++;
                System.out.println("Sender.java: startConnection(): " + Thread.currentThread().getName() + " RECEIVED SYN: " + (ackNum - 1) + " SETTING ACK TO: " + ackNum + " SEQUENCE NUMBER= " + seqNum);
                established = true;
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
        this.socket = new DatagramSocket(remotePort);
        this.network = new Network(socket, remotePort, remoteIp, mtu, ackedSegments, buffer);
        Runnable senderRunnable = new SendingThread();
        Runnable receiverRunnable = new ReceiveThread();
        SenderTimeout senderTimeout = new SenderTimeout();
        senderThread = new Thread(senderRunnable, "Sender Thread");
        receiveThread = new Thread(receiverRunnable, "Receiver Thread");
        timeoutThread = new Thread(senderTimeout, "Timeout Thread");
        senderThread.start();
        receiveThread.start();
        timeoutThread.start();
    }
    
}
