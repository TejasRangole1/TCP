import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class ReceiverUtility extends Utility {
    
    private ByteArrayOutputStream byteStream;
    private DataOutputStream outStream;
    private DatagramSocket socket;
    private InetAddress remoteIP;
    private final int HEADER_SIZE = 24;
    private int MTU;

    public ReceiverUtility(DatagramSocket dataSocket, int MSS) throws SocketException {
        this.MTU = MSS;
        this.socket = dataSocket;
        byteStream = new ByteArrayOutputStream();
        outStream = new DataOutputStream(byteStream);
    }

    public DataInputStream receiveSegment() throws IOException {
        byte[] incomingData = new byte[HEADER_SIZE];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        remoteIP = incomingPacket.getAddress();
        ByteArrayInputStream bin = new ByteArrayInputStream(incomingData);
        DataInputStream din = new DataInputStream(bin);
        return din;
    }

    public int[] extractFlagAndLength(int lengthEntry){
        // first element is length, last element is flag
        int[] res = new int[2];
        int mask = 1;
        int length = 0, flag = 0;
        for(int i = 0; i < 3; i++){
             flag += lengthEntry & mask;
        }
        length = length >> 3;
        res[0] = length;
        res[1] = flag;
        return res;
    }

    public void processAndSendAck(DataInputStream is, int responseFlag, int seqNum) throws IOException{
        int seq = is.readInt();
        int nextByteExpected = seq + 1;
        int ack = is.readInt();
        long timestamp = is.readLong();
        int lengthEntry = is.readInt();
        int[] lengthAndFlag = extractFlagAndLength(lengthEntry);
        int length = lengthAndFlag[0], flag = lengthAndFlag[1];
        byte[] nothing = new byte[0];
        System.out.println("Receiver.java: RECEVIED SEQ NUM: " + seq + " FLAG: " + flag + " LENGTH: " + length);
        sendSegment(nothing, responseFlag, nextByteExpected, (short) 0, seqNum, timestamp);
    }

    
    
}