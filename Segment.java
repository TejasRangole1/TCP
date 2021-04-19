import java.net.DatagramPacket;

public class Segment  {

    private final int SYN = 4;
    private final int FIN = 2;
    private final int ACK = 1;
    private final int SYN_ACK = 5;
    private final int DATA = 3;


    private int byteSequenceNumber;
    private int acknowledgement;
    private int length;
    private long timestamp;
    private int flag;
    private int numTransmissions = 0;
    private short checksum;
    private byte[] payload;
    private int totalAcks;

    public Segment(int sequence, int ack, long timestamp, int length, int flag, short checksum, byte[] payloadData){
        this.byteSequenceNumber = sequence;
        this.acknowledgement = ack;
        this.length = length;
        this.timestamp = timestamp;
        this.flag = flag;
        this.checksum = checksum;
        this.payload = payloadData;
        this.numTransmissions = 1;
        totalAcks = 0;
    }

    public int getSeqNum(){
        return byteSequenceNumber;
    }

    public int getLength() {
        return length;
    }

    public int getAck(){
        return acknowledgement;
    }

    public int getFlag() {
        return flag;
    }

    public byte[] getPayload(){
        return payload;
    }

    public short getChecksum() {
        return checksum;
    }
        
    public long getTimestamp(){
        return this.timestamp;
    }

    public void incrementTransmissions(){
        numTransmissions++;
    }

    public int getTransmissions(){
        return numTransmissions;
    }

    public int getTotalAcks() {
        return totalAcks;
    }

    public void incrementAcks() {
        totalAcks++;
    }

    public void resetTotalAcks() {
        totalAcks = 0;
    }

    public void updateTimestamp() {
        this.timestamp = System.nanoTime();
    }

    public void setTotalAcks(int acks) {
        this.totalAcks = acks;
    }
    

}
