import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Class to create DatagramPackets
 */
public class Utility {

    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int FIN_ACK = 3;
    private final int DATA = 0;

    private final int HEADER_SIZE = 24;
    private int MTU;
    private InetAddress remoteIP;
    private int remotePort;
    private DatagramSocket socket;

    public Utility(int mtu, int port, DatagramSocket dataSocket) {
        this.MTU = mtu;
        this.remotePort = port;
        this.socket = dataSocket;
    }

    public Utility(int mtu, String ip, int port, DatagramSocket dataSocket) throws UnknownHostException {
        this.MTU = mtu;
        this.socket = dataSocket;
        remoteIP = InetAddress.getByName(ip);
        this.remotePort = port;
    }

    private String getFlagOutput(int flag) {
        if(flag == 4){
            return "S";
        }
        else if(flag == 5) {
            return "S A";
        }
        else if(flag == 1) {
            return "A";
        }
        else if(flag == 2) {
            return "F";
        }
        else if(flag == 3) {
            return "F A";
        }
        return "A D";
    }

    private byte[] serialize(int byteSeqNum, int ack, long timestamp, int length, int flag, short checksum, byte[] payloadData) {
        byte[] data = new byte[HEADER_SIZE + MTU];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putInt(byteSeqNum);
        bb.putInt(ack);
        bb.putLong(timestamp);
        length  = (length << 3) | flag;
        bb.putInt(length);
        bb.putShort(checksum);
        bb.put(payloadData);
        return data;
    }

    public Segment deserialize(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        int sequence = bb.getInt();
        int acknowledgement = bb.getInt();
        long timestamp = bb.getLong();
        int length = bb.getInt();
        int flag = length & ((1 << 3) - 1);
        length >>= 3;
        short checksum = bb.getShort();
        byte[] payload = new byte[length];
        bb.get(payload);

        short computedChecksum = computeChecksum(payload, acknowledgement, sequence, timestamp, length);
        //System.out.println("Utility.java: deserialize(): computedChecksum: " + computedChecksum);
        if (computedChecksum != checksum){
            System.out.println("checksums dont match");
            return null;
        } else {
            //System.out.println("checksums match!");
        }

        String flagOutput = getFlagOutput(flag);
        Segment incomingSegment = new Segment(sequence, acknowledgement, timestamp, length, flag, payload);
        System.out.println("rcv " + System.nanoTime() + " " + flagOutput + " " + sequence + " " + length + " " + acknowledgement);
        return incomingSegment;
    }

    public void sendPacket(int byteSeqNum, int ack, long timestamp, int length, int flag, byte[] payloadData) throws IOException{
        short computedChecksum = computeChecksum(payloadData, ack, byteSeqNum, timestamp, length);
        //System.out.println("Utility.java: sendPacket(): computedChecksum = " + computedChecksum);
        byte[] payload = serialize(byteSeqNum, ack, timestamp, length, flag, computedChecksum, payloadData);
        DatagramPacket outgoingPacket = new DatagramPacket(payload, payload.length, remoteIP, remotePort);
        socket.send(outgoingPacket);
        String flagOutput = getFlagOutput(flag);
        System.out.println("snd " + timestamp + " " + flagOutput + " " + byteSeqNum + " " + length + " " + ack);
    }

    public Segment receivePacketSender() throws IOException{
        byte[] incomingData = new byte[HEADER_SIZE + MTU];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        return deserialize(incomingPacket.getData());
    }

    public Segment receivePacketReceiver() throws IOException {
        byte[] incomingData = new byte[HEADER_SIZE + MTU];
        DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
        socket.receive(incomingPacket);
        remoteIP = incomingPacket.getAddress();
        return deserialize(incomingPacket.getData());
    }

    public short computeChecksum(byte[] data, int ack, int seqNum, long timestamp, int length){
        int sum = 0;
        
        sum = addByteArray(ByteBuffer.allocate(4).putInt(seqNum).array(), sum);
        sum = addByteArray(ByteBuffer.allocate(4).putInt(ack).array(), sum);
        sum = addByteArray(ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array(), sum);
        sum = addByteArray(ByteBuffer.allocate(4).putInt(length).array(), sum);
        sum = addByteArray(data, sum);

        return (short) ~sum;

    }

    /**
     * takes 32 bit array, divides it into 16 bit arrays, and adds them to the given sum.
     * 17th bit carry over is also handled in this method
     * @param data
     * @param sum from previous calculation 
     * @return new sum
     */
    public int addByteArray(byte[] data, int sum){

        if (data.length == 0) return sum;

        boolean odd = (data.length%2 == 0)? false: true; //data will have an extra byte at the end if length is odd
        int halfLength = data.length/2;
        int start = 0;
        
        if (data.length > 1){
            for (int i = 0; i < halfLength; i++){ // truncated data by 2 bytes (16 bits) every loop and add it to sum
                byte[] data1 = Arrays.copyOfRange(data, start, start+2);
                ByteBuffer wrapped = ByteBuffer.wrap(data1); 
                short num = wrapped.getShort(); 

                sum += num;
                if (BigInteger.valueOf(sum).testBit(17)){
                    sum = killKthBit(sum, 17);
                    sum += 1; 
                }

                start += 2;
            }
        }

        if (!odd)
            return sum;

        // System.out.println("Data length is odd");

        byte[] data1 = new byte[2];
        data1[1] = data[data.length-1];

        ByteBuffer wrapped = ByteBuffer.wrap(data1); 
        short num = wrapped.getShort(); 

        sum+= num;
        if (BigInteger.valueOf(sum).testBit(17)){
            sum = killKthBit(sum, 17);
            sum += 1; 
        }
        
        return sum; 

    }

    int killKthBit(int n, int k){
        // kth bit of n is being set to 0 by this operation 
        return n & ~(1 << (k - 1)) ;
    }



}
