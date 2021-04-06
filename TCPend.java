public class TCPend {

    public static void main(String[] args) throws Exception{
        if(args.length == 12){
            // Sender
            int port = Integer.parseInt(args[1]);
            String remoteIP  = args[3];
            int remotePort = Integer.parseInt(args[5]);
            int mtu = Integer.parseInt(args[9]);
            Sender sender = new Sender(port, remotePort, remoteIP, mtu);
            sender.startConnection();
        }        
        else{
            // Receiver
            int port = Integer.parseInt(args[1]);
            int mtu = Integer.parseInt(args[3]);
            Receiver receiver = new Receiver(port, mtu);
            receiver.startConnection();
        }
    }
}