import org.jtransforms.fft.DoubleFFT_1D;

public class MASS {
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
	
	static void reverseInPlace(double[] v)
	{
		if(v==null)return;
		int mid = v.length/2;
		int end = v.length-1;
		double x;
		for(int i=0;i<mid;++i) {
			x=v[i];
			v[i]=v[end-i];
			v[end-i]=x;
		}
	}
	
	static double[] reverse(double[] v)
	{
		if(v==null)return null;
		double[] out = new double[v.length];
		int end = v.length-1;
		for(int i=0;i<v.length;++i) {
			out[end-i]=v[i];
		}
		return out;
	}
	
	static double[] extend(double[] v, int tail)
	{
		if(v==null)return null;
		double[] out = new double[v.length+tail];
		for(int i=0;i<v.length;++i) {
			out[i]=v[i];
		}
		return out;
	}
	
	static double[] elementwiseComplexMultiplication(double[] a, double[] b)
	{
		if(a==null)return null;
		if(b==null)return null;
		int n = Math.min(a.length, b.length);
		double[] out = new double[n];
		for(int i=0;i<n;i+=2)
		{
			//complex multiplication
			out[i]=(a[i]*b[i]) - (a[i+1]*b[i+1]);
			out[i+1]=(a[i]*b[i+1]) + (a[i+1]*b[i]);
		}
		return out;
	}
	
	static double[] FFT(double[] a)
	{
		double[] out = extend(a, a.length);
		DoubleFFT_1D fft = new DoubleFFT_1D(out.length);//one bin per second
		fft.realForward(out);
		return out;
	}
	
	static double[] inverseFFT(double[] a)
	{
		double[] out = new double[a.length/2];
		DoubleFFT_1D fft = new DoubleFFT_1D(a.length);
		fft.realInverse(a, true);
		for(int i=0;i<out.length;++i)
		{
			out[i]=a[i];
		}
		return out;
	}

	static double[] slidingDotProduct(double[] Q, double[] T)
	{
		if(Q==null)return null;
		if(T==null)return null;	
		if(Q.length>T.length)return null;
		int n = T.length;
		int m = Q.length;
		double[] Ta = extend(T, n);
		double[] Qr = reverse(Q);
		double[] Qra = extend(Qr, 2*n-m);
		double[] Qraf = FFT(Qra);
		double[] Taf = FFT(Ta);
		double[] QT = inverseFFT(elementwiseComplexMultiplication(Qraf, Taf));
		return QT;
	}
	
	static boolean checkForNan(double[] v){
		for(int i=0;i<v.length;++i){
			if(Double.isNaN(v[i]))return false;
		}
		return true;
	}
	
	static double[] mass(double[] Q, double[] T)
	{
		double[] QT = slidingDotProduct(Q,T);
		SeriesStatistics stats = new SeriesStatistics(Q,T);
		double[] D = new double[stats.muT.length];
		double m = Q.length;
		for(int i=0;i<D.length;++i){
			double x = 2*m*(1-((QT[i]-m*stats.muQ*stats.muT[i])/(m*stats.sigmaQ*stats.sigmaT[i])));
			if(x<0)
				D[i]=0;
			else if(Double.isFinite(x))
				D[i]=Math.sqrt(x);
			else
				D[i]=0.00001;
		}
		if(!checkForNan(D))System.out.println("D has NaNs");
		return D;
	}
	
	
}
