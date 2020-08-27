public class SeriesStatistics {
	double[] Q = null;
	double[] T = null;
	double muQ = 0;
	double sigmaQ = 0;
	double[] muT = null;
	double[] sigmaT = null;
	public SeriesStatistics(double[] Q, double[] T)
	{
		if(Q==null)return;
		if(T==null)return;
		if(Q.length>T.length)return;
		this.Q=Q;
		this.T=T;
		init();
	}

	public void init()
	{
		if(Q==null)return;
		if(T==null)return;
		if(Q.length>T.length)return;
		double ms = 0d;
		double ss = 0d;
		int i;
		for(i=0;i<Q.length;++i){
			ms+=Q[i];
			ss+=Q[i]*Q[i];
		}
		int n = Q.length;
		muQ=ms/n;
		sigmaQ=Math.sqrt((ss/(n-1))-((ms*ms)/(n*(n-1))));
		if(Double.isNaN(sigmaQ))Main.die("sigmaQ is NaN", new Exception());
		int m = T.length-Q.length;
		muT=new double[m];
		sigmaT=new double[m];
		ms=0d;
		ss=0d;
		i=0;
		for(i=0;i<n;++i){
			ms+=T[i];
			ss+=T[i]*T[i];
		}
		for(;i<T.length;++i){
			muT[i-n]=ms/n;
			sigmaT[i-n]=Math.sqrt((ss/(n-1))-((ms*ms)/(n*(n-1))));
			ms-=T[i-n];
			ms+=T[i];
			ss-=T[i-n]*T[i-n];
			ss+=T[i]*T[i];
			if(Double.isNaN(sigmaT[i-n]))Main.die("sigmaT[i-n] is NaN", new Exception());
		}
	}
}
