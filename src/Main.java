import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Main {

	public static void printUsage(String[] invocation) {
		String inv = "["+invocation.length+"]";
		for(int i=0;i<invocation.length;++i) {
			if(i!=0)inv+=" ";
			inv+=invocation[i];
		}
		System.out.println("invocation: "+inv);
		System.out.println("-h            : help (this listing)");
		System.out.println("-f <filename> : read the given file");
	}
	
	public static void main(String[] args) {
		if(args.length < 2 || args[0] == "-h") {
			printUsage(args);
			System.exit(-1);
		}
		if(args.length == 2) {
			System.out.println("{"+args[0]+"}");
			if(args[0].equals("-f")) {
				System.out.println(args[1]);
				if(!parse(args[1])) {
					System.err.println("failed to parse "+args[1]);
					System.exit(-1);
				}
			}else {
				printUsage(args);
				System.exit(-1);
			}
		}
		System.exit(0);
	}
	
	public static boolean parse(String fname) {
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
		do {
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
			++count;
		}while (aByte != -1);
		System.out.println("bytes read: "+count);
		try {
			ins.close();
		} catch (IOException e) {
			System.err.println("failed to close: "+fname+", but read it all, so ignoring.");
			e.printStackTrace();
		}
		return true;
	}
}
