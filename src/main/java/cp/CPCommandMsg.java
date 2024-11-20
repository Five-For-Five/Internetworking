package cp;

import core.Msg;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPCommandMsg extends CPMsg {
    protected static final String CP_COMMAND_HEADER = "command";
    private int id;
    private int cookie;
    private int length;
    private long checksum;
    private String command;
    private String message;
    public CPCommandMsg() {}

    public CPCommandMsg(int cookie, int id) {
        this.cookie = cookie;
        this.id = id;
    }

    @Override
    protected void create(String sentence) {
        // Split the input into command and optional message
        String[] parts = sentence.split("\\s+", 2);
        String command = parts[0];
        String message = (parts.length > 1) ? parts[1] : null;

        // Validate command
        if (!command.equals("status") && !command.equals("print")) {
            throw new IllegalArgumentException("Invalid command: " + command);
        }

        // Set length based on command and message
        this.length = command.length() + (message != null ? message.length() + 1 : 0); // +1 for the separating space if message exists

        // Build the message excluding the checksum
        StringBuilder sb = new StringBuilder();
        sb.append(CP_COMMAND_HEADER).append(" ");
        sb.append(id).append(" ").append(cookie).append(" ").append(length).append(" ");
        sb.append(command);
        if (message != null && !message.isEmpty()) {
            sb.append(" ").append(message);
        }

        String msgWithoutChecksum = sb.toString();

        // Calculate checksum
        this.checksum = calculateChecksum(msgWithoutChecksum);

        // Append checksum to the message
        this.data = msgWithoutChecksum + " " + this.checksum;
        this.dataBytes = this.data.getBytes();

        // Call the superclass method to finalize message
        super.create(this.data);
    }


    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        // Exclude checksum for parsing
        int lastSpaceIndex = sentence.lastIndexOf(' ');
        if (lastSpaceIndex == -1)
            throw new IllegalMsgException();

        String checksumStr = sentence.substring(lastSpaceIndex + 1);
        String msgWithoutChecksum = sentence.substring(0, lastSpaceIndex);

        // Exclude 'cp ' from checksum calculation
        String checksumData = msgWithoutChecksum.substring(3); // Exclude 'cp '

        // Recalculate the checksum and verify
        long calculatedChecksum = calculateChecksum(checksumData);
        try {
            this.checksum = Long.parseLong(checksumStr);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        if (this.checksum != calculatedChecksum) {
            throw new IllegalMsgException();
        }

        // Now parse the message
        String[] initialParts = msgWithoutChecksum.split("\\s+", 4);

        if (initialParts.length < 4)
            throw new IllegalMsgException();

        if (!initialParts[0].equals(CP_COMMAND_HEADER))
            throw new IllegalMsgException();

        try {
            this.id = Integer.parseInt(initialParts[1]);
            this.cookie = Integer.parseInt(initialParts[2]);
            this.length = Integer.parseInt(initialParts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        // Now, extract the command and message from the rest of the string
        // Find the index of the length field in the original message
        int headerLength = initialParts[0].length() + initialParts[1].length() + initialParts[2].length() + initialParts[3].length() + 4; // +4 for spaces
        String commandAndMessage = msgWithoutChecksum.substring(headerLength);

        if (commandAndMessage.length() != length)
            throw new IllegalMsgException();

        // Now extract command and message
        String[] cmdParts = commandAndMessage.split("\\s+", 2);
        this.command = cmdParts[0];

        // Validate command
        if (!this.command.equals("status") && !this.command.equals("print")) {
            throw new IllegalMsgException();
        }

        if (cmdParts.length > 1) {
            this.message = cmdParts[1];
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
    public int getCookie() { return cookie; }
    public String getCommand() { return command; }
    public String getMessage() { return message; }
}
