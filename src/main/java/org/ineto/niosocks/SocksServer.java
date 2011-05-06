package org.ineto.niosocks;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

public class SocksServer {

  private ChannelGroup clientsGroup;
  
  private Channel listenChannel;
  private OrderedMemoryAwareThreadPoolExecutor pipelineExecutor;
  private ChannelFactory factory;
  private ClientSocketChannelFactory clientFactory;
  
  public SocksServer(Properties props) {
    
    int port = Integer.parseInt(props.getProperty("socks.port"));

    int threads = 1;
    if (props.getProperty("server.threads") == null) {
      threads = Runtime.getRuntime().availableProcessors() * 2 + 1;
    } else {
      threads = Integer.parseInt(props.getProperty("socks.threads"));
    }

    clientFactory =  new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), threads);
    ServerBootstrap bootstrap = new ServerBootstrap(factory);

    // 200 threads max, Memory limitation: 1MB by channel, 1GB global, 100 ms of timeout
    pipelineExecutor = new OrderedMemoryAwareThreadPoolExecutor(200, 1048576, 1073741824, 100, TimeUnit.MILLISECONDS, Executors.defaultThreadFactory());

    bootstrap.setPipelineFactory(new SocksPipelineFactory(clientsGroup, pipelineExecutor, clientFactory));
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    bootstrap.setOption("child.reuseAddress", true);
    bootstrap.setOption("child.connectTimeoutMillis", 100);
    bootstrap.setOption("readWriteFair", true);

    listenChannel = bootstrap.bind(new InetSocketAddress(port));

  }
 
  public void join() {
    listenChannel.getCloseFuture().awaitUninterruptibly();
  }
  
  public void shutdown() {
    System.out.println("Shutdown server");
    listenChannel.close().awaitUninterruptibly();
    clientsGroup.close().awaitUninterruptibly();
    pipelineExecutor.shutdownNow();
    factory.releaseExternalResources();
    clientFactory.releaseExternalResources();
  }
  
}