/**
 * The {@code BluePackage} class facilitates the transmission of large data as smaller parcels
 * over a Bluetooth communication channel. It handles parcel splitting, parcel tracking, and provides
 * utility methods to manage and request specific parcels.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 *
 * This class is essential for managing data transfers where the size of a single transmission is limited.
 */
package offgrid.geogram.ble;


import static offgrid.geogram.ble.BluetoothCentral.maxSizeOfMessages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

public class BluetoothMessage {

    private static final int TEXT_LENGTH_PER_PARCEL = maxSizeOfMessages;
    private final int messageParcelsTotal;
    private String[] messageParcels;
    private final String checksum;
    private static final String TAG = "BluetoothMessage";
    private final String
            id,
    idFromSender;
    private String message = null;
    private final TreeMap<String, String> messageBox = new TreeMap<>();

    public BluetoothMessage(String idFromSender, String messageToSend) {
        this.id = generateRandomId();
        this.idFromSender = idFromSender;
        this.message = messageToSend;
        this.messageParcelsTotal = (int) Math.ceil((double) message.length() / TEXT_LENGTH_PER_PARCEL);
        this.checksum = calculateChecksum(message);
        splitDataIntoParcels();
    }

    /**
     * Calculates a 4-letter checksum for the given data.
     *
     * @param data The input data for which to calculate the checksum.
     * @return A 4-letter checksum.
     */
    public String calculateChecksum(String data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        int sum = 0;
        for (char c : data.toCharArray()) {
            sum += c; // Add ASCII value of each character
        }

        // Reduce the sum to 4 letters
        char[] checksum = new char[4];
        for (int i = 0; i < 4; i++) {
            checksum[i] = (char) ('A' + (sum % 26));
            sum /= 26; // Shift to the next letter
        }
        return new String(checksum);
    }

    /**
     * Splits the data into smaller parcels based on TEXT_LENGTH_PER_PARCEL.
     * Each parcel will contain at most {@code TEXT_LENGTH_PER_PARCEL} characters.
     */
    private void splitDataIntoParcels() {
        int dataLength = message.length();
        messageParcels = new String[messageParcelsTotal];

        // add the header
        String uidHeader = id + "0";
        String header =
                uidHeader
                + ":"
                + idFromSender
                + ":"
                + messageParcelsTotal
                + ":"
                + checksum
                ;
        messageBox.put(uidHeader, header);

        for (int i = 0; i < messageParcelsTotal; i++) {
            int start = i * TEXT_LENGTH_PER_PARCEL;
            int end = Math.min(start + TEXT_LENGTH_PER_PARCEL, dataLength);
            String text = message.substring(start, end);
            messageParcels[i] = text;
            int value = i + 1;
            String uid = id + value;
            messageBox.put(uid,
                    uid
                    + ":"
                    + text
            );
        }
    }
    /**
     * Generates a unique random ID using two bytes for each data transmission.
     *
     * @return A unique 2-byte random ID as a hexadecimal string.
     */
    public String generateRandomId() {
        Random random = new Random();
        char firstChar = (char) ('A' + random.nextInt(26)); // Random letter A-Z
        char secondChar = (char) ('A' + random.nextInt(26)); // Random letter A-Z
        return "" + firstChar + secondChar;
    }

    public int getMessageParcelsTotal() {
        return this.messageBox.size();
    }

    public String[] getMessageParcels() {
        return messageParcels;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getId() {
        return id;
    }

    public String getIdFromSender() {
        return idFromSender;
    }

    public String getMessage() {
        return message;
    }

    public TreeMap<String, String> getMessageBox() {
        return messageBox;
    }

    public String getOutput() {
        String output = "";
        if(messageBox.isEmpty()){
            return "";
        }
        for (String key : messageBox.keySet()) {
            output += messageBox.get(key) + " | ";
        }
        return output.substring(0, output.length() - 3);
    }
}
