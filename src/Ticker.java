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
	public TreeMap<Long,Double> representativeSlopes = new TreeMap<Long, Double>();
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

	public static double computeCorrelation(Double slope, Double otherSlope) {
		if(slope==null || otherSlope==null)return 0;
		double ds = slope-otherSlope;
		double magDs = Math.abs(ds);
		final double limit = 0.1;
		if(magDs<limit){
			return ds;
		}else if(magDs>1-limit){
			return ds;
		}
		return 0;
	}

	public TreeMap<Long, Double> computeCorrelation(long duration, Ticker ticker) {
		System.out.println("computeCorrelation("+duration+", "+ticker.symbol);
		ticker.computeRepresentativeMoments(duration);
		System.out.println("done");
		computeRepresentativeMoments(duration);
		System.out.println("no really, I'm done");
		TreeMap<Long, Double> correlation = new TreeMap<Long, Double>();
		for(Entry<Long, Double> entry : representativeSlopes.entrySet()) {
			System.out.println(entry);
			correlation.put(entry.getKey(), 
					computeCorrelation(entry.getValue(), 
							ticker.getRepresentativeSlope(entry.getKey())));
		}
		return correlation;
	}

	public Double getRepresentativeSlope(long time){
		if(representativeSlopes==null||representativeSlopes.isEmpty())return null;
		Entry<Long, Double> entry = representativeSlopes.floorEntry(time);
		if(entry==null)return null;
		if(entry.getKey()<time-representativeMomentDuration){
			return null;
		}
		return entry.getValue();
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
		representativeSlopes.clear();
		Entry<Long,Moment> entry = moments.firstEntry();
		Entry<Long,Moment> previousEntry = entry;
		do {
			System.out.println(entry);
			Moment moment = computeRepresentativeMoment(entry.getKey(), duration);
			if(moment==null) {
				entry=null;
			}else {
				representativeMoments.put(entry.getKey(), moment);
				if(previousEntry.getValue().price!=0) {
					representativeSlopes.put(entry.getKey(), 
							(double) ((entry.getValue().price-previousEntry.getValue().price)/
									(previousEntry.getValue().price)));
				}
				previousEntry = entry;
				entry = moments.ceilingEntry(entry.getKey()+duration);
			}
		}while(entry==null);
		return representativeMoments.size();
	}

	public Moment computeRepresentativeMoment(long time, long duration) {
		long halfD = duration/2;
		Entry<Long, Moment> entry = moments.ceilingEntry(time-halfD);
		if(entry==null || entry.getKey()>time+duration) {
			return null;
		}
		long sumPrice=0;
		int sumVolume=0;
		do {
			sumPrice+=entry.getValue().price * entry.getValue().size;
			sumVolume+=entry.getValue().size;
			entry=moments.higherEntry(entry.getKey());
		}while(entry!=null);
		return new Moment(sumPrice/sumVolume,sumVolume);
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
