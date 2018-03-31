package socs.network.node;
import socs.network.message.SOSPFPacket;
import  socs.network.node.Link;
import java.net.Socket;
import java.io.*;
import socs.network.node.RouterDescription;

public class LSPThread extends Thread{
    private Socket serverHandle;
    private Link link;
    private short portnum;

    public LSPThread(Link _link, short _portnum){
        this.link = _link;
        this.portnum = _portnum;
    }
}