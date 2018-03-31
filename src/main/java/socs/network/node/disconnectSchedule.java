package socs.network.node;
import java.util.TimerTask;

public class disconnectSchedule extends TimerTask {
    private short port;
    public disconnectSchedule(short port){
        this.port=port;
    }
    @Override
    public void run() {
        System.out.println("disconnecting " + port);
    }
}