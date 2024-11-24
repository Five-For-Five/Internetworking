package cp;

import core.Msg;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPCookieVerificationResponseMsg extends CPMsg {
    protected static final String CP_CVERRES_HEADER = "cookie_verification_response";
    private boolean success;
    private String cookie;
    private String message;
    private String checksum;

    @Override
    protected void create(String data) {
        // Data should be formatted as "success cookie [message]"
        String[] fields = data.split("\\s+");
        if (fields.length < 2) {
            throw new IllegalArgumentException();
        }

        this.success = fields[0].equalsIgnoreCase("ok");
        this.cookie = fields[1];
        this.message = (fields.length > 2) ? fields[2] : null;

        // Generate checksum for the message fields
        String checksumData = (success ? "ok" : "error") + " " + cookie;
        if (message != null) {
            checksumData += " " + message;
        }
        this.checksum = String.valueOf(generateChecksum(checksumData));

        // Construct the message
        StringBuilder msg = new StringBuilder(CP_CVERRES_HEADER + " " + (success ? "ok" : "error") + " " + cookie);
        if (message != null) {
            msg.append(" ").append(message);
        }
        msg.append(" ").append(this.checksum);
        this.data = msg.toString();
        super.create(this.data);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        if (!sentence.startsWith(CP_CVERRES_HEADER)) {
            throw new IllegalMsgException();
        }
        String[] fields = sentence.split("\\s+");
        if (fields.length < 3) {
            throw new IllegalMsgException();
        }

        this.success = fields[1].equalsIgnoreCase("ok");
        this.cookie = fields[2];
        this.message = (fields.length > 4) ? fields[3] : null;
        this.checksum = fields[fields.length - 1];

        // Verify checksum
        String checksumData = (success ? "ok" : "error") + " " + cookie;
        if (message != null) {
            checksumData += " " + message;
        }
        long calculatedChecksum = generateChecksum(checksumData);
        if (Long.parseLong(this.checksum) != calculatedChecksum) {
            throw new IllegalMsgException();
        }

        return this;
    }

    private long generateChecksum(String data) {
        Checksum crc32 = new CRC32();
        crc32.update(data.getBytes(), 0, data.length());
        return crc32.getValue();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCookie() {
        return cookie;
    }

    public String getMessage() {
        return message;
    }
}
