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

/**
 * class representing receiver
 * @author Olenka
 *
 */
public class Receiver implements Runnable {
    
    static final String HOSTNAME = "localhost";
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

    /**
     * constructor 
     * @param buffer byte buffer
     * @param port port to create a datagram socket
     */
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

    /**
     * recieves the information sent
     */
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

                buffer = dp.getData();
                port = dp.getPort();
                int number = parseByteArray();
                if (number != 0) {
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

    /**
     * gets a byte array, parses it and stores the received information into the
     * corresponding variables
     * 
     * @return received file size
     */
    public int parseByteArray() {
        byte[] seqNrBytes = new byte[1];
        byte[] checkSumBytes = new byte[8];
        System.arraycopy(buffer, 0, seqNrBytes, 0, seqNrBytes.length);
        System.arraycopy(buffer, 1, checkSumBytes, 0, checkSumBytes.length);
        seqNr = seqNrBytes[0];
        if (seqNr != 0 && seqNr != 1) {
            return 0; // TODO
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
                file = new File(fileName);
                fileSize = fileLengthByte[0] << 24 | (fileLengthByte[1] & 0xFF) << 16 | (fileLengthByte[2] & 0xFF) << 8
                        | (fileLengthByte[3] & 0xFF);
                dataCounter = fileSize / (buffer.length - 9);
                // if (fileSize % (buffer.length - 9) != 0) {
                // ++dataCounter;
                // }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else {
            long dataSize = buffer.length - 9;
            if (dataCounter == 0) {
                dataSize = fileSize % (buffer.length - 9);
            }
           
            data = new byte[(int) dataSize];
            System.arraycopy(buffer, 9, data, 0, (int) dataSize); // file.length() fileSize
      
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

    /**
     * sends an acknoledgement to sender
     */
    public void sendACK() {
        try {
            byte[] seqBytes = new byte[] { (byte) seqNr };

            ds.send(new DatagramPacket(seqBytes, 1, addressToSend, portToSend));

            System.out.println("Receiver: Sent ACK: " + seqBytes[0]);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * generates checksum
     * 
     * @param bytes
     *            number of bytes
     * @return checksum
     */
    private long getChecksum(byte[] bytes) {
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return checksum.getValue();
    }

    /**
     * defines reciever's condition
     * 
     * @return an actual condiition
     */
    public Condition defineConditon() {
        byte[] dataToCheck;
        if (currentState == State.IDLE) {
            dataToCheck = fileNameByte;
        } else {
            dataToCheck = data;
        }

        actualChecksum = getChecksum(dataToCheck);

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

    /**
     * triggers a transition in state machine
     * 
     * @param input
     *            an input condition
     */
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

    /**
     * enum representing states
     * 
     * @author Olenka
     *
     */
    enum State {
        IDLE, WAIT_FOR_0, WAIT_FOR_1
    }

    /**
     * enum representing conditions
     * 
     * @author Olenka
     *
     */
    enum Condition {
        CHECK_OK_NOT_ALL_REC, CHECK_OK_ALL_REC, DUPLICATE_SQNR_OR_CHECK_NOT_OK
    }

    /**
     * class representing transition
     * 
     * @author Olenka
     *
     */
    abstract class Transition {
        /**
         * triggers a transition
         * 
         * @param input
         *            an input condition
         * @return new state after the transition execution
         */
        abstract public State execute(Condition input);
    }

    /**
     * special transition that leaves a state machine in the same state as it was
     * before it
     * 
     * @author Olenka
     *
     */
    class StayInState extends Transition {
        @Override
        public State execute(Condition input) {

            if (checkSum == actualChecksum) {
                sendACK();
            }
            return currentState;

        }
    }

    /**
     * stating a new session
     * 
     * @author Olenka
     *
     */
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

    /**
     * transition from state_0 to state_1
     * 
     * @author Olenka
     *
     */
    class ZeroToOne extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            --dataCounter;
            sendACK();
            return State.WAIT_FOR_1;
        }
    }

    /**
     * transition from state_1 to state_0
     * 
     * @author Olenka
     *
     */
    class OneToZero extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            --dataCounter;
            sendACK();
            return State.WAIT_FOR_0;
        }
    }

    /**
     * session ends in state_1
     * 
     * @author Olenka
     *
     */
    class EndOne extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            sendACK();
            return State.IDLE;
        }
    }

    /**
     * session ends in state_0
     * 
     * @author Olenka
     *
     */
    class EndZero extends Transition {
        @Override
        public State execute(Condition input) {
            saveFile(data);
            sendACK();
            return State.IDLE;
        }
    }

    /**
     * saves the received data into a file
     * 
     * @param data
     *            bytes received
     */
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

    /**
     * gets a current state
     * 
     * @return current state
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * sets a current state
     * 
     * @param currentState
     *            state to be set
     */
    public void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    /**
     * gets sequence number
     * 
     * @return sequence number
     */
    public int getSeqNr() {
        return seqNr;
    }

    /**
     * gets checksum
     * 
     * @return checksum
     */
    public long getCheckSum() {
        return checkSum;
    }

    /**
     * thread run method
     */
    @Override
    public void run() {
        this.receive();
    }
}
