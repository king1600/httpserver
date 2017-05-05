import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;
import java.nio.ByteBuffer;

public class ClientSocket {
  private boolean open = true;
  private  SelectionKey event;
  private ByteBuffer dataBuffer;
  private  SocketChannel channel;
  private Runnable writeCallback;
  private Queue<byte[]> writeQueue;
  private Consumer<byte[]> readCallback;
  private static final int READ_BUFFER = 4*1024;
  private static final int WRITE_BUFFER = 32*1024;

  public ClientSocket(final SocketChannel chan, final SelectionKey ev) {
    event = ev;
    channel = chan;
    writeQueue = new LinkedList<byte[]>();
  }

  public void internalWrite() {
    try {
      while (!writeQueue.isEmpty()) {
        dataBuffer = ByteBuffer.wrap(writeQueue.remove());
        int written = channel.write(dataBuffer);
        while (written > 0 && dataBuffer.hasRemaining())
          written = channel.write(dataBuffer);
      }
      removeFlag(SelectionKey.OP_WRITE);
      onWrite();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void internalRead() {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      int read = 0;
      dataBuffer = ByteBuffer.allocate(READ_BUFFER);
      while ((read = channel.read(dataBuffer)) > 0) {
        stream.write(dataBuffer.array());
        dataBuffer.clear();
      }
      if (read == -1) close();
      else onRead(stream.toByteArray());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void write(byte[] data) {
    if (data.length > WRITE_BUFFER) {
      byte[] buffer = new byte[WRITE_BUFFER];
      int i, b = 0;
      for (i = 0; i < data.length; i++) {
        if (b > buffer.length) {
          write(buffer);
          buffer = new byte[WRITE_BUFFER];
          b = 0;
        }
        buffer[b] = data[i]; 
        b++;
      }
    } else {
      writeQueue.add(data);
      addFlag(SelectionKey.OP_WRITE);
    }
  }

  public void addFlag(int flag) {
    event.interestOps(event.interestOps() | flag);
  }

  public void removeFlag(int flag) {
    event.interestOps(event.interestOps() & ~flag);
  }

  public boolean isOpen() {
    return open;
  }

  public SelectionKey getEvent() {
    return event;
  }

  public SocketChannel getChannel() {
    return channel;
  }

  public void setRead(Consumer<byte[]> cb) {
    readCallback = cb;
  }

  public void setWrite(Runnable cb) {
    writeCallback = cb;
  }

  public void setEvent(SelectionKey event) {
    this.event = event;
  }

  public void close() {
    try {
      event.cancel();
      channel.close();
    } catch (IOException e) {
    }
    open = false;
  }

  public void onWrite() {
    if (writeCallback != null)
      writeCallback.run();
  }

  public void onRead(byte[] data) {
    if (readCallback != null)
      readCallback.accept(data);
  }
}