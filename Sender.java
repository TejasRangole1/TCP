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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class Sender {

    private 
    
    private final int SYN = 2;
    private final int FIN = 1;
    private final int ACK = 0;

    private long timeout;
    ReentrantLock TOLock;

    private int port;
    private int remotePort;
    private String remoteIp;
    private int sws;
    private int mtu;
    private File file;
    private int seqNum;

    private final int HEADER_SIZE = 24;

    private Map<Integer, TCPacket> ackedSegments;
    private Queue<TCPacket> buffer;
    private Thread timeoutThread;
    private Thread ackThread;
    private DatagramSocket socket;
    private ByteArrayOutputStream byteStream;
    /*
    private class TCPTimeout implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            for(TCPacket packet : buffer){
                if(System.currentTimeMillis() - packet.getTimestamp() >= timeout){

                }
            }
        }
        
    }
    */ 
    

    public Sender(int port, int remotePort, String remoteIp){
        this.port = port;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.timeout = 5000000000L;
        seqNum = 0;
        buffer = new ConcurrentLinkedQueue<TCPacket>();
        ackedSegments = new HashMap<>();
    }

    public void startConnection() {
        try {
            socket = new DatagramSocket(port);
            String test = "Connection Started";
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteStream);
            outStream.writeInt(seqNum);
            outStream.writeInt(seqNum + 1);
            outStream.writeLong(System.currentTimeMillis());
            int length = setLength(test.length(), SYN);
            outStream.writeInt(length);
            outStream.writeShort(0);
            outStream.write(test.getBytes());
            byte[] packetData = byteStream.toByteArray();
            DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length);
            socket.send(outgoingPacket);
            seqNum++;
            byte[] incomingData = new byte[HEADER_SIZE + length];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public int setLength(int length, int flag){
        // shifting length by 3 bits to make room for tcp flags in length field
        length <<= 3;
        // flag = 0 is a TCP ACK, flag = 1 is TCP FIN, flag = 2 is TCP SYN
        int mask = 1;
        length = (flag == 0) ? length | mask : length | (mask << flag);
        return length;
    }

    public DatagramPacket sendPacket(byte[] data, int flag) {
        //DatagramSocket socket = new DatagramSocket(port);
        // String test = "test message";
        try {
            InetAddress dst = InetAddress.getByName(remoteIp);
            DatagramPacket packet = new DatagramPacket(header.array(), header.limit(), dst, remotePort);
            socket.send(packet);
            socket.close();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
       
    }

    
}
