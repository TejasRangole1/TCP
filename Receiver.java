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

    public Receiver(int remotePort, int mtu) throws SocketException{
        this.port = remotePort;
        this.MTU = mtu;
        socket = new DatagramSocket(port);
        this.network = new Network(socket, port); 
    }


    public void processSegment(DataInputStream is) throws IOException{
        int seq = is.readInt();
        int nextByteExpected = seq + 1;
        int ack = is.readInt();
        long timestamp = is.readLong();
        int rawLength = is.readInt();
        int[] lengthAndFlag = network.extractFlagAndLength(rawLength);
        int length = lengthAndFlag[0], flag = lengthAndFlag[1];
        byte[] nothing = new byte[0];
        System.out.println("Receiver.java: RECEVIED SEQ NUM: " + seq + " FLAG: " + flag + " LENGTH: " + length + " ACK: " + ack);
        if(flag == SYN) {
            network.sendSegmentReceiverSide(nothing, ACK, nextByteExpected, (short) 0, isn, timestamp);
        }
        else {
            network.sendSegmentReceiverSide(nothing, ACK, nextByteExpected, (short) 0, seq, timestamp);
        }
        established = (flag == ACK) ? true : false;
    }

    public void startConnection() throws IOException{
        while(!establish) {
            byte[] incomingData = new byte[HEADER_SIZE + MTU];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            DataInputStream is = network.receiveSegmentReceiverSide();
            processSegment(is);
        }
    }

}
   

