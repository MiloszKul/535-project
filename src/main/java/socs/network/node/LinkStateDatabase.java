package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import java.util.Vector;
import java.util.HashMap;
import java.util.Iterator;
import socs.network.node.NetworkGraph;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  String getShortestPath(String destinationIP) {
    NetworkGraph g= new NetworkGraph(rd.simulatedIPAddress,_store);
    return g.getShortestPath(destinationIP);
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }

  /**
   * Adds link, then incremenets LSA sequence number.
   * @param lsdIP IP address of server router.
   * @param ld Link Description of link from server to client.
   */
  public void addLink(String lsdIP, LinkDescription ld){
    LSA lsa = _store.get(lsdIP);
    lsa.links.add(ld);
    lsa.lsaSeqNumber++;
  }

  /**
   * Removes specified link. 
   * @param lsdIP IP address of router we are removing from LSD.
   */
  public void removeLink(String lsdIP){
    //First remove link from this routers LSA.
    LSA thisLSA = _store.get(rd.simulatedIPAddress);
    Iterator<LinkDescription> iter = thisLSA.links.iterator();
    LinkDescription ld;
    while(iter.hasNext()){
      ld = iter.next();
      if(ld.linkID.equals(lsdIP)){
        iter.remove();
        thisLSA.lsaSeqNumber++;
      }
    }

    //Remove that routers lsa from this routers LSD
    _store.remove(lsdIP);
  }

  /**
   * Returns vecotr of LSA, to be added in LSA update.
   */
  public Vector<LSA> getLSA(){
    Vector<LSA> lsavec = new Vector<LSA>();
    lsavec.addAll(_store.values());
    return lsavec;
  }

  /**
   * Gets LSA.
   * @param id LinkID
   * @return Specified LSA
   */
  public LSA getLSA(String id){
    return _store.get(id);
  }


  /**
   * Checks if lsa is included. 
   * @param id LinkID
   * @return true if lsa is contained in the database.
   */
  public boolean containsLSA(String id){
    return _store.keySet().contains(id);
  }


  public void setLSA(LSA lsa, String id){
    _store.put(id,lsa);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
