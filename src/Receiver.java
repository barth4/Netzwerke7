import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Receiver implements Runnable {
    public static final String PATH = "test";
    String HOSTNAME = "localhost";
    static int port = 23456;
    private byte[] buffer;
    private int portToSend;
    private InetAddress addressToSend;

    private DatagramSocket ds;
    private DatagramPacket dp;

    private State currentState;
    private Transition[][] transition;
    long actualChecksum;
    long checkSum;
    private int seqNr;
    byte[] fileNameByte;
    File file;
    private int fileSize;
    byte[] data;
    int counter = 0;
    int dataCounter = 0;

    public Receiver(byte[] buffer, int port) {
        try {
            this.buffer = buffer;
            this.ds = new DatagramSocket(port);
            this.ds.setSoTimeout(3000);
            this.dp = new DatagramPacket(buffer, buffer.length);
            currentState = State.IDLE;
            transition = new Transition[State.values().length][Condition.values().length];
            transition[State.IDLE.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new Start();
            transition[State.WAIT_FOR_1.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new OneToZero();
            transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new ZeroToOne();
            transition[State.WAIT_FOR_1.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndOne();
            transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndZero();
            transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndZero();

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public Receiver() {

        transition = new Transition[State.values().length][Condition.values().length];
        transition[State.IDLE.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new Start();
        transition[State.WAIT_FOR_1.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new OneToZero();
        transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_NOT_ALL_REC.ordinal()] = new ZeroToOne();
        transition[State.WAIT_FOR_1.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndOne();
        transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndZero();
        transition[State.WAIT_FOR_0.ordinal()][Condition.CHECK_OK_ALL_REC.ordinal()] = new EndZero();

    }

    public void receive() {
        try {
            while (true) {

                counter++;
                System.out.println("turn:" + counter);
                ds.receive(dp);
                if (currentState == State.IDLE) {
                    addressToSend = dp.getAddress();
                    portToSend = dp.getPort();
                }
                System.out.println("receiving completed" + counter);

                buffer = dp.getData();
                port = dp.getPort();
                System.out.println("wait for parse");
                int number = parseByteArray();
                System.out.println("parse completed");
                if (number !=0) {
                this.doTransition(this.defineConditon());
                }
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (SocketTimeoutException e) {

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int parseByteArray() {
        byte[] seqNrBytes = new byte[1];
        byte[] checkSumBytes = new byte[8];
        // int fileSize = 0;
        System.arraycopy(buffer, 0, seqNrBytes, 0, seqNrBytes.length);
        int seqToPrint = (int) seqNrBytes[0];
        System.out.println("sended sequenznr: " + seqToPrint);
        System.arraycopy(buffer, 1, checkSumBytes, 0, checkSumBytes.length);
        seqNr = seqNrBytes[0];
        if (seqNr != 0 && seqNr != 1) {
            return 0; //TODO
        }
        checkSum = ByteBuffer.wrap(checkSumBytes).getLong();
        checkSum = Long.parseLong(Long.toUnsignedString(checkSum, 10));

        if (getCurrentState() == State.IDLE) {
            byte[] fileLengthByte = new byte[4];
            fileNameByte = new byte[buffer.length - 13];
            System.arraycopy(buffer, 9, fileLengthByte, 0, fileLengthByte.length);
            System.arraycopy(buffer, 13, fileNameByte, 0, fileNameByte.length);
            fileNameByte = trim(fileNameByte);
            String fileName;
            try {
                fileName = new String(fileNameByte, "UTF-8");// if the charset is UTF-8; "ISO-8859-1"
                file = new File(PATH + fileName);
                fileSize = fileLengthByte[0] << 24 | (fileLengthByte[1] & 0xFF) << 16 | (fileLengthByte[2] & 0xFF) << 8
                        | (fileLengthByte[3] & 0xFF);
                dataCounter = fileSize / (buffer.length - 9);
//                if (fileSize % (buffer.length - 9) != 0) {
//                	++dataCounter;
//                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else {
        	long dataSize = buffer.length - 9;
        	if (dataCounter == 0) {
        		dataSize = fileSize % (buffer.length - 9);
        	}
            System.out.println("FileSize = " + fileSize + " FileLänge = " + file.length());
//            long dataSize = fileSize - file.length() < buffer.length - 9 ? fileSize - file.length() : buffer.length - 9;
            data = new byte[(int) dataSize];
            System.arraycopy(buffer, 9, data, 0, (int) dataSize); // file.length() fileSize
            System.out.println("New data: ");

            for (int j = 0; j < data.length; j++) {
                System.out.print(data[j]);
            }

            System.out.println("");
            System.out.println("Länge der Nutzdaten:" + data.length);
        }
        return fileSize;

    }

    /**
     * trims all zero bytes from the file
     * 
     * @param bytes
     * @return
     */
    private byte[] trim(byte[] bytes) {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0) {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }

    public void sendACK() {
        try {
            byte[] seqBytes = new byte[] { (byte) seqNr };

            ds.send(new DatagramPacket(seqBytes, 1, addressToSend, portToSend));

            System.out.println("ack gesendet" + seqBytes[0]);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static void saveData() {// input param inputstream
    }

    private long getChecksum(byte[] bytes) {
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return checksum.getValue();
    }

    public Condition defineConditon() {
        byte[] dataToCheck;
        if (currentState == State.IDLE) {
            dataToCheck = fileNameByte;
        } else {
            dataToCheck = data;
        }

        actualChecksum = getChecksum(dataToCheck);
        System.out.println("New Checksum: " + actualChecksum);
System.out.println("Current Seq: " + this.seqNr);
        if (checkSum != actualChecksum || (currentState == State.WAIT_FOR_0 && this.seqNr == 1)
                || (currentState == State.WAIT_FOR_1 && this.seqNr == 0)) {
            return Condition.DUPLICATE_SQNR_OR_CHECK_NOT_OK;
        }

        if ((currentState != State.IDLE && (long) (data.length + file.length()) == fileSize) || fileSize == 0) {
            return Condition.CHECK_OK_ALL_REC;
        } else {
            return Condition.CHECK_OK_NOT_ALL_REC;
        }
    }

    public void doTransition(Condition input) {
        System.out.println("Condition: " + input);

        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        System.out.println("Transition: " + trans);
        if (trans != null) {
            currentState = trans.execute(input);
        } else {
            Transition defTr = new StayInState();
            defTr.execute(Condition.DUPLICATE_SQNR_OR_CHECK_NOT_OK);
        }
        System.out.println("INFO State: " + currentState);
    }

    enum State {
        IDLE, WAIT_FOR_0, WAIT_FOR_1
    }

    enum Condition {
        CHECK_OK_NOT_ALL_REC, CHECK_OK_ALL_REC, DUPLICATE_SQNR_OR_CHECK_NOT_OK
    }

    abstract class Transition {
        abstract public State execute(Condition input);
    }

    class StayInState extends Transition {
        @Override
        public State execute(Condition input) {
            System.out.println("Wir bleiben im Zustand");
            System.out.println("Checksum: " + checkSum + " ; " + "actual checksum " + actualChecksum);
            if (checkSum == actualChecksum) {
                sendACK();
            }
            return currentState;

        }
    }

    class Start extends Transition {
        @Override
        public State execute(Condition input) {
            try {
                file.createNewFile();

                sendACK();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return State.WAIT_FOR_1;
        }
    }

    class ZeroToOne extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            --dataCounter;
            sendACK();
            return State.WAIT_FOR_1;
        }
    }

    class OneToZero extends Transition {
        @Override
        public State execute(Condition input) {
            System.out.println("Now in transition method onetozero");
            saveFile(data);
            --dataCounter;
            sendACK();
            System.out.println("Now in transition method onetozero ack sended");
            return State.WAIT_FOR_0;
        }
    }

    class EndOne extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            sendACK();
            return State.IDLE;
        }
    }

    class EndZero extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            sendACK();
            return State.IDLE;
        }
    }

    public void saveFile(byte data[]) {

        FileOutputStream out;
        try {
            out = new FileOutputStream(file.getAbsolutePath(), true);
            out.write(data);
            out.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public State getCurrentState() {
        return currentState;
    }

    public void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    public int getSeqNr() {
        return seqNr;
    }

    public long getCheckSum() {
        return checkSum;
    }

    @Override
    public void run() {
        this.receive();
    }
}
