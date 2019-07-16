/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * This modification C 2019 R3 Ltd under Apache License 2
 */
package net.corda.utils.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.example.socksproxy.SocksServerConnectHandler;
import io.netty.example.socksproxy.SocksServerUtils;
import io.netty.handler.codec.socks.SocksAuthRequestDecoder;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.*;

@ChannelHandler.Sharable
public final class AuthenticatedSocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final String userName;
    private final String password;
    private Boolean authenticated = false;

    public AuthenticatedSocksServerHandler(String userName, String password) {

        this.userName = userName;
        this.password = password;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
        switch (socksRequest.version()) {
            case SOCKS4a:
                // we don't support 4
                ctx.close();
                break;
            case SOCKS5:
                if (socksRequest instanceof Socks5InitialRequest) {
                    ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    if (((Socks5PasswordAuthRequest) socksRequest).password().equals(password) && ((Socks5PasswordAuthRequest) socksRequest).username().equals(userName)) {
                        authenticated = true;
                        ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                    } else {
                        ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE));
                    }
                } else if (socksRequest instanceof Socks5CommandRequest) {
                    Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                    if (authenticated && socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        ctx.pipeline().addLast(new SocksServerConnectHandler());
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
                break;
            case UNKNOWN:
                ctx.close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        throwable.printStackTrace();
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
