import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Represents an alternating bit sender.
 * 
 * @author Thilo Barth
 */
public class AltBitSender {

	private boolean log;

	private DatagramSocket socketSend;

	//Indexes of Data packet
	private final int startSeq = 0;
	private final int startChecksum = 1;
	private final int startFileData = 9;
	//Indexes of first packet
	private final int startFileLength = 9;
	private final int startFileName = 13;

	//Every chance has to be <=1
	private final double chanceDelete = 0.1;
	private final double chanceDuplicate = 0.05;
	private final double chanceWrongBits = 0.05;

	private int packetLength;
	private int packetFileDataLength;
	private int packetCount;
	private byte seq = 0;

	private String fileName;
	private byte[] fileArray;
	private int fileLength;

	private InetAddress inetAddr;
	private int port;

	/**
	 * Initializes an AltBitSender.
	 * 
	 * @param hostName of the receiver
	 * @param port of the receiver and of this sender
	 * @param packetLength of the packets to be send
	 * @param filePath to the file to be send
	 * @param log if log should be activated
	 * @throws SocketException if connection is bad
	 * @throws FileNotFoundException if file could not be found or file name is too long
	 * @throws IOException if file stream / location is bad
	 * @throws NullPointerException if some parameters are null
	 */
	public AltBitSender(String hostName, int port, int packetLength, String filePath, boolean log)
			throws SocketException, FileNotFoundException, IOException, NullPointerException {

		this.packetLength = packetLength;
		this.packetFileDataLength = packetLength - startFileData;
		this.log = log;

		readFile(filePath);

		socketSend = new DatagramSocket(port);
		socketSend.setSoTimeout(1000);
		this.packetLength = packetLength;
		this.inetAddr = InetAddress.getByName(hostName);
		this.port = port;
	}

	/**
	 * Sends the file.
	 * 
	 * @throws IOException if connection is bad
	 */
	public void send() throws IOException {
		boolean ackReceived = false;

		do { // Send fileName and size
			sendFirstPacket();
			ackReceived = receiveAck();
		} while (!ackReceived);

		seq = (byte) 1;

		for (int i = 0; i < packetCount; i++) { // Send file
			do {// Correct ack received
				sendFilePacket(i);
				if ((ackReceived = receiveAck()) == true) {
					seq = (byte) ((i)%2);
				}
			} while (!ackReceived);
		}

	}

	/**
	 * Sends the first packet.
	 * 
	 * @throws IOException if connection is bad
	 */
	private void sendFirstPacket() throws IOException {

		// Create packet
		byte[] packetByte = new byte[packetLength];
		byte[] data = fileName.getBytes();
		packetByte[this.startSeq] = seq;
		writeIntoByteArray(packetByte, ByteBuffer.allocate(Long.BYTES).putLong(getChecksum(data)).array(), startChecksum);
		writeIntoByteArray(packetByte, ByteBuffer.allocate(Integer.BYTES).putInt(fileLength).array(), startFileLength);
		writeIntoByteArray(packetByte, data, startFileName);

		printPacket(packetByte, "Sender");

		sendPacket(packetByte);
	}

	/**
	 * Sends the packet of the file with the committed packetNumber.
	 * 
	 * @param packetNumber of all file packets
	 * @throws IOException if connection is bad
	 */
	private void sendFilePacket(int packetNumber) throws IOException {
		int packetSize = packetFileDataLength;

		//Last packet has maybe less bytes
		if (fileArray.length - packetNumber * packetFileDataLength < packetFileDataLength) {
			packetSize = fileArray.length - packetNumber * (packetFileDataLength);
		}

		// Copy data to be send in data array
		byte[] data = new byte[packetSize];
		System.arraycopy(fileArray, packetNumber * (packetFileDataLength), data, 0, packetSize);

		// Create packet
		byte[] packetByte = new byte[packetLength];
		packetByte[startSeq] = seq;
		writeIntoByteArray(packetByte, ByteBuffer.allocate(Long.BYTES).putLong(getChecksum(data)).array(), this.startChecksum);
		writeIntoByteArray(packetByte, data, startFileData);

		printPacket(packetByte, "Sender");

		sendPacket(packetByte);
	}

	/**
	 * Sends a packet to the receiver. It calls first a method to simulate an
	 * unreliable channel with dropping packets, duplicating packets and 
	 * change (wrong bits) packets.
	 * 
	 * @param packet to be sent
	 * @throws IOException if connection is bad
	 */
	private void sendPacket(byte[] packet) throws IOException {
		Random chance = new Random();

		if (simulateDelete(chance)) {
			return;
		}

		if (packet.length != 0) {
			byte[] packetToSend = simulateWrongBits(packet, chance);
			DatagramPacket dp = new DatagramPacket(packetToSend, packetLength, inetAddr, port);

			if (simulateDuplicate(chance)) {
				socketSend.send(dp);
			}

			socketSend.send(dp);
		}
	}

