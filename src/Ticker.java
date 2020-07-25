import java.util.Map;
import java.util.TreeMap;

public class Ticker {
	
	public class Moment {
		public Moment() {}
		long price=0;
		int size=0;
		Map<Long,Integer> sells = new TreeMap<Long,Integer>();
		Map<Long,Integer> buys = new TreeMap<Long,Integer>();
	}
	
	public String symbol;
	public Map<Long,Moment> moments = new TreeMap<Long,Moment>();
	
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

}
