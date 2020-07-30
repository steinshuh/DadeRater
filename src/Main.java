

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class Main {

	public static Map<String, Ticker> tickers = new TreeMap<String, Ticker>();
	public static Set<String> symbolFilter = null;

	public static Ticker getTicker(String symbol) {
		if(tickers.containsKey(symbol)) {
			return tickers.get(symbol);
		}
		if(symbolFilter==null || symbolFilter.contains(symbol)){
			Ticker ticker = new Ticker(symbol);
			tickers.put(symbol, ticker);
			return ticker;
		}
		return null;
	}


	public static void printUsage(String[] invocation) {
		//invocation config: 
		// args: -f ./data/20200716_IEXTP1_DEEP1.0.pcap
		//       -f ./data/20180127_IEXTP1_DEEP1.0.pcap
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
		System.out.println("-x <symbol>   : symbol to add to the filter");
		System.out.println("-hd <filename> : read the given and perform a hex dump");
	}

	public static void main(String[] args) {
		boolean needToPrintUsage = (args.length == 0);
		boolean needToIngestFile = false;
		boolean needToHexDump = false;
		boolean needToTestGui = false;
		String fileToIngest = null;
		String fileToHexDump = null;
		int argsI = 0;
		while(argsI < args.length){
			System.out.println("arg: "+args[argsI]);
			if(args[argsI].equals("-h")){
				needToPrintUsage = true;
				++argsI;
			}else if(args[argsI].equals("-f")){
				++argsI;
				if(argsI < args.length){
					needToIngestFile = true;
					fileToIngest = args[argsI];
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else if(args[argsI].equals("-hd")){
				++argsI;
				if(argsI < args.length){
					needToHexDump = true;
					fileToHexDump = args[argsI];
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else if(args[argsI].equals("-x")){
				++argsI;
				if(argsI < args.length){
					if(symbolFilter==null){
						symbolFilter = new TreeSet<String>();
					}
					symbolFilter.add(args[argsI]);
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else if(args[argsI].equals("-tg")){
				needToTestGui=true;
				++argsI;
			}else{
				needToPrintUsage = true;
				++argsI;
			}
		}
		if(needToIngestFile){
			if(!parse(fileToIngest)) {
				System.err.println("failed to parse "+args[1]);
				System.exit(-1);
			}
			Ticker ticker = getTicker("MSFT");
			if(ticker!=null){
				System.out.println("ticker: "+ticker.symbol+" "+ticker.moments.size());
				int priceCount = 0;
				for(Entry<Long, Ticker.Moment> entry : ticker.moments.entrySet()) {
					if(entry.getValue().price>0)++priceCount;
				}
				System.out.println("price count: "+priceCount);
				ticker.initializeTimeSeries();
				JFrame frame = new JFrame();
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setContentPane(ticker.chartPanel);
				frame.pack();
				frame.setVisible(true);
			}
		}
		if(needToPrintUsage){
			printUsage(args);
		}
		if(needToTestGui){
			testGui();
		}
		if(needToHexDump){
			hexDump(fileToHexDump);
		}

	}

	public static void testGui(){
		TimeSeries series = new TimeSeries("time series");
		series.add(new Second(new Date(System.currentTimeMillis())), 100 );  
		series.add(new Second(new Date(System.currentTimeMillis()+1000)), 150);  
		series.add(new Second(new Date(System.currentTimeMillis()+2000)), 70 );  
		series.add(new Second(new Date(System.currentTimeMillis()+3000)), 210 );  
		series.add(new Second(new Date(System.currentTimeMillis()+4000)), 310);
		series.add(new Second(new Date(System.currentTimeMillis()+5000)), 260 );  
		TimeSeriesCollection dataset = new TimeSeriesCollection();  
		dataset.addSeries(series);  
		JFreeChart timechart = ChartFactory.createTimeSeriesChart(  
				"Vistors Count Chart", // Title  
				"Date",         // X-axis Label 
				"Visitors",       // Y-axis Label  
				dataset,        // Dataset  
				true,          // Show legend  
				true,          // Use tooltips  
				false          // Generate URLs  
				);

		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ChartPanel chartPanel = new ChartPanel(timechart);
		chartPanel.setPreferredSize(new Dimension(600, 300));
		chartPanel.setMouseZoomable(true,false);
		frame.setContentPane(chartPanel);
		frame.pack();
		frame.setVisible(true);
	}

	public static boolean hexDump(String fname) {
		InputStream ins = null;
		try {
			ins = new BufferedInputStream(new FileInputStream(fname), 200*1024*1024);
		} catch (FileNotFoundException e) {
			System.err.println("couldn't find the file: "+fname);
			e.printStackTrace();
			return false;
		}
		while(readLine(ins, 16)){}
		return true;
	}

	public static boolean parse(String fname) {

		Map<String,Integer> messageCounts = new TreeMap<String, Integer>();
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
		int messageCount=0;
		do {
			//if(readLine(ins, 16))aByte=1;
			//else aByte=-1;
			messageCount = readHeader(ins);
			//System.out.println(messageCount);
			for(int i=0;i<messageCount;++i){
				int messageSize=readShort(ins);
				//we probably should check this
				if(messageSize>0){
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
					String ab = ""+(char)aByte;
					if(messageCounts.containsKey(ab)) {
						Integer x = messageCounts.get(ab);
						messageCounts.put(ab, x.intValue()+1);
					}else {
						messageCounts.put(ab,  1);
					}
					switch(aByte) {
					case 'S': if(!readAdministrativeMessage(ins, messageSize))fail();
					break;
					case 'D': if(!readSecurityDirectoryMessage(ins, messageSize))fail();
					break;
					case 'H': if(!readTradingStatusMessage(ins, messageSize))fail();
					break;
					case 'O': if(!readOperationalHaltStatusMessage(ins, messageSize))fail();
					break;
					case 'P': if(!readShortSalePriceTestStatusMessage(ins, messageSize))fail();
					break;
					case 'E': if(!readSecurityEventMessage(ins, messageSize))fail();
					break;
					case '8': if(!readBuyPriceLevelUpdateMessage(ins, messageSize))fail();
					break;
					case '5': if(!readSellPriceLevelUpdateMessage(ins, messageSize))fail();
					break;
					case 'T': if(!readTradeReportMessage(ins, messageSize))fail();
					break;
					case 'X': if(!readOfficialPriceMessage(ins, messageSize))fail();
					break;
					case 'B': if(!readTradeBreakMessage(ins, messageSize))fail();
					break;
					case 'A': if(!readAuctionInformationMessage(ins, messageSize))fail();
					break;
					default:
						System.out.println("unknown message type (size["+messageSize+"]): "+displayChar(aByte)+" "+shortHex(aByte)+" "+aByte);
					}
					++count;
				}
			}
		}while (messageCount >=0);
		System.out.println("messages read: "+count);
		try {
			ins.close();
		} catch (IOException e) {
			System.err.println("failed to close: "+fname+", but read it all, so ignoring.");
			e.printStackTrace();
		}
		for(String ab : messageCounts.keySet()) {
			System.out.println(ab+" "+messageCounts.get(ab));
		}
		System.out.println("tickers: "+tickers.size());

		return true;
	}

	public static void fail() {
		NullPointerException e = new NullPointerException();
		e.printStackTrace();
		throw(e);
	}

	private static int _lineNumber = 0;
	public static String shortHex(int v){
		String rv=v<16?"0":"";
		rv+=Integer.toHexString(v);
		return rv;
	}
	public static char displayChar(int x){
		if(' ' <= (char)x && (char)x <= '}'){
			return (char)x;
		}else{
			return '.';
		}
	}
	public static boolean readLine(InputStream s, int length) {
		String codes = "";
		String vis = "";
		for(int i=0;i<length;++i){
			int x = readByte(s);
			if(x<0)return false;
			codes+= shortHex(x);
			codes+=" ";
			vis+=displayChar(x);
		}
		System.out.println(codes +"| " + vis + "    "+_lineNumber+" - "+(_lineNumber+length));
		_lineNumber+=length;
		return true;
	}

	public static String readString(InputStream s, int length) {
		String v = "";
		for(int i=0;i<length;++i) {
			try {
				int x = s.read();
				if(x==-1)return null;
				v+=(char)x;
			} catch (IOException e) {
				System.err.println("IO exception");
				e.printStackTrace();
				return null;
			}
		}
		return v.trim();
	}

	public static long readLong(InputStream s) {
		long v = 0;
		long modifier = 1;
		for(int i=0;i<8;++i) {
			try {
				int x = s.read();
				if(x==-1) {
					fail();
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
		int v = 0;
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

	public static int readShort(InputStream s) {
		int v = 0;
		int modifier = 1;
		for(int i=0;i<2;++i) {
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

	public static long readPrice(InputStream s) {
		return readLong(s);
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

	public static boolean readAdministrativeMessage(InputStream s, int messageSize) {
		int computedMessageSize =1;
		int systemEvent = readByte(s);
		++computedMessageSize;
		if(systemEvent<0) return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0) return false;
		if(computedMessageSize!=messageSize)fail();
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

	public static boolean readSecurityDirectoryMessage(InputStream s, int messageSize) {
		int computedMessageSize =1;
		int flags=readByte(s);
		++computedMessageSize;
		if(flags < 0)return false;
		long t=readTimeStamp(s);
		computedMessageSize+=8;
		if(t < 0) return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(symbol == null)return false;
		int roundLotSize = readInt(s);
		computedMessageSize+=4;
		if(roundLotSize <0) return false;
		long adjustedPOCPrice = readPrice(s);
		computedMessageSize+=8;
		if(adjustedPOCPrice < 0)return false;
		int LULDTier = readByte(s);
		++computedMessageSize;
		if(LULDTier < 0)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readTradingStatusMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int tradingStatus = readByte(s);
		++computedMessageSize;
		if(tradingStatus < 0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		String reason = readString(s,4);
		computedMessageSize+=4;
		if(null==reason)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readOperationalHaltStatusMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int operationalHaltStatus = readByte(s);
		++computedMessageSize;
		if(operationalHaltStatus<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readShortSalePriceTestStatusMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int shortSalePriceTestStatus = readByte(s);
		++computedMessageSize;
		if(shortSalePriceTestStatus<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		int detail = readByte(s);
		++computedMessageSize;
		if(detail<0)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readSecurityEventMessage(InputStream s, int messageSize) {
		int computedMessageSize = 1;
		int securityEvent = readByte(s);
		++computedMessageSize;
		if(securityEvent<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readBuyPriceLevelUpdateMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int eventFlags = readByte(s);
		++computedMessageSize;
		if(eventFlags<0)fail();
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)fail();
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)fail();
		int size = readInt(s);
		computedMessageSize+=4;
		if(size<0)fail();
		long price = readPrice(s);
		computedMessageSize+=8;
		if(price<0)fail();
		if(computedMessageSize!=messageSize)fail();
		Ticker ticker = getTicker(symbol);
		if(ticker!=null){
			ticker.addBuy(t, price, size);
		}
		return true;
	}

	public static boolean readSellPriceLevelUpdateMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int eventFlags = readByte(s);
		++computedMessageSize;
		if(eventFlags<0)fail();
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)fail();
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)fail();
		int size = readInt(s);
		computedMessageSize+=4;
		if(size<0)fail();
		long price = readPrice(s);
		computedMessageSize+=8;
		if(price<0)fail();
		if(computedMessageSize!=messageSize)fail();
		Ticker ticker = getTicker(symbol);
		if(ticker!=null){
			ticker.addSell(t, price, size);
		}
		return true;
	}

	public static boolean readTradeReportMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int saleConditionFlags = readByte(s);
		++computedMessageSize;
		if(saleConditionFlags<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		int size = readInt(s);
		computedMessageSize+=4;
		if(size<0)return false;
		long price = readPrice(s);
		computedMessageSize+=8;
		if(price<0)return false;
		long tradeId = readLong(s);
		computedMessageSize+=8;
		if(tradeId<0)return false;
		if(computedMessageSize!=messageSize)fail();
		Ticker ticker = getTicker(symbol);
		if(ticker!=null){
			ticker.setPrice(t, price, size);
		}
		return true;
	}

	public static boolean readOfficialPriceMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int priceType = readByte(s);
		++computedMessageSize;
		if(priceType<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		long officialPrice = readPrice(s);
		computedMessageSize+=8;
		if(officialPrice<0)return false;
		if(computedMessageSize!=messageSize)fail();
		Ticker ticker = getTicker(symbol);
		if(ticker!=null){
			ticker.setPrice(t, officialPrice);
		}
		return true;
	}

	public static boolean readTradeBreakMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int saleConditionFlags = readByte(s);
		++computedMessageSize;
		if(saleConditionFlags<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		int size = readInt(s);
		computedMessageSize+=4;
		if(size<0)return false;
		long price = readPrice(s);
		computedMessageSize+=8;
		if(price<0)return false;
		long tradeId = readLong(s);
		computedMessageSize+=8;
		if(tradeId<0)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static boolean readAuctionInformationMessage(InputStream s, int messageSize) {
		int computedMessageSize = 1;
		int auctionType = readByte(s);
		++computedMessageSize;
		if(auctionType<0)return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)return false;
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)return false;
		int pairedShares = readInt(s);
		computedMessageSize+=4;
		if(pairedShares<0)return false;
		long referencePrice = readPrice(s);
		computedMessageSize+=8;
		if(referencePrice<0)return false;
		long indicativeClearingPrice = readPrice(s);
		computedMessageSize+=8;
		if(indicativeClearingPrice<0)return false;
		int imbalanceShares = readInt(s);
		computedMessageSize+=4;
		if(imbalanceShares<0)return false;
		int imbalanceSide = readByte(s);
		++computedMessageSize;
		if(imbalanceSide<0)return false;
		int extensionNumber = readByte(s);
		++computedMessageSize;
		if(extensionNumber<0)return false;
		int scheduledAuctionTime = readEventTime(s);
		computedMessageSize+=4;
		if(scheduledAuctionTime<0)return false;
		long auctionBookClearingPrice = readPrice(s);
		computedMessageSize+=8;
		if(auctionBookClearingPrice<0)return false;
		long collarReferencePrice = readPrice(s);
		computedMessageSize+=8;
		if(collarReferencePrice<0)return false;
		long lowerAuctionCollar = readPrice(s);
		computedMessageSize+=8;
		if(lowerAuctionCollar<0)return false;
		long upperAuctionCollar = readPrice(s);
		computedMessageSize+=8;
		if(upperAuctionCollar<0)return false;
		if(computedMessageSize!=messageSize)fail();
		return true;
	}

	public static int readHeader(InputStream s){
		int matchIndex=0;
		int[] header = {1,0,4,0x80};
		int rb=0;
		do {
			rb = readByte(s);
			if(rb==header[matchIndex]){
				++matchIndex;
				if(matchIndex>=header.length){
					//matched
					rb=readInt(s);//channel id
					if(rb<0)return rb;
					rb=readInt(s);//session id
					if(rb<0)return rb;
					rb = readShort(s);//payload length
					if(rb<0)return rb;
					rb = readShort(s);//message count
					if(rb<0)return rb;
					int messageCount=rb;
					long streamOffset = readLong(s);
					if(streamOffset<0)return -1;
					long firstSequenceNumber = readLong(s);
					if(firstSequenceNumber<0)return -1;
					long t = readTimeStamp(s);
					if(t<0)return -1;
					return messageCount;
				} 
			} else {
				matchIndex=0;
				if(rb==header[matchIndex]){
					++matchIndex;
				}
			}
		}while(rb>=0);
		return rb;
	}
}
