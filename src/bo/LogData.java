package bo;

/**
 * bo:business object 类似于 vo:value object
 * 
 * LogData的每一个实例用于表示wtmpx文件中的一条日志信息
 * 
 * @author wxw
 */
public class LogData {
	/**
	 * 日志在wtmpx文件中的长度 每一条日志的长度都是372字节
	 */
	public static final int LOG_LENGTH = 372;

	/**
	 * user在单条日志的起始字节
	 */
	public static final int USER_OFFSET = 0;

	/**
	 * user在日志中占用的字节量
	 */
	public static final int USER_LENGTH = 32;

	/**
	 * PID在单条日志的起始字节
	 */
	public static final int PID_OFFSET = 68;

	/**
	 * TYPE在单条日志的起始字节
	 */
	public static final int TYPE_OFFSET = 72;

	/**
	 * TIME在单条日志的起始字节
	 */
	public static final int TIME_OFFSET = 80;

	/**
	 * HOST在单条日志的起始字节
	 */
	public static final int HOST_OFFSET = 114;

	/**
	 * HOST在日志中占用的字节量
	 */
	public static final int HOST_LENGTH = 258;

	/**
	 * 日志类型：登入操作
	 */
	public static final short TYPE_LOGIN = 7;

	/**
	 * 日志类型：登出操作
	 */
	public static final short TYPE_LOGOUT = 8;

	/**
	 * 登陆用户的用户名
	 */
	private String user;

	/**
	 * 进程ID
	 */
	private int pid;

	/**
	 * 日志类型（登入/登出）
	 */
	private short type;

	/**
	 * 日志生成的时间（登入和登出的时间） 单位：s
	 */
	private int time;

	/**
	 * 登陆用户的IP地址
	 */
	private String host;

	public LogData(String user, int pid, short type, int time, String host) {
		super();
		this.user = user;
		this.pid = pid;
		this.type = type;
		this.time = time;
		this.host = host;
	}

	/**
	 * 给定一个字符串 将该字符串转换为一个LogData对象
	 * 
	 * （格式应该是当前类toString方法生成）
	 */
	public LogData(String line) {
		// 1.按照","拆分字符串
		String[] arr = line.split(",");
		// 2.将数组中每一项设置到属性上即可
		this.user = arr[0];
		this.pid = Integer.parseInt(arr[1]);
		this.type = Short.parseShort(arr[2]);
		this.time = Integer.parseInt(arr[3]);
		this.host = arr[4];
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public int getPid() {
		return pid;
	}

	public void setPid(int pid) {
		this.pid = pid;
	}

	public short getType() {
		return type;
	}

	public void setType(short type) {
		this.type = type;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@Override
	public String toString() {
		return user + "," + pid + "," + type + "," + time + "," + host;
	}
}
