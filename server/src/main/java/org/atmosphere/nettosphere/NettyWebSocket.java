/*
 * Copyright 2015 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.nettosphere;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.util.IOUtils;
import org.atmosphere.websocket.WebSocket;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atmosphere.nettosphere.util.Utils.REMOTELY_CLOSED;

public class NettyWebSocket extends WebSocket {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocket.class);
    private final Channel channel;
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);
    private int bufferBinarySize = Integer.MAX_VALUE;
    private int bufferStringSize = Integer.MAX_VALUE;
    private boolean binaryWrite = false;
    private final boolean noInternalAlloc;
    private final String idString;

    public NettyWebSocket(Channel channel, AtmosphereConfig config, boolean noInternalAlloc) {
        super(config);
        this.channel = channel;

        String s = config.getInitParameter(ApplicationConfig.WEBSOCKET_MAXBINARYSIZE);
        if (s != null) {
            bufferBinarySize = Integer.valueOf(s);
        }

        s = config.getInitParameter(ApplicationConfig.WEBSOCKET_MAXTEXTSIZE);
        if (s != null) {
            bufferStringSize = Integer.valueOf(s);
        }
        this.noInternalAlloc = noInternalAlloc;
        idString = String.valueOf(channel.getId());
    }

    public WebSocket resource(AtmosphereResource r) {
        super.resource(r);
        if (r != null && r.getRequest() != null) {
            try {
                binaryWrite = IOUtils.isBodyBinary(r.getRequest());
            } catch (Exception ex) {
                logger.trace("", ex);
                // Don't fail for any reason.
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (!channel.isOpen()) throw REMOTELY_CLOSED;
        logger.trace("WebSocket.write()");

        if (binaryWrite) {
            channel.write(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(data.getBytes("UTF-8"))));
        } else {
            channel.write(new TextWebSocketFrame(data));
        }
        lastWrite = System.currentTimeMillis();
        return this;
    }

    public WebSocket write(byte[] data) throws IOException {
        _write(data, 0, data.length);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocket write(byte[] data, int offset, int length) throws IOException {
        _write(data, offset, length);
        return this;
    }

    void _write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);
        if (!channel.isOpen()) throw REMOTELY_CLOSED;

        if (binaryWrite) {
            channel.write(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(data, offset, length)));
        } else {
            channel.write(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(data, offset, length)));
        }
        lastWrite = System.currentTimeMillis();
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        AtmosphereResourceImpl impl = AtmosphereResourceImpl.class.cast(resource());
        if (impl != null) {
            channel.write(new CloseWebSocketFrame()).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Send a WebSocket Ping
     *
     * @param payload the bytes to send
     * @return this
     */
    public WebSocket sendPing(byte[] payload) {
        channel.write(new PingWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)));
        return this;
    }

    /**
     * Send a WebSocket Pong
     *
     * @param payload the bytes to send
     * @return this
     */
    public WebSocket sendPong(byte[] payload) {
        channel.write(new PongWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)));
        return this;
    }

    /**
     * Return the underlying {@link Channel#getId()}
     *
     * @return underlying {@link Channel#getId()}
     */
    public int channelUuid() {
        return channel.getId();
    }
}
