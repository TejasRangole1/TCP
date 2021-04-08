import java.net.DatagramPacket;

public class Segment  {

    private DatagramPacket packet;
    private int seqNum;
    private long timestamp;
    private int numTransmissions = 1;

    public Segment(DatagramPacket dPacket, int dSeqNum, long dTimestamp){
        this.packet = dPacket;
        this.seqNum = dSeqNum;
        this.timestamp = dTimestamp;
        this.numTransmissions = 1;
    }

    public int getSeqNum(){
        return seqNum;
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

    public DatagramPacket getPacket(){
        return this.packet;
    }
}
