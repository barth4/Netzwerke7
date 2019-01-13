import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

public class Main {

    public static void main(String[] args) {
        try {
            AltBitSender abs = new AltBitSender("localhost", 23457, 23456, 1024, "./toSend/test.png", true);
            byte[] buffer = new byte[1024];
            Receiver rec = new Receiver(buffer, 23456);
            Thread t = new Thread(rec);
            t.start();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            abs.send();
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
//        //AltBitSender sender;
//            new Receiver(new byte[1024], 23456).run();
//        //		try {
//        //			sender = new AltBitSender("10.179.5.36", 23457, 1024, "test.jpeg", true);
//        //			sender.send();
//        //		} catch (SocketException e) {
//        //			// TODO Auto-generated catch block
//        //			e.printStackTrace();
//        //		} catch (FileNotFoundException e) {
//        //			// TODO Auto-generated catch block
//        //			e.printStackTrace();
//        //		} catch (NullPointerException e) {
//        //			// TODO Auto-generated catch block
//        //			e.printStackTrace();
//        //		} catch (IOException e) {
//        //			// TODO Auto-generated catch block
//        //			e.printStackTrace();
//        //		}
//    }


