import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

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

	public TreeMap<Long, Double> computeCorrelation(long duration, Ticker ticker) {
		System.out.println("computeCorrelation("+duration+", "+ticker.symbol);
		ticker.computeRepresentativeMoments(duration);
		System.out.println("done (ticker): "+ticker.representativeMoments.size()
				+", "+ticker.representativeDeltas.size());
		computeRepresentativeMoments(duration);
		System.out.println("done (this): "+this.representativeMoments.size()
				+", "+this.representativeDeltas.size());
		TreeMap<Long, Double> correlation = new TreeMap<Long, Double>();
		for(Entry<Long, Double> entry : this.representativeDeltas.entrySet()){
			Entry<Long, Double> otherDelta = 
					ticker.representativeDeltas.ceilingEntry(entry.getKey());
			double c = computeCorrelation(entry, otherDelta);
			correlation.put(entry.getKey(), c);
		}
		System.out.println("no really, I'm done");
		return correlation;
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
			try {writer.write(", buy_"+i);}
			catch (IOException e) {die("dumpMoments: failed to write buy header "+filename,e);}
		}
		try {writer.write(", price");}
		catch (IOException e) {die("dumpMoments: failed to write price header "+filename,e);}
		for(i=0;i<maxSells;++i){
			try {writer.write(", sell_"+i);}
			catch (IOException e) {die("dumpMoments: failed to write sell header "+filename,e);}
		}
		try {writer.write("\n");}
		catch (IOException e) {die("dumpMoments: failed to write a newline "+filename,e);}
		for(Entry<Long, Moment> entry : moments.entrySet()){
			try {writer.write(""+Main.nanoSecondsToDays(entry.getKey()));}
			catch (IOException e) {die("dumpMoments: failed to write time value "+filename,e);}
			i=0;
			if(entry.getValue().buys!=null){
				for(Entry<Long, Integer> buyEntry : entry.getValue().buys.entrySet()){
					try {writer.write(", "+buyEntry.getKey().toString());}
					catch (IOException e) {die("dumpMoments: failed to write buy value "+filename,e);}
					++i;
				}
			}
			for(;i<maxBuys;++i){
				try {writer.write(", ");}
				catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}
			}
			if(maxBuys>0)
				try {writer.write(", ");}
			catch (IOException e) {die("dumpMoments: failed to write a comma "+filename,e);}
			if(entry.getValue().price>0){
				try {writer.write(""+entry.getValue().price);}
				catch (IOException e) {die("dumpMoments: failed to write price value "+filename,e);}
			}
			i=0;
			if(entry.getValue().sells!=null){
				for(Entry<Long, Integer> sellEntry : entry.getValue().sells.entrySet()){
					try {writer.write(", "+sellEntry.getKey().toString());}
					catch (IOException e) {die("dumpMoments: failed to write sell value "+filename,e);}
				}
				++i;
			}
			try {writer.write("\n");}
			catch (IOException e) {die("dumpMoments: failed to write a newline "+filename,e);}
		}		
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
