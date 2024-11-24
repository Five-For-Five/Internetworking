package cp;

import core.*;
import exceptions.*;
import phy.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Random;

public class CPProtocol extends Protocol {
    private static final int CP_TIMEOUT = 2000;
    private static final int CP_HASHMAP_SIZE = 20;
    private int cookie;
    private int id;
    private PhyConfiguration PhyConfigCommandServer;
    private PhyConfiguration PhyConfigCookieServer;
    private final PhyProtocol PhyProto;
    private final cp_role role;
    HashMap<PhyConfiguration, Cookie> cookieMap;
    Random rnd;

    private static final int MAX_ID = 65535;
    private int lastMessageId = -1;

    private int generateMessageId() {
        if (lastMessageId >= MAX_ID) {
            lastMessageId = 0;
        } else {
            lastMessageId++;
        }
        return lastMessageId;
    }

    private enum cp_role {
        CLIENT, COOKIE, COMMAND
    }

    // Constructor for clients
    public CPProtocol(InetAddress rname, int rp, PhyProtocol phyP) throws UnknownHostException {
        this.PhyConfigCommandServer = new PhyConfiguration(rname, rp, proto_id.CP);
        this.PhyProto = phyP;
        this.role = cp_role.CLIENT;
        this.cookie = -1;
    }
    // Constructor for servers
    public CPProtocol(PhyProtocol phyP, boolean isCookieServer) {
        this.PhyProto = phyP;
        if (isCookieServer) {
            this.role = cp_role.COOKIE;
            this.cookieMap = new HashMap<>();
            this.rnd = new Random();
        } else {
            this.role = cp_role.COMMAND;
        }
    }

