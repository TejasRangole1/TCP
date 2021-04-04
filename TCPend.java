public class TCPend {

    public static void main(String[] args) throws Exception{
        if(args.length == 12){
            // Sender
            int port = Integer.parseInt(args[1]);
            String remoteIP  = args[3];
            int remotePort = Integer.parseInt(args[5]);
            Sender sender = new Sender(port, remotePort, remoteIP);
            sender.sendPacket();
        }        
        else{
            // Receiver
            int port = Integer.parseInt(args[1]);
            Receiver receiver = new Receiver(port);
            receiver.receieve();
        }
    }
}