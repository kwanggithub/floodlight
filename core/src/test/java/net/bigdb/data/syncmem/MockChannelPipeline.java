package net.bigdb.data.syncmem;

import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

public class MockChannelPipeline implements ChannelPipeline {

    @Override
    public void addFirst(String name, ChannelHandler handler) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addLast(String name, ChannelHandler handler) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addBefore(String baseName, String name, ChannelHandler handler) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addAfter(String baseName, String name, ChannelHandler handler) {
        // TODO Auto-generated method stub

    }

    @Override
    public void remove(ChannelHandler handler) {
        // TODO Auto-generated method stub

    }

    @Override
    public ChannelHandler remove(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandler removeFirst() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandler removeLast() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void
            replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        // TODO Auto-generated method stub

    }

    @Override
    public ChannelHandler
            replace(String oldName, String newName, ChannelHandler newHandler) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
            ChannelHandler newHandler) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandler getFirst() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandler getLast() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandler get(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandlerContext getContext(ChannelHandler handler) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandlerContext getContext(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void sendUpstream(ChannelEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendDownstream(ChannelEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public Channel getChannel() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelSink getSink() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void attach(Channel channel, ChannelSink sink) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isAttached() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<String> getNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ChannelFuture execute(Runnable arg0) {
        // TODO Auto-generated method stub
        return null;
    }

}
