package org.example;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class AggregatorServer {

    private static final int PORT = 8080;
    // Redis connection string - assumes default local Redis
    private static final String REDIS_URI = "redis://localhost:6379";

    public static void main(String[] args) throws Exception {
        // Initialize Redis Client (Single connection shared)
        RedisClient redisClient = RedisClient.create(REDIS_URI);
        StatefulRedisConnection<String, String> redisConnection = redisClient.connect();
        AggregationService aggregationService = new AggregationService(redisConnection);

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            // Custom Handler for /api/dashboard
                            ch.pipeline().addLast(new DashboardHandler(aggregationService));
                        }
                    });

            System.out.println("Server started on port " + PORT);
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            redisConnection.close();
            redisClient.shutdown();
        }
    }
}