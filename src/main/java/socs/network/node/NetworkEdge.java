package socs.network.node;

public class NetworkEdge{
    private NetworkNode a;
    private NetworkNode b;
    private int weight;

    public NetworkEdge(NetworkNode a,NetworkNode b,int weight){
        this.a=a;
        this.b=b;
        this.weight=weight;
    }
    public NetworkNode getNodeA(){
        return a;
    }
    public NetworkNode getNodeB(){
        return b;
    }
    public int getWeight(){
        return weight;
    }
}