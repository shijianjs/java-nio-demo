package com.example.javaniodemo.niotest;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * 【错误写法】
 * <p>
 * https://docs.oracle.com/en/java/javase/17/core/time-query-nio-example.html
 */
public class TimeServer {
    @Test
    public void timeServer() throws Exception {
        final int port = 8013;
        final Charset charset = CharsetUtil.CHARSET_UTF_8;
        final CharsetEncoder encoder = charset.newEncoder();
        final ByteBuffer dbuf = ByteBuffer.allocateDirect(1024);
        // setup
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(port));

        while (true) {
            try (
                    final SocketChannel sc = ssc.accept()
            ) {
                final String now = DateUtil.formatDateTime(new Date());
                System.out.println("now: " + now);
                sc.write(encoder.encode(CharBuffer.wrap(now + "\r\n")));
                System.out.println(sc.socket().getInetAddress() + " : " + now);
            }
        }
    }

    /**
     * https://docs.oracle.com/en/java/javase/17/core/time-query-nio-example.html
     */
    @Test
    public void timeClient() throws Exception {
        final int port = 8013;
        final Charset charset = CharsetUtil.CHARSET_UTF_8;
        final CharsetDecoder decoder = charset.newDecoder();
        final ByteBuffer dbuf = ByteBuffer.allocateDirect(1024);

        try (
                final SocketChannel sc = SocketChannel.open()
        ) {
            final InetSocketAddress isa = new InetSocketAddress(port);
            sc.connect(isa);
            dbuf.clear();
            sc.read(dbuf);
            dbuf.flip();
            final CharBuffer cb = decoder.decode(dbuf);
            System.out.println(isa + " : " + cb);
        }
    }

    /**
     * https://docs.oracle.com/en/java/javase/17/core/non-blocking-time-server-nio-example.html
     */
    @Test
    public void timeServerNoBlocking() throws Exception {
        final int port = 8013;
        final Selector acceptSelector = SelectorProvider.provider().openSelector();
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        final InetSocketAddress isa = new InetSocketAddress(port);
        ssc.socket().bind(isa);
        ssc.register(acceptSelector, SelectionKey.OP_ACCEPT);
        int keysAdded = 0;
        while ((keysAdded = acceptSelector.select()) > 0) {
            System.out.println("while");
            final Set<SelectionKey> readyKeys = acceptSelector.selectedKeys();
            final Iterator<SelectionKey> i = readyKeys.iterator();
            while (i.hasNext()) {
                final SelectionKey sk = i.next();
                i.remove();
                final ServerSocketChannel nextReady = (ServerSocketChannel) sk.channel();
                final Socket s = nextReady.accept().socket();
                final PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                final String now = DateUtil.formatDateTime(new Date());
                out.println(now);
                out.close();
            }

        }
    }


    @Test
    public void selectKeyTest() {
        System.out.println("SelectionKey.OP_READ: " + SelectionKey.OP_READ);
        System.out.println("SelectionKey.OP_WRITE: " + SelectionKey.OP_WRITE);
        System.out.println("SelectionKey.OP_CONNECT: " + SelectionKey.OP_CONNECT);
        System.out.println("SelectionKey.OP_ACCEPT: " + SelectionKey.OP_ACCEPT);
    }
}
