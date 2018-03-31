package socs.network.node;
import java.util.TimerTask;

public class HeartbeatTask extends TimerTask {
    private short port;
    public HeartbeatTask(short port){
        this.port=port;
    }
    @Override
    public void run() {
        System.out.println("disconnecting " + port);
        //need to run disconnect here
    }
}