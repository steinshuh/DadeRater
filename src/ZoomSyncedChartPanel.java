import java.awt.geom.Rectangle2D;
import java.util.Set;
import java.util.TreeSet;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;


public class ZoomSyncedChartPanel extends ChartPanel implements Comparable {


	private static final long serialVersionUID = 1L;
	public ZoomSyncedChartPanel(JFreeChart chart) {
		super(chart);
	}

	public ZoomSyncedChartPanel(JFreeChart chart, boolean useBuffer) {
		super(chart, useBuffer);
	}

	public ZoomSyncedChartPanel(JFreeChart chart, boolean properties,
			boolean save, boolean print, boolean zoom, boolean tooltips) {
		super(chart, properties, save, print, zoom, tooltips);
	}

	public ZoomSyncedChartPanel(JFreeChart chart, int width, int height,
			int minimumDrawWidth, int minimumDrawHeight, int maximumDrawWidth,
			int maximumDrawHeight, boolean useBuffer, boolean properties,
			boolean save, boolean print, boolean zoom, boolean tooltips) {
		super(chart, width, height, minimumDrawWidth, minimumDrawHeight,
				maximumDrawWidth, maximumDrawHeight, useBuffer, properties,
				save, print, zoom, tooltips);
	}

	public ZoomSyncedChartPanel(JFreeChart chart, int width, int height,
			int minimumDrawWidth, int minimumDrawHeight, int maximumDrawWidth,
			int maximumDrawHeight, boolean useBuffer, boolean properties,
			boolean copy, boolean save, boolean print, boolean zoom,
			boolean tooltips) {
		super(chart, width, height, minimumDrawWidth, minimumDrawHeight,
				maximumDrawWidth, maximumDrawHeight, useBuffer, properties,
				copy, save, print, zoom, tooltips);
	}
	
	Set<ZoomSyncedChartPanel> syncedPanels = new TreeSet<ZoomSyncedChartPanel>();
	public void addSyncedPanel(ZoomSyncedChartPanel p){
		if(p==this)return;
		syncedPanels.add(p);
	}
	
	public void rawZoom(Rectangle2D selection){
		super.zoom(selection);
	}
	public void zoom(Rectangle2D selection){
		super.zoom(selection);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoom(selection);
		}
	}
	
	public void rawZoomInBoth(double x, double y){
		super.zoomInBoth(x, y);
	}
	public void zoomInBoth(double x, double y){
		super.zoomInBoth(x,y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomInBoth(x,y);
		}
	}
	
	public void rawZoomInDomain(double x, double y){
		super.zoomInDomain(x, y);
	}
	public void zoomInDomain(double x, double y){
		super.zoomInDomain(x,y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomInDomain(x, y);
		}
	}

	public void rawZoomInRange(double x, double y){
		super.zoomInRange(x, y);
	}
	public void zoomInRange(double x, double y){
		super.zoomInRange(x, y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomInRange(x, y);
		}
	}
	
	public void rawZoomOutBoth(double x, double y){
		super.zoomOutBoth(x, y);
	}
	public void zoomOutBoth(double x, double y){
		super.zoomOutBoth(x, y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomOutBoth(x, y);
		}
	}
	
	public void rawZoomOutDomain(double x, double y){
		super.zoomOutDomain(x, y);
	}
	public void zoomOutDomain(double x, double y){
		super.zoomOutDomain(x, y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomOutDomain(x, y);
		}
	}
	
	public void rawZoomOutRange(double x, double y){
		super.zoomOutRange(x, y);
	}
	public void zoomOutRange(double x, double y){
		super.zoomOutRange(x, y);
		for(ZoomSyncedChartPanel p : syncedPanels){
			p.rawZoomOutRange(x,  y);
		}
	}

	public int compareTo(Object arg) {
		if(arg == this)return 0;
		return Integer.compare(hashCode(), arg.hashCode());
	}
}
