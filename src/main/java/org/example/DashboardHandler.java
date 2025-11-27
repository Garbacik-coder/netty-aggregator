package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.concurrent.CompletableFuture;

public class DashboardHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String URI_PATH = "/api/dashboard";
    private final AggregationService aggregationService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardHandler(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!URI_PATH.equals(req.uri())) {
            sendResponse(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
            return;
        }

        if (!req.method().equals(HttpMethod.GET)) {
            sendResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
            return;
        }

        aggregationService.getAggregatedData()
                .thenAccept(responseObject -> {
                    try {
                        String json = mapper.writeValueAsString(responseObject);
                        sendResponse(ctx, HttpResponseStatus.OK, json);
                    } catch (Exception e) {
                        sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\": \"Serialization failed\"}");
                    }
                });
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}