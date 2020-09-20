import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;

public class Ticker {

	public class Moment {
		public Moment() {}
		public Moment(Moment moment) {
			price=moment.price;
			size=moment.size;
		}
		public Moment(long price, int size) {
			this.price=price;
			this.size=size;
		}
		long price=0;
		int size=0;
		long bidPrice=0;
		int bidSize=0;
		long askPrice=0;
		int askSize=0;
		Map<Long,Integer> sells = new TreeMap<Long,Integer>();
		Map<Long,Integer> buys = new TreeMap<Long,Integer>();
		boolean isEmpty() {
			return price==0 &&
					size==0 &&
					bidPrice==0 &&
					askPrice==0 &&
					sells.size()==0 &&
					buys.size()==0;
		}
	}

	public static void main(String[] args) {
		int queryLength=256;
		int stepSize=10;
		double distanceThreshold=15;
		int predictOffset=60;
		System.out.println("Ticker.main");
		System.out.println("\treading files");
		PriceVector msftPv = readPriceVector("external/testdata/msftvs.csv");
		PriceVector aaplPv = readPriceVector("external/testdata/aaplvs.csv");
		PriceVector googlPv = readPriceVector("external/testdata/googlvs.csv");
		System.out.println("\tq");
		for(int dt = 1;dt <= distanceThreshold;++dt){
			System.out.println("distance threshold (closest n):"+dt);
			q(queryLength,stepSize,dt,predictOffset,msftPv);
			q(queryLength,stepSize,dt,predictOffset,aaplPv);
			q(queryLength,stepSize,dt,predictOffset,googlPv);
		}
		System.out.println("\tdone");

	}




	public String symbol;
	public TreeMap<Long,Moment> moments = new TreeMap<Long,Moment>();

	public TreeMap<Long,Moment> representativeMoments = new TreeMap<Long,Moment>();
	public TreeMap<Long,Double> representativeDeltas = new TreeMap<Long, Double>();
	public long representativeMomentDuration = 0;

	public Ticker(String symbol) {
		this.symbol=symbol;
	}

	public void saveMoments(String filename) {
		if(filename==null)Main.die("Ticker("+symbol+").saveMoments null filename", new Exception());;
		if(moments==null)Main.die("Ticker("+symbol+").saveMoments null moments", new Exception());;
		try {
			FileWriter fw = new FileWriter(filename);
			fw.write(symbol+"\n");
			fw.write(""+moments.size()+"\n");
			for(Entry<Long, Moment> entry : moments.entrySet()) {
				fw.write(""+entry.getKey()+"\n");
				Moment m = entry.getValue();
				fw.write(""+m.price+"\n");
				fw.write(""+m.size+"\n");
				fw.write(""+m.bidPrice+"\n");
				fw.write(""+m.bidSize+"\n");
				fw.write(""+m.askPrice+"\n");
				fw.write(""+m.askSize+"\n");
				if(m.buys==null) {
					fw.write("0\n");
				}else {
					fw.write(""+m.buys.size()+"\n");
					for(Entry<Long, Integer> buyEntry : m.buys.entrySet()) {
						fw.write(""+buyEntry.getKey()+", "+buyEntry.getValue()+"\n");
					}
				}
				if(m.sells==null) {
					fw.write("0\n");
				}else {
					fw.write(""+m.sells.size()+"\n");
					for(Entry<Long, Integer> sellEntry : m.sells.entrySet()) {
						fw.write(""+sellEntry.getKey()+", "+sellEntry.getValue()+"\n");
					}
				}
			}
			fw.close();
		} catch (Exception e) {
			Main.die("Ticker("+symbol+").saveMoments " + filename + " failed.", e);
		}
	}

