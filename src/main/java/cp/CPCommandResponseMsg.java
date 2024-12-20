package cp;

import core.Msg;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;
class CPCommandResponseMsg extends CPMsg {
    protected static final String CP_CMDRES_HEADER = "command_response";
    private int id;
    private String success; // "ok" or "error"
    private int length;
    private String message; // Optional
    private long checksum;

    public CPCommandResponseMsg() {}

    public CPCommandResponseMsg(int id, String success, String message) {
        this.id = id;
        this.success = success;
        this.message = message;
    }

    @Override
    protected void create(String sentence) {
        // Calculate length
        int length = (message != null ? message.length() : 0);

        // Build message without checksum
        StringBuilder sb = new StringBuilder();
        sb.append(CP_CMDRES_HEADER).append(" ");
        sb.append(id).append(" ").append(success).append(" ").append(length);
        if (message != null && !message.isEmpty()) {
            sb.append(" ").append(message);
        }

        String msgWithoutChecksum = sb.toString();

        // Calculate checksum
        this.checksum = calculateChecksum(msgWithoutChecksum);

        // Append checksum
        this.data = msgWithoutChecksum + " " + this.checksum;
        this.dataBytes = this.data.getBytes();

        // Call the superclass method to add 'cp' header
        super.create(this.data);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        // Exclude checksum for parsing
        int lastSpaceIndex = sentence.lastIndexOf(' ');
        String checksumStr = sentence.substring(lastSpaceIndex + 1);
        String msgWithoutChecksum = sentence.substring(0, lastSpaceIndex);

        // Recalculate the checksum and verify
        long calculatedChecksum = calculateChecksum(msgWithoutChecksum);
        try {
            this.checksum = Long.parseLong(checksumStr);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        if (this.checksum != calculatedChecksum) {
            throw new IllegalMsgException();
        }

        String[] parts = msgWithoutChecksum.split("\\s+", 5);

        if (parts.length < 5)
            throw new IllegalMsgException();

        if (!parts[0].equals(CP_CMDRES_HEADER))
            throw new IllegalMsgException();

        try {
            this.id = Integer.parseInt(parts[1]);
            this.success = parts[2];
            this.length = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        // Extract message if present
        if (length > 0) {
            if (parts.length < 5)
                throw new IllegalMsgException();
            this.message = parts[4];
        } else {
            this.message = null;
        }

        return this;
    }

    private long calculateChecksum(String data) {
        Checksum crc32 = new CRC32();
        byte[] bytes = data.getBytes();
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

    // Getters
    public int getId() { return id; }
    public boolean getSuccess() { return success.equals("ok"); }
    public String getMessage() { return message; }
}