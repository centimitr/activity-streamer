import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

@SuppressWarnings("WeakerAccess")
public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static Gson g = new Gson();

    private ArrayList<Connectivity> clientConns = new ArrayList<>();
    private Connectivity serverConn;
    private Listener listener;
    private boolean term = false;

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public Control() {
        setMessageHandlers();
        connectServerNode();
        startListen();
    }


    private void startListen() {
        try {
            listener = new Listener(Settings.getLocalPort(), this::handleIncomingConn);
        } catch (IOException e) {
            log.fatal("failed to startup a listening thread: " + e);
            System.exit(-1);
        }
    }

    private void connectServerNode() {
        if (Settings.getRemoteHostname() != null) {
            try {
                serverConn = new Connectivity(Settings.getRemoteHostname(), Settings.getRemotePort(), this::startAuthentication);
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    private void setMessageHandlers() {
        MessageProtocol.getInstance()
                .registerHandler(MessageCommands.LOGOUT, context -> {
                    Message m = context.read(Message.class);
                    // {"command":"LOGOUT"}
                    log.info("@LOGOUT");
                    context.close();
                }).registerHandler(MessageCommands.INVALID_MESSAGE, context -> {
                    MessageInfo m = context.read(MessageInfo.class);
                    // {"command":"INVALID_MESSAGE", "info":"this is info"}
                    log.info("@INVALID_MESSAGE: " + m.info);
                }
        );
    }

    private synchronized void startAuthentication(Connectivity c) {
        boolean ok;
        ok = c.sendln(new MessageSecret(MessageCommands.AUTHENTICATE.name(), Settings.getSecret()));
        log.info("Authentication: " + ok);
        
    }

    private synchronized void handleIncomingConn(Listener l, Socket s) {
        log.debug("incoming connection: " + Settings.socketAddress(s));

        try {
            Connectivity c = new Connectivity(s, conn -> {
                try {
                    boolean term = false;
                    String data;
                    while (!term && (data = conn.in.readLine()) != null) {
                        term = process(conn, data);
                    }
                    log.debug("connection closed to " + Settings.socketAddress(s));
                    handleClosedConn(conn);
                    conn.in.close();
                } catch (IOException e) {
                    log.error("connection " + Settings.socketAddress(s) + " closed with exception: " + e);
                    handleClosedConn(conn);
                }
            });
            clientConns.add(c);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void handleClosedConn(Connectivity c) {
        if (!term) clientConns.remove(c);
    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    private synchronized boolean process(Connectivity c, String msg) {
        // todo: remove this debug use code
        if (!msg.startsWith("{")) {
            System.out.println("RCV: " + msg);
            c.sendln("R: " + msg);
            return false;
        }
        MessageContext mc = new MessageContext();
        boolean ok = mc.parse(msg);
        if (ok) {
            mc.process();
            c.sendln("RJ: " + msg);
            return mc.needClose();
        }
        return true;
    }


    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                log.debug("doing activity");
                term = doActivity();
            }
        }
        log.info("closing " + clientConns.size() + " connections");
        // clean up
        for (Connectivity c : clientConns) {
            c.close();
        }
        if (serverConn != null) {
            serverConn.close();
        }
        listener.terminate();
    }

    public boolean doActivity() {
        System.out.println("DoActivity!");
        return false;
    }

    public final void terminate() {
        term = true;
    }

//    public final ArrayList<Connection> getConnections() {
//        return connections;
//    }
}
