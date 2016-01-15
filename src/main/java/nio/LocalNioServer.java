package nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 
 * @author liuyq
 *
 */
public class LocalNioServer {

	private static final Queue<byte[]> queue = new ConcurrentLinkedQueue<>();

	/**
	 * bindSystemIn
	 */
	private static void bindSystemIn() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				ReadableByteChannel source = Channels.newChannel(System.in);
				ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
				try {
					while (source.read(buffer) != -1) {
						// buffer.flip();
						byte[] bs = new byte[buffer.position()];
						buffer.get(bs);

						queue.add(bs);
						buffer.clear();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * main
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		bindSystemIn();

		try (ServerSocketChannel server = ServerSocketChannel.open()) {

			server.configureBlocking(false);
			InetSocketAddress isa = new InetSocketAddress(InetAddress.getLocalHost(), 1234);
			server.socket().bind(isa);

			Selector selector = Selector.open();
			server.register(selector, SelectionKey.OP_ACCEPT);

			System.out.println("Ready to select!!");

			while (true && !Thread.currentThread().isInterrupted()) {

				try {
					selector.select(); // select
					Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
					while (keys.hasNext()) {
						final SelectionKey key = (SelectionKey) keys.next();
						keys.remove();

						if (!key.isValid()) {
							System.err.println("key: " + key + " is not valid..");
							continue;
						}

						if (key.isAcceptable()) {
							ServerSocketChannel c = (ServerSocketChannel) key.channel();
							SocketChannel sc = c.accept();
							sc.configureBlocking(false);
							sc.register(selector, SelectionKey.OP_READ);
							System.out.println("Accept!!");
						} else if (key.isReadable()) {
							SocketChannel sc = (SocketChannel) key.channel();
							ByteBuffer buffer = ByteBuffer.allocate(16384);
							sc.read(buffer);
							System.out.println(buffer.toString());
							key.interestOps(SelectionKey.OP_WRITE);
						} else if (key.isWritable()) {
							while (!queue.isEmpty()) {
								ByteBuffer buf = ByteBuffer.wrap(queue.poll());
								if (buf != null) {
									SocketChannel sc = (SocketChannel) key.channel();
									sc.write(buf);
									if (buf.remaining() > 0) {
										break;
									}
								}
							}
							if (queue.isEmpty()) {
								key.interestOps(SelectionKey.OP_READ);
							}
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		} catch (Exception e) {

		} finally {

		}

	}

}
