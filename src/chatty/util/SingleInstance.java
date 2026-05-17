
package chatty.util;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Tries to open a server socket on the given port to check if another instance
 * already did that, in which case it can send a message to the other instance.
 * Otherwise it will keep the server socket open and listen for messages from
 * other instances itself.
 * 
 * @author tduva
 */
public class SingleInstance {
    
    private static final Logger LOGGER = Logger.getLogger(SingleInstance.class.getName());
    
    private static NewInstanceListener listener;
    private static ServerSocket serverSocket;
    
    /**
     * Tries to register the current instance to the given port. The port is
     * used to listen for new instances in case registering succeeds (this is
     * the first instance and the port isn't yet taken by another program).
     * Otherwise no action is performed by this function, but you may use
     * {@link notifyRunningInstance(int, String)} to send info to the already
     * running instance.
     *
     * @param port The port to register
     * @return true if registering succeeded, false when an error occured (most
     * likely the port already taken and another instance already running)
     */
    public static boolean registerInstance(int port) {
        try {
            serverSocket = new ServerSocket(port, 0,
                    InetAddress.getLoopbackAddress());
            final ServerSocket server = serverSocket;
            Runnable connectionListener = () -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        handleConnection(socket);
                    } catch (IOException ex) {
                        LOGGER.warning("Error: "+ex);
                        break;
                    }
                }
            };
            new Thread(connectionListener).start();
            LOGGER.info("Registered port "+port);
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    public static void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                LOGGER.warning("Error closing server socket: "+ex);
            }
        }
    }
    
    /**
     * Handles an incoming connection from listening to the registered port.
     * Reads all received text and notifies the listener if there is one.
     *
     * @param socket The Socket to the client to receive data on
     */
    private static void handleConnection(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            int character;
            StringBuilder b = new StringBuilder();
            while ((character = reader.read()) != -1) {
                b.append((char)character);
            }
            LOGGER.info(String.format("Received instance message: %s [%s]",
                    b, socket));
            if (listener != null) {
                listener.newInstance(b.toString());
            }
        } catch (IOException ex) {
            LOGGER.warning("Error handling connection: "+ex);
        }
    }
    
    /**
     * Sends the given message to the given port, where the already running
     * instance should be listening on.
     * 
     * @param port The port to send the message to
     * @param message The message to send
     */
    public static void notifyRunningInstance(int port, String message) {
        try {
            LOGGER.info("Notifying already running instance: "+message);
            InetSocketAddress address = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), port);
            try (Socket connection = new Socket()) {
                connection.connect(address, 500);
                try (PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(
                                connection.getOutputStream(), StandardCharsets.UTF_8))) {
                    output.print(message);
                }
            }
        } catch (IOException ex) {
            LOGGER.warning("Error notifying instance: "+ex);
        }
    }
    
    /**
     * Sets the listener to send messages about new instances to.
     * 
     * @param listener 
     */
    public static void setNewInstanceListener(NewInstanceListener listener) {
        SingleInstance.listener = listener;
    }
    
    public interface NewInstanceListener {
        
        /**
         * Notifies about a message being received from a new instance.
         * 
         * @param message 
         */
        void newInstance(String message);
    }
    
    public static void main(String[] args) {
        // For testing
        
        int port = 12345;
        registerInstance(port);
        notifyRunningInstance(port, "{\"channel\":\"test\"}");
        
        NewInstanceListener listener = message -> System.out.println(message);
        setNewInstanceListener(listener);
    }
    
}
