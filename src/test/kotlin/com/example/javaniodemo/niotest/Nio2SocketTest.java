package com.example.javaniodemo.niotest;

import cn.hutool.core.io.BufferUtil;
import lombok.With;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

/**
 * 【错误写法】
 *
 * https://www.baeldung.com/java-nio2-async-socket-channel
 */
public class Nio2SocketTest {

    @Test
    public void runServerFutureGetBlock() throws Exception {
        final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        server.bind(new InetSocketAddress(4555));
        final Future<AsynchronousSocketChannel> acceptFuture = server.accept();
        // future直接阻塞get了，待优化
        final AsynchronousSocketChannel clientChannel = acceptFuture.get();
        if ((clientChannel != null) && (clientChannel.isOpen())) {
            while (true) {
                final ByteBuffer buffer = ByteBuffer.allocate(32);
                final Future<Integer> readResult = clientChannel.read(buffer);
                readResult.get();
                buffer.flip();
                final Future<Integer> writeResult = clientChannel.write(buffer);
                writeResult.get();
                buffer.clear();
            }
        }
        clientChannel.close();
        server.close();
    }

    @Test
    public void serverWithCompletionHandler() throws Exception {
        final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        server.bind(new InetSocketAddress(4555));
        while (true) {
            server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {
                @Override
                public void completed(AsynchronousSocketChannel result, Object attachment) {
                    if (server.isOpen()) {
                        // 循环接收请求
                        server.accept(null, this);
                    }
                    final AsynchronousSocketChannel clientChannel = result;
                    if ((clientChannel != null) && (clientChannel.isOpen())) {
                        final ByteBuffer buffer = ByteBuffer.allocate(32);
                        final Future<Integer> readResult = clientChannel.read(buffer);
                        class ReadInfo {
                            String action;
                            ByteBuffer buffer;
                        }
                        final ReadInfo readInfo = new ReadInfo();
                        readInfo.action = "read";
                        readInfo.buffer = buffer;
                        clientChannel.read(buffer, readInfo, new CompletionHandler<Integer, ReadInfo>() {
                            @Override
                            public void completed(Integer result, ReadInfo attachment) {
                                final String action = attachment.action;
                                if ("read".equals(action)){
                                    attachment.buffer.flip();
                                    attachment.action="write";
                                    clientChannel.write(attachment.buffer,attachment,this);
                                    attachment.buffer.clear();
                                }else if("write".equals(attachment.action)){
                                    final ByteBuffer buffer1 = ByteBuffer.allocate(32);
                                    attachment.buffer=buffer1;
                                    attachment.action="read";
                                    clientChannel.read(buffer1,attachment,this);
                                }
                            }

                            @Override
                            public void failed(Throwable exc, ReadInfo attachment) {

                            }
                        });
                    }
                }

                @Override
                public void failed(Throwable exc, Object attachment) {

                }
            });
            System.in.read();
        }
    }

    /**
     * put
     * 写模式下，往buffer里写一个字节，并把postion移动一位。写模式下，一般limit与capacity相等。
     * flip
     * 写完数据，需要开始读的时候，将postion复位到0，并将limit设为当前postion。
     * get
     * 从buffer里读一个字节，并把postion移动一位。上限是limit，即写入数据的最后位置。
     * clear
     * 将position置为0，并不清除buffer内容。
     * mark & reset
     * mark相关的方法主要是mark()(标记)和reset()(回到标记).
     *
     * @throws Exception
     */
    @Test
    public void clientFutureBlock() throws Exception {
        final AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
        final Future<Void> clientFuture = client.connect(new InetSocketAddress("127.0.0.1", 4555));
        clientFuture.get();

        final ByteBuffer buffer = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
        final Future<Integer> writeFuture = client.write(buffer);
        writeFuture.get();
        buffer.flip();

        final ByteBuffer input = ByteBuffer.allocate(64);
        final Future<Integer> readFuture = client.read(input);
        readFuture.get();
        input.flip();
        final byte[] array = BufferUtil.readBytes(input);

        System.out.println(array.length);
        System.out.println(new String(array));
    }
}