	/**
	 * Simulates if the packet got changed during sending.
	 * 
	 * @param packet to be changed
	 * @param chance a random generator
	 * @return the edited packet
	 */
	private byte[] simulateWrongBits(byte[] packet, Random chance) {
		byte[] packetToReturn = packet.clone();
		if (chance.nextDouble() <= chanceWrongBits) {
			for (int i = 0; i < packet.length; i++) {
				packetToReturn[i] = packet[packet.length - 1 - i];
			}
			printMessage("AltBitSender: Packet got editted.");
		}

		return packetToReturn;
	}

	/**
	 * Calculates if the packet should be send twice or not.
	 * 
	 * @param chance a random generator
	 * @return true if the packet should be send twice, otherwise false.
	 */
	private boolean simulateDuplicate(Random chance) {
		if (chance.nextDouble() <= chanceDuplicate) {
			printMessage("AltBitSender: Packet dublicated");
			return true;
		}
		return false;
	}

	/**
	 * Calculates if the packet should be deleted or not.
	 * 
	 * @param chance a random generator
	 * @return true if the packet should be dropped, otherwise false.
	 */
	private boolean simulateDelete(Random chance) {
		if (chance.nextDouble() <= chanceDelete) {
			printMessage("AltBitSender: Packet dropped");
			return true;
		}
		return false;
	}

	/**
	 * Tries to receive the acknowledge number and checks if it is correct.
	 * 
	 * @return true if ack was correct, returns false if ack was incorrect or
	 *         timeout
	 * @throws IOException if connection is bad
	 */
	private boolean receiveAck() throws IOException {
		byte[] ack = new byte[1];
		DatagramPacket packet = new DatagramPacket(ack, ack.length, inetAddr, port);
		try {
			socketSend.receive(packet);
			printPacket(ack, "Receiving Ack");
			if (ack[0] == seq) { // Check if send seq equals ack
				return true;
			}
			// return false wrong ack
		} catch (SocketTimeoutException e) {
			printMessage("AltBitSender: Receiving Ack timeout");
		} // return false timeout
		return false;
	}

	/**
	 * Generates a CRC32 checksum for the committed bytes.
	 * 
	 * @param bytes on which the checksum should be generated
	 * @return long, the checksum of bytes
	 */
	private long getChecksum(byte[] bytes) {
		Checksum checksum = new CRC32();
		checksum.update(bytes, 0, bytes.length);
		return checksum.getValue();
	}

	/**
	 * Writes the bytes of arrayCopy in arrayPaste starting at position start. Start
	 * counts from 0.
	 * 
	 * @param arrayPaste in which should be copied
	 * @param arrayCopy from which should be copied
	 * @param start index of pasting in array. Counts from 0.
	 * @return false if arrayPaste is too small. True if success.
	 */
	private boolean writeIntoByteArray(byte[] arrayPaste, byte[] arrayCopy, int start) {
		if (arrayPaste.length < arrayCopy.length + start) {
			return false; // Size does not match
		}
		for (int i = 0; i < arrayCopy.length; i++) {
			arrayPaste[i + start] = arrayCopy[i];
		}
		return true;
	}

	/**
	 * Reads the file in a byte array and calculates the numbers of packets to be
	 * send.
	 * 
	 * @param filePath the location of the file.
	 * @throws FileNotFoundException if the file was not found or file name is too long
	 * @throws IOException if the file could not be read
	 */
	private void readFile(String filePath) throws FileNotFoundException, IOException {
		File file = new File(filePath);

		fileName = file.getName();
		if (fileName == null || fileName.length() > (packetLength - startFileName) / 2) {
			throw new FileNotFoundException(
					"File Name too long. Max" + (int) ((packetLength - startFileName) / 2) + " chars.");
		} // File name too long

		// Read in file to byte array
		fileLength = (int) file.length();
		fileArray = new byte[fileLength];
		InputStream is = new FileInputStream(file);
		is.read(fileArray);
		is.close();

		this.packetCount = fileLength / (packetFileDataLength);
		if (fileLength % (packetFileDataLength) != 0) { // Always round up
			++packetCount;
		}
	}

	/**
	 * Print information of data packet like seq/ack or checksum.
	 * 
	 * @param packet to be printed
	 * @param stream who received/send the packet
	 */
	private void printPacket(byte[] packet, String stream) {
		String toPrint = "---------------------\n" + "AltBitSender: " + stream + ":\nSeq/Ack: " + packet[0] + "\nChecksum: ";
		for (int i = startChecksum; i < startChecksum + Long.BYTES; i++) {
			toPrint = toPrint + packet[i];
		}
		printMessage(toPrint);
	}

	/**
	 * Prints the committed message if log is activated.
	 * 
	 * @param message to be printed
	 */
	private void printMessage(String message) {
		if (log) {
			System.out.println(message);
		}
	}

}