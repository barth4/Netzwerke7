import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

public class MainSender {

	public static void main(String[] args) {
		AltBitSender sender;
		try {
			sender = new AltBitSender("10.181.111.205", 23456, 1024, "test2.png", true);
			sender.send();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