    public void setCookieServer(InetAddress rname, int rp) throws UnknownHostException {
        this.PhyConfigCookieServer = new PhyConfiguration(rname, rp, proto_id.CP);
    }


    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {

        if (cookie < 0) {
            // Request a new cookie from server
            // Either updates the cookie attribute or returns with an exception
            requestCookie();
        }

        // Task 1.2.1: complete send method
        // 1a. Check that a legal command is issued
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        int messageId = generateMessageId();
        // 1b. Create a command message object
        CPCommandMsg cmdMsg = new CPCommandMsg(cookie, messageId);
        cmdMsg.create(s);

        this.id = messageId;
        // 1c. Send the command to the command server
        // Use the PHY layer to send the message
        this.PhyProto.send(new String(cmdMsg.getDataBytes()), this.PhyConfigCommandServer);
    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        CPMsg cpmIn = null;

        if (this.role == cp_role.COOKIE) {
            try {
                // Wait indefinitely for a message
                Msg receivedMsg = this.PhyProto.receive();

                // Verify the message is a CP protocol message
                if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP) {
                    return null;
                }

                // Parse the incoming message
                try {
                    cpmIn = (CPMsg) new CPMsg().parse(receivedMsg.getData());

                    if (cpmIn instanceof CPCookieRequestMsg) {
                        // Handle cookie request
                        CPCookieRequestMsg cookieRequestMsg = (CPCookieRequestMsg) cpmIn;

                        // Generate a cookie
                        int cookieValue = rnd.nextInt(65536); // Generate a cookie between 0 and 65535
                        long timeOfCreation = System.currentTimeMillis();

                        // Store the cookie associated with the client
                        PhyConfiguration clientConfig = (PhyConfiguration) receivedMsg.getConfiguration();
                        Cookie cookie = new Cookie(timeOfCreation, cookieValue);
                        cookieMap.put(clientConfig, cookie);

                        // Create a CPCookieResponseMsg
                        CPCookieResponseMsg responseMsg = new CPCookieResponseMsg(true);
                        responseMsg.create(String.valueOf(cookie.getCookieValue()));

                        // Send it back to the client
                        this.PhyProto.send(new String(responseMsg.getDataBytes()), clientConfig);
                    } else {
                        // Unexpected message type, discard or handle accordingly
                    }

                } catch (IWProtocolException e) {
                    return null;
                }

            } catch (SocketTimeoutException e) {
                return null;
            }
        } else if (this.role == cp_role.COMMAND) {
            // Server-side receive implementation
            try {
                // Wait indefinitely for a message
                Msg receivedMsg = this.PhyProto.receive();

                // Verify the message is a CP protocol message
                if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP) {
                    return null;
                }

                // Parse the command message
                try {
                    cpmIn = (CPMsg) new CPMsg().parse(receivedMsg.getData());

                    if (cpmIn instanceof CPCommandMsg) {
                        CPCommandMsg commandMsg = (CPCommandMsg) cpmIn;

                        // Extract fields
                        String command = commandMsg.getCommand();
                        String message = commandMsg.getMessage();
                        int msgId = commandMsg.getId();
                        int msgCookie = commandMsg.getCookie();

                        // For simplicity, assume the cookie is valid
                        // Process the message by changing it to uppercase
                        String responseMessage = null;
                        String responseStatus = "ok";

                        if (command.equals("print")) {
                            responseMessage = (message != null) ? message.toUpperCase() : null;
                        } else if (command.equals("status")) {
                            // Handle status command
                            responseMessage = "{\"status\": \"Server is running\"}";
                        } else {
                            responseStatus = "error";
                            responseMessage = "Unknown command";
                        }

                        // Create a CPCommandResponseMsg
                        CPCommandResponseMsg responseMsg = new CPCommandResponseMsg(msgId, responseStatus, responseMessage);
                        responseMsg.create(null);

                        // Send it back to the client
                        PhyConfiguration clientConfig = (PhyConfiguration) receivedMsg.getConfiguration();
                        this.PhyProto.send(new String(responseMsg.getDataBytes()), clientConfig);
                    }

                } catch (IWProtocolException e) {
                    // Silently discard if parsing fails
                    return null;
                }

            } catch (SocketTimeoutException e) {
                // Handle timeout
                return null;
            }
        } else if (this.role == cp_role.CLIENT) {
            // Client-side receive implementation
            try {
                // 2a. Wait for response with a 3-second timeout
                Msg receivedMsg = this.PhyProto.receive(CP_TIMEOUT);

                // Verify the message is a CP protocol message
                if (((PhyConfiguration) receivedMsg.getConfiguration()).getPid() != proto_id.CP) {
                    return null;
                }

                // 2b. Parse the message
                try {
                    cpmIn = (CPMsg) new CPMsg().parse(receivedMsg.getData());

                } catch (IWProtocolException e) {
                    // Silently discard if parsing fails
                    return null;
                }

                // 2c. Check if the response matches the sent command message
                if (cpmIn instanceof CPCommandResponseMsg) {
                    CPCommandResponseMsg responseMsg = (CPCommandResponseMsg) cpmIn;

                    // Compare message IDs
                    if (responseMsg.getId() != id) {
                        return null;
                    }

                    // 2d. Check if the command server accepted the command
                    if (!responseMsg.getSuccess()) {
                        //throw new IWProtocolException();
                    }

                    // 2e. Return to the client appropriately
                    return responseMsg;
                }

            } catch (SocketTimeoutException e) {
                // Handle timeout
                //throw new IWProtocolException("Timeout waiting for response from server");
            }
        }

        return null;
    }


    // Method for the client to request a cookie
    public void requestCookie() throws IOException, IWProtocolException {
        CPCookieRequestMsg reqMsg = new CPCookieRequestMsg();
        reqMsg.create(null);
        Msg resMsg = new CPMsg();

        boolean waitForResp = true;
        int count = 0;
        while(waitForResp && count < 3) {
            this.PhyProto.send(new String(reqMsg.getDataBytes()), this.PhyConfigCookieServer);

            try {
                Msg in = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP)
                    continue;
                resMsg = ((CPMsg) resMsg).parse(in.getData());
                if(resMsg instanceof CPCookieResponseMsg)
                    waitForResp = false;
            } catch (SocketTimeoutException e) {
                count += 1;
            } catch (IWProtocolException ignored) {
            }
        }

        if(count == 3)
            throw new CookieRequestException();
        if(resMsg instanceof CPCookieResponseMsg && !((CPCookieResponseMsg) resMsg).getSuccess()) {
            throw new CookieRequestException();
        }
         assert resMsg instanceof CPCookieResponseMsg;
         this.cookie = ((CPCookieResponseMsg)resMsg).getCookie();
    }
}

class Cookie {
    private final long timeOfCreation;
    private final int cookieValue;

    public Cookie(long toc, int c) {
        this.timeOfCreation = toc;
        this.cookieValue = c;
    }

    public long getTimeOfCreation() {
        return timeOfCreation;
    }

    public int getCookieValue() { return cookieValue;}
}

