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
			switch(aByte) {
			case 'S': if(!readAdministrativeMessage(ins))fail();
			case 'D': if(!readSecurityDirectoryMessage(ins))fail();
			case 'H': if(!readTradingStatusMessage(ins))fail();
			case 'O': if(!readOperationalHaltStatusMessage(ins))fail();
			case 'P': if(!readShortSalePriceTestStatusMessage(ins))fail();
			case 'E': if(!readSecurityEventMessage(ins))fail();
			case '8': if(!readBuyPriceLevelUpdateMessage(ins))fail();
			case '5': if(!readSellPriceLevelUpdateMessage(ins))fail();

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
	
	public static void fail() {
		NullPointerException e = new NullPointerException();
		e.printStackTrace();
		throw(e);
	}
	
	public static String readString(InputStream s, int length) {
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
	
	public static long readLong(InputStream s) {
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
	
	public static int readInt(InputStream s) {
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
	
	public static int readPrice(InputStream s) {
		return readInt(s);
	}
	
	public static int readByte(InputStream s) {
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
	
	public static long readTimeStamp(InputStream s) {
		return readLong(s);
		
	}
	
	public static Date timeStampToDate(long t) {
		return new Date(t/1000000);
	}
	
	public static int readEventTime(InputStream s) {
		return 0;
	}
	
	public static boolean readAdministrativeMessage(InputStream s) {
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
	
	public static boolean readSecurityDirectoryMessage(InputStream s) {
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
	
	public static boolean readTradingStatusMessage(InputStream s) {
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
	
	public static boolean readOperationalHaltStatusMessage(InputStream s) {
		int operationalHaltStatus = readByte(s);
		if(operationalHaltStatus<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		return true;
	}
	
	public static boolean readShortSalePriceTestStatusMessage(InputStream s) {
		int shortSalePriceTestStatus = readByte(s);
		if(shortSalePriceTestStatus<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int detail = readByte(s);
		if(detail<0)return false;
		return true;
	}
	
	public static boolean readSecurityEventMessage(InputStream s) {
		int securityEvent = readByte(s);
		if(securityEvent<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		return true;
	}
	
	public static boolean readBuyPriceLevelUpdateMessage(InputStream s) {
		int eventFlags = readByte(s);
		if(eventFlags<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int size = readInt(s);
		if(size<0)return false;
		int price = readPrice(s);
		if(price<0)return false;
		return true;
	}
	
	public static boolean readSellPriceLevelUpdateMessage(InputStream s) {
		int eventFlags = readByte(s);
		if(eventFlags<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int size = readInt(s);
		if(size<0)return false;
		int price = readPrice(s);
		if(price<0)return false;
		return true;
	}
	
	public static boolean readTradeReportMessage(InputStream s) {
		int saleConditionFlags = readByte(s);
		if(saleConditionFlags<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int size = readInt(s);
		if(size<0)return false;
		int price = readPrice(s);
		if(price<0)return false;
		long tradeId = readLong(s);
		if(tradeId<0)return false;
		return true;
	}
	
	public static boolean readOfficialPriceMessage(InputStream s) {
		int priceType = readByte(s);
		if(priceType<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int officialPrice = readPrice(s);
		if(officialPrice<0)return false;
		return true;
	}
	
	public static boolean readTradeBreakMessage(InputStream s) {
		int saleConditionFlags = readByte(s);
		if(saleConditionFlags<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int size = readInt(s);
		if(size<0)return false;
		int price = readPrice(s);
		if(price<0)return false;
		long tradeId = readLong(s);
		if(tradeId<0)return false;
		return true;
	}
	
	public static boolean readAuctionInformationMessage(InputStream s) {
		int auctionType = readByte(s);
		if(auctionType<0)return false;
		long t = readTimeStamp(s);
		if(t<0)return false;
		String symbol = readString(s,8);
		if(null==symbol)return false;
		int pairedShares = readInt(s);
		if(pairedShares<0)return false;
		int referencePrice = readPrice(s);
		if(referencePrice<0)return false;
		int indicativeClearingPrice = readPrice(s);
		if(indicativeClearingPrice<0)return false;
		int imbalanceShares = readInt(s);
		if(imbalanceShares<0)return false;
		int imbalanceSide = readByte(s);
		if(imbalanceSide<0)return false;
		int extensionNumber = readByte(s);
		if(extensionNumber<0)return false;
		int scheduledAuctionTime = readEventTime(s);
		if(scheduledAuctionTime<0)return false;
		int auctionBookClearingPrice = readPrice(s);
		if(auctionBookClearingPrice<0)return false;
		int collarReferencePrice = readPrice(s);
		if(collarReferencePrice<0)return false;
		int lowerAuctionCollar = readPrice(s);
		if(lowerAuctionCollar<0)return false;
		int upperAuctionCollar = readPrice(s);
		if(upperAuctionCollar<0)return false;
		return true;
	}
}
