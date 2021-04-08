import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
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
    private int ackNumber;

    private final int HEADER_SIZE = 24;
    private boolean established = false;

    private Map<Integer, Segment> ackedSegments;
    private ConcurrentLinkedQueue<Segment> buffer;
    private Thread senderThread;
    private Thread receiveThread;
    private Thread timeoutThread;
    private DatagramSocket socket;
    private Network network;

    private class SenderTimeout implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            for(Segment segment : buffer){
                if(System.nanoTime() - segment.getTimestamp() >= timeout) {
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

    private class SendingThread implements Runnable {

        public void startConnection() throws IOException{
            byte[] data = new byte[0];
            network.sendSegmentSenderSide(data, SYN, 0, (short) 0, seqNum);
            while(!established){
                if(Thread.currentThread().isInterrupted()){
                    network.sendSegmentSenderSide(data, SYN, 0, (short) 0, seqNum);
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

        public void startConnection(){
            try {
                network.receiveSegmentSenderSide();
                established = true;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void run(){
           startConnection();
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
        Runnable senderRunnable = new SendingThread();
        Runnable receiverRunnable = new ReceiveThread();
        Runnable senderTimeout = new SenderTimeout();
        senderThread = new Thread(senderRunnable);
        receiveThread = new Thread(receiverRunnable);
        timeoutThread = new Thread(senderTimeout);
        senderThread.start();
        receiveThread.start();
    }
    
}
