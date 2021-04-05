import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class Sender {
    /*
    private long timeout = 5000000000L;
    ReentrantLock TOLock;
    private int sws;
    private int mtu;
    private File file;
    private Map<Integer, TCPacket> ackedSegments;
    private Queue<TCPacket> buffer = new ConcurrentLinkedQueue<TCPacket>();
    private Thread timeoutThread;
    private Thread ackThread;

    private class TCPTimeout implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            for(TCPacket packet : buffer){

            }
        }
        
    }
    */ 
    private int port;
    private int remotePort;
    private String remoteIp;

    public Sender(int port, int remotePort, String remoteIp){
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
    }

    public void sendPacket() throws Exception{
        DatagramSocket socket = new DatagramSocket(port);
        String test = "test message";
        InetAddress dst = InetAddress.getByName(remoteIp);
        DatagramPacket packet = new DatagramPacket(test.getBytes(), test.length(), dst, remotePort);
        socket.send(packet);
        socket.close();
    }
    
}
