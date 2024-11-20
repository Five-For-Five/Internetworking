package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPMsg extends Msg {
    protected static final String CP_HEADER = "cp";
    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_HEADER))
            throw new IllegalMsgException();

        String[] parts = sentence.split("\\s+", 2);
        if (parts.length < 2)
            throw new IllegalMsgException();

        String messageType = parts[1].split("\\s+")[0]; // Get the message type
        CPMsg parsedMsg;

        switch (messageType) {
            case CPCookieRequestMsg.CP_CREQ_HEADER:
                parsedMsg = new CPCookieRequestMsg();
                break;
            case CPCookieResponseMsg.CP_CRES_HEADER:
                parsedMsg = new CPCookieResponseMsg();
                break;
            case CPCommandMsg.CP_COMMAND_HEADER:
                parsedMsg = new CPCommandMsg();
                break;
            case CPCommandResponseMsg.CP_CMDRES_HEADER:
                parsedMsg = new CPCommandResponseMsg();
                break;
            default:
                throw new IllegalMsgException();
        }

        // Remove the 'cp ' prefix before parsing
        String messageContent = sentence.substring(CP_HEADER.length() + 1);
        parsedMsg.parse(messageContent);
        return parsedMsg;
    }

}
