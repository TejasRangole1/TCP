import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Receiver {
    
    private int port;
    private DatagramSocket socket;
    private ByteArrayOutputStream byteStream;
    private DataOutputStream outStream;
    private int isn = 200;
    private int mtu;
    private int nextByteExpected;
    private final int HEADER_SIZE = 24; 
    private final int SYN = 2;
    private final int FIN = 1;
    private final int ACK = 0;
    private final int SYN_ACK = 5;

    public Receiver(int remotePort, int mtu){
        this.port = remotePort;
        this.mtu = mtu;
        byteStream = new ByteArrayOutputStream();
        outStream = new DataOutputStream(byteStream); 
    }

    public void startConnection(){
        try {
            socket = new DatagramSocket(port);
            byte[] incomingData = new byte[HEADER_SIZE + mtu];
            DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
            socket.receive(incomingPacket);
            ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
            DataInputStream din = new DataInputStream(bin);
            int seqNum = din.readInt();
            nextByteExpected = seqNum + 1;
            System.out.println("Receiver.java: startConnection(): received SYN= " + din.readInt());
            byte[] outgoingData = new byte[1];
            DatagramPacket outgoingPacket = createPacket(outgoingData, SYN_ACK, nextByteExpected, (short) 0, isn);
            socket.send(outgoingPacket);
            socket.close();

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
    public int setLength(int length, int flag){
        // shifting length left by 3 bits to make room for tcp flags in length field
        length <<= 3;
        // flag = 0 is a TCP ACK, flag = 1 is TCP FIN, flag = 2 is TCP SYN
        int mask = 1;
        length = length | (mask << flag);
        return length;
    }

    public DatagramPacket createPacket(byte[] data, int flag, int ack, short checksum, int seqNum) throws IOException {
        outStream.writeInt(seqNum);
        outStream.writeInt(ack);
        outStream.writeLong(System.currentTimeMillis());
        int length = setLength(data.length, flag);
        outStream.writeInt(length);
        outStream.writeShort(checksum);
        outStream.write(data);
        byte[] packetData = byteStream.toByteArray();
        DatagramPacket outgoingPacket = new DatagramPacket(packetData, packetData.length);
        return outgoingPacket;
    }
    /*
    public void receieve() throws Exception{
        DatagramSocket socket = new DatagramSocket(remotePort);
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, 1024);
        socket.receive(packet);
        String str = new String(packet.getData(), 0, packet.getLength());
        System.out.println("Received string: " + str);
        socket.close();
    }
    */
}
