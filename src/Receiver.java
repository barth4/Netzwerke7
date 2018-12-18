import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Receiver implements Runnable {
    public static final String PATH = "d:\\";
    private byte[] buffer;

    private DatagramSocket ds;
    private DatagramPacket dp;

    private State currentState;
    private Transition[][] transition;

    long checkSum;
    private int seqNr;
    byte[] fileNameByte;
    File file;
    private int fileSize;
    byte[] data;

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
                ds.receive(dp);
                buffer = dp.getData();
                parseByteArray();
                this.doTransition(this.defineConditon());
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
        //int fileSize = 0;
        System.arraycopy(buffer, 0, seqNrBytes, 0, seqNrBytes.length);
        System.arraycopy(buffer, 1, checkSumBytes, 0, checkSumBytes.length);
        seqNr = seqNrBytes[0];

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
                file = new File (PATH + fileName);
                fileSize = fileLengthByte[0] << 24 | (fileLengthByte[1] & 0xFF) << 16
                        | (fileLengthByte[2] & 0xFF) << 8 | (fileLengthByte[3] & 0xFF);

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else {
            data = new byte[buffer.length - 9];
            data = trim(data);

            System.arraycopy(buffer, 9, data, 0, data.length);
        }
        return fileSize;

    }
/**
 * trims all zero bytes from the file
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
            DatagramPacket ack = new DatagramPacket(seqBytes, 1);
            ds.send(ack);
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
   
        long actualChecksum = getChecksum(dataToCheck);
        if (checkSum != actualChecksum || (currentState == State.WAIT_FOR_0 && this.seqNr == 1)
                || (currentState == State.WAIT_FOR_1 && this.seqNr == 0)) {
            return Condition.DUPLICATE_SQNR_OR_CHECK_NOT_OK;
        }

        if ((currentState != State.IDLE && (long) (data.length + file.length()) == fileSize) || fileSize == 0){
            return Condition.CHECK_OK_ALL_REC;
        } else {
            return Condition.CHECK_OK_NOT_ALL_REC;
        }
    }

    public void doTransition(Condition input) {
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
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
            if (checkSum == buffer.length) {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
            return State.WAIT_FOR_1;
        }
    }

    class ZeroToOne extends Transition {
        @Override
        public State execute(Condition input) {
            return State.WAIT_FOR_1;
        }
    }

    class OneToZero extends Transition {
        @Override
        public State execute(Condition input) {
            return State.WAIT_FOR_0;
        }
    }

    class EndOne extends Transition {
        @Override
        public State execute(Condition input) {
            return State.IDLE;
        }
    }

    class EndZero extends Transition {
        @Override
        public State execute(Condition input) {
            return State.IDLE;
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
