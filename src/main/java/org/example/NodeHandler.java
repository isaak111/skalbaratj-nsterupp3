package org.example;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.example.balancer.LoadBalancer;
import org.example.client.NodeStartupInitializer;
import org.example.proxy.ReverseProxyServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeHandler {
    private final ReverseProxyServer server;
    private final LoadBalancer balancer;

    private final ReentrantReadWriteLock lock;
    private boolean alive = true;



    private int portCounter;
    private final List<Node> activeNodes;
    private final List<Node> closingQueue;
    private final List<Node> startingQueue;
    private final int minimumAmountOfNodes;

    public NodeHandler(ReverseProxyServer server, LoadBalancer balancer) {
        this.server = server;
        this.balancer = balancer;
        this.activeNodes = new ArrayList<>();
        this.startingQueue = new ArrayList<>();
        this.closingQueue = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.minimumAmountOfNodes = 4;
        this.portCounter = 8080;

        this.start(minimumAmountOfNodes);
        this.startThread();
    }

    public  void addStartedNode(Node node){
    try {
        lock.writeLock().lock();

    var input = node.getProcess().getInputStream();
    var bytes = new byte [10000];
    var read = input.read(bytes);
    System.out.println(new String(bytes, 0, read));

    startingQueue.remove(node);
    activeNodes.add(node);
    } catch (IOException e) {
        e.printStackTrace();
    } finally {
        lock.writeLock().unlock();
    }
 }
 public Node next() {
        try {
            lock.readLock().lock();
            return balancer.next(activeNodes);
        } finally {
            lock.readLock().unlock();
        }
 }

 private void start(int amount) {
     System.out.println("Starting " + amount +" nodes.");
     for (int i = 0; i < amount; i++) {
         var node = new Node(portCounter++);
         startingQueue.add(node);
         try{
             node.start();
         }catch (IOException e) {
                e.printStackTrace();
         }
     }
 }

 private void checkup() throws IOException{
        var iterator = startingQueue.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();

            try {
                var bootstrap = new Bootstrap();
                bootstrap
                        .group(server.getWorkerGroup())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                        .handler(new NodeStartupInitializer(node, this))
                        .connect("localhost", node.getPort())
                        .sync();
            } catch (Exception ignored) {

            }
        }

        iterator = closingQueue.iterator();
        while (iterator.hasNext()) {
            var node = iterator.next();

            if(!node.getConnections().isEmpty()) continue;

            node.stop();
            iterator.remove();
        }


        var requests = 0.0;
        for (var node : activeNodes) {
            requests += node.getRequests();
            node.resetRequests();
        }

        requests = requests / (double) activeNodes.size();
     System.out.println("R/s: " + requests);

     if (requests < 1.0 && activeNodes.size() > minimumAmountOfNodes){
         closeOne();
     } else if(requests >= 7.0) {
         int amount = (int) (requests / 7.0);
         amount -= startingQueue.size();

         if(amount > 0)
             start(amount);
     }
 }

 private void closeOne() {
     System.out.println("Closing a node");

     var i = activeNodes.size() -1;
     var node = activeNodes.get(i);
     activeNodes.remove(i);

     closingQueue.add(node);
 }
 public void closeAll() {
        try {
            lock.writeLock().lock();
            alive = false;
            activeNodes.forEach(Node::stop);
            closingQueue.forEach(Node::stop);
            startingQueue.forEach(Node::stop);
        }finally {
            lock.writeLock().unlock();
        }
 }

 private void startThread() {
        var thread = new Thread(this::runThread);
        thread.start();
 }
 private void runThread() {
        try{
            lock.writeLock().lock();
            if(!alive) {
                return;
            }
            checkup();
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        runThread();
 }
}