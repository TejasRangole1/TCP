import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class TCPacket  {

    private ByteBuffer packet;
    private int seqNum;
    private long timestamp;
    private int numTransmissions;

    public TCPacket(ByteBuffer dPacket, int dSeqNum, long dTimestamp, int dChecksum){
        this.packet = dPacket;
        this.seqNum = dSeqNum;
        this.timestamp = dTimestamp;
    }

    public long getTimestamp(){
        return this.timestamp;
    }
}
