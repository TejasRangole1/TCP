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
    private int mtu;
    private int nextByteExpected;
    private final int HEADER_SIZE = 24; 
    private final int SYN = 2;
    private final int FIN = 1;
    private final int ACK = 0;
    private final int SYN_ACK = 5;
    private Network network;

    public Receiver(int remotePort, int mtu) throws SocketException{
        this.port = remotePort;
        this.mtu = mtu;
        socket = new DatagramSocket(port);
        this.network = new Network(socket, remotePort); 
    }

    public void startConnection() throws IOException{
        boolean established = false;
        while(!established) {
            byte[] incomingData = new byte[HEADER_SIZE];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            System.out.println("Receiver: startConnection(): incoming packet IP address: " + incomingPacket.getAddress());
            DataInputStream is = network.receiveSegment("Receiver");
            int ack = is.readInt() + 1;
            byte[] nothing = new byte[0];
            network.sendSegmentReceiverSide(nothing, SYN_ACK, ack, (short) 0, isn, incomingPacket.getAddress());
            established = true;
        }
    }
}
   

