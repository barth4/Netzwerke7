import java.io.File;
import java.util.zip.CRC32;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.swing.text.html.parser.ParserDelegator;

public class Receiver {
    public static final String PATH = "d:\\";
    private byte[] buffer;
    private int counter = 0;
    private String name;

    private DatagramSocket ds;
    private DatagramPacket dp;

    private State currentState;
    private Transition[][] transition;

    long checkSum;
    private int seqNr;
    private String fileName;
    private int fileSize;
    byte[] data;

     public Receiver(byte[] buffer, int port) {
     try {
     this.buffer = buffer;
     this.ds = new DatagramSocket(port);
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
                parseByteArray(dp.getData());
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

    public int parseByteArray(byte[] receivedData) {
        byte[] seqNrBytes = new byte[1];
        byte[] checkSumBytes = new byte[8];
        int fileLength = 0;
        System.arraycopy(receivedData, 0, seqNrBytes, 0, seqNrBytes.length);
        System.arraycopy(receivedData, 1, checkSumBytes, 0, checkSumBytes.length);
        seqNr = seqNrBytes[0];

        
        checkSum = checkSumBytes[0] << 56 | (checkSumBytes[1] & 0xFF) << 48 | (checkSumBytes[2] & 0xFF) << 40
                | (checkSumBytes[3] & 0xFF) << 32 | (checkSumBytes[4] & 0xFF) << 24 | (checkSumBytes[5] & 0xFF) << 16
                | (checkSumBytes[6] & 0xFF) << 8 | (checkSumBytes[7] & 0xFF);

        // if (getCurrentState() == State.IDLE) {
        byte[] fileLengthByte = new byte[4];
        byte[] fileNameByte = new byte[receivedData.length - 13];
        System.arraycopy(receivedData, 9, fileLengthByte, 0, fileLengthByte.length);
        System.arraycopy(receivedData, 13, fileNameByte, 0, fileNameByte.length);

        String fileName;
        try {
            fileName = new String(fileNameByte, "UTF-8");// if the charset is UTF-8; "ISO-8859-1"
            System.out.println(fileName);

            fileLength = fileLengthByte[0] << 24 | (fileLengthByte[1] & 0xFF) << 16 | (fileLengthByte[2] & 0xFF) << 8
                    | (fileLengthByte[3] & 0xFF);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // } else {
        // data = new byte[receivedData.length - 9];
        // System.arraycopy(receivedData, 9, data, 0, data.length);
        // }
        return fileLength;

    }

    public void sendACK() {
        try {
            byte [] seqBytes = new byte[] {(byte) seqNr};
            DatagramPacket ack = new DatagramPacket(seqBytes, 1);
            ds.send(ack);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    public static void saveData() {// input param inputstream
    }

    public Condition defineConditon() {
        CRC32 crc = new CRC32();
        crc.update(data);
       long  actualChecksum = crc.getValue();
        if (checkSum != actualChecksum || (currentState == State.WAIT_FOR_0 && this.seqNr == 1)
                || (currentState == State.WAIT_FOR_1 && this.seqNr == 0)) {
            return Condition.DUPLICATE_SQNR_OR_CHECK_NOT_OK;
        }
        File file = new File(PATH + fileName);
        if ((long) (data.length + this.fileSize) == file.length()) {
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
            if (checkSum == data.length) {
                sendACK();
            }
            return currentState;

        }
    }

    class Start extends Transition {
        @Override
        public State execute(Condition input) {
            File file = new File(PATH + fileName);
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
}
