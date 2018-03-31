package socs.network.node;
import java.util.TimerTask;

public class HeartbeatTask extends TimerTask {
    private short port;
    //1=disconnect, 2 = send heartbeat
    int type;
    public HeartbeatTask(short port,int type){
        this.port=port;
        this.type=type;
    }
    @Override
    public void run() {
        System.out.println("disconnecting " + port);

        if(type==1){
            //TODO need to run disconnect here
        }
        if(type==2){
            //TODO need to send heartbeat here
        }
    }
}