import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

public class Main {

	public static void main(String[] args) {
		AltBitSender sender;
		new Thread(new Receiver(new byte[1024], 23456)).start();
		try {
			sender = new AltBitSender("10.179.5.36", 23457, 1024, "test.jpeg", true);
			sender.send();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
