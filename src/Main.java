import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class Main {

	public static void printUsage(String[] invocation) {
		//invocation config: 
		// args: -f ./data/20200716_IEXTP1_DEEP1.0.pcap
		// VM args: -Xms12g -Xmx12g
		// takes about a minute to run
		String inv = "["+invocation.length+"]";
		for(int i=0;i<invocation.length;++i) {
			if(i!=0)inv+=" ";
			inv+=invocation[i];
		}
		System.out.println("invocation: "+inv);
		System.out.println("-h            : help (this listing)");
		System.out.println("-f <filename> : read the given file");
	}
	
	public static void main(String[] args) {
		if(args.length < 2 || args[0] == "-h") {
			printUsage(args);
			System.exit(-1);
		}
		if(args.length == 2) {
			System.out.println("{"+args[0]+"}");
			if(args[0].equals("-f")) {
				System.out.println(args[1]);
				if(!parse(args[1])) {
					System.err.println("failed to parse "+args[1]);
					System.exit(-1);
				}
			}else {
				printUsage(args);
				System.exit(-1);
			}
		}
		System.exit(0);
	}
	
	public static boolean parse(String fname) {
		InputStream ins = null;
		try {
			ins = new BufferedInputStream(new FileInputStream(fname), 200*1024*1024);
		} catch (FileNotFoundException e) {
			System.err.println("couldn't find the file: "+fname);
			e.printStackTrace();
			return false;
		}
		int aByte = -1;
		long count = 0;
		do {
			try {
				aByte = ins.read();
			} catch (IOException e) {
				System.err.println("IO exception while reading: "+fname);
				e.printStackTrace();
				try {
					ins.close();
				} catch (IOException ee) {
					System.err.println("failed to close: "+fname);
					ee.printStackTrace();
				}
				return false;
			}
			++count;
		}while (aByte != -1);
		System.out.println("bytes read: "+count);
		try {
			ins.close();
		} catch (IOException e) {
			System.err.println("failed to close: "+fname+", but read it all, so ignoring.");
			e.printStackTrace();
		}
		return true;
	}
	
	public static String readString(BufferedInputStream s, int length) {
		String v = "";
		for(int i=0;i<length;++i) {
			try {
				int x = s.read();
				if(x==-1)return null;
				v+=Character.forDigit(x, 10);
			} catch (IOException e) {
				System.err.println("IO exception");
				e.printStackTrace();
				return null;
			}
		}
		return v.trim();
	}
	
	public static long readLong(BufferedInputStream s) {
		long v = -1;
		long modifier = 1;
		for(int i=0;i<8;++i) {
			try {
				int x = s.read();
				if(x==-1) {
					return -1;
				}
				v+=x*modifier;
				modifier *= 256;
			} catch (IOException e) {
				System.err.println("IO exception");
				e.printStackTrace();
				return -2;
			}
		}
		return v;
	}
	
	public static int readInt(BufferedInputStream s) {
		int v = -1;
		int modifier = 1;
		for(int i=0;i<4;++i) {
			try {
				int x = s.read();
				if(x==-1) {
					return -1;
				}
				v+=x*modifier;
				modifier *= 256;
			} catch (IOException e) {
				System.err.println("IO exception");
				e.printStackTrace();
				return -2;
			}
		}
		return v;
	}
	
	public static int readPrice(BufferedInputStream s) {
		return readInt(s);
	}
	
	public static int readByte(BufferedInputStream s) {
		int v = -1;
		try {
			v = s.read();
		} catch (IOException e) {
			System.err.println("IO exception");
			e.printStackTrace();
			return -2;
		}
		return v;
	}
	
	public static long readTimeStamp(BufferedInputStream s) {
		return readLong(s);
		
	}
	
	public static Date timeStampToDate(long t) {
		return new Date(t/1000000);
	}
	
	public static int readEventTime(BufferedInputStream s) {
		return 0;
	}
	
	public static boolean readAdministrativeMessage(BufferedInputStream s) {
		int systemEvent = readByte(s);
		if(systemEvent<0) return false;
		long t = readTimeStamp(s);
		if(t<0) return false;
		switch(systemEvent) {
		case 'O': return true;
		case 'S': return true;
		case 'R': return true;
		case 'M': return true;
		case 'E': return true;
		case 'C': return true;
		}
		
		return false;
	}
	
	public static boolean readSecurityDirectoryMessage(BufferedInputStream s) {
		int flags=readByte(s);
		if(flags < 0)return false;
		long t=readTimeStamp(s);
		if(t < 0) return false;
		String symbol = readString(s,8);
		if(symbol == null)return false;
		int roundLotSize = readInt(s);
		if(roundLotSize <0) return false;
		int adjustedPOCPrice = readPrice(s);
		if(adjustedPOCPrice < 0)return false;
		int LULDTier = readByte(s);
		if(LULDTier < 0)return false;
		
		return true;
	}
	
	public static boolean readTradingStatusMessage(BufferedInputStream s) {
		int tradingStatus = readByte(s);
		if(tradingStatus < 0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		String reason = readString(s,4);
		if(null==reason)return false;
		return true;
	}
}
