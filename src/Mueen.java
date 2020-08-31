import java.util.Date;

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
import org.jtransforms.fft.DoubleFFT_1D;

public class Mueen {

	public static void main(String[] args) {
		//Data Input
		final int n = 2000;
		final int m = 20;
		double[] x = new double[n];
		double[] y = new double[m];
		for ( int i = 0 ; i < n ; ++i )
		{
			double d = Math.random();
			x[i] = d;
			if( i < m )
			{
				y[i] = d;
			}
		}
		double[] dist = new double[n];

		dist = findNN(x,y,n,m,dist);

		showDoublePanel("x", x, 0L, 1);
		showDoublePanel("y", y, 0L, 1);
		showDoublePanel("dist", dist, 0L, 1);

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
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "time", "v", timeSeriesCollection, true, true, false);
		ZoomSyncedChartPanel chartPanel = new ZoomSyncedChartPanel(chart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500,350));
		chartPanel.setMouseZoomable(true,false);
		panel.add(chartPanel);

		JScrollPane scrollPane = new JScrollPane(panel);
		frame.setContentPane(scrollPane);
		frame.pack();
		frame.setVisible(true);

	}

	
	static double[] zNorm(double[] x, int n, double[] y)
	{
		double ex = 0, ex2 = 0;
		for(int i = 0 ; i < n ; i++ )
		{
			ex += x[i];
			ex2 += x[i]*x[i];
		}
		double	mean = ex/n;
		double std = ex2/n;
		std = Math.sqrt(std-mean*mean);
		for(int i = 0 ; i < n ; i++ )
			y[i] = (x[i]-mean)/std;
		return y;
	}


	static double[] findNN(double[] x, double[] y, int n, int m, double[] dist)
	{

		//Assume n > m
		if(n<=m)return null;

		//Allocation
		double[] cx = new double[n+1];
		double[] cx2 = new double[n+2];
		double[] cy = new double[m+1];
		double[] cy2 = new double[m+1];

		//Normalize
		x = zNorm(x,n,x);
		y = zNorm(y,m,y);

		//Compute the cumulative sums
		cx[0] = cx2[0] = cy[0] = cy2[0] = 0.0;
		for( int i = 1 ; i <= n; ++i )
		{
			cx[i] = cx[i-1]+x[i-1];
			cx2[i] = cx2[i-1]+x[i-1]*x[i-1];
			if( i <= m )
			{
				cy[i] = cy[i-1]+y[i-1];
				cy2[i] = cy2[i-1]+y[i-1]*y[i-1];

			}

		}

		//Compute the multiplication numbers
		double[] z = new double[n];
		z = multiply(x,n,y,m,z);

		//y Stats

		double sumy = cy[m];
		double sumy2 = cy2[m];
		double meany = sumy/m;
		double sigmay = (sumy2/m)-meany*meany;
		sigmay = Math.sqrt(sigmay);


		//The Search
		for( int j = 0 ; j < n-m+1 ; ++j )
		{
			double sumxy = z[m-1+j];

			double sumx = cx[j+m]-cx[j];
			double sumx2 = cx2[j+m]-cx2[j];
			double meanx = sumx/m;
			double sigmax = (sumx2/m)-meanx*meanx;
			sigmax = Math.sqrt(sigmax);

			double c = ( sumxy - m*meanx*meany ) / ( m*sigmax*sigmay );
			dist[j] = Math.sqrt(2*m*(1-c));

		}

		return dist;
	}

	static double[] multiply(double[] x, int n , double[] y , int m , double[] z)
	{

		//assuming n > m
		double[] X = new double[n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
		double[] Y = new double[n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
		//double[] XX = new double[4*n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
		//double[] YY = new double[4*n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
		double[] Z = new double[n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
		//double[] ZZ = new double[4*n];//(fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);



		/*for(int i = 0 ; i < 2*n ; i++ )
		{
			X[i][1] = 0; Y[i][1] = 0; //iaginary part is always zero
			if(i < n )
				X[i][0] = x[i];
			else
				X[i][0] = 0;

			if(i < m )
				Y[i][0] = y[m-i-1]; //reversing y
			else
				Y[i][0] = 0;
		}*/
		for(int i=0;i<n;++i) {
			X[i]=x[i];
		}
		int j = m-1;
		for(int i=0;i<m;++i) {
			Y[i]=y[j];
			--j;
		}


		//p = fftw_plan_dft_1d(2 * n, X, XX, FFTW_FORWARD, FFTW_ESTIMATE);
		//fftw_execute(p); 
		DoubleFFT_1D fftx = new DoubleFFT_1D(X.length);
		fftx.realForward(X);

		//p = fftw_plan_dft_1d(2 * n, Y, YY, FFTW_FORWARD, FFTW_ESTIMATE);
		//fftw_execute(p); 
		DoubleFFT_1D ffty = new DoubleFFT_1D(Y.length);
		ffty.realForward(Y);

		/*for(int i = 0 ; i < 2*n; i++)
		{
			ZZ[i][0] = XX[i][0]*YY[i][0] - XX[i][1]*YY[i][1]; 
			ZZ[i][1] = XX[i][1]*YY[i][0] + XX[i][0]*YY[i][1];
		}*/
		for(int i=0;i<n;i+=2) {
			Z[i]=X[i]*Y[i] - X[i+1]*Y[i+1];
			Z[i+1]=X[i+1]*Y[i] + X[i]*Y[i+1];
		}

		//p = fftw_plan_dft_1d(2 * n, ZZ , Z , FFTW_BACKWARD, FFTW_ESTIMATE);
		//fftw_execute(p); 
		DoubleFFT_1D fftz = new DoubleFFT_1D(Z.length);
		fftz.realInverse(Z, false);

		/*for(int i = 0; i < 2*n; i++ )
			z[i] = Z[i][0]/(2*n);*/

		for(int i=0;i<n;++i) {
			z[i]=Z[i]/n;
		}

		//fftw_destroy_plan(p);
		//fftw_free(X); fftw_free(Y);
		//fftw_free(XX); fftw_free(YY);
		//fftw_free(Z); fftw_free(ZZ);

		return z;
	}

}
/* #include <stdio.h>
#include <stdlib.h>
#include <fftw3.h>
#include <math.h>



double * multiply(double * x, int n , double * y , int m , double * z);
double * zNorm(double * x, int n, double * y);
double * findNN(double * x, double * y, int n, int m, double * dist);



int main(int argc, char* argv[])
{
	//Assume n > m

	int n = atol(argv[3]); 
	int m = atol(argv[4]);
	double *x, *y, *dist;

	//Memory Allocation

	FILE * fp ; errno_t err = fopen_s(&fp,argv[1],"r");
	  if( err )
      printf_s( "The file fscanf.out was not opened\n" );
	FILE * fp1 ; err = fopen_s(&fp1,argv[2],"r");
		   if( err )
      printf_s( "The file fscanf.out was not opened\n");

	x = (double *)malloc(sizeof(double) * n);
	y = (double *)malloc(sizeof(double) * m);
	dist = (double *)malloc(sizeof(double) * n);

	//Data Input
	for ( int i = 0 ; i < n ; i ++ )
	{
		double d;
		fscanf_s(fp,"%lf",&d);
		x[i] = d;
		if( i < m )
		{
			fscanf_s(fp1,"%lf",&d);
			y[i] = d;
		}
	}

	dist = findNN(x,y,n,m,dist);

	double minm = 99999999999999.000222;
	int mini = 0;
	for ( int i = 0 ; i < n-m+1	 ; i++ )
		if( dist[i] < minm )
		{	minm = dist[i]; mini = i; }

	printf("Nearest Neighbor Distance is %lf\nNearest Neighbor location is %d (starting at 1)\n",minm, mini);
	fclose(fp); fclose(fp1);




	free(x); free(y); free(dist);

	system("PAUSE");
}


double * findNN(double * x, double * y, int n, int m, double * dist)
{

	//Assume n > m
	double *z ;
	double *cx, *cx2, *cy, *cy2;

	//Allocation
	cx = (double *)malloc(sizeof(double) * (n+1));
	cx2 = (double *)malloc(sizeof(double) * (n+1));
	cy = (double *)malloc(sizeof(double) * (m+1));
	cy2 = (double *)malloc(sizeof(double) * (m+1));

	//Normalize
	x = zNorm(x,n,x);
	y = zNorm(y,m,y);

	//Compute the cumulative sums
	cx[0] = cx2[0] = cy[0] = cy2[0] = 0.0;
	for( int i = 1 ; i <= n; i++ )
	{
		cx[i] = cx[i-1]+x[i-1];
		cx2[i] = cx2[i-1]+x[i-1]*x[i-1];
		if( i <= m )
		{
			cy[i] = cy[i-1]+y[i-1];
			cy2[i] = cy2[i-1]+y[i-1]*y[i-1];

		}

	}

	//Compute the multiplication numbers
	z = (double *)malloc(sizeof(double)*2*n);
	z = multiply(x,n,y,m,z);

	//y Stats

	double sumy = cy[m];
	double sumy2 = cy2[m];
	double meany = sumy/m;
	double sigmay = (sumy2/m)-meany*meany;
	sigmay = sqrt(sigmay);


	//The Search
	for( int j = 0 ; j < n-m+1 ; j=j+1 )
	{
				double sumxy = z[m-1+j];

				double sumx = cx[j+m]-cx[j];
				double sumx2 = cx2[j+m]-cx2[j];
				double meanx = sumx/m;
				double sigmax = (sumx2/m)-meanx*meanx;
				sigmax = sqrt(sigmax);

				double c = ( sumxy - m*meanx*meany ) / ( m*sigmax*sigmay );
				dist[j] = sqrt(2*m*(1-c));

	}

    free(cx); free(cx2); free(cy); free(cy2);
	free(z);
	return dist;
}


double * zNorm(double * x, int n, double * y)
{
	double ex = 0, ex2 = 0;
	for(int i = 0 ; i < n ; i++ )
	{
		ex += x[i];
		ex2 += x[i]*x[i];
	}
	double	mean = ex/n;
    double std = ex2/n;
    std = sqrt(std-mean*mean);
	for(int i = 0 ; i < n ; i++ )
		y[i] = (x[i]-mean)/std;
	return y;
}

double * multiply(double * x, int n , double * y , int m , double * z)
{
	fftw_complex * X, * Y, * Z , *XX, *YY, *ZZ;
    fftw_plan p;

	//assuming n > m
	X = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
	Y = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
	XX = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
	YY = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
	Z = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);
	ZZ = (fftw_complex*) fftw_malloc(sizeof(fftw_complex) * 2 * n);


	for(int i = 0 ; i < 2*n ; i++ )
	{
		X[i][1] = 0; Y[i][1] = 0; //iaginary part is always zero
		if(i < n )
			X[i][0] = x[i];
		else
			X[i][0] = 0;

		if(i < m )
			Y[i][0] = y[m-i-1]; //reversing y
		else
			Y[i][0] = 0;
	}


    p = fftw_plan_dft_1d(2 * n, X, XX, FFTW_FORWARD, FFTW_ESTIMATE);
    fftw_execute(p); 

    p = fftw_plan_dft_1d(2 * n, Y, YY, FFTW_FORWARD, FFTW_ESTIMATE);
    fftw_execute(p); 

	for(int i = 0 ; i < 2*n; i++)
	{
		ZZ[i][0] = XX[i][0]*YY[i][0] - XX[i][1]*YY[i][1]; 
		ZZ[i][1] = XX[i][1]*YY[i][0] + XX[i][0]*YY[i][1];
	}

	p = fftw_plan_dft_1d(2 * n, ZZ , Z , FFTW_BACKWARD, FFTW_ESTIMATE);
    fftw_execute(p); 


	for(int i = 0; i < 2*n; i++ )
		z[i] = Z[i][0]/(2*n);

	fftw_destroy_plan(p);
    fftw_free(X); fftw_free(Y);
	fftw_free(XX); fftw_free(YY);
	fftw_free(Z); fftw_free(ZZ);

	return z;
}*/