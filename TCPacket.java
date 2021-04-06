import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class TCPacket  {

    private DatagramPacket packet;
    private int seqNum;
    private long timestamp;
    private int numTransmissions;

    public TCPacket(DatagramPacket dPacket, int dSeqNum, long dTimestamp, int dChecksum){
        this.packet = dPacket;
        this.seqNum = dSeqNum;
        this.timestamp = dTimestamp;
    }

    public long getTimestamp(){
        return this.timestamp;
    }
}
