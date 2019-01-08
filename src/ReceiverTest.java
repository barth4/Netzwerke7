import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;

import org.junit.Assert;
import org.junit.Test;

public class ReceiverTest {

//    Receiver receiver = new Receiver();
//
//    @Test
//    public void testDoTransitionStart() {
//
//        receiver.setCurrentState(Receiver.State.IDLE);
//        receiver.doTransition(Receiver.Condition.CHECK_OK_NOT_ALL_REC);
//        Assert.assertEquals(Receiver.State.WAIT_FOR_1, receiver.getCurrentState());
//    }
//
//    @Test
//    public void testDoTransitionOneToZero() {
//
//        receiver.setCurrentState(Receiver.State.WAIT_FOR_1);
//        receiver.doTransition(Receiver.Condition.CHECK_OK_NOT_ALL_REC);
//        Assert.assertEquals(Receiver.State.WAIT_FOR_0, receiver.getCurrentState());
//    }
//
//    @Test
//    public void testDoTransitionZeroToOne() {
//
//        receiver.setCurrentState(Receiver.State.WAIT_FOR_0);
//        receiver.doTransition(Receiver.Condition.CHECK_OK_NOT_ALL_REC);
//        Assert.assertEquals(Receiver.State.WAIT_FOR_1, receiver.getCurrentState());
//    }
//
//    @Test
//    public void testDoTransitionEndZero() {
//
//        receiver.setCurrentState(Receiver.State.WAIT_FOR_0);
//        receiver.doTransition(Receiver.Condition.CHECK_OK_ALL_REC);
//        Assert.assertEquals(Receiver.State.IDLE, receiver.getCurrentState());
//    }
//
//    @Test
//    public void testDoTransitionEndOne() {
//
//        receiver.setCurrentState(Receiver.State.WAIT_FOR_1);
//        receiver.doTransition(Receiver.Condition.CHECK_OK_ALL_REC);
//        Assert.assertEquals(Receiver.State.IDLE, receiver.getCurrentState());
//    }
//
//    // @Test
//
//    public void testExecuteStart() {
//        int seqNr = 1;
//        byte[] seqNrByte = new byte[] { (byte) seqNr };
//        long checksum = 654321789;
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        DataOutputStream dos = new DataOutputStream(baos);
//        try {
//            dos.writeLong(checksum);
//            dos.close();
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        byte[] checkSumByte = baos.toByteArray();
//        int value = 1695609641;
//        byte[] fileLength = new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
//                (byte) value };
//        String fileNameString = "bla.txt";
//        byte[] fileName = fileNameString.getBytes();
//        byte[] wholeArray = new byte[seqNrByte.length + checkSumByte.length + fileLength.length + fileName.length];
//        System.arraycopy(seqNrByte, 0, wholeArray, 0, seqNrByte.length);
//        System.arraycopy(checkSumByte, 0, wholeArray, seqNrByte.length, checkSumByte.length);
//        System.arraycopy(fileLength, 0, wholeArray, seqNrByte.length + checkSumByte.length, fileLength.length);
//        System.arraycopy(fileName, 0, wholeArray, seqNrByte.length + checkSumByte.length + fileLength.length,
//                fileName.length);
//        Receiver receiver = new Receiver();
//        Assert.assertEquals(1695609641, receiver.parseByteArray(wholeArray));
//        Assert.assertEquals(654321789, receiver.getCheckSum());
//        Assert.assertEquals(1, receiver.getSeqNr());
//    }
	
//	@Test
//	public void testRunRemote() {
//		AltBitSender sender;
//		try {
//			sender = new AltBitSender("10.179.5.36", 23456, 1024, "test2.png", true);
//			sender.send();
//		} catch (SocketException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (NullPointerException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

    @Test
    public void testRun() {

        try {
        	//Auf localhost eingestellt
            AltBitSender abs = new AltBitSender("localhost", 23456, 1024, "test2.png", true);
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
