package org.wowtools.hppt.run.ss.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsUtil;

/**
 * @author liuyu
 * @date 2024/2/7
 */
@Slf4j
public class WebsocketServerSessionService {
    public WebsocketServerSessionService(SsConfig ssConfig) throws Exception {
        log.info("*********");
        LoginClientService loginClientService = new LoginClientService(ssConfig.clientIds);
        ServerSessionManager serverSessionManager = SsUtil.createServerSessionManagerBuilder(ssConfig).build();

        ServerBootstrap serverBootstrap;

        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                                  @Override
                                  protected void initChannel(NioSocketChannel ch) throws Exception {
                                      ChannelPipeline pipeline = ch.pipeline();
                                      pipeline.addLast(new HttpServerCodec())
                                              .addLast(new ChunkedWriteHandler())
                                              .addLast(new HttpObjectAggregator(1024 * 1024 * 10))
                                              .addLast(new WebSocketServerProtocolHandler("/s", null, false, 1024 * 1024 * 50, false, true, 10000L))
                                              .addLast(new WebSocketServerHandler(ssConfig, serverSessionManager, loginClientService))
                                      ;
                                  }
                              }
                );

        serverBootstrap.bind(ssConfig.port).sync();
    }
}
