package socs.network.node;
import java.util.TimerTask;

public class HeartbeatTask extends TimerTask {
    private short port;
    //1=disconnect, 2 = send heartbeat
    int type;
    //router instance for running methods;
    Router router;
    public HeartbeatTask(short port,int type,Router router){
        this.port=port;
        this.type=type;
        this.router=router;
    }
    @Override
    public void run() {

        if(type==1){
            //TODO need to run disconnect here
            System.out.println("disconnecting " + port);
            router.disconnect(port);
        }
        if(type==2){
            //TODO need to send heartbeat here
            //need new method to run a sender here
            //System.out.println("Pingign: " + port);
            router.ping(port);
        }
    }
}