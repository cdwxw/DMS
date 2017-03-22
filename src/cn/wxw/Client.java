package cn.wxw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cn.wxw.bo.LogData;
import cn.wxw.bo.LogRec;
import cn.wxw.util.IOUtil;

/**
 * 客户端应用程序 运行在UNIX服务器端(DMS采集端)
 * 
 * 作用是定期读取系统日志文件wtmpx文件 收集每个用户的登入登出日志，将匹配成对的日志信息发送至DMS服务器端。
 * 
 * @author wxw
 */
public class Client {
	/**
	 * unix系统日志文件 wtmpx文件
	 */
	private File logFile;

	/**
	 * 保存每次解析后的日志文件
	 */
	private File textLogFile;

	/**
	 * 保存每次解析日志文件后的位置（书签）的文件
	 */
	private File lastPositionFile;

	/**
	 * 每次从wtmpx文件中解析日志的条数
	 */
	private int batch;

	/**
	 * 保存每次配对完毕后的所有配对日志的文件
	 */
	private File logRecFile;

	/**
	 * 保存每次配对后，没有配对成功的登入日志的文件
	 */
	private File loginFile;

	/**
	 * 构造方法中初始化
	 */
	public Client() {
		try {
			this.batch = 100;
			logFile = new File("wtmpx");
			lastPositionFile = new File("last-position.txt");
			textLogFile = new File("log.txt");
			logRecFile = new File("logrec.txt");
			loginFile = new File("login.txt");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * 该方法为第一大步的第2小步的逻辑 用于检查wtmpx文件是否还有新数据可读
	 * 
	 * @return -1：没有数据可读了 其他数字：继续读取的位置
	 */
	public long hasLogs() {
		try {
			// 默认从文件开始读取
			long lastposition = 0;
			/*
			 * 这里有两种情况：
			 * 
			 * 1.没有找到last-posiontion.txt文件，说明从来没有读过wtmpx文件
			 * 
			 * 2.有last-posiontion.txt文件，那么就从该文件记录的位置开始读取
			 */
			if (lastPositionFile.exists()) {
				lastposition = IOUtil.readLong(lastPositionFile);
			}
			/*
			 * 必要判断，wtmpx文件的总大小减去这次准备开始读取的位置，应当大于一条日志所占用的字节量(372)
			 */
			if (logFile.length() - lastposition < LogData.LOG_LENGTH) {
				lastposition = -1;
			}
			return lastposition;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * 第一大步： 解析日志
	 * 
	 * 从wtmpx文件中一次读取batch条日志，并解析为batch行字符串，每行字符串表示一条日志，然后写入log.txt文件中。
	 * 
	 * @return true：解析成功 false：解析失败
	 */
	public boolean readNextLogs() {
		/*
		 * 解析步骤：
		 * 
		 * 1.首先判断wtmpx文件是否存在
		 * 
		 * 2.是否还有新数据可读
		 * 
		 * 3.从上一次读取的位置开始继续读取
		 * 
		 * 4.循环batch次，读取batch个372字节，并转换为batch条日志
		 * 
		 * 5.将解析后的batch条日志写入log.txt文件中
		 */

		// 预留一个判断
		/*
		 * 为了避免重复执行第一大步，导致原来第一大步中已经解析的日志文件被废弃，我们可以先判断，若第一步执行完毕后生成的log.txt文件存在，就不再执行第一步了。
		 * 
		 * 该文件会在第二大步执行完毕后删除
		 */
		if (textLogFile.exists()) {
			// 该文件存在，说明解析过了，不再重复第一大步
			return true;
		}

		/*
		 * 业务逻辑
		 */
		// 1
		if (!logFile.exists()) {
			return false;
		}
		// 2
		long lastposition = hasLogs();
		if (lastposition < 0) {
			return false;
		}
		try {
			// 3
			// 创建RandomAccessFile来读取日志文件
			RandomAccessFile raf = new RandomAccessFile(logFile, "r");
			// 移动游标到指定位置，开始继续读取
			raf.seek(lastposition);
			// 4
			// 定义一个集合，用于保存解析后的日志
			List<LogData> logs = new ArrayList<LogData>();
			// 循环batch次，解析batch条日志
			for (int i = 0; i < batch; i++) {
				/*
				 * 每次循环读完一条日志后，判断当前RandomAccessFile读取的位置是否在wtmpx文件中还有内容可读
				 */
				if (logFile.length() - raf.getFilePointer() < LogData.LOG_LENGTH) {
					break;
				}
				// 读取用户名
				String user = IOUtil.readString(raf, LogData.USER_LENGTH);
				// 读取pid
				raf.seek(lastposition + LogData.PID_OFFSET);
				int pid = IOUtil.readInt(raf);
				// 读取type
				raf.seek(lastposition + LogData.TYPE_OFFSET);
				short type = IOUtil.readShort(raf);
				// 读取time
				raf.seek(lastposition + LogData.TIME_OFFSET);
				int time = IOUtil.readInt(raf);
				// 读取host
				raf.seek(lastposition + LogData.HOST_OFFSET);
				String host = IOUtil.readString(raf, LogData.HOST_LENGTH);
				// 将lastposition设置为当前raf的游标位置
				lastposition = raf.getFilePointer();
				/*
				 * 将解析出来的数据存入一个LogData对象中，再将该对象存入集合中
				 */
				LogData log = new LogData(user, pid, type, time, host);
				logs.add(log);
			}

			// System.out.println("共解析了" + logs.size() + "条日志");
			// for (LogData l : logs) {
			// System.out.println(l);
			// }

			// 5
			/*
			 * 将解析后的日志写入log.txt文件中
			 */
			IOUtil.saveList(logs, textLogFile);
			/*
			 * 将这次解析后，RandomAccessFile的游标位置记录，以便于下次解析的时候继续读取
			 */
			IOUtil.saveLong(lastposition, lastPositionFile);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 将给定的日志存入给定的Map中
	 */
	public void putLogToMap(LogData log, Map<String, LogData> map) {
		map.put(log.getUser() + "," + log.getPid() + "," + log.getHost(), log);
	}

	/**
	 * 第二大步： 匹配日志
	 * 
	 * 将解析后的日志，和上次没有匹配成功的日志一起匹配成对
	 * 
	 * @return true：匹配成功 false：匹配失败
	 */
	public boolean matchLogs() {
		/*
		 * 匹配步骤：
		 * 
		 * 1.读取log.txt文件，将第一大步解析出的日志读取出来并转换为若干个LogData对象存入List集合中等待配对
		 * 
		 * 2.读取login.txt文件，将上一次没有配对成功的登入日志读取出来并转换为若干个LogData对象，也存入List集合中，等待这次配对
		 * 
		 * 3.循环List，将登入与登出日志分别存入到两个Map中，value就是对应的LogData日志对象，key都是[user,pid,ip]这样格式的字符串
		 * 
		 * 4.循环登出的map，并通过key寻找登入map中的登入日志，以达到配对目的，将配对的日志转换为一个LogRec对象存入一个List集合中
		 * 
		 * 5.将所有配对成功的日志写入文件logrec.txt
		 * 
		 * 6.将所有没有配对成功的日志写入文件login.txt
		 * 
		 * 7.第二大步执行完毕后删除第一大步解析出的log.txt文件
		 */

		// 必要的判断
		if (!textLogFile.exists()) {
			return false;
		}
		// 预留一个判断
		/*
		 * 当第二步已经执行完毕后，会生成两个文件：logrec.txt, login.txt
		 * 
		 * 若第三步在执行时出现错误，我们若重新执行第二步会将上次第二步已经配对的logrec.txt被覆盖。
		 * 
		 * 为此，需要做一个必要判断，就是logrec.txt文件若存在，则说明第二步已经完成但第三步没有顺利执行。
		 * 
		 * 因为第三步执行完毕后，会将该文件删除。所以，若存在，则第二步不再循环执行。
		 */
		if (logRecFile.exists()) {
			return true;
		}

		/*
		 * 业务逻辑
		 */
		try {
			// 1
			List<LogData> list = IOUtil.loadLogData(textLogFile);

			// 2
			if (loginFile.exists()) {
				list.addAll(IOUtil.loadLogData(loginFile));
			}

			// 3
			Map<String, LogData> loginMap = new HashMap<String, LogData>();
			Map<String, LogData> logoutMap = new HashMap<String, LogData>();
			for (LogData log : list) {
				if (log.getType() == LogData.TYPE_LOGIN) {
					putLogToMap(log, loginMap);
				}
				if (log.getType() == LogData.TYPE_LOGOUT) {
					putLogToMap(log, logoutMap);
				}
			}

			// 4
			// 用于存放所有配对成功的日志的集合
			List<LogRec> logRecList = new ArrayList<LogRec>();
			Set<Entry<String, LogData>> set = logoutMap.entrySet();
			for (Entry<String, LogData> e : set) {
				// 从登出map中取出key
				String key = e.getKey();
				/*
				 * 根据登出的key，从登入map中以相同的key删除元素，删除的就是对应的登入日志
				 */
				LogData login = loginMap.remove(key);
				if (login != null) {
					// 匹配后，转换为一个LogRec对象
					LogRec logrec = new LogRec(login, e.getValue());
					// 将配对日志存入集合
					logRecList.add(logrec);
				}
			}
			// 出了for循环，相当于配对工作完成

			// 5
			IOUtil.saveList(logRecList, logRecFile);

			// 6
			Collection<LogData> c = loginMap.values();
			IOUtil.saveList(new ArrayList<LogData>(c), loginFile);

			// 7
			if (textLogFile.exists()) {
				textLogFile.delete();
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			/*
			 * 若第二步出现异常，那么第二步生成的配对文件logrec.txt文件就是无效的。应当删除，以便于重新执行第二步
			 */
			if (logRecFile.exists()) {
				logRecFile.delete();
			}
			return false;
		}
	}

	/**
	 * 第三大步：发送至服务端
	 * 
	 * 将配对的日志发送至服务端
	 */
	public boolean sendLogToServer() {
		/*
		 * 步骤：
		 * 
		 * 1.创建Socket用于连接服务端
		 * 
		 * 2.通过Socket获取输出流，并逐步包装缓冲字符输出流，字符集是utf8
		 * 
		 * 3.创建缓冲字符输入流，用于读取logrec.txt(读取配对日志)
		 * 
		 * 4.从logrec.txt文件中读取每一行日志信息，并发送至服务端
		 * 
		 * 5.通过Socket获取输入流，并逐步包装为缓冲字符输入流，用于读取服务端的响应
		 * 
		 * 6.读取服务端的响应，若是"OK"，则说明服务端成功接收啦我们发送的配对日志，那么就将logrec.txt文件删除，第三步执行完毕；若返回响应不是"OK"，则表示发送不成功，那么该方法返回false，应当尝试重新执行第三步
		 */

		// 必要的判断
		if (!logRecFile.exists()) {
			return false;
		}

		/*
		 * 业务逻辑
		 */
		Socket socket = null;
		BufferedReader br = null;
		try {
			// 1
			socket = new Socket("localhost", 8088);
			// 2
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "utf8"));
			// 3
			br = new BufferedReader(new InputStreamReader(new FileInputStream(logRecFile)));
			// 4
			// 循环从logrec.txt文件中读取每一行配对日志，并发送至服务端
			String line = null;
			while ((line = br.readLine()) != null) {
				pw.println(line);
			}
			// 最后发送一个“over”表示发送完毕
			pw.println("over");
			pw.flush();
			// 已经将logrec.txt文件中的内容发送了
			// 发送完，将读取文件的流关掉，为了第三步执行完毕删logrec.txt文件
			br.close();
			// 5
			BufferedReader brServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf8"));
			// 6
			String response = brServer.readLine();
			if ("OK".equals(response)) {
				// 服务端正确接收发送的日之后，就可以将第二步生成的logrec.txt文件删除了
				logRecFile.delete();
				return true;
			}
			return false;
		} catch (Exception e) {
			return false;
		} finally {
			// 将socket关闭
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
			// 了不起的盖茨币
			// 读取文件的输入流也可能没关闭
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * 客户端开始工作的方法
	 */
	public void start() {
		/*
		 * 开始方法中，我们要循环以下三个步骤：
		 * 
		 * 1.从wtmpx文件中一次解析batch条日志
		 * 
		 * 2.将解析后的日志，和上次没有匹配成功的日志一起匹配成对
		 * 
		 * 3.将匹配成对的日志发送至服务端
		 */
		while (true) {
			// 1
			readNextLogs();

			// 2
			matchLogs();

			// 3
			sendLogToServer();
		}
	}

	public static void main(String[] args) {
		Client client = new Client();
		client.start();
	}
}
