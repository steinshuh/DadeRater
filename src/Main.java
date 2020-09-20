

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class Main {
	//see how predictable the day is to itself
	//sliding window
	//every n seconds (10, for now)
	//define Q
	//compute dist
	//check for dist < threshold (3, for now)
	//determine the mean & sd at some future time t (60 seconds, for now)
	//also try nearest neighbor
	//compute profit?

	public static TreeMap<Long, Long> marketHours = new TreeMap<>();
	public static Map<String, Ticker> tickers = new TreeMap<>();
	public static TreeMap<String, TreeSet<String>> comparisons = new TreeMap<>();
	public static Set<String> symbolFilter = null;
	//filename, symbols
	public static TreeMap<String, TreeSet<String>> dumps = new TreeMap<>();
	public static TreeMap<String, TreeSet<String>> valueDumps = new TreeMap<>();
	public static TreeMap<String, String> saves = new TreeMap<>();
	public static TreeSet<String> filesToLoad = new TreeSet<>();

	public static void writeMarketHours(FileWriter fw) throws IOException{
		fw.write(""+marketHours.size()+"\n");
		for(Entry<Long,Long> entry : marketHours.entrySet()){
			fw.write(""+entry.getKey()+", "+entry.getValue()+"\n");
		}
	}
	
	public static void loadMarketHours(BufferedReader breader, String filename) throws IOException {
		String s = breader.readLine();
		if(s==null) {
			breader.close();
			Main.die("Main.loadMarketHours " + filename + " cut off before count", new Exception());
		}
		int count = Integer.parseInt(s);
		int i=0;
		for(s=breader.readLine();s!=null && i<count;) {
			String[] hours = s.split(",");
			if(hours.length!=2){
				breader.close();
				Main.die("Main.loadMarketHours " + filename + " badly formatted hours (not two entries): "+s, new Exception());
			}
			Long startHours = Long.parseLong(hours[0].trim());
			Long endHours = Long.parseLong(hours[1].trim());
			if(!marketHours.containsKey(startHours)){
				marketHours.put(startHours, endHours);
			}
			++i;
		}
	}
	
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
	public static void die(String message, Exception e){
		e.printStackTrace();
		System.err.println(message+", "+e);
		System.exit(-1);
	}

	public static void printUsage(String[] invocation) {
		//invocation config: 
		// args: -f ./data/20200716_IEXTP1_DEEP1.0.pcap
		//       -f ./data/20180127_IEXTP1_DEEP1.0.pcap
		//       -f ./data/20200716_IEXTP1_DEEP1.0.pcap -f ./data/20200716_IEXTP1_TOPS1.6.pcap -x MSFT -x GOOGL -x AAPL -c AAPL GOOGL -c AAPL MSFT -c GOOGL AAPL -c GOOGL MSFT -c MSFT AAPL -c MSFT GOOGL -df AAPL aapl.csv -df GOOGL googl.csv -df MSFT msft.csv -q 256 10 4 60		
		//       -f ./data/20200716_IEXTP1_DEEP1.0.pcap -f ./data/20200716_IEXTP1_TOPS1.6.pcap -x MSFT -x GOOGL -x AAPL -save AAPL aapl.mom -save GOOGL googl.mom -save MSFT msft.mom
		//       -load ./data/moms/aapl.mom -load ./data/moms/googl.mom -load ./data/moms/msft.mom
		// VM args: -Xms12g -Xmx12g
		// takes about a minute to run
		String inv = "["+invocation.length+"]";
		for(int i=0;i<invocation.length;++i) {
			if(i!=0)inv+=" ";
			inv+=invocation[i];
		}
		System.out.println("invocation: "+inv);
		System.out.println("-h                        : help (this listing)");
		System.out.println("-f <filename>             : read the given file");
		System.out.println("-x <symbol>               : symbol to add to the filter");
		System.out.println("-hd <filename>            : read the given and perform a hex dump");
		System.out.println("-c <symbol> <symbol>      : compare two symbols");
		System.out.println("-df <symbol> <filename>   : dump symbol data to the given file");
		System.out.println("-dv <symbol> <filename>   : dump raw symbol values to the given file");
		System.out.println("-save <symbol> <filename> : save symbol moments to the given file");
		System.out.println("-load <filename>          : load symbol moments to the given file");
		System.out.println("-q <query length sec.> <step sec.> <distance threshold>");
	}

	public static void main(String[] args) {
		boolean needToPrintUsage = (args.length == 0);
		boolean needToIngestFile = false;
		boolean needToHexDump = false;
		boolean needToTestGui = false;
		boolean needToDumpFile = false;
		boolean needToDumpValueFile = false;
		boolean needToSaveFile = false;
		boolean needToLoadFiles = false;
		TreeSet<String> filesToIngest = new TreeSet<String>();
		String fileToHexDump = null;
		boolean needToQ = false;
		int qQueryLength = 0;
		int qStepSeconds = 0;
		double qDistanceThreshold = 0;
		int qPredictOffset = 60;
		int argsI = 0;
		while(argsI < args.length){
			System.out.println("arg: "+args[argsI]);
			if(args[argsI].equals("-h")){
				needToPrintUsage = true;
				++argsI;
			}else if(args[argsI].equals("-load")){
				++argsI;
				if(argsI < args.length){
					needToLoadFiles = true;
					String f = args[argsI];
					filesToLoad.add(f);
					System.out.println("\t"+f);
					++argsI;
				} else {
					needToPrintUsage = true;										
				}
			}else if(args[argsI].equals("-save")){
				++argsI;
				if(argsI < args.length){
					String symbol = args[argsI];
					System.out.println("\t"+symbol);
					++argsI;
					if(argsI < args.length){
						needToSaveFile = true;
						String fileToSave = args[argsI];
						saves.put(symbol, fileToSave);
						System.out.println("\t"+fileToSave);
						++argsI;
					} else {
						needToPrintUsage = true;					
					}
				} else {
					needToPrintUsage = true;										
				}
			}else if(args[argsI].equals("-q")){
				++argsI;
				if(argsI < args.length){
					qQueryLength = Integer.parseInt(args[argsI]);
					System.out.println("\t"+qQueryLength);
					++argsI;
					if(argsI < args.length){
						qStepSeconds = Integer.parseInt(args[argsI]);
						System.out.println("\t"+qStepSeconds);
						++argsI;
						if(argsI < args.length){
							qDistanceThreshold=Double.parseDouble(args[argsI]);
							System.out.println("\t"+qDistanceThreshold);
							++argsI;
							if(argsI < args.length){
								qPredictOffset=Integer.parseInt(args[argsI]);
								System.out.println("\t"+qPredictOffset);
								needToQ=true;
								++argsI;
							} else {
								needToPrintUsage = true;
							}
						} else {
							needToPrintUsage = true;													
						}
					} else {
						needToPrintUsage = true;						
					}
				} else {
					needToPrintUsage = true;
				}
			}else if(args[argsI].equals("-df")){
				++argsI;
				if(argsI < args.length){
					String symbol = args[argsI];
					System.out.println("\t"+symbol);
					++argsI;
					if(argsI < args.length){
						needToDumpFile = true;
						String fileToDump = args[argsI];
						TreeSet<String> symbolsToDump = null;
						if(!dumps.containsKey(fileToDump)){
							symbolsToDump = new TreeSet<String>();
							dumps.put(fileToDump, symbolsToDump);
						}else{
							symbolsToDump=dumps.get(fileToDump);
						}
						symbolsToDump.add(symbol);
						System.out.println("\t"+fileToDump);
						++argsI;
					} else {
						needToPrintUsage = true;					
					}
				} else {
					needToPrintUsage = true;										
				}
			}else if(args[argsI].equals("-dv")){
				++argsI;
				if(argsI < args.length){
					String symbol = args[argsI];
					System.out.println("\t"+symbol);
					++argsI;
					if(argsI < args.length){
						needToDumpValueFile = true;
						String fileToDumpValues = args[argsI];
						TreeSet<String> symbolsToDumpValues = null;
						if(!valueDumps.containsKey(fileToDumpValues)){
							symbolsToDumpValues = new TreeSet<String>();
							valueDumps.put(fileToDumpValues, symbolsToDumpValues);
						}else{
							symbolsToDumpValues=valueDumps.get(fileToDumpValues);
						}
						symbolsToDumpValues.add(symbol);
						System.out.println("\t"+fileToDumpValues);
						++argsI;
					} else {
						needToPrintUsage = true;					
					}
				} else {
					needToPrintUsage = true;										
				}
			}else if(args[argsI].equals("-f")){
				++argsI;
				if(argsI < args.length){
					needToIngestFile = true;
					filesToIngest.add(args[argsI]);
					System.out.println("\t"+args[argsI]);
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else if(args[argsI].equals("-hd")){
				++argsI;
				if(argsI < args.length){
					needToHexDump = true;
					fileToHexDump = args[argsI];
					System.out.println("\t"+fileToHexDump);
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
					System.out.println("\t"+args[argsI]);					
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else if(args[argsI].equals("-tg")){
				needToTestGui=true;
				++argsI;
			}else if(args[argsI].equals("-c")){
				++argsI;
				if(argsI < args.length){
					String firstSymbol = args[argsI];
					System.out.println("\t"+firstSymbol);
					++argsI;
					if(argsI < args.length) {
						String secondSymbol = args[argsI];
						System.out.println("\t"+secondSymbol);
						TreeSet<String> secondSymbols=null;
						if(comparisons.containsKey(firstSymbol)) {
							secondSymbols=comparisons.get(firstSymbol);
						} else {
							secondSymbols=new TreeSet<String>();
							comparisons.put(firstSymbol, secondSymbols);
						}
						secondSymbols.add(secondSymbol);
					} else {
						needToPrintUsage = true;
					}
					++argsI;
				} else {
					needToPrintUsage = true;					
				}
			}else{
				needToPrintUsage = true;
				++argsI;
			}
		}


		//process the command line arguments
		if(needToLoadFiles) {
			for(String f : filesToLoad) {
				try {
					Ticker ticker = initializeTickerFromMoments(f);
					if(ticker==null){
						die("Main -load "+f+" didn't create ticker", new Exception());
					}
				} catch (Exception e) {
					die("Main -load " + f + " failed.", e);
				}
			}
		}
		if(needToIngestFile){
			for(String fileToIngest : filesToIngest) {
				System.out.print("parsing "+fileToIngest);
				System.out.flush();
				if(!parse(fileToIngest)) {
					System.err.println("failed to parse "+args[1]);
					System.exit(-1);
				}
				System.out.println(" ok");
			}
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			for(Ticker ticker : tickers.values()){
				System.out.println("ticker: "+ticker.symbol+" "+ticker.moments.size());
				int priceCount = 0;
				for(Entry<Long, Ticker.Moment> entry : ticker.moments.entrySet()) {
					if(entry.getValue().price>0)++priceCount;
				}
				System.out.println("price count: "+priceCount);
				ticker.initializeTimeSeries();

				panel.add(ticker.chartPanel);
			}
			for(Ticker ticker : tickers.values()){
				for(Ticker ticker2 : tickers.values()){
					ticker.chartPanel.addSyncedPanel(ticker2.chartPanel);
				}
			}
			JScrollPane scrollPane = new JScrollPane(panel);
			frame.setContentPane(scrollPane);
			frame.pack();
			frame.setVisible(true);
		}
		if(needToTestGui){
			testGui();
		}
		if(needToHexDump){
			hexDump(fileToHexDump);
		}
		if(!comparisons.isEmpty()) {
			System.out.println("Not doing comparisons anymore, might later");
			/*JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			for(Entry<String, TreeSet<String>> entry : comparisons.entrySet()) {
				Ticker ticker = getTicker(entry.getKey());
				for(String symbol : entry.getValue()) {
					Ticker ticker2 = getTicker(symbol);
					String name = ticker.symbol+" vs "+ticker2.symbol;
					TreeMap<Long, Double> comparison = ticker.computeCorrelation(256, ticker2);
					TimeSeries series = new TimeSeries(name);
					for(Entry<Long, Double> comparisonEntry : comparison.entrySet()) {
						Date t = timeStampToDate(comparisonEntry.getKey());
						try {
							series.addOrUpdate(new Second(t), comparisonEntry.getValue());
						} catch(SeriesException e) {
							System.err.println("Error adding to series: "+e);
							Main.fail();
						}
					}
					XYDataset dataset = new TimeSeriesCollection(series);
					JFreeChart chart = ChartFactory.createTimeSeriesChart(name, "time", "corelation", dataset, true, true, false);
					ZoomSyncedChartPanel chartPanel = new ZoomSyncedChartPanel(chart);
					chartPanel.setPreferredSize(new java.awt.Dimension(500,350));
					chartPanel.setMouseZoomable(true,false);
					panel.add(chartPanel);
				}
			}
			JScrollPane scrollPane = new JScrollPane(panel);
			frame.setContentPane(scrollPane);
			frame.pack();
			frame.setVisible(true);*/
		}
		if(needToDumpFile){
			for(Entry<String,TreeSet<String>> entry : dumps.entrySet()){
				String fileToDump = entry.getKey();
				TreeSet<String> symbolsToDump = entry.getValue();
				FileWriter writer = null;
				try {
					writer = new FileWriter(fileToDump);
				} catch (IOException e) {
					die("failed to open file: "+fileToDump,e);
				}
				if(writer==null)die("failed to open file: "+fileToDump,
						new Exception());
				for(String symbol : symbolsToDump){
					Ticker ticker = tickers.get(symbol);
					if(ticker!=null){
						ticker.dumpMoments(fileToDump, writer);
					}
				}
				try {
					writer.close();
				} catch (IOException e) {
					die("failed to close file: "+fileToDump,e);
				}
			}
		}
		if(needToDumpValueFile){
			for(Entry<String,TreeSet<String>> entry : valueDumps.entrySet()){
				String fileToDump = entry.getKey();
				String[] splitFname = fileToDump.split("\\.");
				String prefix = splitFname[0];
				String suffix = "";
				if(splitFname.length>1){
					suffix=splitFname[splitFname.length-1];
					for(int i=1;i<splitFname.length-1;++i){
						prefix+=splitFname[i];
					}
				}

				TreeSet<String> symbolsToDump = entry.getValue();
				int symbolCount = 0;
				for(String symbol : symbolsToDump){
					Ticker ticker = tickers.get(symbol);
					if(ticker!=null){
						String fname=fileToDump;
						if(symbolsToDump.size()>1){
							fname=prefix+"_"+symbolCount;
							if(!"".equals(suffix)){
								fname+="."+suffix;
							}
						}
						ticker.savePriceVector(fname);
					}
				}
			}
		}
		if(needToSaveFile){
			for(Entry<String,String> entry : saves.entrySet()){
				String symbol = entry.getKey();
				String f = entry.getValue();
				Ticker ticker = tickers.get(symbol);
				if(ticker==null) {
					System.err.println("trying to save "+f+" but "+symbol+" is not initialized/found.");
				}else {
					ticker.saveMoments(f);
				}
			}
		}
		if(needToQ){
			q(qQueryLength, qStepSeconds, qDistanceThreshold, qPredictOffset);
		}
		System.out.println("testing dft");
		for(Entry<String,Ticker> entry : tickers.entrySet()){
			System.out.println("  testing dft on "+entry.getKey());
			entry.getValue().computeDFT();
			System.out.println("  done");
		}
		if(needToPrintUsage){
			printUsage(args);
		}
		System.out.println("ok");
	}
	
	public static Ticker initializeTickerFromMoments(String f) throws IOException {
		FileReader reader = new FileReader(f);
		BufferedReader breader = new BufferedReader(reader);
		String symbol = breader.readLine();
		Ticker ticker = getTicker(symbol);
		ticker.loadMoments(breader, f);
		breader.close();
		return ticker;
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

	private static boolean _headerIsDeep = false;
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
				if(messageSize>0){
					if(_headerIsDeep) {
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
						case 'S': if(!readDeepSystemEventMessage(ins, messageSize))fail();
						break;
						case 'D': if(!readDeepSecurityDirectoryMessage(ins, messageSize))fail();
						break;
						case 'H': if(!readDeepTradingStatusMessage(ins, messageSize))fail();
						break;
						case 'O': if(!readDeepOperationalHaltStatusMessage(ins, messageSize))fail();
						break;
						case 'P': if(!readDeepShortSalePriceTestStatusMessage(ins, messageSize))fail();
						break;
						case 'E': if(!readDeepSecurityEventMessage(ins, messageSize))fail();
						break;
						case '8': if(!readDeepBuyPriceLevelUpdateMessage(ins, messageSize))fail();
						break;
						case '5': if(!readDeepSellPriceLevelUpdateMessage(ins, messageSize))fail();
						break;
						case 'T': if(!readDeepTradeReportMessage(ins, messageSize))fail();
						break;
						case 'X': if(!readDeepOfficialPriceMessage(ins, messageSize))fail();
						break;
						case 'B': if(!readDeepTradeBreakMessage(ins, messageSize))fail();
						break;
						case 'A': if(!readDeepAuctionInformationMessage(ins, messageSize))fail();
						break;
						default:
							System.out.println("unknown message type (size["+messageSize+"]): "+displayChar(aByte)+" "+shortHex(aByte)+" "+aByte);
						}
					} else {
						//tops	
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
						case 'S': if(!readDeepSystemEventMessage(ins, messageSize))fail();
						break;
						case 'D': if(!readDeepSecurityDirectoryMessage(ins, messageSize))fail();
						break;
						case 'H': if(!readDeepTradingStatusMessage(ins, messageSize))fail();
						break;
						case 'O': if(!readDeepOperationalHaltStatusMessage(ins, messageSize))fail();
						break;
						case 'P': if(!readDeepShortSalePriceTestStatusMessage(ins, messageSize))fail();
						break;
						case 'Q': if(!readTopsQuoteUpdateMessage(ins, messageSize))fail();
						break;
						case 'T': if(!readDeepTradeReportMessage(ins, messageSize))fail();
						break;
						case 'X': if(!readDeepOfficialPriceMessage(ins, messageSize))fail();
						break;
						case 'B': if(!readDeepTradeBreakMessage(ins, messageSize))fail();
						break;
						case 'A': if(!readDeepAuctionInformationMessage(ins, messageSize))fail();
						break;
						default:
							System.out.println("unknown message type (size["+messageSize+"]): "+displayChar(aByte)+" "+shortHex(aByte)+" "+aByte);
						}

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

	private static long startOfRegularMarketHours=0;
	public static boolean readDeepSystemEventMessage(InputStream s, int messageSize) {
		int computedMessageSize =1;
		int systemEvent = readByte(s);
		++computedMessageSize;
		if(systemEvent<0) return false;
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0) return false;
		if(computedMessageSize!=messageSize)fail();
		switch(systemEvent) {
		case 'O': return true;//start of messages
		case 'S': return true;//start of system hours
		case 'R': startOfRegularMarketHours=t; return true;//start of regular market hours
		case 'M': marketHours.put(startOfRegularMarketHours,t);return true;//end of regular market hours
		case 'E': return true;//end of system hours
		case 'C': return true;//end of messages
		}

		return false;
	}

	public static boolean readDeepSecurityDirectoryMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepTradingStatusMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepOperationalHaltStatusMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepShortSalePriceTestStatusMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepSecurityEventMessage(InputStream s, int messageSize) {
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

	public static boolean readTopsQuoteUpdateMessage(InputStream s, int messageSize) {
		int computedMessageSize=1;
		int flags = readByte(s);
		++computedMessageSize;
		if(flags<0)fail();
		long t = readTimeStamp(s);
		computedMessageSize+=8;
		if(t<0)fail();
		String symbol = readString(s,8);
		computedMessageSize+=8;
		if(null==symbol)fail();
		int bidSize = readInt(s);
		computedMessageSize+=4;
		if(bidSize<0)fail();
		long bidPrice = readPrice(s);
		computedMessageSize+=8;
		if(bidPrice<0)fail();
		long askPrice = readPrice(s);
		computedMessageSize+=8;
		if(askPrice<0)fail();
		int askSize = readInt(s);
		computedMessageSize+=4;
		if(askSize<0)fail();
		if(computedMessageSize!=messageSize)fail();
		Ticker ticker = getTicker(symbol);
		if(ticker!=null){
			ticker.addQuote(t, bidPrice, bidSize, askPrice, askSize);
		}
		return true;
	}

	public static boolean readDeepBuyPriceLevelUpdateMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepSellPriceLevelUpdateMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepTradeReportMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepOfficialPriceMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepTradeBreakMessage(InputStream s, int messageSize) {
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

	public static boolean readDeepAuctionInformationMessage(InputStream s, int messageSize) {
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
		int[] deepHeader = {1,0,4,0x80};
		int[] topsHeader = {1,0,3,0x80};
		int rb=0;
		boolean readingTops=true;
		boolean readingDeep=true;
		do {
			rb = readByte(s);
			if((readingDeep && rb==deepHeader[matchIndex]) ||
					(readingTops && rb==topsHeader[matchIndex])){
				if(readingDeep && rb!=deepHeader[matchIndex])
					readingDeep=false;
				if(readingTops && rb!=topsHeader[matchIndex])
					readingTops=false;
				++matchIndex;
				if((readingDeep && matchIndex>=deepHeader.length) ||
						readingTops && matchIndex>=topsHeader.length){
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
					_headerIsDeep=readingDeep;
					return messageCount;
				} 
			} else {
				matchIndex=0;
				readingTops=true;
				readingDeep=true;
				if(rb==topsHeader[matchIndex] || rb==deepHeader[matchIndex]){
					++matchIndex;
				}
			}
		}while(rb>=0);
		return rb;
	}

	public static double nsToExcelDays(long ns) {
		return nanoSecondsToDays(ns)+25569;
	}

	public static double nanoSecondsToDays(long nanoSeconds){
		long milliSeconds=nanoSeconds/(1000000);
		double seconds = (double)(milliSeconds)/1000d;
		return seconds/(60*60*24);
	}

	public static void q(int queryLength, int stepSize, double distanceThreshold, int predictOffset){
		for(Ticker ticker : tickers.values()){
			ticker.q(queryLength, stepSize, distanceThreshold, predictOffset);
		}
	}
}