	public void loadMoments(BufferedReader breader, String filename) {
		if(moments==null) {
			moments=new TreeMap<Long,Moment>();
		}else {
			moments.clear();
		}
		int i=0;
		try {
			String s = breader.readLine();
			if(s==null) {
				breader.close();
				Main.die("Ticker.loadMoments " + filename + " no entries past symbol", new Exception());
			}
			int momentCount = Integer.parseInt(s);
			for(s=breader.readLine();s!=null && i<momentCount;) {			
				Long t = Long.parseLong(s);
				Moment m = new Moment();
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before price", new Exception());
				}
				m.price=Long.parseLong(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before size", new Exception());
				}
				m.size=Integer.parseInt(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before bidPrice", new Exception());
				}
				m.bidPrice=Long.parseLong(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before bidSize", new Exception());
				}
				m.bidSize=Integer.parseInt(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before askPrice", new Exception());
				}
				m.askPrice=Long.parseLong(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before askSize", new Exception());
				}
				m.askSize=Integer.parseInt(s);
				s=breader.readLine();
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before sells size", new Exception());
				}
				int sellsSize = Integer.parseInt(s);
				Map<Long,Integer> sells = m.sells;
				Map<Long,Integer> buys = m.buys;
				int sellsI=0;
				for(s=breader.readLine();sellsI<sellsSize;s=breader.readLine()) {
					if(s==null) {
						breader.close();
						Main.die("Ticker.loadMoments " + filename + " moment cut off before sells pair", new Exception());
					}
					String[] ss = s.split(",");
					long v = Long.parseLong(ss[0]);
					int sz = Integer.parseInt(ss[1].trim());
					sells.put(v, sz);
					++sellsI;
				}
				if(s==null) {
					breader.close();
					Main.die("Ticker.loadMoments " + filename + " moment cut off before buys size", new Exception());
				}
				int buysSize = Integer.parseInt(s);
				int buysI=0;
				for(s=breader.readLine();buysI<buysSize;s=breader.readLine()) {
					if(s==null) {
						breader.close();
						Main.die("Ticker.loadMoments " + filename + " moment cut off before buys pair", new Exception());
					}
					String[] ss = s.split(",");
					long v = Long.parseLong(ss[0]);
					int sz = Integer.parseInt(ss[1].trim());
					buys.put(v, sz);
					++buysI;
				}
				moments.put(t, m);
				++i;
			}
			if(i!=moments.size()) {
				System.err.println("Ticker.loadMoments "+filename+" read length is not the same as read value count: " 
						+ moments.size() + ":" + i);
			}
			trimMoments();
			updateMarketHours();
		} catch (Exception e) {
			Main.die("Ticker.loadMoments " + filename + " failed at moment "+i+".", e);
		}
	}

	/**
	 * Remove leading and trailing empty moments
	 */
	void trimMoments() {
		if(moments==null)return;
		while(!moments.isEmpty() && moments.firstEntry().getValue().isEmpty()) {
			moments.remove(moments.firstKey());
		}
		while(!moments.isEmpty() && moments.lastEntry().getValue().isEmpty()) {
			moments.remove(moments.lastKey());
		}
	}

	public void updateMarketHours() {
		if(moments==null)return;
		if(moments.isEmpty())return;
		long start = moments.firstKey();
		long end = moments.lastKey();
		Entry<Long,Long> entry = Main.marketHours.floorEntry(start);
		if(entry==null || entry.getValue()<start) {
			//nothing or it comes before, so check trailing
			entry = Main.marketHours.higherEntry(start);
			if(entry==null || entry.getKey()>end) {
				//nothing after, so add
				Main.marketHours.put(start, end);
			} else if(entry.getKey()>end){
				//after is completely after, so add
				Main.marketHours.put(start, end);				
			} else {
				//overlaps with after
				long tToDelete = entry.getKey();
				end = Math.max(end, entry.getValue());
				Main.marketHours.remove(tToDelete);
				Main.marketHours.put(start, end);
			}
		} else {
			//overlaps
			end = Math.max(end, entry.getValue());
			start = entry.getKey();
			Main.marketHours.replace(start, end);
		}
	}


	public Moment getMoment(long time) {
		if(moments.containsKey(time)) {
			return moments.get(time);
		}
		Moment moment = new Moment();
		moments.put(time, moment);
		return moment;
	}

	public static void showDoublePanel(String title, double[] values, long startTime, int step){
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		frame.add(panel);

		TimeSeries timeSeries = new TimeSeries(title);
		final long billion = 1000000000;
		for(int i=0;i<values.length;i+=step) {
			Date t = Main.timeStampToDate(startTime + (i*billion));
			try {
				timeSeries.addOrUpdate(new Second(t), values[i]);
			} catch(SeriesException e) {
				System.err.println("Error adding to timeSeries: "+e);
				Main.fail();
			}
		}
		TimeSeriesCollection timeSeriesCollection = new TimeSeriesCollection(timeSeries);
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "time", "price", timeSeriesCollection, true, true, false);
		ZoomSyncedChartPanel chartPanel = new ZoomSyncedChartPanel(chart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500,350));
		chartPanel.setMouseZoomable(true,false);
		panel.add(chartPanel);

		JScrollPane scrollPane = new JScrollPane(panel);
		frame.setContentPane(scrollPane);
		frame.pack();
		frame.setVisible(true);

	}

	public static void showLongPanel(String title, long[] values, long startTime, int step){
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		frame.add(panel);

		TimeSeries timeSeries = new TimeSeries(title);
		final long billion = 1000000000;
		for(int i=0;i<values.length;i+=step) {
			Date t = Main.timeStampToDate(startTime + (i*billion));
			try {
				timeSeries.addOrUpdate(new Second(t), values[i]);
			} catch(SeriesException e) {
				System.err.println("Error adding to timeSeries: "+e);
				Main.fail();
			}
		}
		TimeSeriesCollection timeSeriesCollection = new TimeSeriesCollection(timeSeries);
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "time", "price", timeSeriesCollection, true, true, false);
		ZoomSyncedChartPanel chartPanel = new ZoomSyncedChartPanel(chart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500,350));
		chartPanel.setMouseZoomable(true,false);
		panel.add(chartPanel);

		JScrollPane scrollPane = new JScrollPane(panel);
		frame.setContentPane(scrollPane);
		frame.pack();
		frame.setVisible(true);

	}

	public void spoutStats(String msg, double[] v){
		if(v==null)
		{
			System.out.println(symbol+" "+msg+" <null>.");
			return;
		}
		double minV = v[0];
		double maxV = v[0];
		for(int i=1;i<v.length;++i){
			if(v[i]>maxV)maxV=v[i];
			else if(v[i]<minV)minV=v[i];
		}
		double d = maxV-minV;
		System.out.println(symbol+" "+msg+" max:"+maxV+", min:"+minV+", d:"+d+", n:"+v.length);
	}

	public void computeDFT(){
		if(moments.isEmpty())return;
		PriceVector priceVector = computePriceVector();
		double[] prices = new double[priceVector.prices.length];
		for(int i=0;i<priceVector.prices.length;++i){
			prices[i]=(double)priceVector.prices[i];
		}
		double[] normalizedPrices = normalize(prices);
		showDoublePanel(symbol+" prices", normalizedPrices, priceVector.startTime, 1);
		int qsz = 256;
		double[] Q = new double[qsz];
		for(int i=0;i<Q.length;++i) Q[i]=normalizedPrices[7200+i+(5*qsz)];


		double[] D = MASS.mass(Q, normalizedPrices);
		showDoublePanel(symbol+" D", D, priceVector.startTime, 1);

	}

	public void computeAltDFT(){
		if(moments.isEmpty())return;
		PriceVector priceVector = computePriceVector();
		double[] prices = new double[priceVector.prices.length];
		for(int i=0;i<priceVector.prices.length;++i){
			prices[i]=(double)priceVector.prices[i];
		}
		spoutStats("prices",prices);
		//double[] normalizePrices = normalize(computePriceVector());
		//spoutStats("normalizePrices",normalizePrices);
		showDoublePanel(symbol+" price", prices, priceVector.startTime, 1);
		FFTbase fft = new FFTbase();
		boolean ok= fft.adjustedFft(prices, null,
				true);
		if(!ok)System.out.println("fft computation failed for "+symbol);
		showDoublePanel(symbol+" DFT", fft.xReal, priceVector.startTime, 1);

	}

	public static double[] fftVector(double[] v)
	{
		if(v==null)return null;
		double[] out = new double[2*v.length];
		for(int i=0;i<v.length;++i){
			out[i]=v[i];
		}
		return out;	
	}

	public static double[] normalize(double[] v)
	{
		if(v==null)return null;
		double minV = v[0];
		double maxV = v[0];
		for(int i=1;i<v.length;++i){
			if(v[i]>maxV)maxV=v[i];
			else if(v[i]<minV)minV=v[i];
		}
		double d = maxV-minV;
		double[] out = new double[v.length];
		for(int i=0;i<v.length;++i){
			out[i]=(v[i]-minV)/d;
		}
		return out;	
	}

	public static double[] computeContainedInverse(double[] v)
	{
		if(v==null)return null;
		double[] out = new double[v.length];
		for(int i=0;i<v.length;++i){
			if(v[i]>0)
				out[i]=1/1+v[i];
			else
				out[i]=1/-1+v[i];
		}
		return out;
	}

	public static class PriceVector {
		public String symbol=null;
		public long[] prices=null;
		public long[] bids=null;
		public long[] asks=null;
		public long startTime=0L;
		public PriceVector(String symbol, long startTime, long[] prices, long[] bids, long[] asks){
			this.symbol=symbol;
			this.startTime=startTime;
			this.prices=prices;
			this.bids=bids;
			this.asks=asks;

		}
	}

	public PriceVector computePriceVector(){
		//on the first market interval
		Entry<Long,Long> entry = Main.marketHours.firstEntry();
		if(entry==null)return null;
		return computePriceVector(entry.getKey(), entry.getValue());
	}

	public PriceVector computePriceVector(long hoursStart, long hoursEnd){
		if(moments.isEmpty())return null;

		SortedMap<Long, Moment> tradingMoments = moments.subMap(hoursStart, hoursEnd);
		long st = tradingMoments.firstKey();
		long et = tradingMoments.lastKey();
		long totalDuration = et - st;
		//total duration is in nanoseconds
		final long billion = 1000000000L;
		int totalDurationSeconds = (int)(totalDuration / billion);
		if(totalDurationSeconds%2==1)++totalDurationSeconds;
		//made even and doubled so we can use this for FFTs
		long prices[] = new long[totalDurationSeconds];//initialized to 0
		long pricesV[] = new long[totalDurationSeconds];
		long bids[] = new long[totalDurationSeconds];
		long bidsV[] = new long[totalDurationSeconds];
		long asks[] = new long[totalDurationSeconds];
		long asksV[] = new long[totalDurationSeconds];

		for(Entry<Long, Moment> momentEntry : tradingMoments.entrySet()){
			long t = momentEntry.getKey();
			Moment m = momentEntry.getValue();
			int index = (int)((t - st)/billion);
			if(index>=prices.length)break;
			prices[index]=m.price;
			pricesV[index]+=m.size;
			bids[index]=m.bidPrice;
			bidsV[index]+=m.bidSize;
			asks[index]=m.askPrice;
			asksV[index]+=m.askSize;
		}
		long previousPrice=0;
		long previousBid=0;
		long previousAsk=0;
		for(int i=0;i<totalDurationSeconds;++i){
			if(previousPrice==0&&prices[i]!=0)previousPrice=prices[i];
			if(previousBid==0&&bids[i]!=0)previousBid=bids[i];
			if(previousAsk==0&&asks[i]!=0)previousAsk=asks[i];
			if(previousPrice!=0 && previousBid!=0 && previousAsk!=0)break;
		}		
		for(int i=0;i<totalDurationSeconds;++i){
			if(prices[i]==0) prices[i]=previousPrice;
			else previousPrice = prices[i];
			if(bids[i]==0) bids[i]=previousBid;
			else previousBid = bids[i];
			if(asks[i]==0) asks[i]=previousAsk;
			else previousAsk = asks[i];
		}
		return new PriceVector(symbol,st,prices,bids,asks);
	}

	public void savePriceVector(String filename)
	{
		savePriceVector(computePriceVector(), filename);
	}

	public static void savePriceVector(PriceVector priceVector, String filename)
	{
		if(priceVector==null)Main.die("Ticker.savePriceVector null priceVector", new Exception());
		if(filename==null)Main.die("Ticker("+priceVector.symbol+").savePriceVector null filename", new Exception());;
		try {
			FileWriter fw = new FileWriter(filename);
			fw.write(priceVector.symbol+"\n");
			fw.write(""+priceVector.startTime+"\n");
			fw.write(""+priceVector.prices.length+"\n");
			for(int i=0;i<priceVector.prices.length;++i) {
				fw.write(""+priceVector.prices[i]+
						", "+priceVector.bids[i]+
						", "+priceVector.asks[i]+"\n");
			}
			fw.close();
		} catch (Exception e) {
			Main.die("Ticker("+priceVector.symbol+").savePriceVector " + filename + " failed.", e);
		}
	}


	public static PriceVector readPriceVector(String filename)
	{
		if(filename==null)Main.die("Ticker.readPriceVector null filename", new Exception());
		try {
			FileReader reader = new FileReader(filename);
			BufferedReader breader = new BufferedReader(reader);
			String symbol = breader.readLine();
			if(symbol==null) {
				breader.close();
				Main.die("Ticker.readPriceVector " + filename + " empty file", new Exception());
			}
			String s = breader.readLine();
			if(s==null) {
				breader.close();
				Main.die("Ticker.readPriceVector " + filename + " no entries past symbol", new Exception());
			}
			long t = Long.parseLong(s);
			s = breader.readLine();
			if(s==null) {
				breader.close();
				Main.die("Ticker.readPriceVector " + filename + " no entries past t", new Exception());
			}
			long[] prices= new long[Integer.parseInt(s)];
			long[] bids= new long[Integer.parseInt(s)];
			long[] asks= new long[Integer.parseInt(s)];
			int i=0;
			for(s=breader.readLine();s!=null && i<prices.length;s=breader.readLine()) {
				String[] vs = s.split(",");
				if(vs.length!=3){
					Main.die("Ticker("+symbol+").readPriceVector failed split at line "+(i+3), new Exception());
				}
				prices[i]=Long.parseLong(vs[0]);
				bids[i]=Long.parseLong(vs[0]);
				asks[i]=Long.parseLong(vs[0]);
				++i;
			}
			if(i!=prices.length) {
				System.err.println("Ticker.readPriceVector "+filename+" read length is not the same as read value count: " 
						+ prices.length + ":" + i);
			}
			breader.close();
			return new PriceVector(symbol,t,prices,bids,asks);
		} catch (Exception e) {
			Main.die("Ticker.readPriceVector " + filename + " failed.", e);
			return null;
		}

	}


	public static double computeCorrelation(
			Entry<Long, Double> delta, 
			Entry<Long, Double> otherDelta) {
		if(delta==null)return 0;
		if(otherDelta==null)return 0;
		double deltaV = delta.getValue();
		double otherDeltaV = otherDelta.getValue();		

		double dv=deltaV-otherDeltaV;
		double cv=1/(1+Math.abs(dv));

		if(deltaV>=0){
			if(otherDeltaV>=0){
				//positively correlated
				return cv;
			}else{
				//negatively correlated
				return -cv;
			}
		}else{
			if(otherDeltaV<0){
				//positively correlated
				return cv;
			}else{
				//negatively correlated
				return -cv;
			}
		}
	}



	public Moment getRepresentativeMoment(long time) {
		if(representativeMoments==null ||representativeMoments.isEmpty()) return null;
		Entry<Long, Moment> entry = representativeMoments.floorEntry(time);
		if(entry.getKey() < time-representativeMomentDuration) {
			return null;
		}
		return entry.getValue();
	}

	public int computeRepresentativeMoments(long duration) {
		if(duration==representativeMomentDuration) {
			return representativeMoments.size();
		}
		representativeMomentDuration=duration;
		representativeMoments.clear();
		representativeDeltas.clear();
		Entry<Long,Moment> foot = null;
		long priceSum=0;
		int priceCount=0;
		Moment previousMoment = null;
		Moment representativeMoment = null;
		for(Entry<Long, Moment> entry : moments.entrySet()){
			if(foot==null || foot.getKey()+duration <= entry.getKey()){
				if(foot!=null){
					if(priceCount==0)priceCount=1;
					representativeMoment = new Moment(priceSum/priceCount, priceCount);
					representativeMoments.put(foot.getKey(), representativeMoment);
					if(previousMoment!=null){
						//Moment deltaMoment = 
						//		new Moment(representativeMoment.price-previousMoment.price,
						//				representativeMoment.size-previousMoment.size);
						Double d = 0==previousMoment.price?0:(double)(representativeMoment.price/previousMoment.price);
						representativeDeltas.put(foot.getKey(), d);
					}
				}
				previousMoment=representativeMoment;
				foot=entry;
				priceSum=entry.getValue().price;
				priceCount=entry.getValue().size;
			}else{
				priceSum+=entry.getValue().price;
				priceCount+=entry.getValue().size;
			}
		}
		if(foot!=null){
			if(priceCount==0)priceCount=1;
			representativeMoment = new Moment(priceSum/priceCount, priceCount);
			representativeMoments.put(foot.getKey(), representativeMoment);
			if(previousMoment!=null){
				Moment deltaMoment = 
						new Moment(representativeMoment.price-previousMoment.price,
								representativeMoment.size-previousMoment.size);
				Double d = (double)(deltaMoment.price/1000);
				representativeDeltas.put(foot.getKey(), d);
			}
		}

		return representativeMoments.size();
	}

	//greatest key less than time
	public Moment lowerMoment(long time) {
		Entry<Long, Moment> entry = moments.lowerEntry(time);
		if(entry==null)return null;
		return entry.getValue();
	}

	//least key greater than time
	public Moment higherMoment(long time) {
		Entry<Long, Moment> entry = moments.higherEntry(time);
		if(entry==null)return null;
		return entry.getValue();
	}

	//greatest key less than or equal to time
	public Moment floorMoment(long time) {
		Entry<Long, Moment> entry = moments.floorEntry(time);
		if(entry==null)return null;
		return entry.getValue();
	}

	//least key greater than or equal to time
	public Moment ceilingMoment(long time) {
		Entry<Long, Moment> entry = moments.ceilingEntry(time);
		if(entry==null)return null;
		return entry.getValue();		
	}

	public void addSell(long time, long value, int volume) {
		Moment moment = getMoment(time);
		moment.sells.put(value,volume);
	}

	public void addQuote(long time, long bidPrice, int bidSize, long askPrice, int askSize) {
		Moment moment = getMoment(time);
		moment.bidPrice=bidPrice;
		moment.bidSize=bidSize;
		moment.askPrice=askPrice;
		moment.askSize=askSize;
	}

	public void addBuy(long time, long value, int volume) {
		Moment moment = getMoment(time);
		moment.buys.put(value,volume);		
	}

	public void setPrice(long time, long price) {
		Moment moment = getMoment(time);
		moment.price = price;
	}
	public void setPrice(long time, long price, int size) {
		Moment moment = getMoment(time);
		moment.price = price;
		moment.size = size;
	}
	public void die(String message, Exception e){
		Main.die(message, e);;
	}
	public void dumpMoments(String filename, FileWriter writer){
		int maxBuys = 0;
		int maxSells = 0;
		for(Entry<Long, Moment> entry : moments.entrySet()){
			if(entry.getValue().buys!=null &&
					entry.getValue().buys.size() > maxBuys)
				maxBuys=entry.getValue().buys.size();
			if(entry.getValue().sells!=null &&
					entry.getValue().sells.size() > maxSells)
				maxSells=entry.getValue().sells.size();
		}

		try {
			writer.write("time");
		} catch (IOException e) {
			die("dumpMoments: failed to write time header "+filename,e);
		}
		int i=0;
		for(i=0;i<maxBuys;++i){
			try {writer.write(", buy_"+i+", buy_"+i+" vol.");}
			catch (IOException e) {die("dumpMoments: failed to write buy header "+filename,e);}
		}
		try {writer.write(", bid, bid vol.");}
		catch (IOException e) {die("dumpMoments: failed to write bid header "+filename,e);}		
		try {writer.write(", price, price vol.");}
		catch (IOException e) {die("dumpMoments: failed to write price header "+filename,e);}
		try {writer.write(", ask, ask vol.");}
		catch (IOException e) {die("dumpMoments: failed to write ask header "+filename,e);}
		for(i=0;i<maxSells;++i){
			try {writer.write(", sell_"+i+", sell_"+i+" vol.");}
			catch (IOException e) {die("dumpMoments: failed to write sell header "+filename,e);}
		}
		try {writer.write("\n");}
		catch (IOException e) {die("dumpMoments: failed to write a newline "+filename,e);}
		for(Entry<Long, Moment> entry : moments.entrySet()){
			try {writer.write(""+Main.nsToExcelDays(entry.getKey()));}
			catch (IOException e) {die("dumpMoments: failed to write time value "+filename,e);}
			i=0;
			if(entry.getValue().buys!=null){
				for(Entry<Long, Integer> buyEntry : entry.getValue().buys.entrySet()){
					try {writer.write(", "+((double)buyEntry.getKey())/10000d);}
					catch (IOException e) {die("dumpMoments: failed to write buy value "+filename,e);}
					try {writer.write(", "+buyEntry.getValue());}
					catch (IOException e) {die("dumpMoments: failed to write buy volume "+filename,e);}
					++i;
				}
			}
			for(;i<maxBuys;++i){
				try {writer.write(", , ");}
				catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}
			}
			if(entry.getValue().bidPrice>0){
				try {writer.write(", "+((double)entry.getValue().bidPrice)/10000d);}
				catch (IOException e) {die("dumpMoments: failed to write bid price "+filename,e);}
				try {writer.write(", "+entry.getValue().bidSize);}
				catch (IOException e) {die("dumpMoments: failed to write bid volume "+filename,e);}
			} else {
				try {writer.write(", , ");}
				catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}				
			}
			if(entry.getValue().price>0){
				try {writer.write(", "+((double)entry.getValue().price)/10000d);}
				catch (IOException e) {die("dumpMoments: failed to write price value "+filename,e);}
				try {writer.write(", "+entry.getValue().size);}
				catch (IOException e) {die("dumpMoments: failed to write price volume "+filename,e);}
			} else {
				try {writer.write(", , ");}
				catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}				
			}
			if(entry.getValue().askPrice>0){
				try {writer.write(", "+((double)entry.getValue().askPrice)/10000d);}
				catch (IOException e) {die("dumpMoments: failed to write ask price "+filename,e);}
				try {writer.write(", "+entry.getValue().askSize);}
				catch (IOException e) {die("dumpMoments: failed to write ask volume "+filename,e);}
			} else {
				try {writer.write(", , ");}
				catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}				
			}
			i=0;
			if(entry.getValue().sells!=null){
				for(Entry<Long, Integer> sellEntry : entry.getValue().sells.entrySet()){
					try {writer.write(", "+((double)sellEntry.getKey())/10000d);}
					catch (IOException e) {die("dumpMoments: failed to write sell value "+filename,e);}
					try {writer.write(", "+sellEntry.getValue());}
					catch (IOException e) {die("dumpMoments: failed to write sell volume "+filename,e);}
				}
				++i;
			}
			try {writer.write("\n");}
			catch (IOException e) {die("dumpMoments: failed to write a newline "+filename,e);}
		}		
	}

	public void q(int queryLength, int stepSize, double distanceThreshold, int predictOffset){
		//see how predictable the day is to itself
		//sliding window
		//every n seconds (10, for now)
		//define Q
		//compute dist
		//check for dist < threshold (3, for now)
		//determine the mean & sd at some future time t (60 seconds, for now)
		//also try nearest neighbor
		//compute profit?
		if(moments.isEmpty())return;
		System.out.println("q for "+symbol);
		PriceVector priceVector = computePriceVector();
		q(queryLength,stepSize,distanceThreshold,predictOffset,priceVector);
	}
	static public void q(int queryLength, int stepSize, double distanceThreshold, int predictOffset, PriceVector priceVector){

		double[] prices = new double[priceVector.prices.length];
		for(int i=0;i<priceVector.prices.length;++i){
			prices[i]=(double)priceVector.prices[i];
		}
		double[] T = normalize(prices);
		double[] Q = new double[queryLength];
		int firstT = 0;//need to scan for this
		int successes = 0;
		int failures = 0;
		int unknown = 0;
		double sumError = 0;
		double totalCount = 0;
		for(int t=firstT;t<T.length-Q.length-predictOffset;t+=stepSize){
			for(int i=0;i<Q.length;++i) Q[i]=T[i];
			double[] D = MASS.mass(Q, T);
			double sum = 0;
			double sum2 = 0;
			double count = 0 ;
			TreeMap<Double, Integer> bestMatches = new TreeMap<Double, Integer>();
			TreeMap<Integer, Double> bestMatchCrossRef = new TreeMap<Integer, Double>();
			//TODO need to suppress local similar matches
			for(int dt = firstT; dt<T.length-Q.length-predictOffset; ++dt){
				if((dt + queryLength < t || t + queryLength < dt)){
					if(bestMatches.size()<distanceThreshold){
						//this needs to make sure local things don't get selected
						bestMatches.put(D[dt], dt);
						bestMatchCrossRef.put(dt, D[dt]);
					} else {
						if(D[dt] < bestMatches.lastKey()){
							//get the entry less than or equal to (won't be equal to)
							Entry<Integer, Double> entry = bestMatchCrossRef.floorEntry(dt);
							int clobberT= -1;
							if(entry != null){
								//close enough to clobber
								if(dt-entry.getKey()<queryLength){
									if(D[dt] < entry.getValue()){
										//clobber
										clobberT=entry.getKey();
									}
								}
							}
							entry=bestMatchCrossRef.ceilingEntry(dt);
							if(entry != null){
								//close enough to clobber
								if(entry.getKey()-dt<queryLength){
									if(D[dt] < entry.getValue()){
										if(clobberT == -1){
											clobberT=entry.getKey();
										}else{
											if(D[clobberT] < entry.getValue()){
												clobberT=entry.getKey();
											}
										}
									}
								}
							}
							if(clobberT>-1){
								if(D[dt]<bestMatches.lastKey()){
									if(bestMatches.size()==distanceThreshold){
										bestMatchCrossRef.remove(bestMatches.lastEntry().getValue());
										bestMatches.remove(bestMatches.lastKey());
									}
									bestMatches.put(D[dt], dt);
									bestMatchCrossRef.put(dt, D[dt]);
								}
							} else {
								bestMatches.remove(D[dt]);
								bestMatchCrossRef.remove(clobberT);
								bestMatches.put(D[dt], dt);
								bestMatchCrossRef.put(dt, D[dt]);
							}
						}
					}
				}
			}
			for(Entry<Double, Integer> entry : bestMatches.entrySet()){
				int dt = entry.getValue();
				double predict = T[dt+queryLength+predictOffset];
				sum += predict;
				sum2 += predict*predict;
				++count;
			}
			if(count > 0){//TODO make an argument
				double actual = T[t+queryLength+predictOffset];
				double mean = sum/count;
				double error = Math.abs(actual-mean);
				++totalCount;
				sumError+=error;
				double variance = sum2-((sum*sum)/count);
				double standardDeviation = Math.sqrt(Math.abs(variance));
				if((mean-standardDeviation) < actual && actual < (mean+standardDeviation)){
					++successes;
				} else {
					++failures;
				}
			} else {
				++unknown;
			}
		}
		double averageError = sumError/totalCount;
		System.out.println("Q: "+priceVector.symbol+" success: "+successes+
				"  failures: "+failures+"  unknown: "+unknown+
				"  average error: "+averageError);

	}


	//----------- time series -----------
	public XYDataset dataset = null;
	public JFreeChart chart = null;
	public ZoomSyncedChartPanel chartPanel = null;
	public TimeSeries series = null;


	void initializeTimeSeries() {
		series = new TimeSeries(symbol);
		for(Entry<Long, Moment> entry : moments.entrySet()) {
			if(entry.getValue().price>0) {
				Date t = Main.timeStampToDate(entry.getKey());
				try {
					series.addOrUpdate(new Second(t), entry.getValue().price);
				} catch(SeriesException e) {
					System.err.println("Error adding to series: "+e);
					Main.fail();
				}
			}
		}
		dataset = new TimeSeriesCollection(series);
		chart = ChartFactory.createTimeSeriesChart(symbol, "time", "price", dataset, true, true, false);
		chartPanel = new ZoomSyncedChartPanel(chart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500,350));
		chartPanel.setMouseZoomable(true,false);
	}

}
