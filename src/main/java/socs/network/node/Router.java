package socs.network.node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TimerTask;
import java.util.Timer;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

public class Router {
  //pass by for heartbeat giving weird error due to threads??
  private Router router=this;
  protected LinkStateDatabase lsd;

  private int timeout=20000;//10 secs timeout setting between heartbeats
  //receiver tasks tracking
  TimerTask[] receiverTimerTasks= new TimerTask[4];
  //receiver timers
  Timer[] receiverTimers = new Timer[4];
  //sender tasks tracking
  TimerTask[] senderTimerTasks = new TimerTask[4];
  //sender timers 
  Timer[] senderTimers = new Timer[4];

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];
  private ServerSocket serverSocket;

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processPortNumber = (short)Integer.parseInt(config.getString("socs.network.router.port"));
    
    lsd = new LinkStateDatabase(rd);
    try{
      serverSocket = new ServerSocket(rd.processPortNumber);
      rd.processIPAddress = serverSocket.getLocalSocketAddress().toString();

      new Thread(new Runnable(){
        public void run(){
        try{
          while(true){
            Socket clientSocketHandle = serverSocket.accept();
            boolean initHello = false;
            ObjectOutputStream output = new ObjectOutputStream(clientSocketHandle.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocketHandle.getInputStream());

            //Read packet from new link.
            SOSPFPacket received;
            received = (SOSPFPacket)input.readObject();
            //verification that this attachement already exists create if it does not
            int portNumber=attachExists(received.srcIP,received.srcProcessPort);
            if(portNumber == -1){
              portNumber=attach(received.srcProcessIP,received.srcProcessPort,received.srcIP,(short) 1);
            }
            //heartbeat config for this connection
            heartBeatConfig(portNumber);

            //retrieve the attachment status set appropriate status if it doesnt
            RouterDescription crd = ports[portNumber].router2;
            if(received.sospfType == 0){
              if(received.ping) continue; //just a ping so return
              if(crd.status == null){
                crd.status = RouterStatus.INIT;
              }
              else{
                crd.status = RouterStatus.TWO_WAY;
              }
              crd.simulatedIPAddress = received.srcIP;
              System.out.println("set " + crd.simulatedIPAddress + " state to " + crd.status + ";");
          
              //Send response packet
              SOSPFPacket outPacket = new SOSPFPacket();
              outPacket.dstIP = crd.simulatedIPAddress;
              outPacket.sospfType = 0;
              outPacket.srcIP = rd.simulatedIPAddress;
              outPacket.srcProcessPort = rd.processPortNumber;
              output.writeObject(outPacket);
              try{
              received = (SOSPFPacket)input.readObject();
              }catch(Exception e){
                e.printStackTrace();
              }
              //read response
              if(received.sospfType ==0){
                crd.status = RouterStatus.TWO_WAY;
                System.out.println("received HELLO from " + received.srcIP + ";");
                System.out.println("set " + crd.simulatedIPAddress + " state to " + crd.status + ";");
              }
              
              /**
              * Established TWO_WAY, now need to create LinkDescription for this link, and add
              * this link to LSA of server. Then broadcast to LSA to all neighbors.
              */
              LinkDescription ld = new LinkDescription();
              ld.linkID = crd.simulatedIPAddress;
              ld.portNum = portNumber;
              ld.tosMetrics = ports[portNumber].weight;
              lsd.addLink(rd.simulatedIPAddress, ld);
              lsaBroadcast();
            }
              //if LSA update.
            else if(received.sospfType == 1){
              System.out.println("Received lsa update from " + received.srcIP);
              serverLSAUpdate(received, (short)portNumber);
            }
            System.out.print(">>");
          }
        }catch(IOException e){
          System.out.println(e);
        }catch(ClassNotFoundException e){
          System.out.println(e);
        }
        }
      }).start();
    }catch(IOException e){
      System.out.println(e);
    }
  }
  //heart beat sender and receiver config for cleaner code
  private void heartBeatConfig(int portNumber){

    //cancel reception timer if the port is not there 
    if(receiverTimerTasks[portNumber] !=null){
      receiverTimers[portNumber].cancel();
      receiverTimers[portNumber] = new Timer(true);
      receiverTimerTasks[portNumber] = new HeartbeatTask((short)portNumber,1,router);
      receiverTimers[portNumber].schedule(receiverTimerTasks[portNumber],timeout);
    }else{
      receiverTimers[portNumber] = new Timer(true);
      //initiate a new task if this 1 was not already configured
      receiverTimerTasks[portNumber] = new HeartbeatTask((short)portNumber,1,router);
      receiverTimers[portNumber].schedule(receiverTimerTasks[portNumber],timeout);
    }


    //create sender if necessary
    if(senderTimerTasks[portNumber] == null){
      //instantiate a timer for this sender
      senderTimers[portNumber] = new Timer(true);
      //initiate a new task if this 1 was not already configured
      senderTimerTasks[portNumber] = new HeartbeatTask((short)portNumber, 2, router);
      //start the sender task at half the timeout
      senderTimers[portNumber].schedule(senderTimerTasks[portNumber],0,timeout/2);
    }
  }
  //kills heart beat operation for specific port
  private void heartBeatCancel(short portNumber){
    receiverTimers[portNumber].cancel();
    receiverTimers[portNumber]=null;
    receiverTimerTasks[portNumber].cancel();
    receiverTimerTasks[portNumber]=null;

    senderTimers[portNumber].cancel();
    senderTimers[portNumber]=null;
    senderTimerTasks[portNumber].cancel();
    senderTimerTasks[portNumber]=null;
  }
  /**
   * Handles server lsa updates.
   * Receives lsa Packet, need to determine if any updates took place.
   * <p/>
   * Checks if linkStateID is in this routers LSD, if not then return. If it does,
   * need to compare sequence number to determine if an update took place.
   * @param lsPacket Link state Packet received from client.
   */
  private void serverLSAUpdate(SOSPFPacket lsPacket,short portNum){
    String srcIP = lsPacket.srcIP;
    LSA currLSA;
    boolean updated = false;
    if(lsPacket.lsaArray == null){
      System.out.println("vector is null");
      return;
    }
    for(LSA lsa : lsPacket.lsaArray){
      //Check if link is contained in DB, then compare sequence number. 
      if(lsd.containsLSA(lsa.linkStateID)){
        currLSA = lsd.getLSA(lsa.linkStateID);
        if(lsa.lsaSeqNumber > currLSA.lsaSeqNumber){//i.e. More recent update.
          lsd.setLSA(lsa, lsa.linkStateID);
          updated = true;
        }
      }
      else{//If we don't have this lsa, we should add it. 
        lsd.setLSA(lsa, lsa.linkStateID);
        updated = true;
      }
    }

    //If update occured, need to broadcast to all neighbors except the one that sent it
    if(updated){
      SOSPFPacket lsp;
      Socket client;
      ObjectOutputStream output;
      for(int i = 0; i < 4; i++){
        if(ports[i] != null && i != portNum && ports[i].router2.status == RouterStatus.TWO_WAY){ //Skip host that sent lsaupdate.
          lsp = new SOSPFPacket();
          lsp.srcProcessIP = ports[i].router1.processIPAddress;
          lsp.srcProcessPort = ports[i].router1.processPortNumber;
          lsp.srcIP = ports[i].router1.simulatedIPAddress;
          lsp.dstIP = ports[i].router2.simulatedIPAddress;
          lsp.routerID = ports[i].router1.simulatedIPAddress;
          lsp.neighborID = lsp.routerID;                    //I don't know why but we set this to ourselves.
          lsp.sospfType = (short)1;                         // we are doing an LSA update.
          lsp.lsaArray = lsd.getLSA();

          //Packet created, no establish link and send packekts.
          try{
            client = new Socket(ports[i].router2.processIPAddress, 
                                ports[i].router2.processPortNumber);
            output = new ObjectOutputStream(client.getOutputStream());
            output.writeObject(lsp);
          }catch(Exception e){
            e.printStackTrace();
          }
        }
      }
    }
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
     // String path = lsd.getShortestPath(destinationIP);
      System.out.println(lsd.getShortestPath(destinationIP));
  }

  public void ping(short portNumber){
    if(ports[portNumber] == null){ //port has been disconnected, so cancel this sender
      heartBeatCancel(portNumber);
      return;
    }

    RouterDescription crd = ports[portNumber].router2;
    SOSPFPacket hello = new SOSPFPacket();
    hello.dstIP = crd.simulatedIPAddress;
    hello.sospfType = 0;
    hello.srcIP = rd.simulatedIPAddress;
    hello.srcProcessPort = rd.processPortNumber;
    hello.ping = true;
    try{
      Socket client = new Socket(ports[portNumber].router2.processIPAddress,
                                ports[portNumber].router2.processPortNumber);
      ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream());
      output.writeObject(hello);
      // System.out.println(router.rd.simulatedIPAddress + "  " + router.rd.processPortNumber + " Sent hello to " + 
      //                    ports[portnumber].router2.simulatedIPAddress + " port:" +
      //                    ports[portnumber].router2.processPortNumber);
    }catch(IOException e){
      e.printStackTrace();
    }
  }

  public void disconnect(short portnumber){
    //calls process detect are we allowed to change methods to public ? if so just change process Disconnect to public
    processDisconnect(portnumber);
  }
  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   * @TODO: remove the sender heartbeat task, the LSA from LSD and set that port to null then broadcast.
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {
    //Remove sender/Receiver heartbeat task.
    heartBeatCancel(portNumber);

    //Remove LSD info.
    lsd.removeLink(ports[portNumber].router2.simulatedIPAddress);

    //Set port to null.
    ports[portNumber] = null;

    //Broadcast to neighbors.
    lsaBroadcast();
    
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,String simulatedIP, short weight) {

      //prevent self attachment
      if(simulatedIP.equals(rd.simulatedIPAddress)){
        System.out.println("Cannot attach router to itself");
        return;
      }
      //attempt attach print correct result
      int tryAttach = attach(processIP,processPort,simulatedIP,weight);
      
      if(tryAttach > 0){
        System.out.println("attachment done: port " + tryAttach);
        return;
      }
      else if(tryAttach == -2){
        System.out.println("Attachment exists");
        return;
      }else{
        System.out.println("no empty ports");
        return;
      }
  }
  /**
   * 
   * create a new attachment called by attach or by server thread
   * 
   * @param processIP   actual processIP 
   * @param processPort actual process port
   * @param simulatedIP the fake ip of the attached process
   * @param weight      link weight
   * 
   * @return -1           if ports full
   *         -2           if attachment exists
   *         portnumber   if attachment sucessful
   * 
   * 
   */
  private int attach(String processIP, short processPort,String simulatedIP, short weight){
    //verify existence of this attachment
    if(attachExists(simulatedIP,processPort) >0) return -2;
    
    //find empty port
    int port=-1;
    for(int i=0;i<4;i++){
      if(ports[i]==null){
        port=i;
      }
    }

    //empty port exists create attachment
    if(port!=-1){
      //create remote router
      RouterDescription rR = new RouterDescription(processIP, processPort, simulatedIP);
      //create a link between local and remote router
      Link link = new Link(rd, rR, weight);
      //save link in this port
      ports[port] = link;

      return port;
    }
    //no empty ports
    else{

      return -1;  
    }
  }
  /**
   * Check if port with specified IP already exists.
   * 
   * @param simulatedIP fake ip assigned to remote
   * @param processPort actual process port of remote router
   * 
   * @return -1 if attachment does not exist
   *         portnumber if attachment exists
   *         
   */
  private int attachExists(String simulatedIP, short processPort){
    //loop ports looking if they match params
    for(int i = 0; i < 4; i++){
      if(ports[i]!=null && ports[i].router2.simulatedIPAddress.equals(simulatedIP) && ports[i].router2.processPortNumber == processPort)
        return i;
    }
    return -1;
  }

  /**
   * broadcast Hello to neighbors
   * Spawn thread to listen. If a packet is received though ObjectInputStream, send an object/packet through OutPutStream
   * Do above on infinite loop.
   */
  private void processStart() {
    Thread sender = new Thread(){
        public void run(){
        for(short i = 0; i< 4; i++){
          if(ports[i] != null){
            try{
              System.out.println();
              //create socket between local and remote
              Socket client = new Socket(ports[i].router2.processIPAddress,
                                         ports[i].router2.processPortNumber);

              System.out.println("Connected to " + ports[i].router2.simulatedIPAddress);

              //create output to remote
              ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream());
              
              //create a packet to send
              SOSPFPacket outPacket = new SOSPFPacket();
              outPacket.dstIP = ports[i].router2.simulatedIPAddress;
              ports[i].router2.status = RouterStatus.INIT;
              outPacket.sospfType = 0;
              outPacket.srcIP = rd.simulatedIPAddress;
              outPacket.srcProcessPort = rd.processPortNumber;
              output.writeObject(outPacket);

              //Now wait for reply.
              ObjectInputStream input = new ObjectInputStream(client.getInputStream());
              heartBeatConfig(i);


              //change connection status if packet type 0 is recieved
              SOSPFPacket inPacket = (SOSPFPacket)input.readObject();
              if(inPacket.sospfType == 0){
                System.out.println("received HELLO from " + inPacket.srcIP);
                ports[i].router2.status = RouterStatus.TWO_WAY;
                System.out.println("set " + ports[i].router2.simulatedIPAddress +" state to " + ports[i].router2.status+";");
              }

              //send again
              output.writeObject(outPacket);

              /**
               * We've set up TWO_WAY, now need to create LinkDescription for this link.
               * After we add this link to this routers LSA.
               * Finally send LSP to all neighbors. 
               */
              LinkDescription ld = new LinkDescription();
              ld.linkID = inPacket.srcIP;
              ld.portNum = i;
              ld.tosMetrics = ports[i].weight;
              lsd.addLink(rd.simulatedIPAddress,ld);

              System.out.print(">>");
            }catch(Exception e){
              System.out.println(e);
            }
          }
        }
        //Once we've established our connections, we send out LSPs
        lsaBroadcast();
      }
    };
    sender.start();

  }

  /**
   * Handles client lsa updates: Sends LSP to all neighbors.
   */
  private void lsaBroadcast(){
    SOSPFPacket lsp;
    Socket client;
    ObjectOutputStream output;
    for(int i = 0; i< 4; i++){
      if(ports[i] != null && ports[i].router2.status == RouterStatus.TWO_WAY){
        lsp = new SOSPFPacket();
        lsp.srcProcessIP = ports[i].router1.processIPAddress;
        lsp.srcProcessPort = ports[i].router1.processPortNumber;
        lsp.srcIP = ports[i].router1.simulatedIPAddress;
        lsp.dstIP = ports[i].router2.simulatedIPAddress;
        lsp.routerID = ports[i].router1.simulatedIPAddress;
        lsp.neighborID = lsp.routerID;                    //I don't know why but we set this to ourselves.
        lsp.sospfType = (short)1;                         // we are doing an LSA update.
        lsp.lsaArray = lsd.getLSA();

        //Packet created, no establish link and send packekts.
        try{
          client = new Socket(ports[i].router2.processIPAddress, 
                              ports[i].router2.processPortNumber);
          output = new ObjectOutputStream(client.getOutputStream());
          output.writeObject(lsp);
        }catch(Exception e){
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {
    int portNumber = attachExists(processIP,processPort);
    if(portNumber == -1){
      portNumber = attach(processIP, processPort, simulatedIP, weight);
    }else{ //If port number != -1, then this link has already been added. 
      if(ports[portNumber].router2.status == RouterStatus.TWO_WAY)
        return; //If TWO_WAY already established, we don't need to send again.
    }
    processStart();
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    for(int i =0; i < 4; i++){
      if(ports[i] != null && ports[i].router2.status == RouterStatus.TWO_WAY){
        System.out.println("(port,IP) address of neighbor (" + i + ","+ports[i].router2.simulatedIPAddress + ")");
      }
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {
    //Cancel timer threads first for good measure.
    for(int i = 0 ; i < 4; i++){
      if(receiverTimers[i] != null)
        receiverTimers[i].cancel();
      if(senderTimers[i] != null)
        senderTimers[i].cancel();
    }
    System.exit(0);
  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
