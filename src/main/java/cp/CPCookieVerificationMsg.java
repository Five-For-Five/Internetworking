package cp;

import core.Msg;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPCookieVerificationMsg extends CPMsg {
    protected static final String CP_CVERQ_HEADER = "cookie_verification_request";
    private String cookie;
    private String client;
    private String checksum;

    @Override
    protected void create(String data) {
        // Data should be formatted as "cookie client"
        String[] fields = data.split("\\s+", 2);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Invalid data format for Cookie Verification Message");
        }
        this.cookie = fields[0];
        this.client = fields[1];

        // Generate checksum for the message fields
        String checksumData = cookie + " " + client;
        this.checksum = String.valueOf(generateChecksum(checksumData));

        // Construct the message
        this.data = CP_CVERQ_HEADER + " " + cookie + " " + client + " " + this.checksum;
        super.create(this.data);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        if (!sentence.startsWith(CP_CVERQ_HEADER)) {
            throw new IllegalMsgException();
        }
        String[] fields = sentence.split("\\s+");
        if (fields.length != 4) {
            throw new IllegalMsgException();
        }

        this.cookie = fields[1];
        this.client = fields[2];
        this.checksum = fields[3];

        // Verify checksum
        String checksumData = cookie + " " + client;
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

    public String getCookie() {
        return cookie;
    }

    public String getClient() {
        return client;
    }
}
