package cn.wxw.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import cn.wxw.bo.LogData;

/**
 * 该类是一个工具类 负责读写数据
 * 
 * 把读写逻辑单独定义在该类中的目的是为了重用这些逻辑
 * 
 * @author wxw
 *
 */
public class IOUtil {
	/**
	 * 从给定的文件中读取第一行字符串，并将其转换为一个long值返回
	 */
	public static long readLong(File file) throws IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			return Long.parseLong(br.readLine());
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

	/**
	 * 从给定的RandomAccessFile的当前位置处连续读取给定字节数，并转换为字符串
	 */
	public static String readString(RandomAccessFile raf, int len) throws IOException {
		byte[] buf = new byte[len];
		raf.read(buf);
		String str = new String(buf, "ISO8859-1");
		return str.trim();
	}

	/**
	 * 从给定的RandomAccessFile的当前位置处读取一个int值并返回
	 */
	public static int readInt(RandomAccessFile raf) throws IOException {
		return raf.readInt();
	}

	/**
	 * 从给定的RandomAccessFile的当前位置处读取一个short值并返回
	 */
	public static short readShort(RandomAccessFile raf) throws IOException {
		return raf.readShort();
	}

	/**
	 * 将给定的集合中的每个元素的toString方法返回的字符串，作为一行内容写入给定的文件中
	 * 
	 * List没有加泛型，是为了以后加入logRecList写入文件
	 */
	public static void saveList(List list, File file) throws IOException {
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(file);
			for (Object o : list) {
				pw.println(o);
			}
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
	}

	/**
	 * 将给定的long值作为一行字符串写入给定的文件中
	 */
	public static void saveLong(long l, File file) throws IOException {
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(file);
			pw.println(l);
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
	}

	/**
	 * 从指定的文件中按行读取每一条日志，并转换为一个LogData对象，最终将所有日志对象存入一个List集合中并返回
	 */
	public static List<LogData> loadLogData(File file) throws IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			List<LogData> list = new ArrayList<LogData>();
			String line = null;
			while ((line = br.readLine()) != null) {
				/*
				 * 解析过程应当交给LogData
				 * 
				 * 原因在于该字符串的格式是由LogData自身的toString决定的，所以解析自然也应该交给它
				 */
				LogData log = new LogData(line);
				list.add(log);
			}
			return list;
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}
}
