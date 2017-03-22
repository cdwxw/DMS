package cn.wxw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 服务端应用程序
 * 
 * 多线程不操作流写文件，而把log数据offer队列中，由专门的一条线程poll队列中数据写文件，队列中没有数据时该线程阻塞
 * 
 * 双缓冲队列，offer时可以poll，poll时可以offer
 * 
 * @author wxw
 */
public class Server {
	/**
	 * 运行在服务端的Socket
	 */
	private ServerSocket server;
	/**
	 * 线程池，用于管理客户端连接的交互线程
	 */
	private ExecutorService threadPool;
	/**
	 * 保存所有客户端发过来的配对日志文件
	 */
	private File serverLogFile;
	/**
	 * 创建一个双缓冲队列，用于存储配对日志
	 */
	private BlockingQueue<String> msgQueue;

	/**
	 * 构造方法，用于初始化服务端
	 */
	public Server() throws IOException {
		try {
			System.out.println("初始化服务端");
			// 初始化Socket
			// 创建ServerSocket时需要指定服务端口
			server = new ServerSocket(8088);
			// 初始化线程池
			threadPool = Executors.newFixedThreadPool(10);
			// 初始化保存日志文件
			serverLogFile = new File("server-log.txt");
			// 初始化双缓冲队列
			msgQueue = new LinkedBlockingQueue<String>();
			System.out.println("服务端初始化完毕");
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * 服务端开始工作的方法
	 */
	public void start() {
		try {
			while (true) {
				/*
				 * 将写日志文件的线程启动起来
				 */
				WriteLogThread thread = new WriteLogThread();
				thread.start();

				/*
				 * ServerSocket的accept()方法 用于监听8088端口，等待客户端的连接 该方法是一个阻塞方法，直到一个客户端连接， 否则该方法一直阻塞。若一个客户端连接了， 会返回该客户端的Socket
				 */
				System.out.println("等待客户端连接...");
				Socket socket = server.accept();

				/*
				 * 当一个客户端连接后，启动一个线程ClientHandler， 将该客户端的Socket传入，使得该线程处理与客户端的交互。 这样，我们能再次进入循环，接收下一个客户端的连接了。
				 */
				Runnable clientHandler = new ClientHandler(socket);
				// Thread t = new Thread(handler);
				// t.start();
				/*
				 * 使用线程池分配空闲线程来处理当前连接的客户端
				 */
				threadPool.execute(clientHandler);
			}
		} catch (Exception e) {
			System.out.println("服务端启动异常！");
		}
	}

	/**
	 * 程序入口main
	 */
	public static void main(String[] args) {
		Server server;
		try {
			server = new Server();
			server.start();
		} catch (Exception e) {
			System.out.println("服务端初始化失败");
		}
	}

	/**
	 * 服务端中的一个线程，用于与某个客户端交互 使用线程的目的是使得服务端可以处理多客户端了
	 */
	class ClientHandler implements Runnable {
		/**
		 * 当前线程处理的客户端的socket
		 */
		private Socket socket;

		/**
		 * 根据给定的客户端的Socket创建线程体
		 */
		public ClientHandler(Socket socket) {
			this.socket = socket;
		}

		/**
		 * 该线程会将当前Socket中的输入流获取用来循环读取客户端发过来的消息
		 */
		public void run() {
			// 定义在try语句外的目的是：finally中也可以引用到
			PrintWriter pw = null;
			try {
				pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "utf8"), true);
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf8"));

				/*
				 * 循环读取客户端发送过来的每一组配对日志字符串，每读取到一组，就存入消息队列，等待被写入文件
				 */
				String msg;
				while ((msg = br.readLine()) != null) {
					/*
					 * 若读取到客户端发送的内容是“over”，表示客户端发送日志完毕，应当停止循环了
					 */
					if ("over".equals(msg)) {
						break;
					}
					msgQueue.offer(msg);
				}
				/*
				 * 当推出循环，说明所有客户端发送的日志均接收成功并存入了消息队列
				 * 
				 * 那么我们回复客户端“OK”
				 */
				pw.println("OK");// 自动行刷新
			} catch (Exception e) {
				/*
				 * 在Windows中的客户端，报错通常是因为客户端断开连接
				 */
				pw.println("ERROR");
			} finally {
				/*
				 * 无论是Linux还是Windows用户，当与服务端断开连接后， 我们都应该在服务端与客户端断开连接
				 */
				try {
					socket.close();
				} catch (IOException e) {
					System.out.println("断开连接不成功！");
				}
			}
		}
	}

	/**
	 * 该线程在Server中仅有一个实例
	 * 
	 * 作用：循环从消息队列中取出一个配对日志，并写入server-log.txt文件中，当队列中没有日志后，就阻塞，等待客户端发送新的日志过来
	 */
	class WriteLogThread extends Thread {
		public void run() {
			PrintWriter pw = null;
			try {
				pw = new PrintWriter(new FileOutputStream(serverLogFile, true));
				while (true) {
					if (msgQueue.size() > 0) {
						String log = msgQueue.poll();
						pw.println(log);
					} else {
						pw.flush();
						Thread.sleep(5000);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				pw.close();
			}
		}
	}
}
