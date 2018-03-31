package socs.network.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.List;
import java.util.Collections;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

public class NetworkGraph {
    private ArrayList<NetworkNode> nodes= new ArrayList<NetworkNode>();
    private ArrayList<NetworkEdge> edges= new ArrayList<NetworkEdge>();

    private Set<NetworkNode> settledNodes;
    private Set<NetworkNode> unSettledNodes;
    private HashMap<NetworkNode, NetworkNode> predecessors;
    private HashMap<NetworkNode, Integer> distance;
    
    //constructor that converts a lsd into a graph of the network topology
    public NetworkGraph(String source,HashMap<String, LSA> db){
        for (LSA lsa: db.values()) {
          
          //find current node
          NetworkNode n= getGraphNode(lsa.linkStateID);

          for (LinkDescription ld : lsa.links) {
            //get remote node
            NetworkNode r=getGraphNode(ld.linkID);
            //do not add link to self
            if(lsa.linkStateID !=ld.linkID){
                addEdge(n,r,ld.tosMetrics);
            }
          }
        }
        NetworkNode n = getGraphNode(source);
        dijkstra(n);
    }
    public String getShortestPath(String ip){
        NetworkNode n=getGraphNode(ip);
        LinkedList<NetworkNode> path = new LinkedList<NetworkNode>();
        NetworkNode step = n;

        // check if a path exists
        if (predecessors.get(step) == null) {
            return null;
        }

        path.add(step);
        while (predecessors.get(step) != null) {
            step = predecessors.get(step);
            path.add(step);
        }
        // Put it into the correct order
        Collections.reverse(path);

        String p="";
        NetworkNode prev=null;
        for(NetworkNode i:path){
            if(prev!=null){
                p= p + "(" + getEdge(prev,i).getWeight() + ") " + i.getIp() + "->";
            }else{
                p= p + i.getIp() + "->";   
            }
            prev=i; 
        }
        p= p.substring(0,p.length()-2);
        return p;
    }
    public String toString(){
        String s="Nodes: \n";
        for(NetworkNode n: nodes){
            s= s + n.getIp() + "\n";
        }
        s= s + "Edges: \n";
        for(NetworkEdge e: edges){
            s= s + e.getNodeA().getIp() + " " + e.getNodeB().getIp() + "\n";
        }
        return s;
    }
    //returns current node in graph based on IP or creates a new 1
    private NetworkNode getGraphNode(String ip){
        
        for(NetworkNode n:nodes){
            if(n.getIp().equals(ip)){
                return n;
            }
        }
        NetworkNode n= new NetworkNode(ip);
        nodes.add(n);
        return n;
    
    }
    //adds a edge if it doesnt exist yet
    private void addEdge(NetworkNode a, NetworkNode b,int weight){
        for(NetworkEdge e: edges){
            if((e.getNodeA().getIp().equals(a.getIp()) && e.getNodeB().getIp().equals(b.getIp()))){ 
                return;
            }
        }
        NetworkEdge newE= new NetworkEdge(a,b,weight);
        edges.add(newE);
    }
    private NetworkEdge getEdge(NetworkNode a,NetworkNode b){
        for(NetworkEdge e: edges){
            if((e.getNodeA().getIp().equals(a.getIp()) && e.getNodeB().getIp().equals(b.getIp()))){
                return e;
            }
        }
        return null;
    }
    public void dijkstra(NetworkNode source) {
        settledNodes = new HashSet<NetworkNode>();
        unSettledNodes = new HashSet<NetworkNode>();
        distance = new HashMap<NetworkNode, Integer>();
        predecessors = new HashMap<NetworkNode, NetworkNode>();
        distance.put(source, 0);
        unSettledNodes.add(source);
        while (unSettledNodes.size() > 0) {
            NetworkNode node = getMinimum(unSettledNodes);
            settledNodes.add(node);
            unSettledNodes.remove(node);
            findMinimalDistances(node);
        }
    }
    private int getDistance(NetworkNode node, NetworkNode target) {
        for (NetworkEdge edge : edges) {
            if (edge.getNodeA().getIp().equals(node.getIp())
                    && edge.getNodeB().getIp().equals(target.getIp())) {
                return edge.getWeight();
            }
        }
        return -1;
    }
    private void findMinimalDistances(NetworkNode node) {
        List<NetworkNode> adjacentNodes = getNeighbors(node);
        for (NetworkNode target : adjacentNodes) {
            if (getShortestDistance(target) > getShortestDistance(node)
                    + getDistance(node, target)) {
                distance.put(target, getShortestDistance(node)
                        + getDistance(node, target));
                predecessors.put(target, node);
                unSettledNodes.add(target);
            }
        }

    }
    private List<NetworkNode> getNeighbors(NetworkNode node) {
        List<NetworkNode> neighbors = new ArrayList<NetworkNode>();
        for (NetworkEdge edge : edges) {
            if (edge.getNodeA().getIp().equals(node.getIp())
                    && !isSettled(edge.getNodeB())) {
                neighbors.add(edge.getNodeB());
            }
        }
        return neighbors;
    }

    private NetworkNode getMinimum(Set<NetworkNode> nodes) {
        NetworkNode minimum = null;
        for (NetworkNode NetworkNode : nodes) {
            if (minimum == null) {
                minimum = NetworkNode;
            } else {
                if (getShortestDistance(NetworkNode) < getShortestDistance(minimum)) {
                    minimum = NetworkNode;
                }
            }
        }
        return minimum;
    }

    private boolean isSettled(NetworkNode node) {
        return settledNodes.contains(node);
    }

    private int getShortestDistance(NetworkNode destination) {
        Integer d = distance.get(destination);
        if (d == null) {
            return Integer.MAX_VALUE;
        } else {
            return d;
        }
    }

}
