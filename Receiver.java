import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class Receiver {
    
    private int remotePort;

    public Receiver(int remotePort){
        this.remotePort = remotePort; 
    }

    public void receieve() throws Exception{
        DatagramSocket socket = new DatagramSocket(remotePort);
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, remotePort);
        socket.receive(packet);
        String str = new String(packet.getData(), 0, packet.getLength());
        System.out.println("Received string: " + str);
        socket.close();
    }
}
