import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
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
	}

	public String symbol;
	public TreeMap<Long,Moment> moments = new TreeMap<Long,Moment>();

	public TreeMap<Long,Moment> representativeMoments = new TreeMap<Long,Moment>();
	public TreeMap<Long,Double> representativeDeltas = new TreeMap<Long, Double>();
	public long representativeMomentDuration = 0;

	public Ticker(String symbol) {
		this.symbol=symbol;
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
		double[] prices = priceVector.v;
		double[] normalizedPrices = normalize(prices);
		showDoublePanel(symbol+" prices", normalizedPrices, priceVector.t, 1);
		int qsz = 256;
		double[] Q = new double[qsz];
		for(int i=0;i<Q.length;++i) Q[i]=normalizedPrices[i+(5*qsz)];
		
		
		double[] D = MASS.mass(Q, normalizedPrices);
		showDoublePanel(symbol+" D", D, priceVector.t, 1);
		
	}

	public void computeAltDFT(){
		if(moments.isEmpty())return;
		PriceVector priceVector = computePriceVector();
		double[] prices = priceVector.v;
		spoutStats("prices",prices);
		//double[] normalizePrices = normalize(computePriceVector());
		//spoutStats("normalizePrices",normalizePrices);
		showDoublePanel(symbol+" price", prices, priceVector.t, 1);
		FFTbase fft = new FFTbase();
		boolean ok= fft.adjustedFft(prices, null,
				true);
		if(!ok)System.out.println("fft computation failed for "+symbol);
		showDoublePanel(symbol+" DFT", fft.xReal, priceVector.t, 1);

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

	public class PriceVector {
		public double[] v=null;
		public long t=0L;
		public PriceVector(long t, double[] v){
			this.t=t;
			this.v=v;
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
		double prices[] = new double[totalDurationSeconds];//initialized to 0
		int previousIndex = 0;
		long startT = st;
		int currentVolume = 0;
		double currentSummedPrice = 0;
		int transitions=0;
		for(Entry<Long, Moment> momentEntry : tradingMoments.entrySet()){
			long t = momentEntry.getKey();
			if(st<=t && t<et){
				Moment m = momentEntry.getValue();
				int index = (int)((t - startT)/billion);
				if(m.size > 0 && m.price > 0){
					if(index > previousIndex){
						++transitions;
						//fill in the accumulated price for the previous bin
						if(currentVolume==0){
							prices[previousIndex]=0;
						} else {
							prices[previousIndex]=currentSummedPrice/(double)currentVolume;
						}
						//fill gaps if necessary (linear interpolation for now)
						double gap = index - previousIndex;
						for(double i = 1; i<gap; ++i){
							prices[previousIndex+(int)i]=
									((gap-i)/gap)*prices[previousIndex]
											+ (i/gap)*m.price;
						}
						//start over and update price
						currentVolume = m.size;
						currentSummedPrice = currentVolume * 
								(double)(m.price);
						previousIndex=index;
					}else{
						//increment this bin
						currentVolume += m.size;
						currentSummedPrice += (double)(m.size) * 
								(double)(m.price);
					}
				}
			}
		}
		System.out.println(symbol+" transitions "+transitions);
		//fill in the accumulated price for the final used bin
		prices[Math.min(totalDurationSeconds-1, previousIndex)]=currentSummedPrice/currentVolume;
		//extend to the end of the prices vector
		for(int i=previousIndex+1;i<totalDurationSeconds;++i){
			prices[i]=prices[previousIndex];
		}
		return new PriceVector(st,prices);
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
		double[] prices = priceVector.v;
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
		System.out.println("Q: "+symbol+" success: "+successes+
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
